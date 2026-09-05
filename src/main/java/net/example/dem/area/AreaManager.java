package net.example.dem.area;

import net.example.dem.DEMPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class AreaManager {

    private final DEMPlugin plugin;
    private final Map<String, DungeonArea> areas = new LinkedHashMap<>();
    private File file;

    public AreaManager(DEMPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "areas.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection areasSection = config.getConfigurationSection("areas");
        if (areasSection == null) {
            return;
        }

        for (String name : areasSection.getKeys(false)) {
            ConfigurationSection s = areasSection.getConfigurationSection(name);
            if (s == null) continue;

            DungeonArea area = new DungeonArea(
                    name,
                    s.getString("world"),
                    s.getInt("minX"), s.getInt("minY"), s.getInt("minZ"),
                    s.getInt("maxX"), s.getInt("maxY"), s.getInt("maxZ")
            );
            area.getEnterCommands().addAll(s.getStringList("enter-commands"));
            area.getLeaveCommands().addAll(s.getStringList("leave-commands"));
            areas.put(name.toLowerCase(), area);
        }
    }

    public void save() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "areas.yml");
        }
        plugin.getDataFolder().mkdirs();

        FileConfiguration config = new YamlConfiguration();
        for (DungeonArea area : areas.values()) {
            String base = "areas." + area.getName();
            config.set(base + ".world", area.getWorld());
            config.set(base + ".minX", area.getMinX());
            config.set(base + ".minY", area.getMinY());
            config.set(base + ".minZ", area.getMinZ());
            config.set(base + ".maxX", area.getMaxX());
            config.set(base + ".maxY", area.getMaxY());
            config.set(base + ".maxZ", area.getMaxZ());
            config.set(base + ".enter-commands", area.getEnterCommands());
            config.set(base + ".leave-commands", area.getLeaveCommands());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar areas.yml: " + e.getMessage());
        }
    }

    public void addArea(DungeonArea area) {
        areas.put(area.getName().toLowerCase(), area);
        save();
    }

    public boolean removeArea(String name) {
        boolean removed = areas.remove(name.toLowerCase()) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public DungeonArea getArea(String name) {
        return areas.get(name.toLowerCase());
    }

    public Map<String, DungeonArea> getAreas() {
        return areas;
    }
}
