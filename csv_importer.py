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
POLL_SECONDS = 30
TELEGRAM_POLL_SECONDS = 10
WEB_SERVER_PORT = 8766
EXPECTED_HEADER = ["Sucursal", "Fecha", "Origen", "Tipo", "Mensaje"]
MAX_EVENTOS_EN_PANTALLA = 8
# Mismos nombres que BotTexts.CMD_PANEL_ON/CMD_PANEL_OFF (Kotlin, solo para que aparezcan en el
# /help de la app) — no hay una sola fuente de verdad entre los dos lenguajes, si se renombran
# aquí hay que renombrarlos también allá a mano.
CMD_PANEL_ON = "/panelon"
CMD_PANEL_OFF = "/paneloff"

log = logging.getLogger("miSecretaria.csv_importer")

# Estado en memoria solo para el panel — la fuente de verdad de cada evento sigue siendo el
# logger (y por lo tanto miSecretaria.log); esto es una vista, no un registro paralelo.
stats = {
    "archivos_importados": 0,
    "filas_importadas": 0,
    "errores": 0,
    "ultimo_archivo": None,
    "ultimo_archivo_hora": None,
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


def telegram_send_message(token: str, chat_id: str, text: str) -> None:
    if not token or not chat_id:
        return
    url = f"https://api.telegram.org/bot{token}/sendMessage"
    payload = urllib.parse.urlencode({"chat_id": chat_id, "text": text}).encode()
    try:
        urllib.request.urlopen(urllib.request.Request(url, data=payload), timeout=15)
    except Exception:
        log.exception("no se pudo responder por Telegram")


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
            while True:
                if cuenta_regresiva <= 0:
                    ciclo(conn)
                    cuenta_regresiva = POLL_SECONDS
                if cuenta_regresiva_telegram <= 0:
                    revisar_comandos_panel(conn, token, chat_id)
                    cuenta_regresiva_telegram = TELEGRAM_POLL_SECONDS
                live.update(render(cuenta_regresiva))
                time.sleep(1)
                cuenta_regresiva -= 1
                cuenta_regresiva_telegram -= 1
    except KeyboardInterrupt:
        log.info("Detenido por el usuario (Ctrl+C). El panel web, si estaba encendido, sigue corriendo — usa /paneloff.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
