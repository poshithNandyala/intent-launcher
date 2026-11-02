#!/usr/bin/env bash
# Builds Intent WITHOUT Gradle or Android Studio. Needs only JDK 17+, curl, unzip and zip.
#
#   ./build.sh          -> out/Intent-<version>.apk  (signed, ready to install)
#   ./build.sh test     -> runs the unit tests for the rules engine (pure Kotlin, no phone needed)
#
# First run downloads the tools into .tools/ (all from GitHub, ~150 MB):
#   Kotlin compiler (JetBrains/kotlin), bundletool (google/bundletool: provides aapt2, R8/D8 and apksig)
#   and the public Android 14 SDK jar (Sable/android-platforms).
# If $ANDROID_HOME has platforms/android-34, its android.jar is used instead.
#
# Signing key: keystore/intent.p12 (+ keystore/password.txt), created on first build.
# BACK THEM UP and never commit them: Android only installs an update if it is signed with the same key.
# CI can pass the key with INTENT_KEYSTORE_B64 and INTENT_KEY_PASS.
set -euo pipefail
cd "$(dirname "$0")"

APP_ID=com.poshith.intent
MIN_SDK=26
TARGET_SDK=36
VERSION_CODE=11
VERSION_NAME=1.5.1

KOTLIN_VERSION=2.0.21
BUNDLETOOL_VERSION=1.18.1
TOOLS=${TOOLS_DIR:-.tools}
OUT=out
SRC=app/src/main

quiet() { grep -v -E "JAVA_TOOL_OPTIONS|^warning: \[options\]|^[0-9]+ warnings?$|^Note:" || true; }
need() { command -v "$1" >/dev/null 2>&1 || { echo "missing tool: $1"; exit 1; }; }
need java; need javac; need curl; need unzip; need zip

fetch() { # url dest
    [ -s "$2" ] && return 0
    echo "  downloading $(basename "$2")"
    mkdir -p "$(dirname "$2")"
    curl -fsSL --retry 3 -o "$2.part" "$1" && mv "$2.part" "$2"
}

# ---------------------------------------------------------------- tools
echo "[0/7] tools"
mkdir -p "$TOOLS"
# Code and resources are compiled against the Android 14 (API 34) SDK jar, even though the app
# targets Android 16: the bundled R8 predates Android 15 and, given a newer jar, can rewrite library
# calls to use types that older phones don't have (java.util.SequencedCollection crashed Android 11).
# Nothing in Intent needs an API newer than 34 (compiling against 34 enforces it).
COMPILE_SDK=34
SDK_JAR=${SDK_JAR:-}
AAPT2=${AAPT2:-}
if [ -z "$SDK_JAR" ] && [ -n "${ANDROID_HOME:-}" ] && [ -f "$ANDROID_HOME/platforms/android-$COMPILE_SDK/android.jar" ]; then
    SDK_JAR=$ANDROID_HOME/platforms/android-$COMPILE_SDK/android.jar
fi
if [ -z "$SDK_JAR" ]; then
    SDK_JAR=$TOOLS/android-$COMPILE_SDK.jar
    fetch "https://raw.githubusercontent.com/Sable/android-platforms/master/android-$COMPILE_SDK/android.jar" "$SDK_JAR"
fi
RES_JAR=$SDK_JAR
BUNDLETOOL=$TOOLS/bundletool-$BUNDLETOOL_VERSION.jar
fetch "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar" "$BUNDLETOOL"
if [ -z "$AAPT2" ]; then
    case "$(uname -s)" in Darwin) OS=macos ;; *) OS=linux ;; esac
    AAPT2=$TOOLS/aapt2-$OS
    if [ ! -x "$AAPT2" ]; then
        (cd "$TOOLS" && unzip -o -q "bundletool-$BUNDLETOOL_VERSION.jar" "$OS/aapt2" && mv "$OS/aapt2" "aapt2-$OS" && rmdir "$OS")
        chmod +x "$AAPT2"
    fi
fi
KOTLINC_HOME=$TOOLS/kotlinc-$KOTLIN_VERSION
if [ ! -x "$KOTLINC_HOME/bin/kotlinc" ]; then
    fetch "https://github.com/JetBrains/kotlin/releases/download/v$KOTLIN_VERSION/kotlin-compiler-$KOTLIN_VERSION.zip" "$TOOLS/kotlinc.zip"
    rm -rf "$TOOLS/kotlinc" && (cd "$TOOLS" && unzip -q kotlinc.zip) && mv "$TOOLS/kotlinc" "$KOTLINC_HOME" && rm "$TOOLS/kotlinc.zip"
fi
KOTLINC=$KOTLINC_HOME/bin/kotlinc
STDLIB=$KOTLINC_HOME/lib/kotlin-stdlib.jar
KFLAGS=(-jvm-target 1.8 -no-reflect -Xno-param-assertions -Xno-call-assertions -Xno-receiver-assertions -nowarn)

