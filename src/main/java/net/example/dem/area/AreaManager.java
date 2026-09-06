package net.example.dem.area;

import net.example.dem.DEMPlugin;
import net.example.dem.mob.MobSpawnDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
            area.setJoinWindowSeconds(s.getInt("join-window-seconds", 0));
            area.getStartCommands().addAll(s.getStringList("start-commands"));
            loadMobs(s, area);
            loadDoors(s, area);
            areas.put(name.toLowerCase(), area);
        }
    }

    private void loadDoors(ConfigurationSection areaSection, DungeonArea area) {
        ConfigurationSection doorsSection = areaSection.getConfigurationSection("doors");
        if (doorsSection == null) {
            return;
        }
        for (String doorId : doorsSection.getKeys(false)) {
            ConfigurationSection ds = doorsSection.getConfigurationSection(doorId);
            if (ds == null) continue;

            String world = ds.getString("world");
            if (world == null) continue;

            List<DoorDefinition.CapturedBlock> blocks = new ArrayList<>();
            for (Map<?, ?> raw : ds.getMapList("blocks")) {
                try {
                    int x = ((Number) raw.get("x")).intValue();
                    int y = ((Number) raw.get("y")).intValue();
                    int z = ((Number) raw.get("z")).intValue();
                    String data = String.valueOf(raw.get("data"));
                    blocks.add(new DoorDefinition.CapturedBlock(x, y, z, data));
                } catch (Exception ignored) {
                }
            }

            DoorDefinition door = new DoorDefinition(doorId, world,
                    ds.getInt("min-x"), ds.getInt("min-y"), ds.getInt("min-z"),
                    ds.getInt("max-x"), ds.getInt("max-y"), ds.getInt("max-z"), blocks);
            area.addDoor(door);
        }
    }

    private void loadMobs(ConfigurationSection areaSection, DungeonArea area) {
        ConfigurationSection mobsSection = areaSection.getConfigurationSection("mobs");
        if (mobsSection == null) {
            return;
        }
        for (String mobId : mobsSection.getKeys(false)) {
            ConfigurationSection ms = mobsSection.getConfigurationSection(mobId);
            if (ms == null) continue;

            EntityType baseType;
            try {
                baseType = EntityType.valueOf(ms.getString("base-type", "ZOMBIE"));
            } catch (IllegalArgumentException ex) {
                baseType = EntityType.ZOMBIE;
            }
            MobSpawnDefinition mob = new MobSpawnDefinition(mobId, baseType);
            mob.setDisplayName(ms.getString("display-name", null));
            mob.setHealth(ms.getDouble("health", 20.0));
            mob.setScale(ms.getDouble("scale", 1.0));
            mob.setSpeed(ms.getDouble("speed", -1));
            mob.setDelaySeconds(ms.getInt("delay-seconds", 0));
            mob.setAmount(ms.getInt("amount", 1));

            if (ms.isSet("location.world")) {
                mob.loadRawLocation(
                        ms.getString("location.world"),
                        ms.getDouble("location.x"),
                        ms.getDouble("location.y"),
                        ms.getDouble("location.z"),
                        (float) ms.getDouble("location.yaw"),
                        (float) ms.getDouble("location.pitch"));
            }

            ConfigurationSection equipSection = ms.getConfigurationSection("equipment");
            if (equipSection != null) {
                for (String slotName : equipSection.getKeys(false)) {
                    try {
                        EquipmentSlot slot = EquipmentSlot.valueOf(slotName);
                        ItemStack item = equipSection.getItemStack(slotName);
                        if (item != null) {
                            mob.getEquipment().put(slot, item);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            ConfigurationSection dropSection = ms.getConfigurationSection("drop-on-death");
            if (dropSection != null) {
                for (String slotName : dropSection.getKeys(false)) {
                    try {
                        EquipmentSlot slot = EquipmentSlot.valueOf(slotName);
                        mob.getDropOnDeath().put(slot, dropSection.getBoolean(slotName));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            for (Map<?, ?> effectMap : ms.getMapList("potion-effects")) {
                try {
                    PotionEffectType type = PotionEffectType.getByName(String.valueOf(effectMap.get("type")));
                    if (type == null) continue;
                    int amplifier = ((Number) effectMap.get("amplifier")).intValue();
                    int duration = ((Number) effectMap.get("duration")).intValue();
                    mob.getPotionEffects().add(new MobSpawnDefinition.StoredPotionEffect(type, amplifier, duration));
                } catch (Exception ignored) {
                }
            }

            mob.setNoAi(ms.getBoolean("no-ai", false));
            mob.setSilent(ms.getBoolean("silent", false));
            mob.setInvulnerable(ms.getBoolean("invulnerable", false));
            mob.setGlowing(ms.getBoolean("glowing", false));
            mob.setBaby(ms.getBoolean("baby", false));
            mob.setTag(ms.getString("tag", null));
            mob.setLootTable(ms.getString("loot-table", null));

            area.addMob(mob);
        }
    }

    /**
     * Vuelve a leer areas.yml desde disco, descartando lo que había en memoria.
     * Útil cuando editas el archivo a mano y no quieres reiniciar el servidor.
     */
    public void reload() {
        areas.clear();
        load();
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
            config.set(base + ".join-window-seconds", area.getJoinWindowSeconds());
            config.set(base + ".start-commands", area.getStartCommands());
            saveMobs(config, base, area);
            saveDoors(config, base, area);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar areas.yml: " + e.getMessage());
        }
    }

    private void saveDoors(FileConfiguration config, String areaBase, DungeonArea area) {
        for (DoorDefinition door : area.getDoorsById().values()) {
            String doorBase = areaBase + ".doors." + door.getId();
            config.set(doorBase + ".world", door.getWorld());
            config.set(doorBase + ".min-x", door.getMinX());
            config.set(doorBase + ".min-y", door.getMinY());
            config.set(doorBase + ".min-z", door.getMinZ());
            config.set(doorBase + ".max-x", door.getMaxX());
            config.set(doorBase + ".max-y", door.getMaxY());
            config.set(doorBase + ".max-z", door.getMaxZ());

            List<Map<String, Object>> serialized = new ArrayList<>();
            for (DoorDefinition.CapturedBlock cb : door.getClosedBlocks()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("x", cb.x);
                m.put("y", cb.y);
                m.put("z", cb.z);
                m.put("data", cb.blockData);
                serialized.add(m);
            }
            config.set(doorBase + ".blocks", serialized);
        }
    }

    private void saveMobs(FileConfiguration config, String areaBase, DungeonArea area) {
        for (MobSpawnDefinition mob : area.getMobs()) {
            String mobBase = areaBase + ".mobs." + mob.getId();
            config.set(mobBase + ".base-type", mob.getBaseType().name());
            config.set(mobBase + ".display-name", mob.getDisplayName());
            config.set(mobBase + ".health", mob.getHealth());
            config.set(mobBase + ".scale", mob.getScale());
            config.set(mobBase + ".speed", mob.getSpeed());
            config.set(mobBase + ".delay-seconds", mob.getDelaySeconds());
            config.set(mobBase + ".amount", mob.getAmount());

            if (mob.hasSpawnLocation()) {
                config.set(mobBase + ".location.world", mob.getWorldName());
                config.set(mobBase + ".location.x", mob.getRawX());
                config.set(mobBase + ".location.y", mob.getRawY());
                config.set(mobBase + ".location.z", mob.getRawZ());
                config.set(mobBase + ".location.yaw", (double) mob.getYaw());
                config.set(mobBase + ".location.pitch", (double) mob.getPitch());
            }

            for (Map.Entry<EquipmentSlot, ItemStack> entry : mob.getEquipment().entrySet()) {
                config.set(mobBase + ".equipment." + entry.getKey().name(), entry.getValue());
            }
            for (Map.Entry<EquipmentSlot, Boolean> entry : mob.getDropOnDeath().entrySet()) {
                config.set(mobBase + ".drop-on-death." + entry.getKey().name(), entry.getValue());
            }

            List<Map<String, Object>> effectsSerialized = new ArrayList<>();
            for (MobSpawnDefinition.StoredPotionEffect effect : mob.getPotionEffects()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("type", effect.getType().getName());
                map.put("amplifier", effect.getAmplifier());
                map.put("duration", effect.getDurationSeconds());
                effectsSerialized.add(map);
            }
            config.set(mobBase + ".potion-effects", effectsSerialized);

            config.set(mobBase + ".no-ai", mob.isNoAi());
            config.set(mobBase + ".silent", mob.isSilent());
            config.set(mobBase + ".invulnerable", mob.isInvulnerable());
            config.set(mobBase + ".glowing", mob.isGlowing());
            config.set(mobBase + ".baby", mob.isBaby());
            config.set(mobBase + ".tag", mob.getTag());
            config.set(mobBase + ".loot-table", mob.getLootTable());
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
