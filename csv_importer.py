#!/usr/bin/env python3
"""Importa a miSecretaria.db los CSV que las sucursales mandan por Telegram, con un panel en
vivo en la terminal (vía `rich`) en lugar de una consola que solo va scrolleando texto.

Los teléfonos ya envían el CSV al bot (ver TelegramSyncWorker.kt); Telegram Desktop en esta
máquina los descarga solo a WATCH_DIR. Este script vigila esa carpeta, valida que el CSV tenga
el encabezado esperado (para no tragarse por error un CSV de otro chat) y guarda cada fila en
una base SQLite única, sin duplicar si vuelve a ver el mismo archivo.

También escucha (con su propio dedupe local, sin confirmar nada ante Telegram — mismo criterio
que usa la app Android) los comandos /panelon y /paneloff para encender/apagar `web_server.py`
(el dashboard HTML de solo lectura, `templates/miSecretaria.html`) sin tener que tocar la PC.

Desde v2.47 también responde /help y /start — antes cada teléfono que comparte el bot
contestaba por su cuenta, así que con varias sucursales un solo /help generaba varias
respuestas repetidas (reportado por el usuario en vivo). Como este script es un proceso
único, ahora es el ÚNICO que responde, con un teclado persistente (botones) con todos los
comandos — Telegram los muestra debajo del campo de texto sin quitar la posibilidad de
seguir escribiendo el comando a mano. El resto de comandos (/notificar, /notificarpantalla,
/renombrar) sigue procesándose en cada teléfono exactamente igual que antes — nunca tuvieron
el problema de respuestas repetidas (no responden al chat, o solo responde el único
dispositivo que coincide).

Además vigila `miSecretaria_PublicidadBloqueada.txt` (una línea `billetera|frase` por entrada,
mismo formato interno que usa la app) — pedido explícito del usuario: quería mantener UN SOLO
archivo maestro con la publicidad bloqueada y que se sincronice sola a TODOS los teléfonos de
sus empleados, en vez de marcar cada frase a mano en cada sucursal. Cuando el archivo cambia
(por fecha de modificación), publica `public/publicidad.json` en Firebase Hosting (mismo
hosting, mismo `firebase deploy`, que ya se usa para `update.json`) — cada teléfono lo
descarga solo cada vez que corre su ciclo periódico y REEMPLAZA su "Publicidad bloqueada"
local por completo (ver `AdFilterSync.kt`), así que una frase que el admin quite del archivo
también se quita en los teléfonos, no solo se agregan las nuevas.
⚠️ Se descartó mandarlo por un comando de Telegram (`/publicidadsync`) en una primera versión
de esto: confirmado en vivo que `getUpdates` (lo que usan los teléfonos para "escuchar"
comandos) solo devuelve mensajes que llegan AL bot desde una cuenta de usuario real — nunca
los que el bot mismo manda con `sendMessage`, así que el comando nunca llegaba a ningún
teléfono aunque el envío en sí "funcionara". Firebase Hosting no tiene ese problema porque no
pasa por la cola de updates de Telegram en absoluto.

Desde v2.48 (etapa 1) también vigila `miSecretaria_BilleterasAplicacion.txt` — mismo mecanismo
(Firebase Hosting), pero con una diferencia importante: en vez de REEMPLAZAR toda la
configuración de un teléfono, cada línea del archivo se APLICA puntualmente (agregar/quitar/
modificar UNA billetera o app por nombre) y se filtra por una columna `destino` (`TODOS` o
nombres de sucursal separados por coma) — pedido explícito del usuario, porque no todos sus
empleados/sucursales deben recibir el mismo cambio (ej. un Jefe puede necesitar billeteras que
un cajero no). Para saber qué nombres de sucursal usar en `destino`, el nuevo comando
`/listado` (también exclusivo de la PC, mismo motivo que `/help`) devuelve todas las
sucursales conocidas y hace cuánto se vieron activas.

Desde v2.49 (etapa 2) también recibe `*_config_*.json` — un reporte que cada teléfono manda
solo cuando su configuración de Billeteras/Apps cambia (ver `ConfigReportSync.kt`). Se guarda
solo la versión MÁS RECIENTE por sucursal en `config_sucursales`, y `/listado` la resume
(billeteras/apps apagadas o sin voz) — pedido explícito del usuario, para detectar a tiempo
si un empleado nuevo configuró algo mal, antes de perder facturas/reportes/balances.

Todo evento que aparece en el panel (importación, aviso, error, panel on/off) pasa primero por
`logging` — el panel en pantalla solo muestra los últimos N tomados del mismo logger que
escribe miSecretaria.log, así que nunca hay nada en pantalla que no haya quedado también en
el log.

Se inicia a mano (ver miSecretaria_ImportarCSV.desktop) — sin systemd ni cron, mismo criterio
de "sin auto-arranque" que el resto de los scripts de este usuario. Ctrl+C para detenerlo (esto
NO apaga el dashboard web si estaba encendido — usa /paneloff para eso, o mata el proceso
`web_server.py` a mano).
"""
import csv
import json
import logging
import socket
import sqlite3
import subprocess
import time
import urllib.parse
import urllib.request
from collections import deque
from datetime import datetime, timezone
from logging.handlers import RotatingFileHandler
from pathlib import Path

