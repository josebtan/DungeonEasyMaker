package net.example.dem.gui;

import net.example.dem.area.AreaManager;
import net.example.dem.area.DungeonArea;
import net.example.dem.area.SelectionListener;
import net.example.dem.gui.GuiHolders.AreaMenuHolder;
import net.example.dem.gui.GuiHolders.CommandListMenuHolder;
import net.example.dem.gui.GuiHolders.MainMenuHolder;
import net.example.dem.gui.GuiHolders.MobEditorMenuHolder;
import net.example.dem.gui.GuiHolders.MobEquipMenuHolder;
import net.example.dem.gui.GuiHolders.MobListMenuHolder;
import net.example.dem.gui.GuiHolders.MobTypePickerHolder;
import net.example.dem.mob.MobSpawnDefinition;
import net.example.dem.util.AreaVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Menú de configuración de áreas basado en inventarios (estilo GUI de plugins
 * como EssentialsX/CMI), para no depender de escribir comandos largos a mano.
 *
 * Flujo:
 *  - /dem gui abre el menú principal: lista de áreas + "crear área nueva".
 *  - Cada área abre un submenú con sus botones (ver contorno, comandos de
 *    entrada/salida/arranque, ventana de ingreso, eliminar).
 *  - Las listas de comandos (entrada/salida/arranque) se pueden ver y borrar
 *    desde el GUI; agregar uno nuevo pide escribirlo en el chat (Minecraft no
 *    tiene un campo de texto nativo en inventarios).
 */
public class GuiManager {

    private static final String NAMESPACE = "dungeoncore";

    // Posiciones fijas del "muñeco de papel" en el editor de equipamiento.
    private static final Map<Integer, EquipmentSlot> EQUIP_SLOT_POSITIONS = new LinkedHashMap<>();
    static {
        EQUIP_SLOT_POSITIONS.put(10, EquipmentSlot.HEAD);
        EQUIP_SLOT_POSITIONS.put(11, EquipmentSlot.CHEST);
        EQUIP_SLOT_POSITIONS.put(12, EquipmentSlot.LEGS);
        EQUIP_SLOT_POSITIONS.put(13, EquipmentSlot.FEET);
        EQUIP_SLOT_POSITIONS.put(15, EquipmentSlot.HAND);
        EQUIP_SLOT_POSITIONS.put(16, EquipmentSlot.OFF_HAND);
    }

    // Tipos de entidad elegibles para el selector de "crear mob" (con huevo de
    // spawn o no): cualquier entidad viva, salvo jugadores, armor stands y
    // cosas que no tiene sentido spawnear a mano.
    private static final List<EntityType> SPAWNABLE_TYPES = new ArrayList<>();
    static {
        for (EntityType type : EntityType.values()) {
            if (!type.isAlive()) continue;
            if (type == EntityType.PLAYER || type == EntityType.ARMOR_STAND || type == EntityType.UNKNOWN) continue;
            SPAWNABLE_TYPES.add(type);
        }
        SPAWNABLE_TYPES.sort((a, b) -> a.name().compareTo(b.name()));
    }
    private static final int TYPES_PER_PAGE = 45;

    private final Plugin plugin;
    private final AreaManager areaManager;
    private final SelectionListener selectionListener;

    private final NamespacedKey actionKey;
    private final NamespacedKey areaKey;
    private final NamespacedKey indexKey;
    private final NamespacedKey mobKey;

    private final Map<UUID, PendingInput> pendingInputs = new HashMap<>();

    public GuiManager(Plugin plugin, AreaManager areaManager, SelectionListener selectionListener) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.selectionListener = selectionListener;
        this.actionKey = new NamespacedKey(plugin, NAMESPACE + "_gui_action");
        this.areaKey = new NamespacedKey(plugin, NAMESPACE + "_gui_area");
        this.indexKey = new NamespacedKey(plugin, NAMESPACE + "_gui_index");
        this.mobKey = new NamespacedKey(plugin, NAMESPACE + "_gui_mob");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public boolean isDemGui(InventoryHolder holder) {
        return holder instanceof MainMenuHolder
                || holder instanceof AreaMenuHolder
                || holder instanceof CommandListMenuHolder
                || holder instanceof MobListMenuHolder
                || holder instanceof MobTypePickerHolder
                || holder instanceof MobEditorMenuHolder
                || holder instanceof MobEquipMenuHolder;
    }

    /**
     * Para el editor de equipamiento: las posiciones del muñeco de papel se
     * dejan pasar sin cancelar (necesitan comportamiento normal de inventario
     * para poder poner/sacar ítems); todo lo demás en ese menú sí se cancela.
     */
    public boolean isFreeInteractSlot(InventoryHolder holder, int rawSlot) {
        return holder instanceof MobEquipMenuHolder && EQUIP_SLOT_POSITIONS.containsKey(rawSlot);
    }

