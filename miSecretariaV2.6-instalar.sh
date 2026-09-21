#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/home/beelinkser5max/Descargas/android-studio/jbr
./gradlew assembleDebug
mkdir -p Releases
cp app/build/outputs/apk/debug/app-debug.apk Releases/miSecretariaV2.6-debug.apk
adb wait-for-device
adb install -r Releases/miSecretariaV2.6-debug.apk
echo 'miSecretariaV2.6 instalada correctamente.'