# ---------------------------------------------------------------- unit tests
if [ "${1:-}" = "test" ]; then
    rm -rf "$OUT/test" && mkdir -p "$OUT/test"
    echo "[test] compiling core + tests"
    "$KOTLINC" "${KFLAGS[@]}" -d "$OUT/test" "$SRC/java/com/poshith/intent/core" app/src/test/java 2>&1 | quiet
    java -cp "$OUT/test:$STDLIB" com.poshith.intent.core.TestsKt 2>&1 | quiet
    exit 0
fi

# ---------------------------------------------------------------- signing key
KEYSTORE=keystore/intent.p12
ALIAS=intent
if [ -n "${INTENT_KEYSTORE_B64:-}" ]; then
    mkdir -p keystore && echo "$INTENT_KEYSTORE_B64" | base64 -d > "$KEYSTORE"
fi
if [ ! -f "$KEYSTORE" ]; then
    echo "  creating a new signing key in keystore/  (BACK IT UP: every update must be signed with it)"
    mkdir -p keystore
    head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32 > keystore/password.txt
    chmod 600 keystore/password.txt
    KP=$(cat keystore/password.txt)
    keytool -genkeypair -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
        -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$KP" -keypass "$KP" \
        -dname "CN=Intent, O=Intent Launcher" 2>&1 | quiet
fi
KEY_PASS=${INTENT_KEY_PASS:-$(cat keystore/password.txt 2>/dev/null || true)}
[ -n "$KEY_PASS" ] || { echo "no key password: put it in keystore/password.txt or INTENT_KEY_PASS"; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/signer"

# ---------------------------------------------------------------- resources
echo "[1/7] resources"
sed "s|<manifest |<manifest package=\"$APP_ID\" |" "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$AAPT2" compile --dir "$SRC/res" -o "$OUT/res.zip"
"$AAPT2" link -o "$OUT/base.apk" -I "$RES_JAR" --manifest "$OUT/AndroidManifest.xml" \
    --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
    --version-code $VERSION_CODE --version-name $VERSION_NAME \
    -A "$SRC/assets" --java "$OUT/gen" --proguard "$OUT/aapt-rules.pro" \
    "$OUT/res.zip"

# ---------------------------------------------------------------- code
echo "[2/7] R.java"
javac -proc:none -Xlint:-options -encoding UTF-8 --release 8 -classpath "$SDK_JAR" -d "$OUT/classes" \
    $(find "$OUT/gen" -name '*.java') 2>&1 | quiet

echo "[3/7] Kotlin"
"$KOTLINC" "${KFLAGS[@]}" -classpath "$SDK_JAR:$OUT/classes" -d "$OUT/classes" "$SRC/java" 2>&1 | quiet
test -f "$OUT/classes/com/poshith/intent/HomeActivity.class" || { echo "Kotlin compile failed"; exit 1; }
(cd "$OUT/classes" && jar cf ../app.jar .)

echo "[4/7] dex (R8 shrinks the Kotlin runtime to what the app uses)"
if [ "${NO_R8:-}" != "1" ]; then
    java -cp "$BUNDLETOOL" com.android.tools.r8.R8 --release --min-api $MIN_SDK \
        --lib "$SDK_JAR" --pg-conf app/proguard-rules.pro --pg-conf buildtools/r8-legacy.pro --pg-conf "$OUT/aapt-rules.pro" \
        --output "$OUT/dex" "$OUT/app.jar" "$STDLIB" > "$OUT/r8.log" 2>&1 || true
fi
if [ ! -f "$OUT/dex/classes.dex" ]; then
    [ "${NO_R8:-}" != "1" ] && { echo "  R8 failed, falling back to D8 (details: $OUT/r8.log)"; tail -5 "$OUT/r8.log" | quiet; }
    rm -rf "$OUT/dex" && mkdir -p "$OUT/dex"
    java -cp "$BUNDLETOOL" com.android.tools.r8.D8 --release --min-api $MIN_SDK --lib "$SDK_JAR" \
        --output "$OUT/dex" "$OUT/app.jar" "$STDLIB" 2>&1 | quiet
fi
test -f "$OUT/dex/classes.dex"

# ---------------------------------------------------------------- package + sign
echo "[5/7] packaging"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -9 ../unsigned.apk classes*.dex)
if command -v zipalign >/dev/null 2>&1; then
    zipalign -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk" && mv "$OUT/aligned.apk" "$OUT/unsigned.apk"
fi

echo "[6/7] signing (APK Signature Scheme v2 + v3)"
javac -proc:none -Xlint:-options --release 8 -cp "$BUNDLETOOL" -d "$OUT/signer" buildtools/ApkSign.java 2>&1 | quiet
APK="$OUT/Intent-$VERSION_NAME.apk"
java -cp "$BUNDLETOOL:$OUT/signer" ApkSign sign "$OUT/unsigned.apk" "$APK" "$KEYSTORE" "$ALIAS" "$KEY_PASS" $MIN_SDK 2>&1 | quiet

echo "[7/7] verifying"
java -cp "$BUNDLETOOL:$OUT/signer" ApkSign verify "$APK" 2>&1 | quiet
rm -f "$OUT/unsigned.apk" "$OUT/base.apk" "$OUT/res.zip"
ls -la "$APK"
