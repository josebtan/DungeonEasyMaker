# Dungeon Easy Maker (DEM)

Plugin de Spigot/Paper para crear dungeons de forma sencilla: áreas que ejecutan
comandos al entrar/salir, objetivos que detectan la muerte de mobs etiquetados,
y tablas de loot con recompensas ponderadas.

Todo el plugin se maneja desde un único comando raíz: **`/dem`**.

## Módulos

- **`/dem area`**: define regiones cúbicas que ejecutan comandos al entrar o
  salir, con una varita de selección (similar a los portales de CMI, pero sin
  depender de CMI).
- **`/dem objective`**: rastrea grupos de mobs etiquetados y ejecuta comandos
  cuando todos mueren (por ejemplo, abrir una puerta o spawnear un jefe).
- **`/dem loot`**: tablas de recompensas con pesos/probabilidades.
- **`/dem reload`**: recarga `areas.yml` y `loot.yml` desde disco sin
  reiniciar el servidor.

## Ventana de ingreso (dificultad según cantidad de jugadores)

Cada área puede tener una "ventana de ingreso" opcional:

1. Entra el primer jugador → arranca una cuenta regresiva (`setwindow`).
2. Mientras corre la cuenta, otros jugadores pueden sumarse entrando al área.
3. Al terminar el tiempo, el área se **bloquea**: nadie más puede entrar hasta
   que quede vacía de nuevo.
4. Los comandos de `addstart` se ejecutan **una sola vez**, tal como están
   escritos (no se multiplican por la cantidad de jugadores que entraron).

```
/dem area setwindow nivel1 15
/dem area addstarthere cmi spawnmob zombie;hp{20} 3 [player]
```

Con el ejemplo de arriba, sin importar si entraron 1 o 4 jugadores durante
los 15 segundos, el comando corre una sola vez tal cual está escrito
(3 zombis, siempre). Si querés que la dificultad varíe según cuántos entren,
tenés que escribir vos la lógica en el comando (por ejemplo con distintos
`addstart` según cómo lo configures manualmente), DEM no lo hace automático.

- `setwindow <nombre> 0` desactiva la ventana; el área vuelve a comportarse
  como antes (los `enter-commands` corren normal, uno por jugador, al momento
  de entrar).
- Los `enter-commands`/`leave-commands` siguen funcionando igual con la
  ventana activada (útiles para mensajes de bienvenida, por ejemplo); son los
  `addstart` los que se disparan al cerrarse la cuenta regresiva.
- Si todos los jugadores salen del área antes de que termine la cuenta,
  simplemente no arranca nada y el área queda libre otra vez.
- Si todos salen después de que ya arrancó (área bloqueada), se libera
  automáticamente para un nuevo intento.

## Puertas físicas

Una puerta es un cubo de bloques que capturás tal como lo construiste (su
estado "cerrado"): abrir la rellena de aire, cerrar restaura exactamente lo
que había. Mucho más simple que escribir `fill`/`setblock` a mano, y
reversible.

```
/dem area wand
[marcá las 2 esquinas de la puerta, ya construida]
/dem area door create etapa1 puerta_a_etapa2
/dem area door open etapa1 puerta_a_etapa2
/dem area door close etapa1 puerta_a_etapa2
```

## Dungeons encadenados (varias etapas)

Un dungeon agrupa una secuencia de áreas como una sola corrida. La primera
etapa de la lista es la entrada: su puerta se cierra sola al arrancar y no
se reabre hasta que se completan todas las etapas o el dungeon queda vacío
(nadie parado en ninguna de sus etapas).

```
/dem dungeon create mi_dungeon etapa1 etapa2 etapa3
/dem dungeon setentrance mi_dungeon etapa1 puerta_entrada
/dem dungeon setkickpoint mi_dungeon        (parado afuera, en la salida)
```

