#!/usr/bin/env bash
# One command to verify the project: prefer real Gradle, fall back to the manual chain.
#
# Why this exists: on a normal machine `./gradlew :app:testDebugUnitTest` just works. In a
# sandboxed/offline environment the wrapper can fail two different ways (it needs to write
# ~/.gradle, and it may want to download the Gradle distribution), while a local Gradle
# installation plus the read-only dependency cache works fine. This script picks the best
# available path so "verify the change" is always a single command.
#
# Usage:  bash tools/verify.sh
# Exit 0 == everything compiled and all JVM unit tests passed.
#
# It also reports which path was used, because they do NOT have equal strength:
#   * gradle   — authoritative: KSP codegen, resource processing, real task graph.
#   * fallback — fast feedback only: manual classpath, generated R, stubbed Gaia registry,
#                and it cannot exercise the R8/minified release path at all.

set -uo pipefail
cd "$(dirname "$0")/.."

# Prefer the project's own wrapper: it works whenever ~/.gradle is writable, which is the normal
# case. Only fall back to a pre-extracted distribution plus a workspace-local Gradle home when the
# wrapper genuinely cannot run (read-only ~/.gradle in a locked-down sandbox).
GRADLE_CMD=()
if [ -x "./gradlew" ]; then GRADLE_CMD=("./gradlew"); fi
GRADLE_DIST="${GRADLE_DIST:-$HOME/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle}"
GRADLE_HOME_WORKSPACE=".gradle-dsh/home2"
JDK17="/usr/lib/jvm/java-17-openjdk-amd64"

run_gradle() {
  # JDK 17 is required; Gradle 8.9 does not support the JDK 25 that may be the default here.
  local java_home="${JAVA_HOME:-$JDK17}"
  [ -x "$java_home/bin/java" ] || return 1

  local log="build/verify-gradle.log"
  mkdir -p build

  # Another Gradle instance (e.g. an agent working in parallel) can hold the default
  # .gradle/configuration-cache lock. Point the project cache somewhere private so concurrent
  # runs do not serialize on it, and retry once if the lock is somehow still taken.
  local attempt
  for attempt in 1 2; do
    if [ ${#GRADLE_CMD[@]} -gt 0 ]; then
      # Normal path: project wrapper + the real Gradle User Home, and network allowed in case a
      # dependency needs resolving. JDK 17 is exported because Gradle 8.9 rejects the default JDK 25.
      JAVA_HOME="$java_home" "${GRADLE_CMD[@]}" :app:testDebugUnitTest --console=plain > "$log" 2>&1
    else
      JAVA_HOME="$java_home" \
      XDG_DATA_HOME="$PWD/$GRADLE_HOME_WORKSPACE/xdg-data" \
      GRADLE_USER_HOME="$PWD/$GRADLE_HOME_WORKSPACE" \
      GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches" \
      "$GRADLE_DIST" :app:testDebugUnitTest --console=plain --offline \
        --project-cache-dir=build/verify-project-cache > "$log" 2>&1
    fi
    local gradle_rc=$?
    tail -25 "$log"
    if grep -q 'Timeout waiting to lock' "$log" && [ $attempt -eq 1 ]; then
      echo
      echo "gradle lock contended; retrying in 20s..."
      sleep 20
      continue
    fi
    break
  done

  # The build must actually have succeeded. Checking only the XML reports is wrong:
  # test-results from an earlier run survive a failed build, so a lock timeout or a
  # compile error would otherwise be reported as a pass (this happened once).
  if [ $gradle_rc -ne 0 ] || ! grep -q 'BUILD SUCCESSFUL' "$log"; then
    echo
    echo "gradle did not report BUILD SUCCESSFUL (rc=$gradle_rc)"
    grep -E 'What went wrong|Timeout waiting to lock|FAILURE:|error:' "$log" | head -10
    return 1
  fi

  local xml_dir="app/build/test-results/testDebugUnitTest"
  [ -d "$xml_dir" ] || { echo "no test report at $xml_dir"; return 1; }
  python3 - "$xml_dir" <<'PY'
import glob, re, sys
tests = failures = errors = 0
for path in glob.glob(sys.argv[1] + "/*.xml"):
    head = open(path, encoding="utf-8").read(4000)
    tests += int(re.search(r'tests="(\d+)"', head).group(1))
    failures += int(re.search(r'failures="(\d+)"', head).group(1))
    errors += int(re.search(r'errors="(\d+)"', head).group(1))
print(f"\nGRADLE RESULT: tests={tests} failures={failures} errors={errors}")
sys.exit(1 if (failures or errors or tests == 0) else 0)
PY
}

if [ ${#GRADLE_CMD[@]} -gt 0 ] || { [ -x "$GRADLE_DIST" ] && [ -d "$GRADLE_HOME_WORKSPACE" ]; }; then
  echo "=== verification path: GRADLE (authoritative) ==="
  if run_gradle; then
    echo
    echo "VERIFY PASSED (gradle)"
    exit 0
  fi
  echo
  echo "Gradle path failed; falling back to the manual compile+test chain."
else
  echo "=== verification path: manual fallback (no usable Gradle install) ==="
  echo "    gradle dist: $GRADLE_DIST"
  echo "    gradle home: $GRADLE_HOME_WORKSPACE"
fi

if bash tools/run_unit_tests.sh; then
  echo
  echo "VERIFY PASSED (fallback; R8/release path NOT covered)"
  exit 0
fi

echo
echo "VERIFY FAILED"
exit 1
