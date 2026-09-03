#!/usr/bin/env bash
# Offline APK build for Rasgir (native Android, no androidx, no network).
# Usage: tools/offline-build/build.sh <module-dir> <out.apk>
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TC="$ROOT/.cache/toolchain"
JDK="$TC/jdk4py_extract/jdk4py/java-runtime"
KOTLINC="$TC/package/bin/kotlinc"
STDLIB="$(find "$TC/package/lib" -name 'kotlin-stdlib.jar' | head -1)"
AAPT2="$TC/final/aapt2"
ZIPALIGN="$TC/final/zipalign"
D8JAR="$TC/final/d8.jar"
APKSIGNER="$TC/final/apksigner.jar"
AJ="$TC/androidjars/android-35/android.jar"
KEY="$ROOT/deliverables/keys/rasgir-release.jks"
KSPASS="$(cat "$ROOT/deliverables/keys/keystore-pass.txt")"
export JAVA_HOME="$JDK"
export PATH="$JAVA_HOME/bin:$PATH"
export LD_LIBRARY_PATH="$TC/lib64"

MOD="$1"
OUT="$2"
SRC="$ROOT/$MOD/src/main"
CORE="$ROOT/corelib/src/main/kotlin"
TMP="$ROOT/.cache/build/$MOD"
rm -rf "$TMP"
mkdir -p "$TMP/gen" "$TMP/resc" "$TMP/classes" "$TMP/dex" "$TMP/apk"

echo "[1/6] resources"
find "$SRC/res" -name '*.xml' 2>/dev/null | while read -r f; do
  "$AAPT2" compile -o "$TMP/resc" "$f"
done
"$AAPT2" compile -o "$TMP/resc" "$SRC/res/drawable/ic_launcher.png"

echo "[2/6] link (base apk + R.java)"
PKG="$(grep -m1 'namespace' "$ROOT/$MOD/build.gradle.kts" | sed -E 's/.*namespace = "([^"]+)".*/\1/')"
python3 - "$PKG" "$SRC/AndroidManifest.xml" "$TMP/AndroidManifest.xml" <<'PY'
import sys
pkg, src, dst = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(src, encoding='utf-8').read()
s = s.replace('<manifest ', f'<manifest package="{pkg}" ', 1)
assert 'package=' in s
open(dst, 'w', encoding='utf-8').write(s)
PY

ARGS=()
for f in "$TMP"/resc/*; do ARGS+=("$f"); done
"$AAPT2" link -o "$TMP/apk/base.apk" -I "$AJ" --manifest "$TMP/AndroidManifest.xml" \
  --java "$TMP/gen" --min-sdk-version 26 --target-sdk-version 34 "${ARGS[@]}"

echo "[3/6] compile kotlin ($SRC + corelib)"
find "$SRC/kotlin" "$CORE" -name '*.kt' > "$TMP/sources.txt"
"$KOTLINC" -jvm-target 1.8 -no-stdlib -classpath "$AJ:$STDLIB" -d "$TMP/classes" @"$TMP/sources.txt" 2>"$TMP/compile.log" || {
  echo "kotlinc failed:"; grep -E 'error:' "$TMP/compile.log" | head -60; exit 1; }

echo "[4/6] dex"
# shellcheck disable=SC2046
"$JAVA_HOME/bin/java" -cp "$D8JAR" com.android.tools.r8.D8 --release --lib "$AJ" \
  --min-api 26 --output "$TMP/dex" \
  $(find "$TMP/classes" -name '*.class') "$STDLIB" 2>"$TMP/d8.log" || {
  echo "d8 failed:"; tail -30 "$TMP/d8.log"; exit 1; }
(cd "$TMP/dex" && zip -q -X "$TMP/apk/base.apk" classes.dex)

echo "[5/6] align"
"$ZIPALIGN" -f 4 "$TMP/apk/base.apk" "$TMP/aligned.apk"

echo "[6/6] sign"
"$JAVA_HOME/bin/java" -jar "$APKSIGNER" sign --ks "$KEY" --ks-pass pass:"$KSPASS" \
  --ks-key-alias rasgir --out "$OUT" "$TMP/aligned.apk"
"$JAVA_HOME/bin/java" -jar "$APKSIGNER" verify --print-certs "$OUT" >/dev/null
echo "OK → $OUT"