En el `addstart` de cada etapa (menos la última), abrí la puerta a la
siguiente en vez de un `fill` manual:
```
/dem area addstart etapa1 dem objective watch etapa1 3 broadcast &a¡Etapa 1 superada!~~dem area door open etapa2 puerta_a_etapa3
```

En la última etapa, liberá el dungeon completo en vez de solo abrir una puerta:
```
/dem area addstart etapa3 dem objective watch etapa3 1 broadcast &6¡Dungeon completada!~~dem dungeon complete mi_dungeon
```

Comportamiento automático que ya viene resuelto:
- La entrada se cierra sola al arrancar, y **se ignora** cualquier intento de
  entrar (caminando o por `/tp`/comandos similares) mientras el dungeon esté
  en curso — te devuelve al punto de expulsión configurado.
- Si morís dentro de cualquier etapa, quedás afuera (punto de expulsión) hasta
  que el dungeon se libere; la corrida sigue para el resto del grupo.
- Si todos los participantes salen (o mueren) antes de terminar, el dungeon
  se libera solo: entrada reabierta y todas las etapas reseteadas.
- `/dem dungeon complete <id>` o `/dem dungeon release <id>` fuerzan la
  liberación manualmente si hace falta.

## Puertas de entrada/salida por etapa

Desde el GUI: menú del área → botón **"Puertas"**. Ahí, sin comandos:
- **Entrada / Salida**: click izq/der cicla entre "ninguna" y las puertas ya
  creadas de esa área, para elegir cuál cumple cada rol.
- **Abrir/cerrar entrada** y **Abrir/cerrar salida**: click izq abre, click
  der cierra, directo sobre la puerta asignada a ese rol.
- **Crear puerta nueva**: marcá 2 esquinas con la varita sobre una puerta ya
  construida (eso queda como su estado "cerrado") y ponele un id por chat.

Por comando (equivalente):
```
/dem area setentrydoor etapa1 puerta_entrada
/dem area setexitdoor etapa1 puerta_1_2
```

La de entrada se **cierra sola** al arrancar el evento de esa etapa (cuando
cierra su ventana de ingreso). La de salida la abrís vos a mano desde el
on-complete del objetivo:
```
/dem area door openexit etapa1
```
(o `/dem area door openentry|closeentry|closeexit <área>` para los otros casos).

Ya no hace falta `/dem dungeon setentrance`: la entrada del dungeon completo
es automáticamente la puerta de entrada de su primera etapa.

## Editor de mobs: modo edición con armor stand

Al abrir el menú de un área (`/dem gui` → elegí el área) entrás en **modo
edición**: mientras ese menú (o cualquiera de sus submenús) esté abierto...

- La ventana de ingreso NO se dispara: podés caminar adentro para probar
  posiciones sin arrancar el evento ni bloquear el área.
- Aparece un **armor stand por cada mob** de esa área, mostrando su nombre,
  cabeza y equipo actual — todos visibles a la vez, no solo el que estás
  tocando en ese momento.
- Los armor stands quedan puestos hasta que apretás **Guardar** o
  **Cancelar** en el menú del área (el botón "« Volver" solo navega, no
  cierra el modo edición).

**Crear un mob** ahora es un solo paso: Mobs → Crear mob nuevo → elegís el
tipo (huevo de spawn) → se crea al toque, en la posición donde estás parado,
con un id automático (`mob1`, `mob2`...) y **etiqueta = nombre del área**
(para que el objetivo se arme solo). Se abre su editor directo.

**Clonar un mob**: en la lista, **click derecho** sobre uno existente (no
shift, para que funcione en Bedrock/Geyser) → pedí el id nuevo por chat,
parado en la posición donde va la copia.

**Click físico en el armor stand**: además de desde el GUI, podés
click-derecho el armor stand en el mundo para abrir el editor de ese mob. Si
tenés un arma o armadura en la mano al hacerlo, se la equipa (comportamiento
normal de armor stand) y eso se guarda en la definición del mob. Click
izquierdo (golpe) no hace nada, no le pega ni lo empuja.

