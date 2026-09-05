package net.example.dem.loot;

import net.example.dem.DEMPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LootManager {

    private final DEMPlugin plugin;
    private final Map<String, LootTable> tables = new LinkedHashMap<>();
    private File file;

    public LootManager(DEMPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "loot.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection tablesSection = config.getConfigurationSection("tables");
        if (tablesSection == null) {
            return;
        }

        for (String name : tablesSection.getKeys(false)) {
            LootTable table = new LootTable(name);
            List<Map<?, ?>> entries = tablesSection.getMapList(name + ".entries");
            for (Map<?, ?> entry : entries) {
                int weight = ((Number) entry.get("weight")).intValue();
                String command = String.valueOf(entry.get("command"));
                table.addEntry(weight, command);
            }
            tables.put(name.toLowerCase(), table);
        }
    }

    public void save() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "loot.yml");
        }
        plugin.getDataFolder().mkdirs();

        FileConfiguration config = new YamlConfiguration();
        for (LootTable table : tables.values()) {
            List<Map<String, Object>> serialized = new java.util.ArrayList<>();
            for (LootEntry entry : table.getEntries()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("weight", entry.getWeight());
                map.put("command", entry.getCommand());
                serialized.add(map);
            }
            config.set("tables." + table.getName() + ".entries", serialized);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar loot.yml: " + e.getMessage());
        }
    }

    public LootTable createTable(String name) {
        LootTable table = new LootTable(name);
        tables.put(name.toLowerCase(), table);
        save();
        return table;
    }

    public LootTable getTable(String name) {
        return tables.get(name.toLowerCase());
    }

    public Map<String, LootTable> getTables() {
        return tables;
    }
}