from rich.live import Live
from rich.panel import Panel
from rich.table import Table

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "miSecretaria.db"
LOG_PATH = BASE_DIR / "miSecretaria.log"
WATCH_DIR = Path.home() / "Descargas" / "Telegram Desktop"
ADS_FILE = BASE_DIR / "miSecretaria_PublicidadBloqueada.txt"
ADS_JSON_PATH = BASE_DIR / "public" / "publicidad.json"
WALLETAPP_FILE = BASE_DIR / "miSecretaria_BilleterasAplicacion.txt"
WALLETAPP_JSON_PATH = BASE_DIR / "public" / "billeteras_aplicaciones.json"
DESTINO_TODOS = "TODOS"
# Una sucursal se considera "activa" en /listado si mandó algo (CSV) dentro de esta ventana.
SUCURSAL_ACTIVA_HORAS = 48
# Mismo binario y PATH que usa `release.sh` para lo mismo — los lanzadores .desktop no cargan
# .bashrc/nvm, así que no basta con confiar en el PATH del proceso.
FIREBASE_BIN = Path.home() / ".nvm" / "versions" / "node" / "v22.23.2" / "bin" / "firebase"
POLL_SECONDS = 30
TELEGRAM_POLL_SECONDS = 10
ADS_SYNC_SECONDS = 60
WEB_SERVER_PORT = 8766
EXPECTED_HEADER = ["Sucursal", "Fecha", "Origen", "Tipo", "Mensaje"]
MAX_EVENTOS_EN_PANTALLA = 8
# Mismos nombres que BotTexts.CMD_PANEL_ON/CMD_PANEL_OFF (Kotlin, solo para que aparezcan en el
# /help de la app) — no hay una sola fuente de verdad entre los dos lenguajes, si se renombran
# aquí hay que renombrarlos también allá a mano.
CMD_PANEL_ON = "/panelon"
CMD_PANEL_OFF = "/paneloff"
# v2.47: /help y /start se mudaron ENTERAMENTE a la PC (ver "Tanda v2.47" en CLAUDE.md) —
# antes cada teléfono que comparte el bot respondía por su cuenta, así que con N sucursales
# un solo /help generaba N respuestas repetidas. Este script es un proceso único, así que
# solo responde UNA vez sin importar cuántos teléfonos haya. Los demás comandos
# (/notificar, /notificarpantalla, /renombrar) siguen igual en los teléfonos — nunca tuvieron
# este problema (no responden al chat, o solo responde el único dispositivo que coincide).
CMD_HELP = "/help"
CMD_START = "/start"
# v2.48 (etapa 1): /listado — responde qué sucursales existen y hace cuánto se vieron activas
# (misma idea que /help: solo la PC puede responder esto, ningún teléfono individual conoce
# los nombres de las demás sucursales). Pedido explícito del usuario para poder llenar la
# columna `destino` de miSecretaria_BilleterasAplicacion.txt con nombres reales en vez de
# adivinarlos o tener que recordarlos de memoria.
CMD_LISTADO = "/listado"
# Teclado persistente con todos los comandos (pedido explícito del usuario) — Telegram los
# muestra como botones debajo del campo de texto, sin tapar la posibilidad de seguir
# escribiendo el comando a mano (un bot NO puede deshabilitar el teclado normal, solo
# ofrecer botones de acceso rápido además de él). Los comandos que necesitan texto extra
# (/notificar, /notificarpantalla, /renombrar) al tocarse mandan solo el comando base — el
# bot responde con la sintaxis exacta (ver `notifyUsageError`/`renameUsageError` en
# BotTexts.kt), así que el botón sigue siendo útil como recordatorio aunque no complete el
# mensaje solo. `is_persistent: True` evita que Telegram lo oculte solo tras un rato.
TECLADO_COMANDOS = {
    "keyboard": [
        ["/notificar", "/notificarpantalla"],
        ["/renombrar", CMD_LISTADO],
        [CMD_PANEL_ON, CMD_PANEL_OFF],
        [CMD_HELP],
    ],
    "resize_keyboard": True,
    "is_persistent": True,
}

log = logging.getLogger("miSecretaria.csv_importer")

# Estado en memoria solo para el panel — la fuente de verdad de cada evento sigue siendo el
# logger (y por lo tanto miSecretaria.log); esto es una vista, no un registro paralelo.
stats = {
    "archivos_importados": 0,
    "filas_importadas": 0,
    "errores": 0,
    "ultimo_archivo": None,
    "ultimo_archivo_hora": None,
    "publicidad_frases": None,
    "publicidad_sincronizada_hora": None,
    "walletapp_reglas": None,
    "walletapp_sincronizado_hora": None,
    "inicio": datetime.now(),
}
eventos_recientes: deque = deque(maxlen=MAX_EVENTOS_EN_PANTALLA)
web_server_proc: subprocess.Popen | None = None


class PanelHandler(logging.Handler):
    """Alimenta `eventos_recientes` con lo mismo que ya se mandó al log — el panel nunca
    muestra algo que no haya pasado por el logger (y por lo tanto por miSecretaria.log)."""

    def emit(self, record: logging.LogRecord) -> None:
        eventos_recientes.append((
            datetime.fromtimestamp(record.created).strftime("%H:%M:%S"),
            record.levelname,
            record.getMessage(),
        ))