**Meta automática**: en el menú del área, el botón "Meta automática" cuenta
cuántos mobs tienen la etiqueta = nombre del área y agrega solo
`dem objective watch <área> <cantidad> broadcast ...` a Comandos de arranque
(editalo ahí si querés sumarle abrir una puerta u otro efecto al completarse).

> **Nota sobre "Cancelar":** los cambios de cada botón se guardan al toque
> (para no perder nada si el server se cae a mitad de edición). "Cancelar"
> saca los armor stands y sale del modo edición, pero no deshace ediciones
> que ya hiciste — solo "Eliminar mob"/"Eliminar área" borran algo de verdad.

## Mobs personalizados por área

Cada área puede tener sus propios mobs configurados (tipo CMI, pero nativo de
DEM, sin depender de CMI para esto). Cada mob pertenece a UNA sola área y
tiene una **posición de spawn fija**: se captura parándote en el lugar y
usando "Fijar posición aquí" (GUI) o `/dem area mob setspawnhere` (comando).

Los mobs de un área aparecen junto con los `start-commands`, cuando se cierra
la ventana de ingreso (cada uno respeta su propio delay individual), y se
eliminan solos cuando el área se libera.

Por comando:
```
/dem area mob create nivel1 guardia1 zombie
/dem area mob setspawnhere nivel1 guardia1     (parado donde debe aparecer)
/dem area mob sethealth nivel1 guardia1 40
/dem area mob setscale nivel1 guardia1 1.5
/dem area mob setname nivel1 guardia1 &c&lGuardián
/dem area mob setequip nivel1 guardia1 hand    (usa el ítem en tu mano)
/dem area mob setloot nivel1 guardia1 nivel1_loot
/dem area mob settag nivel1 guardia1 nivel1_[player]
```

O por GUI: `/dem gui` → elegí el área → botón "Mobs" → "Crear mob nuevo" →
elegís el tipo en un selector paginado con huevos de spawn (o cabeza/ícono si
el tipo no tiene huevo) → le escribís un id por chat → se abre su editor
(vida, escala, velocidad, delay, cantidad, posición, efectos, etiqueta, loot,
flags) → botón "Equipamiento" para un editor tipo muñeco de papel donde
arrastrás ítems reales a cada slot.

Requiere Paper 1.21+ (para la escala real de los mobs vía `Attribute.GENERIC_SCALE`).

## GUI de configuración (inventario)

Para no tener que escribir comandos largos a mano, **`/dem gui`** (alias
`/dem menu`) abre un menú tipo inventario donde podés hacer casi todo lo del
módulo de áreas con clicks:

- **Menú principal**: lista de áreas existentes (click para configurar),
  botón para recibir la varita, botón para crear un área nueva y botón de
  recargar.
- **Menú de un área**: ver contorno, seleccionar/deseleccionar, abrir las
  listas de comandos de entrada/salida/arranque, ajustar la ventana de
  ingreso (+5s / -5s con click normal, +30s / -30s con shift+click) y
  eliminar el área (shift+click para confirmar).
- **Lista de comandos**: cada comando es un item que podés click-earlo para
  borrarlo; "Agregar comando" te pide escribirlo en el chat (Minecraft no
  tiene un campo de texto dentro de un inventario) y lo agrega apenas
  respondés.

Crear un área desde el GUI sigue necesitando marcar las 2 esquinas en el
mundo con la varita (eso no se puede hacer desde un inventario), pero ya no
hace falta escribir `/dem area create <nombre>`: el botón "Crear área nueva"
te pide el nombre por chat y la crea directo.

Los comandos de texto (`/dem area addenter`, `addstart`, etc.) siguen
funcionando exactamente igual que antes; el GUI es una capa opcional encima,
no un reemplazo.

## Placeholders y delays en los comandos

