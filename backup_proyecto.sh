#!/bin/bash
# backup_proyecto.sh — Empaqueta TODO el proyecto (código, secrets.properties con el Token/Chat
# ID real de Telegram, miSecretaria.db y miSecretaria.log) en un único .tar.gz portable, listo
# para copiar a otra máquina y descomprimir ahí — como un "Firefox portable": la sesión (tu
# Token/Chat ID, y el historial acumulado en miSecretaria.db) viaja junto con el código, sin
# tener que volver a configurar nada a mano en la máquina nueva.
#
# Excluye: /Archivo (para no incluirse a sí mismo ni backups anteriores), .git (ya está en
# GitHub — en la máquina nueva es más simple clonar de ahí y solo copiar encima los archivos no
# versionados de este backup), .claude (datos de sesión de Claude Code, no es parte del
# proyecto), app/build y .gradle (se regeneran solos al compilar), Releases (esos APK ya están
# en GitHub Releases y se regeneran con build_interna.sh/release.sh).

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DESTINO_DIR="$DIR/Archivo"
TS=$(date +%Y-%m-%d_%H-%M-%S)
TMPBASE=$(mktemp -d)
STAGING="$TMPBASE/miSecretaria"
ARCHIVO_FINAL="$DESTINO_DIR/miSecretaria_Backup_${TS}.tar.gz"

cd "$DIR" || { echo "❌ No se encontró $DIR"; read -p "Presiona ENTER..."; exit 1; }

clear
echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║   📦  Backup Portable — miSecretaria                      ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Incluye: código, secrets.properties (Token/Chat ID),     ║"
echo "║  miSecretaria.db y miSecretaria.log.                       ║"
echo "║  Excluye: /Archivo, .git, .claude, app/build, Releases     ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""
echo "⚠️  secrets.properties viaja con el backup — trátalo como una contraseña,"
echo "   nunca lo subas a un canal público."
echo "📁 Se guardará dentro de: $DESTINO_DIR"
echo "⏳ Preparando copia..."
echo ""

mkdir -p "$STAGING"

rsync -a \
    --exclude='Archivo' \
    --exclude='.git' \
    --exclude='.claude' \
    --exclude='app/build' \
    --exclude='.gradle' \
    --exclude='Releases' \
    --exclude='.idea' \
    --exclude='*.iml' \
    --exclude='local.properties' \
    --exclude='.externalNativeBuild' \
    --exclude='.cxx' \
    --exclude='captures' \
    --exclude='__pycache__' \
    --exclude='*.pyc' \
    --exclude='miSecretaria.db' \
    "$DIR"/ "$STAGING"/

if [ $? -ne 0 ]; then
    echo "❌ Falló la copia de archivos (rsync). Abortando."
    rm -rf "$TMPBASE"
    read -p "Presiona ENTER para cerrar..."
    exit 1
fi

if [ -f "$DIR/miSecretaria.db" ]; then
    echo ""
    echo "⏳ Copiando miSecretaria.db (snapshot consistente, aunque el importador esté escribiendo)..."
    sqlite3 "$DIR/miSecretaria.db" ".backup '$STAGING/miSecretaria.db'"
    echo "  ✅ miSecretaria.db"
fi

echo ""
echo "⏳ Comprimiendo..."
mkdir -p "$DESTINO_DIR"
tar -czf "$ARCHIVO_FINAL" -C "$TMPBASE" "miSecretaria"
RESULTADO=$?

rm -rf "$TMPBASE"

echo ""
if [ $RESULTADO -eq 0 ] && [ -f "$ARCHIVO_FINAL" ]; then
    TAM=$(du -h "$ARCHIVO_FINAL" | cut -f1)
    echo "✅ Backup creado con éxito"
    echo "📁 Ubicación: $ARCHIVO_FINAL"
    echo "📦 Tamaño: $TAM"
    echo ""
    echo "──────────────────────────────────────────────────────────"
    echo "PARA RESTAURAR EN LA MÁQUINA NUEVA:"
    echo "  1. Copia el .tar.gz a la máquina nueva (USB, red, nube)."
    echo "  2. mkdir -p ~/Documents && tar -xzf miSecretaria_Backup_${TS}.tar.gz -C ~/Documents/"
    echo "     → queda listo en ~/Documents/miSecretaria/ (incluye el código Y tu"
    echo "       secrets.properties, así que ya no hace falta reconfigurar el bot)."
    echo "  3. (Opcional, si además quieres el historial de git) en vez del paso 2,"
    echo "     clona desde GitHub y copia encima SOLO estos 3 del .tar.gz extraído:"
    echo "     secrets.properties, miSecretaria.db, miSecretaria.log*"
    echo "  4. Instala Android Studio / configura JAVA_HOME si la máquina nueva no lo"
    echo "     tiene (ver CLAUDE.md, sección 'Datos clave del proyecto')."
    echo "  5. pip3 install -r requirements.txt --break-system-packages"
    echo "  6. cd ~/Documents/miSecretaria && ./gradlew assembleDebug"
    echo "──────────────────────────────────────────────────────────"
else
    echo "❌ Algo falló al crear el backup (código $RESULTADO)."
fi

echo ""
read -p "Presiona ENTER para cerrar esta ventana..."
