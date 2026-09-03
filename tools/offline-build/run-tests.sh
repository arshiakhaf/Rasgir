#!/usr/bin/env bash
# Runs the full JVM test suite of corelib (414 checks) fully offline.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TC="$ROOT/.cache/toolchain"
JDK="$TC/jdk4py_extract/jdk4py/java-runtime"
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
export LD_LIBRARY_PATH="$TC/lib64"
STDLIB="$(find "$TC/package/lib" -name 'kotlin-stdlib.jar' | head -1)"
rm -rf /tmp/rasgir-tcore /tmp/rasgir-ttests
mkdir -p /tmp/rasgir-tcore /tmp/rasgir-ttests
"$TC/package/bin/kotlinc" -jvm-target 1.8 -d /tmp/rasgir-tcore $(find "$ROOT/corelib/src/main/kotlin" -name '*.kt')
"$TC/package/bin/kotlinc" -jvm-target 1.8 -cp /tmp/rasgir-tcore -d /tmp/rasgir-ttests $(find "$ROOT/tests/jvm/src" -name '*.kt')
java -cp /tmp/rasgir-ttests:/tmp/rasgir-tcore:"$STDLIB" ir.rasgir.test.TestMainKt | tee "$ROOT/test-report.txt"
