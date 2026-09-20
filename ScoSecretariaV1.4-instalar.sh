#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/home/beelinkser5max/Descargas/android-studio/jbr
./gradlew assembleDebug
mkdir -p app/build/outputs/apk/debug
cp app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/debug/ScoSecretariaV1.4-debug.apk
adb wait-for-device
adb install -r app/build/outputs/apk/debug/ScoSecretariaV1.4-debug.apk
echo 'ScoSecretariaV1.4 instalada correctamente.'