Todos los comandos que definas (en áreas, objetivos y loot) soportan:

**Placeholders:**
| Placeholder | Se reemplaza por |
|---|---|
| `[player]` / `[playerName]` / `%player%` | nombre del jugador |
| `[world]` | mundo donde ocurrió el evento |
| `[x]` `[y]` `[z]` | coordenadas del jugador en ese momento |

**Delay (retraso opcional):**
Antepón `delay:<segundos>|` al comando para que se ejecute más tarde. El delay
es relativo al momento en que se dispara el evento (no se acumula entre
comandos de la misma lista).

```
delay:3|broadcast &6¡Cuidado, el jefe despierta!
delay:3.5|cmi spawnmob zombie;hp{80} 1 [player]
```

## Compilar

Requiere Java 17+ y Maven.

```bash
mvn package
```

El `.jar` se genera en `target/DungeonEasyMaker.jar`.

## Comandos

```
/dem area wand
/dem area create <nombre>
/dem area addenter <nombre> <comando>
/dem area addleave <nombre> <comando>
/dem area setwindow <nombre> <segundos>
/dem area addstart <nombre> <comando>
/dem area remove <nombre>
/dem area list
/dem area info <nombre>

/dem objective watch <tag> <cantidad> <comando1>~~<comando2>~~...
/dem objective reset <tag>
/dem objective status <tag>

/dem loot create <tabla>
/dem loot addentry <tabla> <peso> <comando>
/dem loot removeentry <tabla> <indice>
/dem loot list <tabla>
/dem loot roll <tabla> <jugador>

/dem gui
/dem reload
```

## Editar los niveles directamente en el archivo

`plugins/DungeonEasyMaker/areas.yml` y `plugins/DungeonEasyMaker/loot.yml` son
archivos de texto plano — puedes editarlos directamente en vez de (o además de)
usar los comandos en el juego. Después de guardar tus cambios, corre
`/dem reload` para que el plugin relea ambos archivos sin tener que reiniciar
el servidor.

### Formato de `areas.yml`

```yaml
areas:
  nivel1:
    world: world
    minX: 100
    minY: 64
    minZ: 200
    maxX: 110
    maxY: 70
    maxZ: 210
    enter-commands:
      - "cmi spawnmob zombie;hp{20};n{{#c00000}Guardián} 3 [player] sp:4"
      - "execute as @e[type=zombie,distance=..10,limit=3,sort=nearest] run tag @s add nivel1_[player]"
      - "dem objective watch nivel1_[player] 3 broadcast &6¡El jefe despierta!~~delay:2|cmi spawnmob zombie;hp{80};n{{#8b00ff}Mini-Jefe};s{2} 1 [player]"
    leave-commands: []
    join-window-seconds: 15
    start-commands:
      - "cmi spawnmob zombie;hp{20};n{{#c00000}Guardián} 3 [player] sp:4"
```

- `minX/minY/minZ` y `maxX/maxY/maxZ` son las dos esquinas del cuboide (caja 3D)
  del área. El área cubre TODA la altura entre esos dos puntos, así que para
  cubrir una sala completa marca una esquina en el piso y la opuesta en el techo.
- Varios comandos en el `on-complete` de `dem objective watch` se separan con
  `~~` (no con salto de línea), porque van todos en una sola línea de comando.
- `join-window-seconds` (0 = desactivado) y `start-commands` son la ventana de
  ingreso: `start-commands` corre una vez por cada jugador que entró durante
  esos segundos, apenas se cierra la cuenta regresiva.

### Formato de `loot.yml`

```yaml
tables:
  nivel1_loot:
    entries:
      - weight: 28
        command: "cmi give [player] bread 16"
      - weight: 5
        command: "cmi give [player] totem_of_undying 1"
```

- `weight` es el peso relativo de esa recompensa (más alto = más probable).
- Soporta los mismos placeholders y `delay:` que las áreas y objetivos.
