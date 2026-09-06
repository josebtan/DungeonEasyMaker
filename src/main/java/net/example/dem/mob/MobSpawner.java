package net.example.dem.mob;

import net.example.dem.area.DungeonArea;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.util.Map;

/**
 * Convierte una {@link MobSpawnDefinition} en una entidad real del mundo,
 * respetando su delay individual, y deja registrada la entidad en el área
 * para que se pueda limpiar automáticamente cuando el área se libere.
 */
public class MobSpawner {

    /** Guardado en el PDC de la entidad para saber qué tabla de loot rolear al morir. */
    public static NamespacedKey lootTableKey(Plugin plugin) {
        return new NamespacedKey(plugin, "dungeoncore_mob_loot_table");
    }

    private MobSpawner() {
    }

    /** Programa la aparición de todos los mobs configurados en el área (respeta el delay de c/u). */
    public static void spawnAllForArea(Plugin plugin, DungeonArea area) {
        for (MobSpawnDefinition def : area.getMobs()) {
            if (!def.hasSpawnLocation()) {
                plugin.getLogger().warning("El mob '" + def.getId() + "' del área '" + area.getName()
                        + "' no tiene posición de spawn configurada (setspawnhere); se omite.");
                continue;
            }
            long delayTicks = Math.max(0, def.getDelaySeconds()) * 20L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> spawnOne(plugin, area, def), delayTicks);
        }
    }

    private static void spawnOne(Plugin plugin, DungeonArea area, MobSpawnDefinition def) {
        Location loc = def.getSpawnLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        for (int i = 0; i < def.getAmount(); i++) {
            Entity entity = loc.getWorld().spawnEntity(loc, def.getBaseType());
            if (!(entity instanceof LivingEntity living)) {
                entity.remove();
                continue;
            }
            applyAttributes(plugin, living, def);
            area.getSpawnedMobEntities().add(living.getUniqueId());
            def.getLiveEntities().add(living.getUniqueId());
        }
    }

    private static void applyAttributes(Plugin plugin, LivingEntity living, MobSpawnDefinition def) {
        if (def.getDisplayName() != null && !def.getDisplayName().isEmpty()) {
            living.setCustomName(ChatColor.translateAlternateColorCodes('&', def.getDisplayName()));
            living.setCustomNameVisible(true);
        }

        AttributeInstance maxHealth = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(def.getHealth());
            living.setHealth(def.getHealth());
        }

        AttributeInstance scaleAttr = living.getAttribute(Attribute.GENERIC_SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(def.getScale());
        }

        if (def.getSpeed() >= 0) {
            AttributeInstance speedAttr = living.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(def.getSpeed());
            }
        }

        living.setAI(!def.isNoAi());
        living.setSilent(def.isSilent());
        living.setInvulnerable(def.isInvulnerable());
        living.setGlowing(def.isGlowing());

        if (def.isBaby() && living instanceof Ageable ageable) {
            ageable.setBaby();
        }

        for (MobSpawnDefinition.StoredPotionEffect effect : def.getPotionEffects()) {
            living.addPotionEffect(new PotionEffect(effect.getType(),
                    effect.getDurationSeconds() * 20, effect.getAmplifier(), true, false));
        }

        EntityEquipment equipment = living.getEquipment();
        if (equipment != null) {
            for (Map.Entry<EquipmentSlot, ItemStack> entry : def.getEquipment().entrySet()) {
                equipment.setItem(entry.getKey(), entry.getValue());
                Boolean drop = def.getDropOnDeath().get(entry.getKey());
                equipment.setDropChance(entry.getKey(), (drop != null && drop) ? 1.0f : 0.0f);
            }
        }

        if (def.getTag() != null && !def.getTag().isEmpty()) {
            living.addScoreboardTag(def.getTag());
        }

        if (def.getLootTable() != null && !def.getLootTable().isEmpty()) {
            living.getPersistentDataContainer().set(lootTableKey(plugin), PersistentDataType.STRING, def.getLootTable());
        }
    }
}
