# miSecretaria — Manual de uso

miSecretaria escucha las notificaciones de tu teléfono, detecta pagos de billeteras móviles
(ZAS, Yasta, Yape, altoke, Bille, Yolo Pago, etc.) y mensajes de apps que elijas (WhatsApp, SMS, etc.),
los **lee en voz alta**, los guarda en un historial dentro de la app y — si el administrador lo
configuró — avisa por Telegram y permite mandar avisos remotos a una o varias sucursales.

Este manual tiene dos partes:

- **[📱 Guía para usuarios de sucursal](#-guía-para-usuarios-de-sucursal)** — instalar la app,
  permisos, uso diario (Historial, Leer, Configuración básica). Para el personal que usa el
  teléfono en el día a día.
- **[🛠️ Guía para el administrador](#️-guía-para-el-administrador)** — configurar el bot de
  Telegram, repartir la app a varias sucursales (incluida la build "Interna"), publicar
  actualizaciones, y las herramientas de respaldo. Para quien gestiona todo desde su
  computadora (dueño del negocio / del bot).

---

# 📱 Guía para usuarios de sucursal

## Instalación

Hay dos formas de recibir la app — cualquiera de las dos funciona igual para el uso diario:

1. **Un APK que te pasó el administrador** (por USB, Bluetooth, o un enlace) — puede ser el
   público o la build **"Interna"** (que ya viene con el bot de Telegram configurado de
   fábrica, sin que tengas que tocar nada). Ábrelo e instálalo.
2. **Desde Configuración → "Buscar actualización"**, si ya tenías una versión instalada — solo
   funciona para el APK público (ver [Actualizaciones](#actualizaciones) más abajo).

Android puede pedirte permitir "instalar apps de fuentes desconocidas" la primera vez — es
normal, acéptalo. Al abrir la app por primera vez, otorga los permisos que te pida (ver
siguiente sección).

## Permisos que pide y para qué sirven

En Configuración vas a ver una fila por cada permiso, en **verde** si ya está concedido y en
**rojo** si falta:

| Permiso | Para qué sirve |
|---|---|
| Acceso a notificaciones | Es el permiso principal — sin él la app no puede leer ninguna notificación. |
| Mostrar sobre otras aplicaciones | Necesario para el aviso flotante ("Pantalla de aviso") cuando llega un pago. |
| Acceso a todos los archivos (fotos/audio/video de WhatsApp) | Permite buscar la foto/video/audio real cuando WhatsApp avisa que llegó uno nuevo (función en desarrollo, ver más abajo). Este permiso abre una pantalla especial de Android ("Acceso a todos los archivos") en vez del diálogo normal — es más fuerte que un permiso típico porque necesita leer las carpetas de otra app directamente. |

Además, en Android puede que tengas que ir a Ajustes del sistema → Batería → "Sin
restricciones" para miSecretaria, para que el teléfono no la duerma y deje de escuchar
notificaciones cuando pasa mucho tiempo sin usarla.

## Uso básico

- **Encendido/Apagado**: en la pantalla principal, el botón grande activa o desactiva por
  completo la escucha de notificaciones (útil si quieres pausarla un rato sin desinstalar).
- **Historial**: lista de todo lo detectado, con filtro por billetera/app arriba. Cada tarjeta
  tiene:
  - **📋 Copiar**: copia el texto de esa notificación al portapapeles (para pegarlo donde
    quieras).
  - **📌 Fijar / 📌 Quitar fijado**: fija hasta 2 notificaciones a la vez para que aparezcan
    siempre primero en la lista, sin importar el filtro. Si ya tienes 2 fijadas, avisa que
    quites una antes de fijar otra.
  - **📝 Nota**: agrega una nota personal a esa notificación (solo la ves tú — no se envía por
    Telegram ni se lee en voz alta).
  - **Eliminar**: borra esa notificación del historial (sin confirmación, como "Quitar" en
    Billeteras/Apps).
  - **🚫 Marcar como publicidad**: silencia mensajes promocionales repetidos de esa misma
    billetera.
  - Botón **"Seleccionar"** (arriba del historial): activa casillas para elegir varias
    notificaciones y borrarlas juntas con **"Eliminar seleccionadas"** (pide confirmación).
  - Botón **"Vaciar historial"**: borra TODO el historial de una vez (pide confirmación —
    no se puede deshacer).
- **Leer**: pantalla para pegar o escribir cualquier texto y que la app lo lea en voz alta
  (con pausa/reanudar y elección de voz Varón/Mujer).

El logo de la pantalla principal, si lo tocas 3 veces seguidas, pide un PIN — eso es solo para
el administrador (ver más abajo); si no lo conoces, simplemente cierra ese diálogo, no afecta
nada de lo que ya usas.

## Configuración que puedes ajustar tú

### Billeteras autorizadas / Aplicaciones (General)

Cada fila muestra el ícono real de la app, su nombre y su paquete (en rojo si falta —
significa que esa regla no va a detectar nada hasta que la corrijas).

- **Agregar Billetera / Agregar Aplicación**: abre un selector con todas las apps instaladas
  en tu teléfono — tócala y queda agregada con su paquete correcto automáticamente. Ya no hace
  falta escribir el nombre del paquete a mano.
- **Quitar**: borra esa regla por completo (por ejemplo, para eliminar una entrada vieja o
  duplicada).
- El interruptor activa/desactiva la regla sin borrarla.

Si una billetera nunca se detecta, lo más probable es que se agregó con el paquete vacío —
bórrala con "Quitar" y vuelve a agregarla desde el selector.

### Voz y avisos

- **Notificación hablada / Pantalla de aviso / Pantalla completa**: activa o desactiva cada
  forma de avisar cuando llega un pago.
- **Tipo de voz**: Varón o Mujer, más un control de velocidad de lectura.
- **Instalar más voces**: abre el instalador de voces de Google TTS si tu teléfono no trae una
  voz masculina o femenina real en español.

### Compartir

- **Compartir Historial / CSV / Log**: manda el contenido como archivo adjunto por cualquier
  app (WhatsApp, correo, Bluetooth, etc.), útil para revisar o respaldar fuera del teléfono.
- **Compartir Aplicación**: comparte el propio instalador (APK) de miSecretaria para que otra
  persona lo instale sin necesidad de internet — sirve para pasarla a otro teléfono de la
  misma sucursal, por ejemplo.

## Actualizaciones

Desde Configuración, el botón **"Buscar actualización"** revisa si hay una versión nueva
publicada y, si la hay, la descarga e instala directamente — no necesitas buscarla a mano.
**Solo funciona si tu app es la build pública** (la que se instaló desde un enlace de
"Buscar actualización" o del repositorio). Si tu teléfono tiene la build **"Interna"** (te la
entregó el administrador ya configurada), las actualizaciones te las pasa él directamente en
un APK nuevo — este botón no encontrará nada porque son builds distintas.

## Funciones en desarrollo (todavía no completas)

- **Medios nuevos de WhatsApp** (fotos/audio/video que te mandan): la app ya detecta cuándo
  WhatsApp anuncia un medio nuevo y busca el archivo real, pero por ahora solo lo registra en
  el log de depuración — todavía no lo reproduce, no guarda copia ni lo reenvía por Telegram.
  Fotos y video ya funcionaban; desde esta versión también busca el audio (notas de voz) leyendo
  la carpeta de WhatsApp directamente en vez de depender del índice de Android (que no
  detectaba las notas de voz de forma confiable) — requiere otorgar el permiso "Acceso a todos
  los archivos" de la tabla de arriba. Sin confirmar todavía en un teléfono real.
- La voz "Varón" puede sonar parecida a "Mujer" en teléfonos sin una voz masculina real
  instalada para español (usa "Instalar más voces" en Configuración para revisar qué voces
  trae tu equipo).

---

# 🛠️ Guía para el administrador

Todo lo de la guía de usuario aplica también a tu teléfono. Esta sección es lo adicional que
solo tú necesitas: configurar el bot de Telegram, repartir la app a varias sucursales, publicar
actualizaciones, y las herramientas que corren en tu computadora (no en los teléfonos).

## PIN de administrador

Toca **3 veces seguidas** el logo (junto a "miSecretaria Vx.x" en la pantalla principal) para
que aparezca el diálogo de PIN. El PIN por defecto es **230985**. Al ingresarlo correctamente,
el Token y Chat ID de Telegram quedan visibles y editables en Configuración durante esa
sesión (se vuelve a tapar si cierras y reabres la app). El resto de la Configuración
(billeteras, voz, avisos, etc.) siempre está disponible, con o sin PIN — el PIN solo protege el
Token/Chat ID para que el personal de sucursal no pueda verlos ni cambiarlos.

## Configurar el bot de Telegram

Permite que la app te mande por Telegram un CSV periódico con las notificaciones nuevas de
cada sucursal, y que tú le mandes avisos a una o todas las sucursales desde tu chat. Los
comandos (`/notificar`, `/renombrar`, `/help`) se procesan casi al instante mientras el
teléfono tenga el servicio de notificaciones activo — no hace falta esperar ni tocar
"Sincronizar ahora" para eso (ese botón sigue sirviendo para el CSV y como respaldo).

1. Habla con **@BotFather** en Telegram, crea un bot nuevo y copia el **Token** que te da
   (una cadena larga con dos puntos en el medio, por ejemplo
   `123456789:AAExampleTokenAbCdEfGhIjKlMnOpQrStUvWx` — es un ejemplo, no un token real).
   Trátalo como una contraseña: cualquiera que lo tenga puede controlar tu bot.
2. Mándale cualquier mensaje a tu bot (por ejemplo `/start`) para que sepa que existes.
3. Para obtener tu **Chat ID**, abre en el navegador (reemplazando `<TOKEN>` por el token real):
   `https://api.telegram.org/bot<TOKEN>/getUpdates` y busca el número dentro de
   `"chat":{"id": ...}`.
4. En la app, toca 3 veces el logo de la pantalla principal, ingresa tu PIN, y ve a
   Configuración → Telegram: pega el **Token del bot** y el **Chat ID**, ponle un nombre a
   esta sucursal/dispositivo (o deja el código alfanumérico automático) y presiona
   **"Guardar y activar"**.
5. Usa **"Enviar mensaje de prueba"** para confirmar que quedó bien conectado.
6. Usa **"Sincronizar ahora"** para revisar comandos pendientes y mandar el CSV al instante,
   sin esperar el intervalo configurado — útil para probar que todo funciona.

**Si el bot no responde a ningún comando (ni `/help`):**
- Revisa que Token y Chat ID estén realmente guardados (toca "Sincronizar ahora" y espera unos
  segundos).
- Ve a Ajustes del sistema → Batería → miSecretaria → "Sin restricciones". Algunos teléfonos
  (sobre todo Tecno/Infinix/Xiaomi y similares) matan las tareas en segundo plano por defecto.
- Si mandaste el comando bien pero de todos modos nada pasa, revisa que lo hayas escrito TODO
  en un solo mensaje (ver advertencia abajo en "Comandos") — es el error más común.

**Nota:** si además del comando el bot te contesta con un mensaje de error explicando la
sintaxis, quiere decir que sí está funcionando — solo el formato del comando estaba mal.

### Comandos que puedes escribirle al bot desde tu Telegram

⚠️ **Muy importante: escribe el comando Y el mensaje juntos, en UN SOLO envío** — no manden
"/notificar TODOS" y luego, en otro mensaje aparte, el texto. Si lo mandas en dos mensajes
separados, el bot no reconoce ninguno de los dos y no hace nada (no te avisa del error salvo
que ya tengas v2.15+, que sí te responde con la sintaxis correcta si te equivocas).

- `/help` (o `/start`) — te devuelve la lista completa de comandos.
- `/notificar TODOS <mensaje>` — **solo audio**: lee el mensaje en voz alta en todas las
  sucursales, sin nada en pantalla. Ejemplo: `/notificar TODOS Cerramos a las 8pm hoy`
- `/notificarpantalla TODOS <mensaje>` — **audio + pantalla**: además de leerlo, lo muestra en
  pantalla completa (si tienes esa opción activada) o como aviso flotante — igual que un pago
  recibido, pero sin decir "Pago recibido" ni inventar un monto. Ejemplo:
  `/notificarpantalla TODOS Vino el proveedor, revisen`
- Con cualquiera de los dos, cambia `TODOS` por el nombre o código de una sucursal para
  avisarle solo a esa. Ejemplo: `/notificar MS-7K2F9Q Reunión a las 3pm`
- `/renombrar <código_actual> <nombre_nuevo>` — si una sucursal se quedó con el código
  alfanumérico automático (ej. `MS-7K2F9Q`) y quieres darle un nombre más claro, así se lo
  cambias sin tocar el teléfono. Ejemplo: `/renombrar MS-7K2F9Q Sucursal Centro`
- `/panelon` / `/paneloff` — enciende/apaga el panel web (`miSecretaria.html`) para ver la base
  de datos local. Estos dos **no los procesan los teléfonos** — los escucha tu computadora
  (`csv_importer.py` tiene que estar corriendo ahí). Ver "Ver el historial en un panel web" más
  abajo.

## Repartir la app a varias sucursales

Todas las sucursales comparten **un solo bot** (un solo Token) — no se crea un bot por
sucursal. Hay dos formas de que cada teléfono quede con el Token/Chat ID sin escribirlo a mano
en cada uno:

- **APK "Interna" (recomendado):** una build especial que ya viene con el Token y Chat ID
  puestos de fábrica — se instala y ya está lista, sin tocar Configuración. Se genera en tu
  computadora (ver [Generar la build "Interna"](#generar-la-build-interna-apk) abajo) y se
  reparte a mano (USB, Bluetooth) a los teléfonos de sucursal — **nunca por un canal público**,
  porque trae tu token en texto plano.
- **Backup/Restauración (alternativa, sirve para cualquier instalación ya hecha):** en tu
  teléfono maestro, Configuración → "Guardar Backup" — el archivo `.json` incluye el Token y
  Chat ID (además de billeteras/apps) — trátalo como una contraseña. En cada sucursal,
  "Restaurar Backup" con ese archivo. El nombre de sucursal NO se copia, cada teléfono conserva
  el suyo.

Todas las sucursales mandan su CSV al mismo chat, y puedes avisarles a todas o a una en
particular con los comandos de arriba.

### Copia de seguridad de la configuración (por teléfono)

- **Guardar/Restaurar Backup**: exporta o importa en un archivo `.json` toda la configuración
  de ESE teléfono (billeteras, apps, preferencias, y el Token/Chat ID si está configurado). El
  backup **no incluye el historial** de notificaciones — eso se exporta aparte, en texto o CSV.

---

## Herramientas de administrador (se corren en tu computadora, no en los teléfonos)

Todo lo siguiente vive en la carpeta del proyecto (`~/Documents/miSecretaria` en el Debian del
administrador) — no es parte de la app Android, son scripts de escritorio.

### Publicar una versión nueva

Doble clic en `miSecretaria_Update.desktop` (o `./release.sh` a mano): compila, publica el
Release en GitHub con el APK adjunto, actualiza el manifiesto de "Buscar actualización", y
hace commit+push. Es lo que hace que el botón "Buscar actualización" de los teléfonos vea la
versión nueva.

### Generar la build "Interna" (APK)

```bash
cd ~/Documents/miSecretaria
cp secrets.properties.example secrets.properties   # solo la primera vez
# editar secrets.properties con tu botToken/chatId reales
./build_interna.sh
```
Genera `Releases/miSecretariaV(x.x)-debug_Interna.apk` — el APK con el Token/Chat ID
pre-rellenados que se reparte a mano a las sucursales (ver arriba). **Nunca se sube a GitHub ni
a ningún canal público.**

### Base de datos local de todos los CSV (`miSecretaria.db`)

Si quieres tener juntos, en un solo lugar consultable, todos los CSV que las sucursales te
mandan por Telegram (en vez de verlos sueltos en el chat):

1. Asegúrate de tener **Telegram Desktop abierto con tu sesión** en tu computadora, en el chat
   donde llegan los CSV, con la descarga automática de archivos activada para ese chat.
2. Corre `python3 csv_importer.py` (o doble clic en `miSecretaria_ImportarCSV.desktop`) desde
   la carpeta del proyecto. Muestra un panel en vivo con métricas (archivos/filas importadas,
   próxima revisión, errores) y los últimos eventos, mientras vigila tu carpeta de descargas y
   guarda cada CSV nuevo en `miSecretaria.db` (SQLite) y `miSecretaria.log` (log detallado, en
   modo debug, con rotación automática).
3. Cierra la ventana o presiona Ctrl+C cuando quieras detenerlo — no se inicia solo ni queda
   como servicio permanente.

### Ver el historial en un panel web (`miSecretaria.html`)

Mientras `csv_importer.py` esté corriendo (ver arriba), puedes encender un dashboard local de
solo lectura con todo lo que hay en `miSecretaria.db` — tarjetas con el total y el desglose por
sucursal, y una tabla con las últimas 200 notificaciones.

- **Desde Telegram (recomendado):** manda `/panelon` al bot para encenderlo y `/paneloff` para
  apagarlo. Estos dos comandos son distintos de los que usan las sucursales (`/notificar`,
  etc.) — los procesa tu computadora, no los teléfonos, así que solo funcionan si
  `csv_importer.py` está corriendo en tu PC y tienes `secrets.properties` configurado.
- **A mano, sin Telegram:** `python3 web_server.py` desde la carpeta del proyecto.
- Una vez encendido, ábrelo en el navegador: `http://localhost:8766` (o
  `http://<IP-de-tu-PC>:8766` desde otro dispositivo de tu misma red).

### Backup portable del proyecto completo (migrar a otra computadora)

Doble clic en `miSecretaria_backup.desktop` (o `bash backup_proyecto.sh`): empaqueta el
proyecto completo — código, `secrets.properties` (tu Token/Chat ID real), `miSecretaria.db` y
`miSecretaria.log` — en un único `.tar.gz` dentro de `Archivo/`, listo para copiar a otra
computadora y descomprimir ahí. Es como un "Firefox portable": la sesión (tu Token/Chat ID, y
todo el historial acumulado en `miSecretaria.db`) viaja junto con el código, sin tener que
reconfigurar nada a mano en la máquina nueva. No incluye `.git` ni los APK de `Releases/`
(ya están en GitHub) para que el archivo quede liviano. El script mismo imprime los pasos
exactos para restaurarlo al terminar.

⚠️ El `.tar.gz` generado contiene tu token real — trátalo como una contraseña, guárdalo en un
lugar privado (no lo subas a un canal público ni a una nube sin cifrar).
