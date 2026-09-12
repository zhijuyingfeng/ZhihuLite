#!/usr/bin/env bash
# Compile every source set and run the JVM unit tests, without Gradle.
#
# The sandbox cannot run the Gradle wrapper (it needs to write ~/.gradle and to
# download the Gradle 8.9 distribution). Everything Gradle would resolve is
# already in the local caches, so this script reproduces the two steps that
# matter for verification:
#
#   1. compile app/src/main/kotlin + app/src/test/java with the Compose and
#      serialization compiler plugins (tools/typecheck.sh already does this)
#   2. run the resulting test classes with the JUnit 4 console runner
#
# Usage:   bash tools/run_unit_tests.sh
# Exit 0 == sources compiled AND all tests passed.
#
# Caveats (deliberate, so results are not over-trusted):
#   * Android framework classes come from the SDK's android.jar stub, which
#     throws at runtime. Tests that touch Android APIs cannot pass here; they
#     are expected to be instrumented tests instead.
#   * The KSP-generated Gaia registry is replaced by tools/typecheck.sh stubs.
#   * The minified (release/R8) path is NOT covered by any of this.

set -uo pipefail
cd "$(dirname "$0")/.."

OUT_DIR="build/typecheck-out"
MAIN_OUT="build/testrun-main"
TEST_OUT="build/testrun-test"

echo "=== step 1/2: compile main + test sources ==="
bash tools/typecheck.sh || { echo "COMPILE FAILED — not running tests"; exit 1; }

MODULES="$HOME/.gradle/caches/modules-2/files-2.1"
TRANSFORMS="$HOME/.gradle/caches/8.9/transforms"
j() { find "$MODULES" -name "$1" 2>/dev/null | grep -v -- '-sources' | grep -v -- '-javadoc' | sort | tail -1; }

STDLIB=$(j 'kotlin-stdlib-2.1.21.jar')
COROUTINES=$(j 'kotlinx-coroutines-core-jvm-*.jar')
SER_CORE=$(j 'kotlinx-serialization-core-jvm-*.jar')
SER_JSON=$(j 'kotlinx-serialization-json-jvm-*.jar')
COL_IMM=$(j 'kotlinx-collections-immutable-jvm-*.jar')
LIFECYCLE_COMMON=$(j 'lifecycle-common-*.jar')
JUNIT=$(j 'junit-4*.jar')
HAMCREST=$(j 'hamcrest-core-*.jar')
KTEST=$(j 'kotlin-test-2.1.21.jar')
KTEST_JUNIT=$(j 'kotlin-test-junit-2.1.21.jar')
ANDROID_JAR="$HOME/Android/Sdk/android.jar"
[ -f "$ANDROID_JAR" ] || ANDROID_JAR="$HOME/Android/Sdk/platforms/android-35/android.jar"

for v in STDLIB COROUTINES JUNIT HAMCREST KTEST KTEST_JUNIT; do
  [ -n "${!v}" ] || { echo "FATAL: missing $v in caches"; exit 2; }
done

CP=$(find "$TRANSFORMS" -name classes.jar 2>/dev/null | tr '\n' ':')
KTOR_JARS=$(find "$MODULES/io.ktor" -name '*-jvm-*.jar' 2>/dev/null | grep -v -- '-sources' | tr '\n' ':')
EXTRA_JARS=$(find "$MODULES" \( -name 'napier-jvm-*.jar' -o -name 'multiplatform-settings-jvm-*.jar' -o -name 'coil-core-jvm-*.jar' -o -name 'coil-network-core-jvm-*.jar' \) 2>/dev/null | grep -v -- '-sources' | tr '\n' ':')
AAR_JARS=$(find build/typecheck-aars -name classes.jar 2>/dev/null | tr '\n' ':')

RUN_CP="${OUT_DIR}:${CP}${KTOR_JARS}${EXTRA_JARS}${AAR_JARS}${STDLIB}:${COROUTINES}:${SER_CORE}:${SER_JSON}:${COL_IMM}:${LIFECYCLE_COMMON}:${JUNIT}:${HAMCREST}:${KTEST}:${KTEST_JUNIT}:${ANDROID_JAR}"

# Discover test classes: look for compiled classes with @Test methods.
echo "=== step 2/2: run JVM unit tests ==="
CLASSES=$(find "$OUT_DIR" -name '*Test.class' 2>/dev/null | sed "s|^$OUT_DIR/||; s|\\.class$||; s|/|.|g" | sort)
if [ -z "$CLASSES" ]; then
  echo "no test classes found under $OUT_DIR"
  exit 1
fi
echo "test classes:"
echo "$CLASSES" | sed 's/^/  /'

FAILED=0
for c in $CLASSES; do
  echo
  echo "----- $c -----"
  java -cp "$RUN_CP" org.junit.runner.JUnitCore "$c"
  rc=$?
  if [ $rc -ne 0 ]; then FAILED=$((FAILED + 1)); fi
done

echo
echo "======================================"
if [ $FAILED -eq 0 ]; then
  echo "RESULT: all test classes passed"
  exit 0
else
  echo "RESULT: $FAILED test class(es) FAILED"
  exit 1
fi
