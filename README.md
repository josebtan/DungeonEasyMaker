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
