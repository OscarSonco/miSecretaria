#!/usr/bin/env python3
"""Dashboard de solo lectura de miSecretaria.db (plantilla: templates/miSecretaria.html).

Se enciende/apaga con /panelon y /paneloff desde Telegram — ver csv_importer.py, que es quien
escucha esos comandos y lanza/mata este proceso (este script NO se conecta solo a Telegram,
solo sirve el dashboard). También se puede correr a mano para probarlo:
    python3 web_server.py

Siempre abre miSecretaria.db en modo `?mode=ro` — nunca escribe ahí, así que puede correr al
mismo tiempo que csv_importer.py sin arriesgar corromper ni bloquear la base.
"""
import sqlite3
from pathlib import Path

from flask import Flask, render_template

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "miSecretaria.db"
PORT = 8766

app = Flask(__name__, template_folder=str(BASE_DIR / "templates"))


def query_db() -> dict:
    if not DB_PATH.exists():
        return {"total": 0, "por_sucursal": [], "recientes": []}
    conn = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
    conn.row_factory = sqlite3.Row
    try:
        total = conn.execute("SELECT COUNT(*) AS n FROM notificaciones").fetchone()["n"]
        por_sucursal = conn.execute(
            "SELECT sucursal, COUNT(*) AS n FROM notificaciones GROUP BY sucursal ORDER BY n DESC"
        ).fetchall()
        recientes = conn.execute(
            "SELECT sucursal, fecha, origen, tipo, mensaje FROM notificaciones ORDER BY id DESC LIMIT 200"
        ).fetchall()
        return {"total": total, "por_sucursal": por_sucursal, "recientes": recientes}
    finally:
        conn.close()


@app.route("/")
def index():
    return render_template("miSecretaria.html", **query_db())


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=PORT, debug=False)
