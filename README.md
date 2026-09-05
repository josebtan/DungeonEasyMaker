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