    /**
     * Si el jugador cierra el editor de equipamiento sin apretar "Guardar y
     * volver", cualquier ítem que haya puesto en los slots del muñeco de
     * papel se le devuelve (si no, se perdería: el inventario del GUI es
     * descartable y no vuelve a existir).
     */
    public void returnEquipItemsOnClose(Player player, Inventory inv) {
        for (Integer slot : EQUIP_SLOT_POSITIONS.keySet()) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                for (ItemStack extra : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
            }
        }
    }

    public boolean hasPendingInput(UUID uuid) {
        return pendingInputs.containsKey(uuid);
    }

    // ----------------------------------------------------------------
    // Menú principal
    // ----------------------------------------------------------------

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new MainMenuHolder(), 54,
                ChatColor.DARK_PURPLE + "DEM - Áreas");
        ((MainMenuHolder) inv.getHolder()).setInventory(inv);

        int slot = 0;
        for (DungeonArea area : areaManager.getAreas().values()) {
            if (slot >= 45) break; // deja libre la última fila para los botones generales
            inv.setItem(slot, buildAreaIcon(area));
            slot++;
        }

        inv.setItem(48, buildItem(Material.BLAZE_ROD, ChatColor.GOLD + "Recibir varita",
                List.of(ChatColor.GRAY + "Click izq/der en un bloque",
                        ChatColor.GRAY + "para marcar las 2 esquinas",
                        ChatColor.GRAY + "de una nueva área."),
                "wand", null, null));
        inv.setItem(49, buildItem(Material.EMERALD, ChatColor.GREEN + "Crear área nueva",
                buildCreateAreaLore(player), "create_area", null, null));
        inv.setItem(50, buildItem(Material.COMPASS, ChatColor.AQUA + "Recargar (reload)",
                List.of(ChatColor.GRAY + "Vuelve a leer areas.yml desde disco"),
                "reload", null, null));

        player.openInventory(inv);
    }

    private ItemStack buildAreaIcon(DungeonArea area) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Mundo: " + area.getWorld());
        lore.add(ChatColor.GRAY + "Entrada: " + area.getEnterCommands().size()
                + "  Salida: " + area.getLeaveCommands().size());
        if (area.getJoinWindowSeconds() > 0) {
            lore.add(ChatColor.GRAY + "Ventana de ingreso: " + area.getJoinWindowSeconds() + "s");
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click para configurar");
        return buildItem(Material.CHEST, ChatColor.GOLD + area.getName(), lore, "open_area", area.getName(), null);
    }

    private List<String> buildCreateAreaLore(Player player) {
        Location p1 = selectionListener.getPos1(player.getUniqueId());
        Location p2 = selectionListener.getPos2(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        if (p1 == null || p2 == null) {
            lore.add(ChatColor.GRAY + "Primero marca las 2 esquinas");
            lore.add(ChatColor.GRAY + "con la varita en el mundo.");
            lore.add(ChatColor.YELLOW + "Click para recibir la varita");
        } else {
            lore.add(ChatColor.GRAY + "Ya tenés 2 esquinas marcadas.");
            lore.add(ChatColor.YELLOW + "Click y escribí el nombre");
            lore.add(ChatColor.YELLOW + "en el chat para crearla.");
        }
        return lore;
    }

    // ----------------------------------------------------------------
    // Menú de una área
    // ----------------------------------------------------------------

    public void openAreaMenu(Player player, String areaName) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }

        Inventory inv = Bukkit.createInventory(new AreaMenuHolder(area.getName()), 36,
                ChatColor.DARK_PURPLE + "Área: " + area.getName());
        ((AreaMenuHolder) inv.getHolder()).setInventory(inv);

        inv.setItem(10, buildItem(Material.SPYGLASS, ChatColor.AQUA + "Ver contorno",
                List.of(ChatColor.GRAY + "Muestra el área con partículas 5s"),
                "show", area.getName(), null));

        boolean isSelected = area.getName().equalsIgnoreCase(selectionListener.getSelectedArea(player.getUniqueId()));
        inv.setItem(11, buildItem(Material.ARROW,
                isSelected ? ChatColor.GREEN + "Área seleccionada ✔" : ChatColor.GRAY + "Seleccionar área",
                List.of(ChatColor.GRAY + "La usan /dem area addenterhere",
                        ChatColor.GRAY + "y comandos similares."),
                "toggle_select", area.getName(), null));

        inv.setItem(13, buildItem(Material.WRITABLE_BOOK,
                ChatColor.YELLOW + "Comandos de entrada (" + area.getEnterCommands().size() + ")",
                List.of(ChatColor.GRAY + "Se ejecutan al entrar, uno por jugador"),
                "open_enter", area.getName(), null));

        inv.setItem(14, buildItem(Material.BOOK,
                ChatColor.YELLOW + "Comandos de salida (" + area.getLeaveCommands().size() + ")",
                List.of(ChatColor.GRAY + "Se ejecutan al salir del área"),
                "open_leave", area.getName(), null));

        inv.setItem(15, buildItem(Material.NETHER_STAR,
                ChatColor.LIGHT_PURPLE + "Comandos de arranque (" + area.getStartCommands().size() + ")",
                List.of(ChatColor.GRAY + "Corren 1 sola vez al",
                        ChatColor.GRAY + "cerrar la ventana de ingreso"),
                "open_start", area.getName(), null));

        inv.setItem(16, buildItem(Material.ZOMBIE_HEAD,
                ChatColor.DARK_GREEN + "Mobs (" + area.getMobsById().size() + ")",
                List.of(ChatColor.GRAY + "Mobs personalizados de esta área.",
                        ChatColor.GRAY + "Aparecen junto con los comandos",
                        ChatColor.GRAY + "de arranque, en su posición fija."),
                "open_mobs", area.getName(), null));

        inv.setItem(20, buildItem(Material.CLOCK,
                ChatColor.GOLD + "Ventana de ingreso: " + area.getJoinWindowSeconds() + "s",
                buildWindowLore(area), "window", area.getName(), null));

        inv.setItem(31, buildItem(Material.BARRIER, ChatColor.RED + "Eliminar área",
                List.of(ChatColor.GRAY + "Shift + click para eliminar",
                        ChatColor.GRAY + "(no se puede deshacer)"),
                "delete", area.getName(), null));

        inv.setItem(27, buildItem(Material.ARROW, ChatColor.WHITE + "« Volver", List.of(), "back_main", null, null));

        player.openInventory(inv);
    }

    private List<String> buildWindowLore(DungeonArea area) {
        List<String> lore = new ArrayList<>();
        if (area.getJoinWindowSeconds() <= 0) {
            lore.add(ChatColor.GRAY + "Desactivada: los comandos de entrada");
            lore.add(ChatColor.GRAY + "corren normal, uno por jugador.");
        } else {
            lore.add(ChatColor.GRAY + "Al entrar el 1er jugador arranca");
            lore.add(ChatColor.GRAY + "una cuenta regresiva de "
                    + area.getJoinWindowSeconds() + "s.");
            String estado = area.isLocked()
                    ? "bloqueada (" + area.getJoiners().size() + " dentro)"
                    : area.isWindowOpen()
                        ? "cuenta regresiva activa (" + area.getJoiners().size() + " unido/s)"
                        : "libre";
            lore.add(ChatColor.GRAY + "Estado actual: " + estado);
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click izq: +5s   Click der: -5s");
        lore.add(ChatColor.YELLOW + "Shift+izq: +30s   Shift+der: -30s");
        return lore;
    }

    // ----------------------------------------------------------------
    // Menú de lista de comandos (entrada / salida / arranque)
    // ----------------------------------------------------------------

    public void openCommandListMenu(Player player, String areaName, CommandListType type) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }

        List<String> commands = listFor(area, type);
        Inventory inv = Bukkit.createInventory(new CommandListMenuHolder(area.getName(), type), 54,
                ChatColor.DARK_PURPLE + "Comandos " + labelFor(type) + ": " + area.getName());
        ((CommandListMenuHolder) inv.getHolder()).setInventory(inv);

        int slot = 0;
        for (int i = 0; i < commands.size() && slot < 45; i++, slot++) {
            List<String> lore = new ArrayList<>(wrapCommand(commands.get(i)));
            lore.add("");
            lore.add(ChatColor.RED + "Click para eliminar");
            inv.setItem(slot, buildItem(Material.PAPER, ChatColor.WHITE + "Comando #" + (i + 1),
                    lore, "remove_command", area.getName(), i));
        }

        inv.setItem(49, buildItem(Material.EMERALD, ChatColor.GREEN + "Agregar comando",
                List.of(ChatColor.GRAY + "Click y escribilo en el chat.",
                        ChatColor.GRAY + "Soporta [player] [world] [x] [y] [z]",
                        ChatColor.GRAY + "y \"delay:<seg>|comando\"."),
                "add_command", area.getName(), null));
        inv.setItem(45, buildItem(Material.ARROW, ChatColor.WHITE + "« Volver", List.of(), "back_area", area.getName(), null));

        player.openInventory(inv);
    }

    private String labelFor(CommandListType type) {
        return switch (type) {
            case ENTER -> "de entrada";
            case LEAVE -> "de salida";
            case START -> "de arranque";
        };
    }

    private List<String> listFor(DungeonArea area, CommandListType type) {
        return switch (type) {
            case ENTER -> area.getEnterCommands();
            case LEAVE -> area.getLeaveCommands();
            case START -> area.getStartCommands();
        };
    }

    private List<String> wrapCommand(String cmd) {
        List<String> lines = new ArrayList<>();
        int max = 40;
        String remaining = cmd;
        if (remaining.isEmpty()) {
            lines.add(ChatColor.GRAY + "(vacío)");
            return lines;
        }
        while (remaining.length() > max) {
            int cut = remaining.lastIndexOf(' ', max);
            if (cut <= 0) cut = max;
            lines.add(ChatColor.GRAY + remaining.substring(0, cut));
            remaining = remaining.substring(cut).trim();
        }
        lines.add(ChatColor.GRAY + remaining);
        return lines;
    }

    // ----------------------------------------------------------------
    // Menú de mobs de un área
    // ----------------------------------------------------------------

    public void openMobListMenu(Player player, String areaName) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }

        Inventory inv = Bukkit.createInventory(new MobListMenuHolder(area.getName()), 54,
                ChatColor.DARK_PURPLE + "Mobs: " + area.getName());
        ((MobListMenuHolder) inv.getHolder()).setInventory(inv);

        int slot = 0;
        for (MobSpawnDefinition mob : area.getMobs()) {
            if (slot >= 45) break;
            inv.setItem(slot, buildMobIcon(mob));
            slot++;
        }

        inv.setItem(49, buildItem(Material.EMERALD, ChatColor.GREEN + "Crear mob nuevo",
                List.of(ChatColor.GRAY + "Elegí el tipo con un huevo de spawn",
                        ChatColor.GRAY + "y después le ponés el nombre/id."),
                "create_mob", area.getName(), null));
        inv.setItem(45, buildItem(Material.ARROW, ChatColor.WHITE + "« Volver", List.of(), "back_area", area.getName(), null));

        player.openInventory(inv);
    }

    /** Selector paginado de tipo base, con huevos de spawn (o cabeza/spawner si el tipo no tiene huevo). */
    public void openMobTypePicker(Player player, String areaName, int page) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(SPAWNABLE_TYPES.size() / (double) TYPES_PER_PAGE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(new MobTypePickerHolder(area.getName(), safePage), 54,
                ChatColor.DARK_PURPLE + "Elegí el tipo (" + (safePage + 1) + "/" + totalPages + ")");
        ((MobTypePickerHolder) inv.getHolder()).setInventory(inv);

        int start = safePage * TYPES_PER_PAGE;
        int end = Math.min(start + TYPES_PER_PAGE, SPAWNABLE_TYPES.size());
        int slot = 0;
        for (int i = start; i < end; i++, slot++) {
            EntityType type = SPAWNABLE_TYPES.get(i);
            inv.setItem(slot, buildItem(entityIcon(type), ChatColor.YELLOW + prettyName(type),
                    List.of(ChatColor.YELLOW + "Click para elegir este tipo"),
                    "pick_type", area.getName(), null, type.name()));
        }

        if (safePage > 0) {
            inv.setItem(45, buildItem(Material.ARROW, ChatColor.WHITE + "« Página anterior",
                    List.of(), "prev_type_page", area.getName(), safePage - 1));
        }
        inv.setItem(49, buildItem(Material.BARRIER, ChatColor.RED + "Cancelar", List.of(), "back_mob_list", area.getName(), null));
        if (safePage < totalPages - 1) {
            inv.setItem(53, buildItem(Material.ARROW, ChatColor.WHITE + "Página siguiente »",
                    List.of(), "next_type_page", area.getName(), safePage + 1));
        }

        player.openInventory(inv);
    }

    private String prettyName(EntityType type) {
        String[] words = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private ItemStack buildMobIcon(MobSpawnDefinition mob) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Tipo: " + mob.getBaseType());
        lore.add(ChatColor.GRAY + "Vida: " + mob.getHealth() + "  Escala: " + mob.getScale());
        lore.add(ChatColor.GRAY + "Delay: " + mob.getDelaySeconds() + "s  Cantidad: " + mob.getAmount());
        lore.add(mob.hasSpawnLocation()
                ? ChatColor.GREEN + "Posición configurada"
                : ChatColor.RED + "Sin posición (usá 'Fijar posición aquí')");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click para editar");
        String display = mob.getDisplayName() != null ? mob.getDisplayName() : mob.getId();
        return buildItem(entityIcon(mob.getBaseType()), ChatColor.GOLD + display, lore,
                "open_mob", null, null, mob.getId());
    }

    /** Un ícono razonable para cada tipo de mob (huevo de spawn si existe, cabeza si no). */
    private Material entityIcon(EntityType type) {
        try {
            Material egg = Material.valueOf(type.name() + "_SPAWN_EGG");
            if (egg.isItem()) {
                return egg;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return switch (type) {
            case ZOMBIE -> Material.ZOMBIE_HEAD;
            case SKELETON -> Material.SKELETON_SKULL;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case PLAYER -> Material.PLAYER_HEAD;
            default -> Material.SPAWNER;
        };
    }

    public void openMobEditorMenu(Player player, String areaName, String mobId) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }
        MobSpawnDefinition mob = area.getMob(mobId);
        if (mob == null) {
            player.sendMessage(ChatColor.RED + "Ese mob ya no existe.");
            openMobListMenu(player, areaName);
            return;
        }

        Inventory inv = Bukkit.createInventory(new MobEditorMenuHolder(area.getName(), mob.getId()), 54,
                ChatColor.DARK_PURPLE + "Mob: " + mob.getId());
        ((MobEditorMenuHolder) inv.getHolder()).setInventory(inv);

        inv.setItem(10, buildItem(Material.NAME_TAG, ChatColor.YELLOW + "Nombre",
                List.of(ChatColor.GRAY + "Actual: " + (mob.getDisplayName() == null ? "(sin nombre)" : mob.getDisplayName()),
                        ChatColor.YELLOW + "Click para escribirlo en el chat"),
                "edit_name", null, null, mob.getId()));

        inv.setItem(11, buildItem(Material.APPLE, ChatColor.RED + "Vida: " + mob.getHealth(),
                List.of(ChatColor.YELLOW + "Click izq: +1   Click der: -1",
                        ChatColor.YELLOW + "Shift+izq: +10   Shift+der: -10"),
                "edit_health", null, null, mob.getId()));

        inv.setItem(12, buildItem(Material.SLIME_BALL, ChatColor.LIGHT_PURPLE + "Escala: " + round2(mob.getScale()),
                List.of(ChatColor.YELLOW + "Click izq: +0.25   Click der: -0.25",
                        ChatColor.YELLOW + "Shift+izq: +1   Shift+der: -1"),
                "edit_scale", null, null, mob.getId()));

        inv.setItem(13, buildItem(Material.SUGAR, ChatColor.AQUA + "Velocidad: "
                        + (mob.getSpeed() < 0 ? "por defecto" : round2(mob.getSpeed())),
                List.of(ChatColor.GRAY + "-1 = usa la del mob vanilla",
                        ChatColor.YELLOW + "Click izq: +0.05   Click der: -0.05",
                        ChatColor.YELLOW + "Shift+click: vuelve a 'por defecto'"),
                "edit_speed", null, null, mob.getId()));

        inv.setItem(14, buildItem(Material.CLOCK, ChatColor.GOLD + "Delay: " + mob.getDelaySeconds() + "s",
                List.of(ChatColor.YELLOW + "Click izq: +1s   Click der: -1s",
                        ChatColor.YELLOW + "Shift+izq: +5s   Shift+der: -5s"),
                "edit_delay", null, null, mob.getId()));

        inv.setItem(15, buildItem(Material.TOTEM_OF_UNDYING, ChatColor.GREEN + "Cantidad: " + mob.getAmount(),
                List.of(ChatColor.YELLOW + "Click izq: +1   Click der: -1",
                        ChatColor.YELLOW + "Shift+izq: +5   Shift+der: -5"),
                "edit_amount", null, null, mob.getId()));

        inv.setItem(19, buildItem(Material.COMPASS, ChatColor.AQUA + "Fijar posición aquí",
                List.of(mob.hasSpawnLocation() ? ChatColor.GREEN + "Ya tiene posición configurada"
                                : ChatColor.RED + "Todavía no tiene posición",
                        ChatColor.YELLOW + "Click para usar tu ubicación actual"),
                "set_spawn_here", null, null, mob.getId()));

        inv.setItem(20, buildItem(Material.IRON_CHESTPLATE, ChatColor.GOLD + "Equipamiento ("
                        + mob.getEquipment().size() + "/6)",
                List.of(ChatColor.GRAY + "Casco, pecho, piernas, botas,",
                        ChatColor.GRAY + "mano y mano secundaria.",
                        ChatColor.YELLOW + "Click para abrir"),
                "open_equip", null, null, mob.getId()));

        inv.setItem(21, buildItem(Material.POTION, ChatColor.DARK_AQUA + "Efectos (" + mob.getPotionEffects().size() + ")",
                List.of(ChatColor.YELLOW + "Click: agregar (escribí en el chat",
                        ChatColor.YELLOW + "\"tipo amplificador segundos\")",
                        ChatColor.YELLOW + "Shift+click: borra el último"),
                "edit_effect", null, null, mob.getId()));

        inv.setItem(22, buildItem(Material.NAME_TAG, ChatColor.YELLOW + "Etiqueta",
                List.of(ChatColor.GRAY + "Actual: " + (mob.getTag() == null ? "(ninguna)" : mob.getTag()),
                        ChatColor.GRAY + "Para usar con /dem objective watch",
                        ChatColor.YELLOW + "Click para escribirla ('clear' para borrarla)"),
                "edit_tag", null, null, mob.getId()));

        inv.setItem(23, buildItem(Material.CHEST, ChatColor.YELLOW + "Loot al morir",
                List.of(ChatColor.GRAY + "Actual: " + (mob.getLootTable() == null ? "(ninguna)" : mob.getLootTable()),
                        ChatColor.YELLOW + "Click para escribir el nombre de la tabla",
                        ChatColor.YELLOW + "('clear' para borrarla)"),
                "edit_loot", null, null, mob.getId()));

        inv.setItem(28, buildToggle(Material.REDSTONE_TORCH, "Sin IA", mob.isNoAi(), "toggle_ai", mob.getId()));
        inv.setItem(29, buildToggle(Material.WOOL, "Silencioso", mob.isSilent(), "toggle_silent", mob.getId()));
        inv.setItem(30, buildToggle(Material.SHIELD, "Invulnerable", mob.isInvulnerable(), "toggle_invulnerable", mob.getId()));
        inv.setItem(31, buildToggle(Material.GLOWSTONE_DUST, "Brillante", mob.isGlowing(), "toggle_glow", mob.getId()));
        inv.setItem(32, buildToggle(Material.EGG, "Bebé", mob.isBaby(), "toggle_baby", mob.getId()));

        inv.setItem(49, buildItem(Material.BARRIER, ChatColor.RED + "Eliminar mob",
                List.of(ChatColor.GRAY + "Shift + click para eliminar",
                        ChatColor.GRAY + "(no se puede deshacer)"),
                "delete_mob", null, null, mob.getId()));
        inv.setItem(45, buildItem(Material.ARROW, ChatColor.WHITE + "« Volver", List.of(), "back_mobs", area.getName(), null));

        player.openInventory(inv);
    }

    private ItemStack buildToggle(Material material, String label, boolean active, String action, String mobId) {
        String name = (active ? ChatColor.GREEN + "✔ " : ChatColor.GRAY + "✘ ") + label;
        List<String> lore = List.of(ChatColor.YELLOW + "Click para " + (active ? "desactivar" : "activar"));
        return buildItem(material, name, lore, action, null, null, mobId);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public void openMobEquipMenu(Player player, String areaName, String mobId) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }
        MobSpawnDefinition mob = area.getMob(mobId);
        if (mob == null) {
            player.sendMessage(ChatColor.RED + "Ese mob ya no existe.");
            openMobListMenu(player, areaName);
            return;
        }

        Inventory inv = Bukkit.createInventory(new MobEquipMenuHolder(area.getName(), mob.getId()), 27,
                ChatColor.DARK_PURPLE + "Equipo: " + mob.getId());
        ((MobEquipMenuHolder) inv.getHolder()).setInventory(inv);

        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), "noop", null, null);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler.clone());
        }
        for (Map.Entry<Integer, EquipmentSlot> entry : EQUIP_SLOT_POSITIONS.entrySet()) {
            ItemStack current = mob.getEquipment().get(entry.getValue());
            inv.setItem(entry.getKey(), current != null ? current.clone() : new ItemStack(Material.AIR));
        }
        inv.setItem(22, buildItem(Material.LIME_DYE, ChatColor.GREEN + "Guardar y volver",
                List.of(ChatColor.GRAY + "Guarda lo que haya en cada slot",
                        ChatColor.GRAY + "(casco, pecho, piernas, botas,",
                        ChatColor.GRAY + "mano, mano secundaria)"),
                "save_equip", null, null, null));

        player.openInventory(inv);
    }

    // ----------------------------------------------------------------
    // Manejo de clicks
    // ----------------------------------------------------------------

    public void handleClick(Player player, InventoryHolder holder, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        Integer index = meta.getPersistentDataContainer().get(indexKey, PersistentDataType.INTEGER);
        String itemMobId = meta.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);

        if (holder instanceof MainMenuHolder) {
            String areaName = meta.getPersistentDataContainer().get(areaKey, PersistentDataType.STRING);
            handleMainMenuAction(player, action, areaName);
        } else if (holder instanceof AreaMenuHolder h) {
            handleAreaMenuAction(player, h.getAreaName(), action, event);
        } else if (holder instanceof CommandListMenuHolder h) {
            handleCommandListAction(player, h.getAreaName(), h.getType(), action, index);
        } else if (holder instanceof MobListMenuHolder h) {
            handleMobListAction(player, h.getAreaName(), action, itemMobId);
        } else if (holder instanceof MobTypePickerHolder h) {
            handleMobTypePickerAction(player, h.getAreaName(), action, index, itemMobId);
        } else if (holder instanceof MobEditorMenuHolder h) {
            handleMobEditorAction(player, h.getAreaName(), h.getMobId(), action, event);
        } else if (holder instanceof MobEquipMenuHolder h) {
            handleMobEquipAction(player, h.getAreaName(), h.getMobId(), action);
        }
    }

    private void handleMainMenuAction(Player player, String action, String areaName) {
        switch (action) {
            case "wand" -> {
                player.getInventory().addItem(selectionListener.createWand());
                player.sendMessage(ChatColor.GREEN + "Varita recibida. Click izq = esquina 1, click der = esquina 2.");
            }
            case "reload" -> {
                areaManager.reload();
                player.sendMessage(ChatColor.GREEN + "Áreas recargadas desde disco.");
                openMainMenu(player);
            }
            case "create_area" -> handleCreateAreaButton(player);
            case "open_area" -> openAreaMenu(player, areaName);
            default -> { }
        }
    }

    private void handleCreateAreaButton(Player player) {
        Location p1 = selectionListener.getPos1(player.getUniqueId());
        Location p2 = selectionListener.getPos2(player.getUniqueId());

        if (p1 == null || p2 == null) {
            player.getInventory().addItem(selectionListener.createWand());
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Marca las 2 esquinas con la varita y abre "
                    + "el menú de nuevo (/dem gui) para ponerle nombre al área.");
            return;
        }
        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage(ChatColor.RED + "Las dos esquinas deben estar en el mismo mundo.");
            return;
        }

        pendingInputs.put(player.getUniqueId(), PendingInput.createArea());
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Escribí en el chat el nombre para la nueva área (o 'cancelar').");
    }

    private void handleAreaMenuAction(Player player, String areaName, String action, InventoryClickEvent event) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }

        switch (action) {
            case "back_main" -> openMainMenu(player);
            case "show" -> {
                AreaVisualizer.showOutline(plugin, player, area.getWorld(),
                        area.getMinX(), area.getMinY(), area.getMinZ(),
                        area.getMaxX(), area.getMaxY(), area.getMaxZ(), 5 * 20);
                player.sendMessage(ChatColor.GREEN + "Mostrando el contorno de '" + area.getName() + "'.");
            }
            case "toggle_select" -> {
                String selected = selectionListener.getSelectedArea(player.getUniqueId());
                if (area.getName().equalsIgnoreCase(selected)) {
                    selectionListener.clearSelectedArea(player.getUniqueId());
                } else {
                    selectionListener.setSelectedArea(player.getUniqueId(), area.getName());
                }
                openAreaMenu(player, areaName);
            }
            case "open_enter" -> openCommandListMenu(player, areaName, CommandListType.ENTER);
            case "open_leave" -> openCommandListMenu(player, areaName, CommandListType.LEAVE);
            case "open_start" -> openCommandListMenu(player, areaName, CommandListType.START);
            case "open_mobs" -> openMobListMenu(player, areaName);
            case "window" -> {
                int delta = event.isShiftClick() ? 30 : 5;
                if (event.isRightClick()) delta = -delta;
                area.setJoinWindowSeconds(area.getJoinWindowSeconds() + delta);
                areaManager.save();
                openAreaMenu(player, areaName);
            }
            case "delete" -> {
                if (event.isShiftClick()) {
                    areaManager.removeArea(area.getName());
                    player.sendMessage(ChatColor.YELLOW + "Área '" + area.getName() + "' eliminada.");
                    openMainMenu(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Mantené shift y hacé click para confirmar la eliminación.");
                }
            }
            default -> { }
        }
    }

    private void handleCommandListAction(Player player, String areaName, CommandListType type,
                                          String action, Integer index) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }

        switch (action) {
            case "back_area" -> openAreaMenu(player, areaName);
            case "add_command" -> {
                pendingInputs.put(player.getUniqueId(), PendingInput.addCommand(areaName, type));
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Escribí el comando en el chat (o 'cancelar').");
            }
            case "remove_command" -> {
                List<String> commands = listFor(area, type);
                if (index != null && index >= 0 && index < commands.size()) {
                    String removed = commands.remove((int) index);
                    areaManager.save();
                    player.sendMessage(ChatColor.YELLOW + "Comando eliminado: " + removed);
                }
                openCommandListMenu(player, areaName, type);
            }
            default -> { }
        }
    }

    private void handleMobListAction(Player player, String areaName, String action, String mobId) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }

        switch (action) {
            case "back_area" -> openAreaMenu(player, areaName);
            case "open_mob" -> openMobEditorMenu(player, areaName, mobId);
            case "create_mob" -> openMobTypePicker(player, areaName, 0);
            default -> { }
        }
    }

    private void handleMobTypePickerAction(Player player, String areaName, String action, Integer page, String typeNameOrNull) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }

        switch (action) {
            case "back_mob_list" -> openMobListMenu(player, areaName);
            case "prev_type_page", "next_type_page" -> openMobTypePicker(player, areaName, page != null ? page : 0);
            case "pick_type" -> {
                EntityType type;
                try {
                    type = EntityType.valueOf(typeNameOrNull);
                } catch (IllegalArgumentException | NullPointerException e) {
                    player.sendMessage(ChatColor.RED + "Tipo inválido, probá de nuevo.");
                    return;
                }
                pendingInputs.put(player.getUniqueId(), PendingInput.createMob(areaName, type));
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Elegiste " + prettyName(type) + ". Ahora escribí en el chat "
                        + "el id para este mob (ej: guardia1), o 'cancelar'.");
            }
            default -> { }
        }
    }

    private void handleMobEditorAction(Player player, String areaName, String mobId, String action, InventoryClickEvent event) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }
        MobSpawnDefinition mob = area.getMob(mobId);
        if (mob == null) {
            player.sendMessage(ChatColor.RED + "Ese mob ya no existe.");
            openMobListMenu(player, areaName);
            return;
        }

        boolean shift = event.isShiftClick();
        boolean right = event.isRightClick();

        switch (action) {
            case "back_mobs" -> openMobListMenu(player, areaName);
            case "edit_name" -> {
                pendingInputs.put(player.getUniqueId(), PendingInput.mobField(PendingInput.Kind.SET_MOB_NAME, areaName, mobId));
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Escribí el nombre en el chat (soporta &colores), o 'cancelar'.");
            }
            case "edit_health" -> {
                double delta = shift ? 10 : 1;
                if (right) delta = -delta;
                mob.setHealth(mob.getHealth() + delta);
                areaManager.save();
                openMobEditorMenu(player, areaName, mobId);
            }
            case "edit_scale" -> {
                double delta = shift ? 1.0 : 0.25;
                if (right) delta = -delta;
                mob.setScale(mob.getScale() + delta);
                areaManager.save();
                openMobEditorMenu(player, areaName, mobId);
            }
            case "edit_speed" -> {
                if (shift) {
                    mob.setSpeed(-1);
                } else {
                    double base = mob.getSpeed() < 0 ? 0 : mob.getSpeed();
                    double delta = right ? -0.05 : 0.05;
                    mob.setSpeed(Math.max(0, base + delta));
                }
                areaManager.save();
                openMobEditorMenu(player, areaName, mobId);
            }
            case "edit_delay" -> {
                int delta = shift ? 5 : 1;
                if (right) delta = -delta;
                mob.setDelaySeconds(mob.getDelaySeconds() + delta);
                areaManager.save();
                openMobEditorMenu(player, areaName, mobId);
            }
            case "edit_amount" -> {
                int delta = shift ? 5 : 1;
                if (right) delta = -delta;
                mob.setAmount(mob.getAmount() + delta);
                areaManager.save();
                openMobEditorMenu(player, areaName, mobId);
            }
            case "set_spawn_here" -> {
                mob.setSpawnLocation(player.getLocation());
                areaManager.save();
                player.sendMessage(ChatColor.GREEN + "Posición de '" + mob.getId() + "' fijada en tu ubicación actual.");
                openMobEditorMenu(player, areaName, mobId);
            }
            case "open_equip" -> openMobEquipMenu(player, areaName, mobId);
            case "edit_effect" -> {
                if (shift) {
                    if (!mob.getPotionEffects().isEmpty()) {
                        mob.getPotionEffects().remove(mob.getPotionEffects().size() - 1);
                        areaManager.save();
                        player.sendMessage(ChatColor.YELLOW + "Se borró el último efecto de '" + mob.getId() + "'.");
                    }
                    openMobEditorMenu(player, areaName, mobId);
                } else {
                    pendingInputs.put(player.getUniqueId(), PendingInput.mobField(PendingInput.Kind.ADD_MOB_EFFECT, areaName, mobId));
                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "Escribí en el chat: <efecto> <amplificador> <segundos> "
                            + "(ej: speed 1 30), o 'cancelar'.");
                }
            }
            case "edit_tag" -> {
                pendingInputs.put(player.getUniqueId(), PendingInput.mobField(PendingInput.Kind.SET_MOB_TAG, areaName, mobId));
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Escribí la etiqueta en el chat ('clear' para borrarla), o 'cancelar'.");
            }
            case "edit_loot" -> {
                pendingInputs.put(player.getUniqueId(), PendingInput.mobField(PendingInput.Kind.SET_MOB_LOOT, areaName, mobId));
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Escribí el nombre de la tabla de loot en el chat "
                        + "('clear' para borrarla), o 'cancelar'.");
            }
            case "toggle_ai" -> { mob.setNoAi(!mob.isNoAi()); areaManager.save(); openMobEditorMenu(player, areaName, mobId); }
            case "toggle_silent" -> { mob.setSilent(!mob.isSilent()); areaManager.save(); openMobEditorMenu(player, areaName, mobId); }
            case "toggle_invulnerable" -> { mob.setInvulnerable(!mob.isInvulnerable()); areaManager.save(); openMobEditorMenu(player, areaName, mobId); }
            case "toggle_glow" -> { mob.setGlowing(!mob.isGlowing()); areaManager.save(); openMobEditorMenu(player, areaName, mobId); }
            case "toggle_baby" -> { mob.setBaby(!mob.isBaby()); areaManager.save(); openMobEditorMenu(player, areaName, mobId); }
            case "delete_mob" -> {
                if (shift) {
                    area.removeMob(mob.getId());
                    areaManager.save();
                    player.sendMessage(ChatColor.YELLOW + "Mob '" + mob.getId() + "' eliminado.");
                    openMobListMenu(player, areaName);
                } else {
                    player.sendMessage(ChatColor.RED + "Mantené shift y hacé click para confirmar la eliminación.");
                }
            }
            default -> { }
        }
    }

    private void handleMobEquipAction(Player player, String areaName, String mobId, String action) {
        if (!"save_equip".equals(action)) {
            return; // los slots del muñeco de papel no llevan acción (se manejan sin cancelar)
        }
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }
        MobSpawnDefinition mob = area.getMob(mobId);
        if (mob == null) {
            player.sendMessage(ChatColor.RED + "Ese mob ya no existe.");
            openMobListMenu(player, areaName);
            return;
        }

        Inventory inv = player.getOpenInventory().getTopInventory();
        for (Map.Entry<Integer, EquipmentSlot> entry : EQUIP_SLOT_POSITIONS.entrySet()) {
            ItemStack item = inv.getItem(entry.getKey());
            if (item == null || item.getType().isAir()) {
                mob.getEquipment().remove(entry.getValue());
            } else {
                mob.getEquipment().put(entry.getValue(), item.clone());
            }
        }
        areaManager.save();
        player.sendMessage(ChatColor.GREEN + "Equipamiento de '" + mob.getId() + "' guardado.");
        openMobEditorMenu(player, areaName, mobId);
    }

    // ----------------------------------------------------------------
    // Captura de chat (nombre de área nueva / comando nuevo)
    // ----------------------------------------------------------------

    public void processChatInput(Player player, String message) {
        PendingInput pending = pendingInputs.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }

        if (message.equalsIgnoreCase("cancelar")) {
            player.sendMessage(ChatColor.YELLOW + "Cancelado.");
            return;
        }

        switch (pending.getKind()) {
            case CREATE_AREA -> createAreaFromChat(player, message.trim());
            case ADD_COMMAND -> addCommandFromChat(player, pending.getAreaName(), pending.getListType(), message);
            case CREATE_MOB -> createMobFromChat(player, pending.getAreaName(), pending.getMobType(), message.trim());
            case SET_MOB_NAME -> setMobNameFromChat(player, pending.getAreaName(), pending.getMobId(), message);
            case SET_MOB_TAG -> setMobTagFromChat(player, pending.getAreaName(), pending.getMobId(), message.trim());
            case SET_MOB_LOOT -> setMobLootFromChat(player, pending.getAreaName(), pending.getMobId(), message.trim());
            case ADD_MOB_EFFECT -> addMobEffectFromChat(player, pending.getAreaName(), pending.getMobId(), message.trim());
        }
    }

    private void createAreaFromChat(Player player, String name) {
        if (name.isEmpty()) {
            player.sendMessage(ChatColor.RED + "El nombre no puede estar vacío.");
            return;
        }
        if (areaManager.getArea(name) != null) {
            player.sendMessage(ChatColor.RED + "Ya existe un área llamada '" + name + "'.");
            return;
        }
        Location p1 = selectionListener.getPos1(player.getUniqueId());
        Location p2 = selectionListener.getPos2(player.getUniqueId());
        if (p1 == null || p2 == null || p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage(ChatColor.RED + "Perdiste la selección de esquinas, marcá de nuevo con la varita.");
            return;
        }

        DungeonArea area = new DungeonArea(name, p1.getWorld().getName(),
                p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                p2.getBlockX(), p2.getBlockY(), p2.getBlockZ());
        areaManager.addArea(area);
        selectionListener.setSelectedArea(player.getUniqueId(), name);
        player.sendMessage(ChatColor.GREEN + "Área '" + name + "' creada.");
        openAreaMenu(player, name);
    }

    private void addCommandFromChat(Player player, String areaName, CommandListType type, String commandText) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            return;
        }
        listFor(area, type).add(commandText);
        areaManager.save();
        player.sendMessage(ChatColor.GREEN + "Comando agregado: " + commandText);
        openCommandListMenu(player, areaName, type);
    }

    private void createMobFromChat(Player player, String areaName, EntityType type, String text) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            return;
        }
        String id = text.split("\\s+")[0];
        if (id.isEmpty()) {
            player.sendMessage(ChatColor.RED + "El id no puede estar vacío.");
            openMobListMenu(player, areaName);
            return;
        }
        if (area.getMob(id) != null) {
            player.sendMessage(ChatColor.RED + "Ya existe un mob '" + id + "' en esta área.");
            openMobListMenu(player, areaName);
            return;
        }

        MobSpawnDefinition mob = new MobSpawnDefinition(id, type);
        area.addMob(mob);
        areaManager.save();
        player.sendMessage(ChatColor.GREEN + "Mob '" + id + "' (" + type + ") creado. Ahora fijale una posición "
                + "parándote donde querés que aparezca.");
        openMobEditorMenu(player, areaName, id);
    }

    private void setMobNameFromChat(Player player, String areaName, String mobId, String name) {
        DungeonArea area = areaManager.getArea(areaName);
        MobSpawnDefinition mob = area == null ? null : area.getMob(mobId);
        if (area == null || mob == null) {
            player.sendMessage(ChatColor.RED + "Ese mob ya no existe.");
            return;
        }
        mob.setDisplayName(name);
        areaManager.save();
        player.sendMessage(ChatColor.GREEN + "Nombre de '" + mob.getId() + "' actualizado.");
        openMobEditorMenu(player, areaName, mobId);
    }

    private void setMobTagFromChat(Player player, String areaName, String mobId, String value) {
        DungeonArea area = areaManager.getArea(areaName);
        MobSpawnDefinition mob = area == null ? null : area.getMob(mobId);
        if (area == null || mob == null) {
            player.sendMessage(ChatColor.RED + "Ese mob ya no existe.");
            return;
        }
        mob.setTag(value.equalsIgnoreCase("clear") ? null : value);
        areaManager.save();
        player.sendMessage(ChatColor.GREEN + "Etiqueta de '" + mob.getId() + "' actualizada.");
        openMobEditorMenu(player, areaName, mobId);
    }

    private void setMobLootFromChat(Player player, String areaName, String mobId, String value) {
        DungeonArea area = areaManager.getArea(areaName);
        MobSpawnDefinition mob = area == null ? null : area.getMob(mobId);
        if (area == null || mob == null) {
            player.sendMessage(ChatColor.RED + "Ese mob ya no existe.");
            return;
        }
        mob.setLootTable(value.equalsIgnoreCase("clear") ? null : value);
        areaManager.save();
        player.sendMessage(ChatColor.GREEN + "Tabla de loot de '" + mob.getId() + "' actualizada.");
        openMobEditorMenu(player, areaName, mobId);
    }

    private void addMobEffectFromChat(Player player, String areaName, String mobId, String text) {
        DungeonArea area = areaManager.getArea(areaName);
        MobSpawnDefinition mob = area == null ? null : area.getMob(mobId);
        if (area == null || mob == null) {
            player.sendMessage(ChatColor.RED + "Ese mob ya no existe.");
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 3) {
            player.sendMessage(ChatColor.RED + "Formato inválido. Escribí: <efecto> <amplificador> <segundos> (ej: speed 1 30)");
            openMobEditorMenu(player, areaName, mobId);
            return;
        }
        org.bukkit.potion.PotionEffectType effectType = org.bukkit.potion.PotionEffectType.getByName(parts[0].toUpperCase(Locale.ROOT));
        if (effectType == null) {
            player.sendMessage(ChatColor.RED + "Efecto inválido: " + parts[0] + " (ej: SPEED, STRENGTH, INVISIBILITY...).");
            openMobEditorMenu(player, areaName, mobId);
            return;
        }
        int amplifier = parseIntSafe(parts[1], 0);
        int seconds = parseIntSafe(parts[2], 30);
        mob.getPotionEffects().add(new MobSpawnDefinition.StoredPotionEffect(effectType, amplifier, seconds));
        areaManager.save();
        player.sendMessage(ChatColor.GREEN + "Efecto agregado a '" + mob.getId() + "'.");
        openMobEditorMenu(player, areaName, mobId);
    }

    private int parseIntSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private ItemStack buildItem(Material material, String name, List<String> lore,
                                 String action, String areaName, Integer index) {
        return buildItem(material, name, lore, action, areaName, index, null);
    }

    private ItemStack buildItem(Material material, String name, List<String> lore,
                                 String action, String areaName, Integer index, String mobId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore);
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (areaName != null) {
            meta.getPersistentDataContainer().set(areaKey, PersistentDataType.STRING, areaName);
        }
        if (index != null) {
            meta.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, index);
        }
        if (mobId != null) {
            meta.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, mobId);
        }
        item.setItemMeta(meta);
        return item;
    }
}
