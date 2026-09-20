#!/usr/bin/env bash
set -e
export JAVA_HOME=/home/beelinkser5max/Descargas/android-studio/jbr

./gradlew assembleDebug

mkdir -p app/build/outputs/apk/debug
cp app/build/outputs/apk/debug/app-debug.apk \
   app/build/outputs/apk/debug/ScoSecretariaV0.1-debug.apk

adb wait-for-device
adb install -r app/build/outputs/apk/debug/ScoSecretariaV0.1-debug.apk
echo "ScoSecretariaV0.1 instalada correctamente."
