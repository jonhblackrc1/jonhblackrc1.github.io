#!/usr/bin/env bash
# Compila Duelo de Barras sin Gradle, con las herramientas de Android de Ubuntu:
#   sudo apt-get install aapt apksigner android-sdk-platform-23 dalvik-exchange zipalign
# La llave de firma (debug.keystore) se crea sola la primera vez.
set -euo pipefail
cd "$(dirname "$0")"
ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$ANDROID_JAR"
javac -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -encoding UTF-8 \
  -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')
dalvik-exchange --dex --output="$OUT/classes.dex" "$OUT/classes"

aapt package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -0 arsc -0 wav -F "$OUT/unsigned.apk"
(cd "$OUT" && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS=${KEYSTORE:-debug.keystore}
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias duelo \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Black Studio" >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass pass:${KS_PASS:-android} --ks-key-alias ${KS_ALIAS:-duelo} \
  --out ../duelo.apk "$OUT/aligned.apk"
rm -f ../duelo.apk.idsig
apksigner verify --print-certs ../duelo.apk | head -1
echo "APK lista: $(cd .. && pwd)/duelo.apk"
