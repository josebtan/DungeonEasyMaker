# Dungeon Easy Maker (DEM)

Plugin de Spigot/Paper para crear dungeons de forma sencilla: áreas que ejecutan
comandos al entrar/salir, objetivos que detectan la muerte de mobs etiquetados,
y tablas de loot con recompensas ponderadas.

## Módulos

- **Áreas** (`/dungeonarea`): define regiones cúbicas que ejecutan comandos al
  entrar o salir, con una varita de selección (similar a los portales de CMI,
  pero sin depender de CMI).
- **Objetivos** (`/dungeon`): rastrea grupos de mobs etiquetados y ejecuta
  comandos cuando todos mueren (por ejemplo, abrir una puerta o spawnear un jefe).
- **Loot** (`/dungeonloot`): tablas de recompensas con pesos/probabilidades.

## Compilar

Requiere Java 17+ y Maven.

```bash
mvn package
```

El `.jar` se genera en `target/DungeonEasyMaker.jar`.

## Comandos

Ver `src/main/resources/plugin.yml` para el detalle de cada comando.

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
      - "dungeon watch nivel1_[player] 3 broadcast &6¡El jefe despierta!~~cmi spawnmob zombie;hp{80};n{{#8b00ff}Mini-Jefe};s{2} 1 [player]"
    leave-commands: []
```

- `minX/minY/minZ` y `maxX/maxY/maxZ` son las dos esquinas del cuboide (caja 3D)
  del área. No hace falta que min sea numéricamente menor, pero es más claro
  dejarlo así.
- `[player]` (o `[playerName]`) se reemplaza automáticamente por el nombre del
  jugador que entra/sale.
- Varios comandos en `on-complete` de `dungeon watch` se separan con `~~` (no
  con salto de línea), porque van todos en una sola línea de comando.

### Formato de `loot.yml`

```yaml
tables:
  nivel1_loot:
    entries:
      - weight: 28
        command: "cmi give %player% bread 16"
      - weight: 5
        command: "cmi give %player% totem_of_undying 1"
```

- `weight` es el peso relativo de esa recompensa (más alto = más probable).
- `%player%` se reemplaza por el nombre del jugador al ejecutar `/dungeonloot roll`.