def setup_logging() -> None:
    """Archivo (miSecretaria.log) en modo DEBUG completo, con rotación (2 MB x 3 respaldos, no
    crece sin límite); el panel en pantalla solo recibe INFO/WARNING/ERROR (el detalle fino de
    cada ciclo queda en el archivo, para no saturar la vista con ruido)."""
    log.setLevel(logging.DEBUG)
    fmt = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s", "%Y-%m-%d %H:%M:%S")

    file_handler = RotatingFileHandler(LOG_PATH, maxBytes=2_000_000, backupCount=3, encoding="utf-8")
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(fmt)
    log.addHandler(file_handler)

    panel_handler = PanelHandler()
    panel_handler.setLevel(logging.INFO)
    log.addHandler(panel_handler)


def leer_secrets() -> tuple[str, str]:
    """Mismo archivo que usa `build_interna.sh` (`secrets.properties`, clave=valor, nunca se
    commitea) — aquí solo se lee para poder escuchar /panelon /paneloff, nunca se escribe."""
    ruta = BASE_DIR / "secrets.properties"
    valores: dict[str, str] = {}
    if ruta.exists():
        for linea in ruta.read_text(encoding="utf-8").splitlines():
            linea = linea.strip()
            if not linea or linea.startswith("#") or "=" not in linea:
                continue
            clave, _, valor = linea.partition("=")
            valores[clave.strip()] = valor.strip()
    return valores.get("botToken", ""), valores.get("chatId", "")


def init_db(conn: sqlite3.Connection) -> None:
    conn.execute(
        """CREATE TABLE IF NOT EXISTS notificaciones (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sucursal TEXT NOT NULL,
            fecha TEXT NOT NULL,
            origen TEXT NOT NULL,
            tipo TEXT NOT NULL,
            mensaje TEXT NOT NULL,
            archivo_origen TEXT NOT NULL,
            importado_en TEXT NOT NULL
        )"""
    )
    conn.execute(
        """CREATE TABLE IF NOT EXISTS archivos_importados (
            nombre TEXT PRIMARY KEY,
            importado_en TEXT NOT NULL
        )"""
    )
    conn.execute(
        """CREATE TABLE IF NOT EXISTS panel_updates_procesados (
            update_id INTEGER PRIMARY KEY,
            procesado_en TEXT NOT NULL
        )"""
    )
    conn.execute(
        """CREATE TABLE IF NOT EXISTS estado_script (
            clave TEXT PRIMARY KEY,
            valor TEXT NOT NULL
        )"""
    )
    conn.execute(
        """CREATE TABLE IF NOT EXISTS config_sucursales (
            sucursal TEXT PRIMARY KEY,
            config_json TEXT NOT NULL,
            actualizado_en TEXT NOT NULL
        )"""
    )
    conn.commit()


def ya_importado(conn: sqlite3.Connection, nombre: str) -> bool:
    fila = conn.execute(
        "SELECT 1 FROM archivos_importados WHERE nombre = ?", (nombre,)
    ).fetchone()
    return fila is not None


def importar_archivo(conn: sqlite3.Connection, path: Path) -> int:
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        if header != EXPECTED_HEADER:
            log.warning("omitido (encabezado no coincide, ¿es de otro chat?): %s (header=%r)", path.name, header)
            return 0
        filas = [fila for fila in reader if len(fila) == len(EXPECTED_HEADER)]

    ahora = datetime.now(timezone.utc).isoformat()
    conn.executemany(
        "INSERT INTO notificaciones (sucursal, fecha, origen, tipo, mensaje, archivo_origen, importado_en) "
        "VALUES (?, ?, ?, ?, ?, ?, ?)",
        [(*fila, path.name, ahora) for fila in filas],
    )
    conn.execute(
        "INSERT INTO archivos_importados (nombre, importado_en) VALUES (?, ?)",
        (path.name, ahora),
    )
    conn.commit()
    return len(filas)


def ciclo(conn: sqlite3.Connection) -> None:
    log.debug("revisando %s", WATCH_DIR)
    if not WATCH_DIR.is_dir():
        log.warning("no existe la carpeta %s (¿Telegram Desktop instalado/con sesión?)", WATCH_DIR)
        return
    candidatos = sorted(WATCH_DIR.glob("*.csv"))
    log.debug("%d archivo(s) .csv en la carpeta, revisando cuáles son nuevos", len(candidatos))
    for path in candidatos:
        if ya_importado(conn, path.name):
            continue
        try:
            n = importar_archivo(conn, path)
            if n:
                stats["archivos_importados"] += 1
                stats["filas_importadas"] += n
                stats["ultimo_archivo"] = path.name
                stats["ultimo_archivo_hora"] = datetime.now().strftime("%H:%M:%S")
                log.info("%s: %d filas importadas", path.name, n)
        except Exception:
            stats["errores"] += 1
            log.exception("error importando %s", path.name)
    ciclo_config(conn)


# --- Reporte de configuración por sucursal (etapa 2 de Billeteras/Aplicaciones, v2.49) -------

def ciclo_config(conn: sqlite3.Connection) -> None:
    """Busca archivos `*_config_*.json` que `ConfigReportSync.kt` manda al chat — cada uno es
    la configuración ACTUAL de Billeteras/Apps de un teléfono. Se guarda solo la versión MÁS
    RECIENTE por sucursal (no un historial — no tiene sentido acumular configuraciones
    viejas), y se marca en `archivos_importados` (mismo mecanismo que los CSV) para no
    reprocesar el mismo archivo dos veces."""
    candidatos = sorted(WATCH_DIR.glob("*_config_*.json"))
    for path in candidatos:
        if ya_importado(conn, path.name):
            continue
        try:
            importar_config(conn, path)
        except Exception:
            stats["errores"] += 1
            log.exception("error importando reporte de configuración %s", path.name)


