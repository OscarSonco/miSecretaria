#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/home/beelinkser5max/Descargas/android-studio/jbr
# El lanzador .desktop no carga .bashrc/nvm, así que forzamos el PATH con las rutas reales.
export PATH="/home/beelinkser5max/.nvm/versions/node/v22.23.2/bin:/usr/bin:$PATH"

VERSION_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)
VERSION_CODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' app/build.gradle.kts)
TAG="v${VERSION_NAME}"
APK_NAME="miSecretariaV${VERSION_NAME}-debug.apk"
REPO="OscarSonco/miSecretaria"

echo "== Compilando miSecretaria v${VERSION_NAME} (code ${VERSION_CODE}) =="
./gradlew assembleDebug
mkdir -p Releases public
cp app/build/outputs/apk/debug/app-debug.apk "Releases/${APK_NAME}"

echo "== Creando Release en GitHub: ${TAG} =="
gh release create "${TAG}" "Releases/${APK_NAME}" \
  --repo "${REPO}" \
  --title "miSecretaria ${TAG}" \
  --notes "Build automática ${TAG}"

APK_URL="https://github.com/${REPO}/releases/download/${TAG}/${APK_NAME}"

echo "== Actualizando update.json =="
cat > public/update.json << EOF
{
  "versionCode": ${VERSION_CODE},
  "versionName": "${VERSION_NAME}",
  "apkUrl": "${APK_URL}",
  "notes": "Nueva versión disponible: ${VERSION_NAME}"
}
EOF
chmod 644 public/update.json public/index.html public/404.html 2>/dev/null || true

echo "== Desplegando en Firebase Hosting =="
firebase deploy --only hosting

echo "== Commit y push =="
git add -A
git commit -m "Release ${TAG}" || echo "(nada nuevo que commitear)"
git push

echo ""
echo "✅ Listo: ${TAG} publicada. URL del APK: ${APK_URL}"
