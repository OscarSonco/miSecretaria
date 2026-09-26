#!/usr/bin/env python3
"""Dashboard de solo lectura de miSecretaria.db (plantilla: miSecretaria.html, en la raíz del
proyecto — el archivo existe ahí siempre, prendido o apagado el servidor; solo que abrirlo
directo con doble clic muestra las llaves `{{ ... }}` sin rellenar, porque es una plantilla
Jinja2, no HTML plano — hay que verlo a través de este servidor, no como archivo suelto).

Se enciende/apaga con /panelon y /paneloff desde Telegram — ver csv_importer.py, que es quien
escucha esos comandos y lanza/mata este proceso (este script NO se conecta solo a Telegram,
solo sirve el dashboard). También se puede correr a mano para probarlo:
    python3 web_server.py

Siempre abre miSecretaria.db en modo `?mode=ro` para LEER — nunca escribe por ese camino, así
que puede correr al mismo tiempo que csv_importer.py sin arriesgar corromper ni bloquear la
base. Las únicas dos operaciones que SÍ escriben (fusionar y eliminar sucursales, ver
`/administrar` más abajo) abren su propia conexión de escritura de corta duración, con
`busy_timeout` para esperar en vez de fallar si csv_importer.py está insertando justo en ese
instante — se cierra apenas termina, nunca queda una conexión de escritura abierta de fondo.
"""
import sqlite3
from pathlib import Path

from flask import Flask, redirect, render_template, request, url_for

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "miSecretaria.db"
PORT = 8766

app = Flask(__name__, template_folder=str(BASE_DIR))


def query_db(sucursal: str = "", origen: str = "") -> dict:
    """Las tarjetas (`por_sucursal`) y las listas de los filtros (`sucursales`/`origenes`)
    siempre reflejan TODA la base, sin importar el filtro activo — así el usuario ve todas
    las opciones disponibles para elegir. `total`/`recientes` sí se filtran: `total` es el
    conteo real filtrado (no limitado a 200), `recientes` son las últimas 200 QUE CALZAN con
    el filtro (filtrado en la base, no en las 200 ya cargadas — así no se pierden resultados
    de sucursales/apps poco frecuentes que quedarían fuera de las últimas 200 globales)."""
    vacio = {
        "total": 0, "por_sucursal": [], "recientes": [],
        "sucursales": [], "origenes": [],
        "filtro_sucursal": sucursal, "filtro_origen": origen,
    }
    if not DB_PATH.exists():
        return vacio
    conn = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
    conn.row_factory = sqlite3.Row
    try:
        por_sucursal = conn.execute(
            "SELECT sucursal, COUNT(*) AS n FROM notificaciones GROUP BY sucursal ORDER BY n DESC"
        ).fetchall()
        sucursales = [fila["sucursal"] for fila in por_sucursal]
        origenes = [
            fila["origen"] for fila in conn.execute(
                "SELECT DISTINCT origen FROM notificaciones ORDER BY origen"
            ).fetchall()
        ]

        condiciones = []
        params: list[str] = []
        if sucursal:
            condiciones.append("sucursal = ?")
            params.append(sucursal)
        if origen:
            condiciones.append("origen = ?")
            params.append(origen)
        clausula = f"WHERE {' AND '.join(condiciones)}" if condiciones else ""

        total = conn.execute(
            f"SELECT COUNT(*) AS n FROM notificaciones {clausula}", params
        ).fetchone()["n"]
        recientes = conn.execute(
            f"SELECT sucursal, fecha, origen, tipo, mensaje FROM notificaciones {clausula} "
            "ORDER BY id DESC LIMIT 200",
            params,
        ).fetchall()
        return {
            "total": total,
            "por_sucursal": por_sucursal,
            "recientes": recientes,
            "sucursales": sucursales,
            "origenes": origenes,
            "filtro_sucursal": sucursal,
            "filtro_origen": origen,
        }
    finally:
        conn.close()


def get_write_conn() -> sqlite3.Connection:
    """Conexión de escritura de corta duración — se abre, se usa y se cierra en la misma
    función que la pide, nunca queda viva entre requests. `busy_timeout` hace que espere
    (hasta 5s) en vez de tirar "database is locked" si csv_importer.py está insertando algo
    justo en ese momento, en vez de fallar la operación por una carrera de milisegundos."""
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA busy_timeout = 5000")
    return conn


def fusionar_sucursales(origenes: list[str], destino: str) -> int:
    """Mueve todos los registros de `origenes` al nombre `destino` — para juntar el historial
    de un teléfono que se renombró varias veces (ej. MS-YCFNS9 -> VIC_PocoX5 ->
    VIC_RedmiNote13Pro) bajo un solo nombre. Si `destino` ya es uno de los `origenes`, esas
    filas quedan igual (UPDATE con el mismo valor)."""
    if not origenes or not destino:
        return 0
    conn = get_write_conn()
    try:
        marcadores = ",".join("?" * len(origenes))
        cur = conn.execute(
            f"UPDATE notificaciones SET sucursal = ? WHERE sucursal IN ({marcadores})",
            [destino, *origenes],
        )
        conn.commit()
        return cur.rowcount
    finally:
        conn.close()


def eliminar_sucursales(sucursales: list[str]) -> int:
    """Borra PERMANENTEMENTE todos los registros de las sucursales dadas — no hay papelera
    acá (a diferencia del Historial de la app Android). Pensado para limpiar sucursales de
    prueba o duplicados viejos que ya se fusionaron a otro nombre."""
    if not sucursales:
        return 0
    conn = get_write_conn()
    try:
        marcadores = ",".join("?" * len(sucursales))
        cur = conn.execute(
            f"DELETE FROM notificaciones WHERE sucursal IN ({marcadores})", sucursales
        )
        conn.commit()
        return cur.rowcount
    finally:
        conn.close()


@app.route("/")
def index():
    sucursal = request.args.get("sucursal", "").strip()
    origen = request.args.get("origen", "").strip()
    msg = request.args.get("msg", "").strip()
    return render_template("miSecretaria.html", msg=msg, **query_db(sucursal, origen))


@app.route("/administrar", methods=["POST"])
def administrar():
    accion = request.form.get("accion", "")
    sucursales = [s for s in request.form.getlist("sucursales") if s]

    if accion == "fusionar":
        destino = request.form.get("destino", "").strip()
        if not sucursales:
            msg = "Elegí al menos una sucursal para fusionar."
        elif not destino:
            msg = "Escribí (o elegí) un nombre destino para la fusión."
        else:
            n = fusionar_sucursales(sucursales, destino)
            msg = f'Fusionadas {len(sucursales)} sucursal(es) en "{destino}" — {n} notificación(es) movidas.'
    elif accion == "eliminar":
        if not sucursales:
            msg = "Elegí al menos una sucursal para eliminar."
        else:
            n = eliminar_sucursales(sucursales)
            msg = f"Eliminados {n} registro(s) de {len(sucursales)} sucursal(es), de forma permanente."
    else:
        msg = "Acción no reconocida."

    return redirect(url_for("index", msg=msg))


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=PORT, debug=False)
