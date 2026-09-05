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
```

- `minX/minY/minZ` y `maxX/maxY/maxZ` son las dos esquinas del cuboide (caja 3D)
  del área. El área cubre TODA la altura entre esos dos puntos, así que para
  cubrir una sala completa marca una esquina en el piso y la opuesta en el techo.
- Varios comandos en el `on-complete` de `dem objective watch` se separan con
  `~~` (no con salto de línea), porque van todos en una sola línea de comando.

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
