package net.example.dem.mob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Configuración de un mob personalizado que pertenece a UNA sola área (no es
 * una biblioteca global). Se persiste dentro de areas.yml, en la sección
 * "mobs" de esa área.
 *
 * La posición de spawn es FIJA: se captura parado en el lugar con
 * "/dem area mob setspawnhere" (o el botón equivalente del GUI), no depende
 * de dónde esté el jugador que dispara la aparición.
 */
public class MobSpawnDefinition {

    /** Un efecto de poción guardado para aplicar al spawnear. */
    public static class StoredPotionEffect {
        private final PotionEffectType type;
        private final int amplifier;
        private final int durationSeconds;

        public StoredPotionEffect(PotionEffectType type, int amplifier, int durationSeconds) {
            this.type = type;
            this.amplifier = amplifier;
            this.durationSeconds = durationSeconds;
        }

        public PotionEffectType getType() {
            return type;
        }

        public int getAmplifier() {
            return amplifier;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }
    }

    private final String id;
    private EntityType baseType;
    private String displayName; // null = sin nombre personalizado

    private double health = 20.0;
    private double scale = 1.0;
    private double speed = -1;  // -1 = usar el valor por defecto del mob
    private int delaySeconds = 0;
    private int amount = 1;

    // Ubicación fija de spawn (null hasta que se configure con setspawnhere)
    private String world;
    private Double x;
    private Double y;
    private Double z;
    private float yaw;
    private float pitch;

    private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    private final Map<EquipmentSlot, Boolean> dropOnDeath = new EnumMap<>(EquipmentSlot.class);
    private final List<StoredPotionEffect> potionEffects = new ArrayList<>();

    private boolean noAi = false;
    private boolean silent = false;
    private boolean invulnerable = false;
    private boolean glowing = false;
    private boolean baby = false;

    private String tag;       // null = sin etiqueta automática
    private String lootTable; // null = sin loot al morir

    // Estado en vivo (NO se persiste): entidades actualmente spawneadas por esta definición
    private final transient List<UUID> liveEntities = new ArrayList<>();

    public MobSpawnDefinition(String id, EntityType baseType) {
        this.id = id;
        this.baseType = baseType;
    }

    public String getId() {
        return id;
    }

    public EntityType getBaseType() {
        return baseType;
    }

    public void setBaseType(EntityType baseType) {
        this.baseType = baseType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = Math.max(1, health);
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = Math.max(0.1, Math.min(16.0, scale));
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(int delaySeconds) {
        this.delaySeconds = Math.max(0, delaySeconds);
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }

    public boolean hasSpawnLocation() {
        return world != null && x != null && y != null && z != null;
    }

    public void setSpawnLocation(Location loc) {
        this.world = loc.getWorld().getName();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.yaw = loc.getYaw();
        this.pitch = loc.getPitch();
    }

    /** Usado solo por el manager al cargar desde disco. */
    public void loadRawLocation(String world, Double x, Double y, Double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Location getSpawnLocation() {
        if (!hasSpawnLocation()) {
            return null;
        }
        World w = Bukkit.getWorld(world);
        if (w == null) {
            return null;
        }
        return new Location(w, x, y, z, yaw, pitch);
    }

    public String getWorldName() {
        return world;
    }

    public Double getRawX() {
        return x;
    }

    public Double getRawY() {
        return y;
    }

    public Double getRawZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public Map<EquipmentSlot, ItemStack> getEquipment() {
        return equipment;
    }

    public Map<EquipmentSlot, Boolean> getDropOnDeath() {
        return dropOnDeath;
    }

    public List<StoredPotionEffect> getPotionEffects() {
        return potionEffects;
    }

    public boolean isNoAi() {
        return noAi;
    }

    public void setNoAi(boolean noAi) {
        this.noAi = noAi;
    }

    public boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    public boolean isInvulnerable() {
        return invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
    }

    public boolean isBaby() {
        return baby;
    }

    public void setBaby(boolean baby) {
        this.baby = baby;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getLootTable() {
        return lootTable;
    }

    public void setLootTable(String lootTable) {
        this.lootTable = lootTable;
    }

    public List<UUID> getLiveEntities() {
        return liveEntities;
    }
}
