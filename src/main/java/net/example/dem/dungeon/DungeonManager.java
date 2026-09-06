package net.example.dem.dungeon;

import net.example.dem.area.AreaManager;
import net.example.dem.area.DoorDefinition;
import net.example.dem.area.DungeonArea;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Administra los dungeons: carga/guarda dungeons.yml y aplica la lógica de
 * "arrancar la corrida" (cerrar la entrada) y "liberar" (reabrir y resetear
 * todas las etapas), que las usan AreaMoveListener y DungeonDeathListener.
 */
public class DungeonManager {

    private final Plugin plugin;
    private final AreaManager areaManager;
    private final File file;
    private final Map<String, Dungeon> dungeons = new LinkedHashMap<>();

    public DungeonManager(Plugin plugin, AreaManager areaManager) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.file = new File(plugin.getDataFolder(), "dungeons.yml");
    }

    public void load() {
        dungeons.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("dungeons");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) continue;

            Dungeon dungeon = new Dungeon(id);
            dungeon.getStages().addAll(s.getStringList("stages"));
            dungeon.setEntranceDoorArea(s.getString("entrance-door-area", null));
            dungeon.setEntranceDoorId(s.getString("entrance-door-id", null));
            if (s.isSet("kick.world")) {
                dungeon.loadRawKickPoint(
                        s.getString("kick.world"),
                        s.getDouble("kick.x"), s.getDouble("kick.y"), s.getDouble("kick.z"),
                        (float) s.getDouble("kick.yaw"), (float) s.getDouble("kick.pitch"));
            }
            dungeons.put(id.toLowerCase(), dungeon);
        }
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        for (Dungeon d : dungeons.values()) {
            String base = "dungeons." + d.getId();
            config.set(base + ".stages", d.getStages());
            config.set(base + ".entrance-door-area", d.getEntranceDoorArea());
            config.set(base + ".entrance-door-id", d.getEntranceDoorId());
            if (d.hasKickPoint()) {
                config.set(base + ".kick.world", d.getKickWorld());
                config.set(base + ".kick.x", d.getKickX());
                config.set(base + ".kick.y", d.getKickY());
                config.set(base + ".kick.z", d.getKickZ());
                config.set(base + ".kick.yaw", (double) d.getKickYaw());
                config.set(base + ".kick.pitch", (double) d.getKickPitch());
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar dungeons.yml: " + e.getMessage());
        }
    }

    public Map<String, Dungeon> getDungeons() {
        return dungeons;
    }

    public Dungeon getDungeon(String id) {
        return dungeons.get(id.toLowerCase());
    }

    public void addDungeon(Dungeon dungeon) {
        dungeons.put(dungeon.getId().toLowerCase(), dungeon);
    }

    public boolean removeDungeon(String id) {
        return dungeons.remove(id.toLowerCase()) != null;
    }

    /** ¿A qué dungeon pertenece esta área como una de sus etapas? null si es independiente. */
    public Dungeon findDungeonByArea(String areaName) {
        for (Dungeon d : dungeons.values()) {
            for (String stage : d.getStages()) {
                if (stage.equalsIgnoreCase(areaName)) {
                    return d;
                }
            }
        }
        return null;
    }

    public boolean isEntranceStage(Dungeon dungeon, String areaName) {
        return !dungeon.getStages().isEmpty() && dungeon.getStages().get(0).equalsIgnoreCase(areaName);
    }

    /** ¿Queda alguien parado en cualquiera de las etapas de este dungeon ahora mismo? */
    public boolean hasAnyoneInside(Dungeon dungeon) {
        for (String stageName : dungeon.getStages()) {
            DungeonArea area = areaManager.getArea(stageName);
            if (area != null && !area.getJoiners().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private DoorDefinition resolveEntranceDoor(Dungeon dungeon) {
        if (dungeon.getEntranceDoorArea() == null || dungeon.getEntranceDoorId() == null) {
            return null;
        }
        DungeonArea area = areaManager.getArea(dungeon.getEntranceDoorArea());
        if (area == null) {
            return null;
        }
        return area.getDoor(dungeon.getEntranceDoorId());
    }

    public void closeEntrance(Dungeon dungeon) {
        DoorDefinition door = resolveEntranceDoor(dungeon);
        if (door != null) {
            door.close();
        }
    }

    public void openEntrance(Dungeon dungeon) {
        DoorDefinition door = resolveEntranceDoor(dungeon);
        if (door != null) {
            door.open();
        }
    }

    /** Arranca la corrida: marca los participantes y cierra la puerta de entrada. */
    public void startDungeon(Dungeon dungeon, Set<UUID> joiners) {
        dungeon.setInProgress(true);
        dungeon.getParticipants().clear();
        dungeon.getParticipants().addAll(joiners);
        closeEntrance(dungeon);
    }

    /**
     * Libera todo el dungeon: reabre la entrada y resetea (desbloquea +
     * despawnea mobs) cada una de sus etapas, hayan sido superadas o no.
     */
    public void release(Dungeon dungeon) {
        dungeon.setInProgress(false);
        dungeon.getParticipants().clear();
        for (String stageName : dungeon.getStages()) {
            DungeonArea area = areaManager.getArea(stageName);
            if (area != null) {
                area.resetWindowState();
            }
        }
        openEntrance(dungeon);
    }
}
