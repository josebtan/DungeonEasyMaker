package net.example.dem.area;

import net.example.dem.mob.MobSpawnDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DungeonArea {

    private final String name;
    private final String world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final List<String> enterCommands = new ArrayList<>();
    private final List<String> leaveCommands = new ArrayList<>();

    // --- Ventana de ingreso (cuenta regresiva por cantidad de jugadores) ---
    // 0 = desactivada: los enterCommands corren normal, uno a uno, como siempre.
    private int joinWindowSeconds = 0;
    // Comandos que se ejecutan una vez cuando se cierra la ventana de ingreso
    // (tal como están escritos, sin multiplicar por cantidad de jugadores).
    private final List<String> startCommands = new ArrayList<>();

    // Mobs personalizados de ESTA área (id -> definición). Se disparan junto
    // con startCommands al cerrarse la ventana de ingreso.
    private final Map<String, MobSpawnDefinition> mobs = new LinkedHashMap<>();

    // Puertas físicas asociadas a esta área (id -> definición).
    private final Map<String, DoorDefinition> doors = new LinkedHashMap<>();

    // Puerta "de entrada" y "de salida" de ESTA etapa (referencian un id
    // dentro de `doors`, de esta misma área). La de entrada se cierra sola
    // al arrancar el evento (cuando cierra la ventana de ingreso); la de
    // salida la abrís vos manualmente desde el on-complete del objetivo.
    private String entryDoorId;
    private String exitDoorId;

    // Estado en vivo de la ventana. No se persiste en areas.yml: siempre
    // arranca "libre" cuando el plugin recarga o reinicia.
    private transient boolean windowOpen = false;
    private transient boolean locked = false;
    private final transient Set<UUID> joiners = new HashSet<>();
    private transient BukkitTask countdownTask;

    // Entidades actualmente spawneadas por los mobs de esta área (de
    // cualquier definición); se limpian solas al liberarse el área.
    private final transient Set<UUID> spawnedMobEntities = new HashSet<>();

    public DungeonArea(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public boolean contains(Location loc) {
        World locWorld = loc.getWorld();
        if (locWorld == null || !locWorld.getName().equals(world)) {
            return false;
        }
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        return x >= minX && x <= maxX + 1
                && y >= minY && y <= maxY + 1
                && z >= minZ && z <= maxZ + 1;
    }

    public String getName() {
        return name;
    }

    public String getWorld() {
        return world;
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public List<String> getEnterCommands() {
        return enterCommands;
    }

    public List<String> getLeaveCommands() {
        return leaveCommands;
    }

    public int getJoinWindowSeconds() {
        return joinWindowSeconds;
    }

    public void setJoinWindowSeconds(int joinWindowSeconds) {
        this.joinWindowSeconds = Math.max(0, joinWindowSeconds);
    }

    public List<String> getStartCommands() {
        return startCommands;
    }

    public Map<String, MobSpawnDefinition> getMobsById() {
        return mobs;
    }

    public List<MobSpawnDefinition> getMobs() {
        return new ArrayList<>(mobs.values());
    }

    public MobSpawnDefinition getMob(String id) {
        return mobs.get(id.toLowerCase());
    }

    public void addMob(MobSpawnDefinition mob) {
        mobs.put(mob.getId().toLowerCase(), mob);
    }

    public boolean removeMob(String id) {
        return mobs.remove(id.toLowerCase()) != null;
    }

    public Map<String, DoorDefinition> getDoorsById() {
        return doors;
    }

    public DoorDefinition getDoor(String id) {
        return doors.get(id.toLowerCase());
    }

    public void addDoor(DoorDefinition door) {
        doors.put(door.getId().toLowerCase(), door);
    }

    public boolean removeDoor(String id) {
        return doors.remove(id.toLowerCase()) != null;
    }

    public String getEntryDoorId() {
        return entryDoorId;
    }

    public void setEntryDoorId(String entryDoorId) {
        this.entryDoorId = entryDoorId;
    }

    public String getExitDoorId() {
        return exitDoorId;
    }

    public void setExitDoorId(String exitDoorId) {
        this.exitDoorId = exitDoorId;
    }

    public DoorDefinition getEntryDoor() {
        return entryDoorId == null ? null : getDoor(entryDoorId);
    }

    public DoorDefinition getExitDoor() {
        return exitDoorId == null ? null : getDoor(exitDoorId);
    }

    public boolean isWindowOpen() {
        return windowOpen;
    }

    public void setWindowOpen(boolean windowOpen) {
        this.windowOpen = windowOpen;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Set<UUID> getJoiners() {
        return joiners;
    }

    public void setCountdownTask(BukkitTask task) {
        this.countdownTask = task;
    }

    public Set<UUID> getSpawnedMobEntities() {
        return spawnedMobEntities;
    }

    /**
     * Elimina del mundo todas las entidades que quedaron vivas de los mobs
     * de esta área. Se llama automáticamente al liberarse el área.
     */
    public void despawnTrackedMobs() {
        for (UUID uuid : spawnedMobEntities) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        spawnedMobEntities.clear();
        for (MobSpawnDefinition def : mobs.values()) {
            def.getLiveEntities().clear();
        }
    }

    /**
     * Vuelve el área a su estado "libre": sin ventana abierta, sin bloqueo,
     * sin jugadores registrados, y sin mobs vivos de este intento. Se usa
     * cuando termina un intento (o cuando todos salen antes de que arranque)
     * para que se pueda usar de nuevo.
     */
    public void resetWindowState() {
        windowOpen = false;
        locked = false;
        joiners.clear();
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        despawnTrackedMobs();
    }
}