def importar_config(conn: sqlite3.Connection, path: Path) -> None:
    datos = json.loads(path.read_text(encoding="utf-8"))
    sucursal = datos.get("sucursal")
    if not sucursal or "billeteras" not in datos or "apps" not in datos:
        log.warning("omitido (no parece un reporte de configuración válido, ¿es de otro chat?): %s", path.name)
        return
    ahora = datetime.now(timezone.utc).isoformat()
    conn.execute(
        "INSERT INTO config_sucursales (sucursal, config_json, actualizado_en) VALUES (?, ?, ?) "
        "ON CONFLICT(sucursal) DO UPDATE SET config_json = excluded.config_json, actualizado_en = excluded.actualizado_en",
        (sucursal, json.dumps(datos, ensure_ascii=False), ahora),
    )
    conn.execute(
        "INSERT INTO archivos_importados (nombre, importado_en) VALUES (?, ?)",
        (path.name, ahora),
    )
    conn.commit()
    log.info("Configuración de '%s' actualizada (%s)", sucursal, path.name)


# --- Sincronización de "Publicidad bloqueada" (miSecretaria_PublicidadBloqueada.txt) ---------

def obtener_estado(conn: sqlite3.Connection, clave: str) -> str | None:
    fila = conn.execute("SELECT valor FROM estado_script WHERE clave = ?", (clave,)).fetchone()
    return fila[0] if fila else None


def guardar_estado(conn: sqlite3.Connection, clave: str, valor: str) -> None:
    conn.execute(
        "INSERT INTO estado_script (clave, valor) VALUES (?, ?) "
        "ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor",
        (clave, valor),
    )
    conn.commit()


def leer_publicidad_bloqueada() -> list[str]:
    """Lee ADS_FILE: una línea `billetera|frase` por entrada (mismo formato interno que usa
    `AdFilterConfig.kt`). Líneas vacías o que empiezan con # se ignoran; una línea sin '|' se
    descarta con una advertencia (probablemente un error de tipeo del admin, mejor avisar que
    mandarla mal a todos los teléfonos)."""
    lineas_validas = []
    for linea in ADS_FILE.read_text(encoding="utf-8").splitlines():
        limpia = linea.strip()
        if not limpia or limpia.startswith("#"):
            continue
        if "|" not in limpia:
            log.warning("línea ignorada en %s (falta '|' billetera/frase): %r", ADS_FILE.name, limpia)
            continue
        lineas_validas.append(limpia)
    return lineas_validas


def publicar_en_firebase() -> bool:
    """`firebase deploy --only hosting` sube TODO `public/` de una — update.json,
    publicidad.json, billeteras_aplicaciones.json, lo que haya. Requiere `firebase` ya
    autenticado en esta máquina (lo mismo que necesita release.sh)."""
    try:
        resultado = subprocess.run(
            [str(FIREBASE_BIN), "deploy", "--only", "hosting"],
            cwd=str(BASE_DIR),
            capture_output=True,
            text=True,
            timeout=120,
        )
        if resultado.returncode != 0:
            log.error("firebase deploy falló (código %d): %s", resultado.returncode, resultado.stderr[-500:])
            return False
        return True
    except Exception:
        log.exception("no se pudo ejecutar firebase deploy")
        return False


def sincronizar_publicidad(conn: sqlite3.Connection) -> None:
    """Si ADS_FILE cambió desde la última sincronización (por fecha de modificación), publica
    la lista completa como JSON en Firebase Hosting — cada teléfono la descarga sola en su
    próximo ciclo periódico y reemplaza su lista local entera (ver `AdFilterSync.kt`). Se
    descartó mandarlo por Telegram: confirmado en vivo que un bot no puede "verse a sí mismo"
    los mensajes que manda con sendMessage, así que un comando nunca llegaba a los teléfonos."""
    if not ADS_FILE.exists():
        return
    mtime = str(ADS_FILE.stat().st_mtime)
    if obtener_estado(conn, "publicidad_mtime") == mtime:
        return
    lineas = leer_publicidad_bloqueada()
    ADS_JSON_PATH.parent.mkdir(parents=True, exist_ok=True)
    ADS_JSON_PATH.write_text(json.dumps(lineas, ensure_ascii=False, indent=2), encoding="utf-8")
    ADS_JSON_PATH.chmod(0o644)  # Firebase Hosting rechaza archivos ejecutables, ver CLAUDE.md
    if publicar_en_firebase():
        guardar_estado(conn, "publicidad_mtime", mtime)
        stats["publicidad_frases"] = len(lineas)
        stats["publicidad_sincronizada_hora"] = datetime.now().strftime("%H:%M:%S")
        log.info("Publicidad bloqueada publicada en Firebase Hosting (%d frases)", len(lineas))
    else:
        log.warning("Publicidad bloqueada: el deploy a Firebase falló, se reintentará en el próximo ciclo")


# --- Sincronización de Billeteras/Aplicaciones (miSecretaria_BilleterasAplicacion.txt) --------

