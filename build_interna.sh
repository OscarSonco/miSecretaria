#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/home/beelinkser5max/Descargas/android-studio/jbr

if [ ! -f secrets.properties ]; then
  echo "Falta secrets.properties (copiar secrets.properties.example y rellenar botToken/chatId)."
  exit 1
fi

VERSION_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)
APK_NAME="miSecretariaV${VERSION_NAME}-debug_Interna.apk"

echo "== Compilando build INTERNA v${VERSION_NAME} (token/Chat ID pre-rellenados) =="
./gradlew assembleDebug -PincludeSecrets=true
mkdir -p Releases
cp app/build/outputs/apk/debug/app-debug.apk "Releases/${APK_NAME}"

echo ""
echo "✅ Listo: Releases/${APK_NAME}"
echo "⚠️  Este APK trae tu Token y Chat ID de Telegram en texto plano. NO lo subas a GitHub"
echo "    Releases ni a ningún canal público — pásalo solo a mano (USB, Bluetooth) a los"
echo "    teléfonos de tus sucursales. Nunca se commitea ni forma parte del release público."
