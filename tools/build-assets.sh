#!/usr/bin/env bash
# Rebuilds the two files the phone app pushes to the projector and bundles them into the app's assets:
#   app/src/main/assets/projector/ksall.jar   the keystone helper (runs on the projector via app_process)
#   app/src/main/assets/projector/pattern.apk the full-screen test-pattern app
# Needs: JDK 17, Android SDK (platform 34, build-tools 34.0.0).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdks/temurin17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
BT="$ANDROID_HOME/build-tools/34.0.0"
OUT=app/src/main/assets/projector
mkdir -p "$OUT" build/helper/classes build/helper/dex

echo "== helper jar"
rm -rf build/helper/classes/* build/helper/dex/*
javac -source 8 -target 8 -d build/helper/classes projector/helper/KsAll.java 2>&1 | grep -v "warning" || true
"$BT/d8" --lib "$ANDROID_HOME/platforms/android-34/android.jar" --output build/helper/dex build/helper/classes/*.class
rm -f "$OUT/ksall.jar"
(cd build/helper/dex && zip -q -X "$PWD/../ksall.jar" classes.dex)
cp build/helper/ksall.jar "$OUT/ksall.jar"

echo "== pattern app"
./gradlew :pattern:assembleDebug -q --console=plain --offline
cp pattern/build/outputs/apk/debug/pattern-debug.apk "$OUT/pattern.apk"

ls -l "$OUT"
