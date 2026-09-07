package net.example.dem.area;

import net.example.dem.mob.MobSpawnDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.Locale;

/**
 * Subcomandos de /dem area mob ... — crear y ajustar los mobs personalizados
 * de un área. Cada mob pertenece a UNA sola área (no es una biblioteca global).
 */
public class MobConfigModule {

    private final AreaManager areaManager;

    public MobConfigModule(AreaManager areaManager) {
        this.areaManager = areaManager;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create":
                return handleCreate(sender, args);
            case "setname":
                return withMob(sender, args, 3, (mob, rest) -> {
                    mob.setDisplayName(String.join(" ", rest));
                    return "Nombre de '" + mob.getId() + "' actualizado a: " + mob.getDisplayName();
                });
            case "sethealth":
                return withMob(sender, args, 3, (mob, rest) -> {
                    double value = parseDouble(rest[0], mob.getHealth());
                    mob.setHealth(value);
                    return "Vida de '" + mob.getId() + "' establecida en " + mob.getHealth();
                });
            case "setscale":
                return withMob(sender, args, 3, (mob, rest) -> {
                    double value = parseDouble(rest[0], mob.getScale());
                    mob.setScale(value);
                    return "Escala de '" + mob.getId() + "' establecida en " + mob.getScale();
                });
            case "setspeed":
                return withMob(sender, args, 3, (mob, rest) -> {
                    double value = parseDouble(rest[0], mob.getSpeed());
                    mob.setSpeed(value);
                    return "Velocidad de '" + mob.getId() + "' establecida en " + mob.getSpeed()
                            + " (-1 = usar la del mob vanilla).";
                });
            case "setdelay":
                return withMob(sender, args, 3, (mob, rest) -> {
                    mob.setDelaySeconds(parseInt(rest[0], mob.getDelaySeconds()));
                    return "Delay de aparición de '" + mob.getId() + "' establecido en "
                            + mob.getDelaySeconds() + "s.";
                });
            case "setamount":
                return withMob(sender, args, 3, (mob, rest) -> {
                    mob.setAmount(parseInt(rest[0], mob.getAmount()));
                    return "Cantidad de '" + mob.getId() + "' establecida en " + mob.getAmount();
                });
            case "setspawnhere":
                return handleSetSpawnHere(sender, args);
            case "addeffect":
                return handleAddEffect(sender, args);
            case "removeeffect":
                return handleRemoveEffect(sender, args);
            case "settag":
                return withMob(sender, args, 3, (mob, rest) -> {
                    String tag = rest[0].equalsIgnoreCase("clear") ? null : rest[0];
                    mob.setTag(tag);
                    return tag == null ? "Etiqueta de '" + mob.getId() + "' eliminada."
                            : "Etiqueta de '" + mob.getId() + "' establecida en: " + tag;
                });
            case "setloot":
                return withMob(sender, args, 3, (mob, rest) -> {
                    String table = rest[0].equalsIgnoreCase("clear") ? null : rest[0];
                    mob.setLootTable(table);
                    return table == null ? "Tabla de loot de '" + mob.getId() + "' eliminada."
                            : "'" + mob.getId() + "' ahora dropea la tabla de loot: " + table;
                });
            case "setequip":
                return handleSetEquip(sender, args);
            case "setequipdrop":
                return handleSetEquipDrop(sender, args);
            case "toggleai":
                return withMob(sender, args, 2, (mob, rest) -> {
                    mob.setNoAi(!mob.isNoAi());
                    return "'" + mob.getId() + "' ahora tiene IA " + (mob.isNoAi() ? "DESACTIVADA" : "activada") + ".";
                });
            case "togglesilent":
                return withMob(sender, args, 2, (mob, rest) -> {
                    mob.setSilent(!mob.isSilent());
                    return "'" + mob.getId() + "' ahora es " + (mob.isSilent() ? "silencioso" : "ruidoso normal") + ".";
                });
            case "toggleinvulnerable":
                return withMob(sender, args, 2, (mob, rest) -> {
                    mob.setInvulnerable(!mob.isInvulnerable());
                    return "'" + mob.getId() + "' ahora es " + (mob.isInvulnerable() ? "invulnerable" : "vulnerable normal") + ".";
                });
            case "toggleglow":
                return withMob(sender, args, 2, (mob, rest) -> {
                    mob.setGlowing(!mob.isGlowing());
                    return "'" + mob.getId() + "' ahora " + (mob.isGlowing() ? "brilla" : "no brilla") + ".";
                });
            case "togglebaby":
                return withMob(sender, args, 2, (mob, rest) -> {
                    mob.setBaby(!mob.isBaby());
                    return "'" + mob.getId() + "' ahora es " + (mob.isBaby() ? "bebé" : "adulto") + ".";
                });
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender, args);
            case "info":
                return handleInfo(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    // ----------------------------------------------------------------

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area mob create <área> <id> <tipoBase>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;

        String id = args[2];
        if (area.getMob(id) != null) {
            sender.sendMessage(ChatColor.RED + "Ya existe un mob '" + id + "' en '" + area.getName() + "'.");
            return true;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(args[3].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Tipo de entidad inválido: " + args[3]
                    + " (ej: ZOMBIE, SKELETON, SPIDER, CREEPER...).");
            return true;
        }

        MobSpawnDefinition mob = new MobSpawnDefinition(id, type);
        if (sender instanceof Player player) {
            mob.setSpawnLocation(player.getLocation());
        }
        area.addMob(mob);
        areaManager.save();
        sender.sendMessage(ChatColor.GREEN + "Mob '" + id + "' (" + type + ") creado en '" + area.getName()
                + "'" + (sender instanceof Player ? " con la posición donde estás parado." : ". Todavía sin posición "
                + "(usá /dem area mob setspawnhere " + area.getName() + " " + id + " parado donde debe aparecer).")); 
        return true;
    }

