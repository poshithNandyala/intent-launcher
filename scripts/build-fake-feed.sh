#!/usr/bin/env bash
# Builds the smoke test's stand-in for YouTube Shorts (scripts/fake-feed) into out/fake-feed.apk.
# Test fixture only: never shipped. Run after ./build.sh (it reuses the tools that downloads).
set -eu
TOOLS=${TOOLS_DIR:-.tools}
SDK_JAR=""
[ -n "${ANDROID_HOME:-}" ] && [ -f "$ANDROID_HOME/platforms/android-34/android.jar" ] && SDK_JAR=$ANDROID_HOME/platforms/android-34/android.jar
[ -z "$SDK_JAR" ] && SDK_JAR=$TOOLS/android-34.jar
BUNDLETOOL=$(ls "$TOOLS"/bundletool-*.jar | head -1)
AAPT2=${AAPT2:-$TOOLS/aapt2-linux}
F=scripts/fake-feed
O=out/fake-feed
rm -rf "$O" && mkdir -p "$O/gen" "$O/classes" "$O/dex" "$O/signer"
"$AAPT2" compile --dir "$F/res" -o "$O/res.zip"
"$AAPT2" link -o "$O/base.apk" -I "$SDK_JAR" --manifest "$F/AndroidManifest.xml" \
    --min-sdk-version 26 --target-sdk-version 34 --java "$O/gen" "$O/res.zip"
javac -proc:none -Xlint:-options --release 8 -classpath "$SDK_JAR" -d "$O/classes" $(find "$O/gen" "$F/src" -name '*.java')
(cd "$O/classes" && jar cf ../app.jar .)
java -cp "$BUNDLETOOL" com.android.tools.r8.D8 --release --min-api 26 --lib "$SDK_JAR" --output "$O/dex" "$O/app.jar"
cp "$O/base.apk" "$O/unsigned.apk"
(cd "$O/dex" && zip -q ../unsigned.apk classes.dex)
keytool -genkeypair -alias fixture -keyalg RSA -keysize 2048 -validity 3650 -keystore "$O/fixture.p12" \
    -storetype PKCS12 -storepass fixture -keypass fixture -dname "CN=Fixture" > /dev/null 2>&1
javac -proc:none -Xlint:-options --release 8 -cp "$BUNDLETOOL" -d "$O/signer" buildtools/ApkSign.java
java -cp "$BUNDLETOOL:$O/signer" ApkSign sign "$O/unsigned.apk" out/fake-feed.apk "$O/fixture.p12" fixture fixture 26
ls -la out/fake-feed.apk
