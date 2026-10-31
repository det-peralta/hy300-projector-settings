#!/usr/bin/env bash
# Builds Projector.apk without Gradle: aapt2 + javac + d8 + zipalign + apksigner.
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-34/android.jar
KS=${KEYSTORE:-$HOME/.android/debug.keystore}
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/res" "$OUT/classes"

if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

"$BT/aapt2" compile --dir res -o "$OUT/res/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -o "$OUT/unsigned.apk" "$OUT/res/res.zip"
javac --release 11 -XDstringConcat=inline -classpath "$JAR" -d "$OUT/classes" $(find src -name '*.java') 2>&1 | grep -v "^warning: \[options\]" || true
[ -n "$(find "$OUT/classes" -name '*.class')" ] || { echo "javac failed"; exit 1; }
"$BT/d8" --min-api 26 --lib "$JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
(cd "$OUT" && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$OUT/Projector.apk" "$OUT/aligned.apk"
echo "built $OUT/Projector.apk"
