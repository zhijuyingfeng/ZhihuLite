#!/usr/bin/env bash
# Typecheck the app's main sources without Gradle.
#
# Why this exists: this sandbox cannot run the Gradle wrapper (needs to write
# ~/.gradle and to download the 8.9 distribution). The Kotlin compiler, the
# Android SDK and every dependency AAR are already in the local caches, so we
# assemble a classpath by hand and invoke the compiler directly. This gives a
# real compile of real sources — not a linter.
#
# Usage:  bash tools/typecheck.sh
# Exit code 0 == the main source set compiles.

set -uo pipefail
cd "$(dirname "$0")/.."

MODULES="$HOME/.gradle/caches/modules-2/files-2.1"
TRANSFORMS="$HOME/.gradle/caches/8.9/transforms"
ANDROID_JAR="$HOME/Android/Sdk/platforms/android-35/android.jar"
R_JAR="app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
OUT_DIR="${OUT_DIR:-build/typecheck-out}"
STUB_DIR="build/typecheck-stubs"

j() { find "$MODULES" -name "$1" 2>/dev/null | grep -v -- '-sources' | grep -v -- '-javadoc' | sort | tail -1; }

KC=$(j 'kotlin-compiler-embeddable-2.1.21.jar')
STDLIB=$(j 'kotlin-stdlib-2.1.21.jar')
REFLECT=$(j 'kotlin-reflect-2.1.21.jar')
SCRIPT=$(j 'kotlin-script-runtime-2.1.21.jar')
DAEMON=$(j 'kotlin-daemon-embeddable-2.1.21.jar')
TROVE=$(j 'trove4j-1.0.20200330.jar')
ANN=$(j 'annotations-23.0.0.jar')
COROUTINES=$(j 'kotlinx-coroutines-core-jvm-*.jar')
SER_CORE=$(j 'kotlinx-serialization-core-jvm-*.jar')
SER_JSON=$(j 'kotlinx-serialization-json-jvm-*.jar')
SER_PLUGIN=$(j 'kotlin-serialization-compiler-plugin-embeddable-2.1.21.jar')
COMPOSE_PLUGIN=$(j 'kotlin-compose-compiler-plugin-embeddable-2.1.21.jar')
COL_IMM=$(j 'kotlinx-collections-immutable-jvm-*.jar')
LIFECYCLE_RT=$(j 'lifecycle-runtime-*.aar')
LIFECYCLE_COMMON=$(j 'lifecycle-common-*.jar')
LIFECYCLE_VM=$(j 'lifecycle-viewmodel-*.aar')
LIFECYCLE_RT_COMPOSE=$(j 'lifecycle-runtime-compose-*.aar')

for v in KC STDLIB REFLECT SCRIPT DAEMON TROVE ANN COROUTINES SER_CORE SER_JSON SER_PLUGIN COMPOSE_PLUGIN COL_IMM; do
  [ -n "${!v}" ] || { echo "FATAL: could not locate $v in $MODULES"; exit 2; }
done

# AARs are archives; extract classes.jar for the ones we need.
AAR_DIR="build/typecheck-aars"
mkdir -p "$AAR_DIR"
for aar in "$LIFECYCLE_RT" "$LIFECYCLE_VM" "$LIFECYCLE_RT_COMPOSE"; do
  [ -n "$aar" ] || continue
  name=$(basename "$aar" .aar)
  if [ ! -f "$AAR_DIR/$name/classes.jar" ]; then
    mkdir -p "$AAR_DIR/$name"
    (cd "$AAR_DIR/$name" && unzip -o -q "$aar" classes.jar 2>/dev/null)
  fi
done
AAR_JARS=$(find "$AAR_DIR" -name classes.jar 2>/dev/null | tr '\n' ':')

# Stub the KSP-generated Gaia registry (generated at real build time).
rm -rf "$STUB_DIR"; mkdir -p "$STUB_DIR/com/nigao/gaia"
cat > "$STUB_DIR/com/nigao/gaia/GaiaStub.kt" <<'KOTLIN'
package com.nigao.gaia