def _on_off(valor: str) -> bool | None:
    """'on'/'off' (o variantes) -> bool; blanco o '-' -> None ("no especificado", el teléfono
    conserva el valor que ya tenga o el default si es una regla nueva)."""
    v = valor.strip().lower()
    if v in ("", "-"):
        return None
    return v in ("on", "1", "si", "sí", "true")


def leer_billeteras_apps() -> list[dict]:
    """Lee WALLETAPP_FILE: una línea por entrada, formato:
    tipo|nombre|destino|accion|packageId|enabled|hablar|con_nombre|llamadas|modo
    (los comentarios del propio archivo explican cada campo). Líneas vacías o que empiezan
    con # se ignoran. Una línea con menos de 4 campos, o con `tipo`/`accion` inválidos, se
    descarta con una advertencia — mejor avisar que sincronizar algo a medias a todas las
    sucursales."""
    entradas = []
    for numero, linea in enumerate(WALLETAPP_FILE.read_text(encoding="utf-8").splitlines(), start=1):
        limpia = linea.strip()
        if not limpia or limpia.startswith("#"):
            continue
        partes = [p.strip() for p in limpia.split("|")]
        if len(partes) < 4:
            log.warning("línea %d ignorada en %s (faltan campos): %r", numero, WALLETAPP_FILE.name, limpia)
            continue
        tipo, nombre, destino_raw, accion = partes[0].lower(), partes[1], partes[2], partes[3].lower()
        if tipo not in ("billetera", "app") or not nombre:
            log.warning("línea %d ignorada en %s (tipo/nombre inválido): %r", numero, WALLETAPP_FILE.name, limpia)
            continue
        destino = [DESTINO_TODOS] if destino_raw.strip().upper() == DESTINO_TODOS else [d.strip() for d in destino_raw.split(",") if d.strip()]
        if not destino:
            log.warning("línea %d ignorada en %s (destino vacío): %r", numero, WALLETAPP_FILE.name, limpia)
            continue
        entrada = {"tipo": tipo, "nombre": nombre, "destino": destino}
        if accion == "quitar":
            entrada["quitar"] = True
        elif accion == "definir":
            resto = partes[4:]
            if len(resto) > 0 and resto[0] not in ("", "-"):
                entrada["packageId"] = resto[0]
            if len(resto) > 1 and _on_off(resto[1]) is not None:
                entrada["enabled"] = _on_off(resto[1])
            if len(resto) > 2 and _on_off(resto[2]) is not None:
                entrada["speechMuted"] = not _on_off(resto[2])  # campo del archivo es "hablar" (positivo)
            if len(resto) > 3 and _on_off(resto[3]) is not None:
                entrada["sayName"] = _on_off(resto[3])
            if tipo == "app":
                if len(resto) > 4 and _on_off(resto[4]) is not None:
                    entrada["callsMuted"] = not _on_off(resto[4])  # campo del archivo es "llamadas" (positivo)
                if len(resto) > 5 and resto[5].strip().lower() in ("distancia", "solo_distancia"):
                    entrada["titleOnly"] = True
                elif len(resto) > 5 and resto[5].strip().lower() in ("normal", "completo"):
                    entrada["titleOnly"] = False
        else:
            log.warning("línea %d ignorada en %s (acción '%s' no es 'definir' ni 'quitar')", numero, WALLETAPP_FILE.name, accion)
            continue
        entradas.append(entrada)
    return entradas


def sincronizar_billeteras_apps(conn: sqlite3.Connection) -> None:
    """Si WALLETAPP_FILE cambió desde la última sincronización, publica las entradas como JSON
    en Firebase Hosting. A diferencia de "Publicidad bloqueada", esto NO reemplaza toda la
    configuración del teléfono — cada entrada se APLICA puntualmente (agregar/quitar/
    modificar UNA regla por nombre, filtrada por `destino`), ver `WalletAppSync.kt`."""
    if not WALLETAPP_FILE.exists():
        return
    mtime = str(WALLETAPP_FILE.stat().st_mtime)
    if obtener_estado(conn, "walletapp_mtime") == mtime:
        return
    entradas = leer_billeteras_apps()
    WALLETAPP_JSON_PATH.parent.mkdir(parents=True, exist_ok=True)
    WALLETAPP_JSON_PATH.write_text(json.dumps(entradas, ensure_ascii=False, indent=2), encoding="utf-8")
    WALLETAPP_JSON_PATH.chmod(0o644)
    if publicar_en_firebase():
        guardar_estado(conn, "walletapp_mtime", mtime)
        stats["walletapp_reglas"] = len(entradas)
        stats["walletapp_sincronizado_hora"] = datetime.now().strftime("%H:%M:%S")
        log.info("Billeteras/Apps publicadas en Firebase Hosting (%d regla(s))", len(entradas))
    else:
        log.warning("Billeteras/Apps: el deploy a Firebase falló, se reintentará en el próximo ciclo")


# --- Control del dashboard web (/panelon, /paneloff) -----------------------------------------