    private boolean handleSetSpawnHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede fijar la posición de spawn.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area mob setspawnhere <área> <id>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        MobSpawnDefinition mob = resolveMob(sender, area, args[2]);
        if (mob == null) return true;

        Location loc = player.getLocation();
        mob.setSpawnLocation(loc);
        areaManager.save();
        sender.sendMessage(ChatColor.GREEN + "Posición de spawn de '" + mob.getId() + "' fijada en tu ubicación actual.");
        return true;
    }

    private boolean handleAddEffect(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area mob addeffect <área> <id> <efecto> <amplificador> <segundos>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        MobSpawnDefinition mob = resolveMob(sender, area, args[2]);
        if (mob == null) return true;

        PotionEffectType type = PotionEffectType.getByName(args[3].toUpperCase(Locale.ROOT));
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Efecto inválido: " + args[3] + " (ej: SPEED, STRENGTH, INVISIBILITY...).");
            return true;
        }
        int amplifier = parseInt(args[4], 0);
        int seconds = parseInt(args[5], 30);
        mob.getPotionEffects().add(new MobSpawnDefinition.StoredPotionEffect(type, amplifier, seconds));
        areaManager.save();
        sender.sendMessage(ChatColor.GREEN + "Efecto agregado a '" + mob.getId() + "': " + type.getName()
                + " nivel " + (amplifier + 1) + " por " + seconds + "s.");
        return true;
    }

    private boolean handleRemoveEffect(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area mob removeeffect <área> <id> <índice>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        MobSpawnDefinition mob = resolveMob(sender, area, args[2]);
        if (mob == null) return true;

        int index = parseInt(args[3], -1) - 1; // el usuario ve índices desde 1
        if (index < 0 || index >= mob.getPotionEffects().size()) {
            sender.sendMessage(ChatColor.RED + "Índice inválido. Usá /dem area mob info " + area.getName()
                    + " " + mob.getId() + " para ver los índices.");
            return true;
        }
        mob.getPotionEffects().remove(index);
        areaManager.save();
        sender.sendMessage(ChatColor.GREEN + "Efecto eliminado de '" + mob.getId() + "'.");
        return true;
    }

    private boolean handleSetEquip(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede usar setequip (usa el ítem en tu mano).");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area mob setequip <área> <id> <hand|offhand|head|chest|legs|feet>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        MobSpawnDefinition mob = resolveMob(sender, area, args[2]);
        if (mob == null) return true;

        EquipmentSlot slot = parseSlot(args[3]);
        if (slot == null) {
            sender.sendMessage(ChatColor.RED + "Slot inválido: " + args[3] + " (hand, offhand, head, chest, legs, feet).");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            sender.sendMessage(ChatColor.RED + "Tenés que tener el ítem en tu mano principal.");
            return true;
        }
        mob.getEquipment().put(slot, item.clone());
        areaManager.save();
        sender.sendMessage(ChatColor.GREEN + "Equipamiento de '" + mob.getId() + "' (" + slot + ") actualizado con "
                + item.getType() + ".");
        return true;
    }

    private boolean handleSetEquipDrop(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area mob setequipdrop <área> <id> <slot> <true|false>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        MobSpawnDefinition mob = resolveMob(sender, area, args[2]);
        if (mob == null) return true;

        EquipmentSlot slot = parseSlot(args[3]);
        if (slot == null) {
            sender.sendMessage(ChatColor.RED + "Slot inválido: " + args[3] + " (hand, offhand, head, chest, legs, feet).");
            return true;
        }
        boolean drop = Boolean.parseBoolean(args[4]);
        mob.getDropOnDeath().put(slot, drop);
        areaManager.save();
        sender.sendMessage(ChatColor.GREEN + "'" + mob.getId() + "' (" + slot + ") ahora "
                + (drop ? "SÍ" : "NO") + " dropea ese ítem al morir.");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area mob remove <área> <id>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        if (!area.removeMob(args[2])) {
            sender.sendMessage(ChatColor.RED + "No existe el mob '" + args[2] + "' en '" + area.getName() + "'.");
            return true;
        }
        areaManager.save();
        sender.sendMessage(ChatColor.YELLOW + "Mob '" + args[2] + "' eliminado de '" + area.getName() + "'.");
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area mob list <área>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        if (area.getMobsById().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "'" + area.getName() + "' todavía no tiene mobs configurados.");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "Mobs de '" + area.getName() + "':");
        for (MobSpawnDefinition mob : area.getMobs()) {
            String posInfo = mob.hasSpawnLocation() ? "posición OK" : ChatColor.RED + "SIN posición";
            sender.sendMessage(ChatColor.GRAY + " - " + mob.getId() + " (" + mob.getBaseType() + ") "
                    + ChatColor.DARK_GRAY + "[" + posInfo + ChatColor.DARK_GRAY + "]");
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area mob info <área> <id>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        MobSpawnDefinition mob = resolveMob(sender, area, args[2]);
        if (mob == null) return true;

        sender.sendMessage(ChatColor.AQUA + "=== Mob '" + mob.getId() + "' (" + area.getName() + ") ===");
        sender.sendMessage(ChatColor.GRAY + "Tipo: " + mob.getBaseType());
        sender.sendMessage(ChatColor.GRAY + "Nombre: " + (mob.getDisplayName() == null ? "(sin nombre)" : mob.getDisplayName()));
        sender.sendMessage(ChatColor.GRAY + "Vida: " + mob.getHealth() + "  Escala: " + mob.getScale()
                + "  Velocidad: " + (mob.getSpeed() < 0 ? "por defecto" : mob.getSpeed()));
        sender.sendMessage(ChatColor.GRAY + "Delay: " + mob.getDelaySeconds() + "s  Cantidad: " + mob.getAmount());
        sender.sendMessage(ChatColor.GRAY + "Posición: " + (mob.hasSpawnLocation()
                ? mob.getWorldName() + " " + Math.round(mob.getRawX()) + "," + Math.round(mob.getRawY()) + "," + Math.round(mob.getRawZ())
                : ChatColor.RED + "sin configurar (usá setspawnhere)"));
        sender.sendMessage(ChatColor.GRAY + "Flags: noAI=" + mob.isNoAi() + " silent=" + mob.isSilent()
                + " invulnerable=" + mob.isInvulnerable() + " glowing=" + mob.isGlowing() + " baby=" + mob.isBaby());
        sender.sendMessage(ChatColor.GRAY + "Etiqueta: " + (mob.getTag() == null ? "(ninguna)" : mob.getTag()));
        sender.sendMessage(ChatColor.GRAY + "Loot al morir: " + (mob.getLootTable() == null ? "(ninguna)" : mob.getLootTable()));
        sender.sendMessage(ChatColor.GRAY + "Equipamiento: " + (mob.getEquipment().isEmpty() ? "(vacío)"
                : mob.getEquipment().keySet().toString()));
        if (!mob.getPotionEffects().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Efectos:");
            int i = 1;
            for (MobSpawnDefinition.StoredPotionEffect effect : mob.getPotionEffects()) {
                sender.sendMessage(ChatColor.GRAY + "  " + i + ") " + effect.getType().getName()
                        + " nivel " + (effect.getAmplifier() + 1) + " por " + effect.getDurationSeconds() + "s");
                i++;
            }
        }
        return true;
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private interface MobAction {
        String apply(MobSpawnDefinition mob, String[] rest);
    }

    /**
     * Azúcar sintáctica para las acciones "simples" con forma
     * <accion> <área> <id> [resto...], que son la mayoría.
     */
    private boolean withMob(CommandSender sender, String[] args, int minArgs, MobAction action) {
        if (args.length < minArgs) {
            sender.sendMessage(ChatColor.RED + "Faltan argumentos para /dem area mob " + args[0] + ".");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        MobSpawnDefinition mob = resolveMob(sender, area, args[2]);
        if (mob == null) return true;

        String[] rest = args.length > 3 ? Arrays.copyOfRange(args, 3, args.length) : new String[0];
        String message = action.apply(mob, rest);
        areaManager.save();
        sender.sendMessage(ChatColor.GREEN + message);
        return true;
    }

    private DungeonArea resolveArea(CommandSender sender, String name) {
        DungeonArea area = areaManager.getArea(name);
        if (area == null) {
            sender.sendMessage(ChatColor.RED + "No existe el área '" + name + "'.");
        }
        return area;
    }

    private MobSpawnDefinition resolveMob(CommandSender sender, DungeonArea area, String id) {
        MobSpawnDefinition mob = area.getMob(id);
        if (mob == null) {
            sender.sendMessage(ChatColor.RED + "No existe el mob '" + id + "' en '" + area.getName() + "'.");
        }
        return mob;
    }

    private EquipmentSlot parseSlot(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "hand", "mainhand" -> EquipmentSlot.HAND;
            case "offhand" -> EquipmentSlot.OFF_HAND;
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Uso: /dem area mob <accion> <área> [id] [...]");
        sender.sendMessage(ChatColor.GRAY + "create <área> <id> <tipoBase>");
        sender.sendMessage(ChatColor.GRAY + "setname|sethealth|setscale|setspeed|setdelay|setamount <área> <id> <valor>");
        sender.sendMessage(ChatColor.GRAY + "setspawnhere <área> <id>  (usa tu posición actual)");
        sender.sendMessage(ChatColor.GRAY + "addeffect <área> <id> <efecto> <amplificador> <segundos>");
        sender.sendMessage(ChatColor.GRAY + "removeeffect <área> <id> <índice>");
        sender.sendMessage(ChatColor.GRAY + "settag|setloot <área> <id> <valor|clear>");
        sender.sendMessage(ChatColor.GRAY + "setequip <área> <id> <slot>  (usa el ítem en tu mano)");
        sender.sendMessage(ChatColor.GRAY + "setequipdrop <área> <id> <slot> <true|false>");
        sender.sendMessage(ChatColor.GRAY + "toggleai|togglesilent|toggleinvulnerable|toggleglow|togglebaby <área> <id>");
        sender.sendMessage(ChatColor.GRAY + "remove|list|info <área> [id]");
    }
}