class GaiaEvent(val key: String = "", val payload: Any? = null) {
    fun <T> getPayloadAs(clazz: Class<T>): T? = payload as? T
}

annotation class GaiaListen(val key: String)

object GaiaEventManager {
    fun registerEventObserver(key: String, callback: (GaiaEvent?) -> Unit) {}
    fun start(event: GaiaEvent) {}
}

fun registerAll() {}
KOTLIN

# The cached R.jar (from an earlier processDebugResources) predates resources added
# since, so regenerate R from the current res/ instead of trusting it.
GEN_DIR="build/typecheck-gen"
rm -rf "$GEN_DIR"; mkdir -p "$GEN_DIR"
python3 tools/gen_r_class.py app/src/main/res "$GEN_DIR/org/nigao/zhihuLite/R.java" org.nigao.zhihuLite || exit 2
javac -d "$GEN_DIR/classes" "$GEN_DIR/org/nigao/zhihuLite/R.java" || exit 2
R_CLASSES="$GEN_DIR/classes"

CP=$(find "$TRANSFORMS" -name classes.jar 2>/dev/null | tr '\n' ':')
# Ktor ships plain JVM jars (no AAR), so it is absent from the transform output.
KTOR_JARS=$(find "$MODULES/io.ktor" -name '*-jvm-*.jar' 2>/dev/null | grep -v -- '-sources' | tr '\n' ':')
# Same for a few other library deps that are plain jars.
EXTRA_JARS=$(find "$MODULES" \( -name 'napier-jvm-*.jar' -o -name 'multiplatform-settings-jvm-*.jar' -o -name 'coil-core-jvm-*.jar' -o -name 'coil-network-core-jvm-*.jar' \) 2>/dev/null | grep -v -- '-sources' | tr '\n' ':')
PROJ_CP="${CP}${KTOR_JARS}${EXTRA_JARS}${AAR_JARS}${STDLIB}:${COROUTINES}:${SER_CORE}:${SER_JSON}:${COL_IMM}:${LIFECYCLE_COMMON}:${R_CLASSES}:${ANDROID_JAR}"

rm -rf "$OUT_DIR"; mkdir -p "$OUT_DIR"
find app/src/main/kotlin -name '*.kt' > "$OUT_DIR/sources.txt"
# Test sources are compiled against the same classpath plus the JUnit/kotlin-test jars.
find app/src/test/java -name '*.kt' >> "$OUT_DIR/sources.txt"
TEST_JARS=$(find "$MODULES" \( -name 'junit-4*.jar' -o -name 'kotlin-test-junit-2.1.21.jar' -o -name 'kotlin-test-2.1.21.jar' -o -name 'hamcrest-core-*.jar' \) 2>/dev/null | grep -v -- '-sources' | tr '\n' ':')
PROJ_CP="${PROJ_CP}:${TEST_JARS}"

echo "sources: $(wc -l < "$OUT_DIR/sources.txt")  classpath entries: $(echo "$PROJ_CP" | tr ':' '\n' | wc -l)"

java -Xmx3g -cp "$KC:$STDLIB:$REFLECT:$SCRIPT:$DAEMON:$TROVE:$ANN:$COROUTINES" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect \
  -classpath "$PROJ_CP" \
  -jvm-target 11 \
  -Xplugin="$SER_PLUGIN" \
  -Xplugin="$COMPOSE_PLUGIN" \
  -d "$OUT_DIR" \
  @"$OUT_DIR/sources.txt" "$STUB_DIR" app/build/generated/source/buildConfig/debug \
  2>&1 | tee "$OUT_DIR/compiler.log" | grep -E "error:|warning: .*(unresolved|deprecat)" | head -60

echo "----"
# Count only real diagnostics ("<file>:<line>:<col>: error: ..."), not the word
# "error" inside a message, so the number is trustworthy as a gate.
ERRORS=$(grep -cE '^[^ ]+:[0-9]+:[0-9]+: error:' "$OUT_DIR/compiler.log")
echo "errors: $ERRORS"
exit $(( ERRORS > 0 ? 1 : 0 ))