def servidor_web_activo() -> bool:
    """Chequeo de conexión real al puerto, no `pgrep` (evita falsos positivos — misma lección
    que ya se aprendió con `web_server.py` de CotizacionDelDolar)."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.5)
        try:
            s.connect(("127.0.0.1", WEB_SERVER_PORT))
            return True
        except OSError:
            return False


def iniciar_servidor_web(token: str, chat_id: str) -> None:
    global web_server_proc
    if servidor_web_activo():
        log.info("El panel web ya estaba encendido")
        telegram_send_message(token, chat_id, "ℹ️ El panel ya estaba encendido.")
        return
    web_server_proc = subprocess.Popen(
        ["python3", str(BASE_DIR / "web_server.py")],
        cwd=str(BASE_DIR),
        start_new_session=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    log.info("Panel web encendido (puerto %d)", WEB_SERVER_PORT)
    telegram_send_message(token, chat_id, f"✅ Panel web encendido en el puerto {WEB_SERVER_PORT} de tu PC.")


def detener_servidor_web(token: str, chat_id: str) -> None:
    global web_server_proc
    if web_server_proc is not None and web_server_proc.poll() is None:
        web_server_proc.terminate()
        web_server_proc = None
        log.info("Panel web apagado")
        telegram_send_message(token, chat_id, "🛑 Panel web apagado.")
    elif servidor_web_activo():
        log.warning("Hay algo en el puerto %d que no arrancó este script — no se apaga solo, revisa a mano", WEB_SERVER_PORT)
        telegram_send_message(token, chat_id, "⚠️ El panel parece encendido por otra vía — apágalo a mano en la PC.")
    else:
        telegram_send_message(token, chat_id, "ℹ️ El panel ya estaba apagado.")


# --- Cliente Telegram mínimo (sin librerías, igual que TelegramClient.kt) --------------------

def telegram_get_updates(token: str) -> list:
    # offset=0 debería devolver TODO lo no confirmado (así lo documenta Telegram), pero se
    # confirmó en vivo (2026-09-25) que no es confiable: mientras `getWebhookInfo` reportaba
    # 15 actualizaciones pendientes reales, offset=0 solo devolvía 1 (la más vieja) — un
    # offset NEGATIVO ("las últimas N de la cola, sin importar confirmación") sí las trajo
    # las 15 completas, de forma repetible. Por eso se usa -100 en vez de 0 — sigue sin
    # confirmar nada ante Telegram (mismo diseño de "cada quien dedupea localmente"), solo
    # cambia CÓMO se pide la cola para no depender de ese comportamiento poco confiable.
    url = f"https://api.telegram.org/bot{token}/getUpdates?offset=-100&timeout=0"
    with urllib.request.urlopen(url, timeout=15) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return data.get("result", []) if data.get("ok") else []


def telegram_send_message(token: str, chat_id: str, text: str, reply_markup: dict | None = None) -> None:
    if not token or not chat_id:
        return
    url = f"https://api.telegram.org/bot{token}/sendMessage"
    campos = {"chat_id": chat_id, "text": text}
    if reply_markup is not None:
        campos["reply_markup"] = json.dumps(reply_markup)
    payload = urllib.parse.urlencode(campos).encode()
    try:
        urllib.request.urlopen(urllib.request.Request(url, data=payload), timeout=15)
    except Exception:
        log.exception("no se pudo responder por Telegram")


def texto_ayuda() -> str:
    """Mismo contenido que `BotTexts.help()` (Kotlin), pero sin la línea final de sucursal —
    esta respuesta la manda la PC, no un teléfono en particular, así que no tiene sentido
    decir "esta sucursal se llama...". Sin una sola fuente de verdad entre los dos lenguajes;
    si se agrega/cambia un comando allá, replicarlo aquí a mano."""
    return (
        "🤖 miSecretaria — comandos del bot:\n"
        "⚠️ Escribe todo en UN SOLO mensaje, no en varios seguidos.\n\n"
        "/notificar TODOS <mensaje>\nSolo AUDIO — lee el mensaje en voz alta, sin nada en pantalla.\n"
        "Ejemplo: /notificar TODOS Cerramos a las 8pm hoy\n\n"
        "/notificarpantalla TODOS <mensaje>\nAUDIO + PANTALLA — además lo muestra en pantalla completa o aviso flotante.\n"
        "Ejemplo: /notificarpantalla TODOS Vino el proveedor, revisen\n\n"
        "Con ambos puedes usar el nombre/código de una sucursal en vez de TODOS, para avisarle solo a esa.\n\n"
        "/renombrar <código_actual> <nombre_nuevo>\nCambia el nombre de una sucursal (código/nombre debe coincidir exacto).\n\n"
        f"{CMD_LISTADO}\nLista todas las sucursales conocidas, hace cuánto se vieron activas, y un resumen de sus Billeteras/Apps (qué está apagado o sin voz) — útil para saber qué nombres usar en miSecretaria_BilleterasAplicacion.txt y para detectar configuraciones mal hechas.\n\n"
        f"{CMD_PANEL_ON}\nEnciende el panel web (miSecretaria.html) para ver la base de datos.\n"
        f"{CMD_PANEL_OFF}\nApaga ese panel web.\n\n"
        f"{CMD_HELP}\nMuestra esta ayuda."
    )


def _resumen_config(config_json: str | None) -> str:
    """Etapa 2: resume billeteras/apps DESACTIVADAS o SILENCIADAS — son las que probablemente
    le interesan al admin para detectar una configuración mal hecha de un empleado nuevo
    (facturas/reportes/balances que se podrían estar perdiendo). Si todo está normal, lo dice
    explícitamente en vez de listar todo (una lista completa de 10+ reglas por sucursal
    haría el mensaje ilegible)."""
    if not config_json:
        return "   (sin reporte de configuración todavía)"
    try:
        datos = json.loads(config_json)
    except Exception:
        return "   (reporte de configuración inválido)"
    problemas = []
    for grupo, etiqueta in (("billeteras", "billetera"), ("apps", "app")):
        for regla in datos.get(grupo, []):
            nombre = regla.get("nombre", "?")
            if not regla.get("enabled", True):
                problemas.append(f"❌ {nombre} ({etiqueta}) APAGADA")
            elif regla.get("speechMuted"):
                problemas.append(f"🔇 {nombre} ({etiqueta}) sin voz")
    total = len(datos.get("billeteras", [])) + len(datos.get("apps", []))
    if not problemas:
        return f"   ✅ {total} regla(s), todas activas y con voz"
    return f"   {total} regla(s) — " + ", ".join(problemas)


def texto_listado(conn: sqlite3.Connection) -> str:
    """/listado — nombres de sucursal conocidos (de `miSecretaria.db`, poblada por los CSV
    que cada teléfono ya manda) + hace cuánto se vio la última vez + (etapa 2) un resumen de
    su configuración de Billeteras/Apps, para detectar si alguna quedó mal configurada.
    Ninguna sucursal individual puede responder esto — cada teléfono solo conoce su propio
    nombre/configuración, así que esto también lo responde exclusivamente la PC."""
    filas = conn.execute(
        "SELECT sucursal, MAX(fecha) AS ultima FROM notificaciones GROUP BY sucursal ORDER BY ultima DESC"
    ).fetchall()
    if not filas:
        return "🤖 Todavía no hay ninguna sucursal registrada en miSecretaria.db."
    ahora = datetime.now(timezone.utc)
    lineas = ["🤖 Sucursales conocidas:"]
    for sucursal, ultima in filas:
        try:
            ultima_dt = datetime.fromisoformat(ultima)
            if ultima_dt.tzinfo is None:
                ultima_dt = ultima_dt.replace(tzinfo=timezone.utc)
            horas = (ahora - ultima_dt).total_seconds() / 3600
            if horas <= SUCURSAL_ACTIVA_HORAS:
                estado = f"🟢 activa (hace {horas:.0f}h)"
            else:
                dias = horas / 24
                estado = f"⚪ inactiva (hace {dias:.0f}d)"
        except Exception:
            estado = f"⚪ última vez: {ultima}"
        lineas.append(f"• {sucursal} — {estado}")
        config_row = conn.execute(
            "SELECT config_json FROM config_sucursales WHERE sucursal = ?", (sucursal,)
        ).fetchone()
        lineas.append(_resumen_config(config_row[0] if config_row else None))
    lineas.append("\nUsa estos nombres exactos en la columna \"destino\" de miSecretaria_BilleterasAplicacion.txt.")
    return "\n".join(lineas)


def ya_procesado_update(conn: sqlite3.Connection, update_id: int) -> bool:
    return conn.execute(
        "SELECT 1 FROM panel_updates_procesados WHERE update_id = ?", (update_id,)
    ).fetchone() is not None


def marcar_update_procesado(conn: sqlite3.Connection, update_id: int) -> None:
    conn.execute(
        "INSERT OR IGNORE INTO panel_updates_procesados (update_id, procesado_en) VALUES (?, ?)",
        (update_id, datetime.now(timezone.utc).isoformat()),
    )
    conn.commit()


def revisar_comandos_panel(conn: sqlite3.Connection, token: str, chat_id: str) -> None:
    """Escucha /panelon, /paneloff, /help y /start. Dedupe local propio (`panel_updates_
    procesados`), independiente del que usa cada teléfono — no hay riesgo de interferencia
    entre este proceso y la app Android."""
    if not token or not chat_id:
        return
    try:
        updates = telegram_get_updates(token)
    except Exception:
        log.debug("no se pudo consultar Telegram (¿sin internet en la PC?)")
        return
    for update in updates:
        update_id = update.get("update_id")
        if update_id is None or ya_procesado_update(conn, update_id):
            continue
        marcar_update_procesado(conn, update_id)
        mensaje = update.get("message") or {}
        if str(mensaje.get("chat", {}).get("id")) != str(chat_id):
            continue
        texto = (mensaje.get("text") or "").strip().lower()
        if texto == CMD_PANEL_ON:
            iniciar_servidor_web(token, chat_id)
        elif texto == CMD_PANEL_OFF:
            detener_servidor_web(token, chat_id)
        elif texto in (CMD_HELP, CMD_START):
            telegram_send_message(token, chat_id, texto_ayuda(), reply_markup=TECLADO_COMANDOS)
            log.info("/help respondido desde la PC (con teclado persistente)")
        elif texto == CMD_LISTADO:
            telegram_send_message(token, chat_id, texto_listado(conn))
            log.info("/listado respondido desde la PC")


def render(segundos_para_revisar: int) -> Panel:
    metricas = Table.grid(padding=(0, 2))
    metricas.add_column(justify="right", style="bold cyan")
    metricas.add_column()
    metricas.add_row("Carpeta vigilada:", str(WATCH_DIR))
    metricas.add_row("Base de datos:", str(DB_PATH.name))
    metricas.add_row("Log:", str(LOG_PATH.name))
    metricas.add_row("Próxima revisión en:", f"{segundos_para_revisar}s (cada {POLL_SECONDS}s)")
    metricas.add_row("", "")
    metricas.add_row("Archivos importados:", f"[bold green]{stats['archivos_importados']}[/]")
    metricas.add_row("Filas importadas:", f"[bold green]{stats['filas_importadas']}[/]")
    metricas.add_row(
        "Último archivo:",
        f"{stats['ultimo_archivo']} ({stats['ultimo_archivo_hora']})" if stats["ultimo_archivo"] else "—",
    )
    errores_estilo = "bold red" if stats["errores"] else "green"
    metricas.add_row("Errores:", f"[{errores_estilo}]{stats['errores']}[/]")
    panel_web_on = servidor_web_activo()
    metricas.add_row(
        "Panel web (/panelon, /paneloff):",
        f"[bold green]🟢 encendido (puerto {WEB_SERVER_PORT})[/]" if panel_web_on else "[dim]🔴 apagado[/]",
    )
    if ADS_FILE.exists():
        if stats["publicidad_frases"] is not None:
            metricas.add_row(
                "Publicidad bloqueada:",
                f"[bold green]publicada[/] ({stats['publicidad_frases']} frases, {stats['publicidad_sincronizada_hora']})",
            )
        else:
            metricas.add_row("Publicidad bloqueada:", "[dim]esperando primer ciclo...[/]")
    else:
        metricas.add_row("Publicidad bloqueada:", f"[dim]{ADS_FILE.name} no existe[/]")
    if WALLETAPP_FILE.exists():
        if stats["walletapp_reglas"] is not None:
            metricas.add_row(
                "Billeteras/Apps:",
                f"[bold green]publicado[/] ({stats['walletapp_reglas']} regla(s), {stats['walletapp_sincronizado_hora']})",
            )
        else:
            metricas.add_row("Billeteras/Apps:", "[dim]esperando primer ciclo...[/]")
    else:
        metricas.add_row("Billeteras/Apps:", f"[dim]{WALLETAPP_FILE.name} no existe[/]")
    uptime = datetime.now() - stats["inicio"]
    metricas.add_row("En marcha desde:", f"{stats['inicio'].strftime('%H:%M:%S')} (hace {str(uptime).split('.')[0]})")

    colores = {"INFO": "green", "WARNING": "yellow", "ERROR": "red"}
    eventos = Table.grid(padding=(0, 1))
    eventos.add_column()
    if eventos_recientes:
        for hora, nivel, mensaje in eventos_recientes:
            color = colores.get(nivel, "white")
            corto = mensaje if len(mensaje) <= 70 else mensaje[:69] + "…"
            eventos.add_row(f"[dim]{hora}[/] [{color}]{nivel:7}[/] {corto}")
    else:
        eventos.add_row("[dim](sin eventos todavía)[/]")

    cuerpo = Table.grid()
    cuerpo.add_row(metricas)
    cuerpo.add_row("")
    cuerpo.add_row(Panel(eventos, title="Últimos eventos", border_style="grey50"))

    return Panel(
        cuerpo,
        title="[bold]miSecretaria[/] — Importador de CSV",
        subtitle="Ctrl+C para detener",
        border_style="cyan",
    )


def main() -> None:
    setup_logging()
    log.info("miSecretaria — importador de CSV iniciado")
    # Rutas completas: quedan en el archivo (DEBUG) pero no en el panel de eventos en pantalla,
    # que ya las muestra en la tabla de métricas y se rompería con líneas tan largas.
    log.debug("vigilando: %s", WATCH_DIR)
    log.debug("base de datos: %s", DB_PATH)
    log.debug("log: %s (debug)", LOG_PATH)
    token, chat_id = leer_secrets()
    if not token or not chat_id:
        log.warning("secrets.properties vacío o sin botToken/chatId — /panelon y /paneloff no van a funcionar")
    conn = sqlite3.connect(DB_PATH)
    init_db(conn)
    try:
        with Live(render(POLL_SECONDS), refresh_per_second=4, screen=False) as live:
            cuenta_regresiva = 0
            cuenta_regresiva_telegram = 0
            cuenta_regresiva_publicidad = 0
            while True:
                if cuenta_regresiva <= 0:
                    ciclo(conn)
                    cuenta_regresiva = POLL_SECONDS
                if cuenta_regresiva_telegram <= 0:
                    revisar_comandos_panel(conn, token, chat_id)
                    cuenta_regresiva_telegram = TELEGRAM_POLL_SECONDS
                if cuenta_regresiva_publicidad <= 0:
                    try:
                        sincronizar_publicidad(conn)
                    except Exception:
                        log.exception("error sincronizando %s", ADS_FILE.name)
                    try:
                        sincronizar_billeteras_apps(conn)
                    except Exception:
                        log.exception("error sincronizando %s", WALLETAPP_FILE.name)
                    cuenta_regresiva_publicidad = ADS_SYNC_SECONDS
                live.update(render(cuenta_regresiva))
                time.sleep(1)
                cuenta_regresiva -= 1
                cuenta_regresiva_telegram -= 1
                cuenta_regresiva_publicidad -= 1
    except KeyboardInterrupt:
        log.info("Detenido por el usuario (Ctrl+C). El panel web, si estaba encendido, sigue corriendo — usa /paneloff.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
