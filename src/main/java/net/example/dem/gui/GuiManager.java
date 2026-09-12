package net.example.dem.gui;

import net.example.dem.area.AreaManager;
import net.example.dem.area.DoorDefinition;
import net.example.dem.area.DungeonArea;
import net.example.dem.area.SelectionListener;
import net.example.dem.gui.GuiHolders.AreaMenuHolder;
import net.example.dem.gui.GuiHolders.CommandListMenuHolder;
import net.example.dem.gui.GuiHolders.DoorMenuHolder;
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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EntityEquipment;
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
    private final NamespacedKey markerAreaKey;
    private final NamespacedKey markerMobKey;

    private final Map<UUID, PendingInput> pendingInputs = new HashMap<>();

    // Confirmación de borrado en 2 clicks normales (sin shift, para que
    // funcione también en Bedrock/Geyser, donde shift+click no llega bien).
    private final Map<UUID, String> pendingConfirm = new HashMap<>();

    /** true = ya era el 2do click, confirmado (y se borra el estado). false = recién se armó, falta confirmar. */
    private boolean confirmOrArm(Player player, String key) {
        if (key.equalsIgnoreCase(pendingConfirm.get(player.getUniqueId()))) {
            pendingConfirm.remove(player.getUniqueId());
            return true;
        }
        pendingConfirm.put(player.getUniqueId(), key);
        return false;
    }

    public GuiManager(Plugin plugin, AreaManager areaManager, SelectionListener selectionListener) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.selectionListener = selectionListener;
        this.actionKey = new NamespacedKey(plugin, NAMESPACE + "_gui_action");
        this.areaKey = new NamespacedKey(plugin, NAMESPACE + "_gui_area");
        this.indexKey = new NamespacedKey(plugin, NAMESPACE + "_gui_index");
        this.mobKey = new NamespacedKey(plugin, NAMESPACE + "_gui_mob");
        this.markerAreaKey = new NamespacedKey(plugin, NAMESPACE + "_marker_area");
        this.markerMobKey = new NamespacedKey(plugin, NAMESPACE + "_marker_mob");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public NamespacedKey getMarkerAreaKey() {
        return markerAreaKey;
    }

    public NamespacedKey getMarkerMobKey() {
        return markerMobKey;
    }

    public boolean isDemGui(InventoryHolder holder) {
        return holder instanceof MainMenuHolder
                || holder instanceof AreaMenuHolder
                || holder instanceof CommandListMenuHolder
                || holder instanceof MobListMenuHolder
                || holder instanceof MobTypePickerHolder
                || holder instanceof MobEditorMenuHolder
                || holder instanceof MobEquipMenuHolder
                || holder instanceof DoorMenuHolder;
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
        pendingConfirm.remove(player.getUniqueId());
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

        // Entrar (o seguir) en modo edición: pone/asegura el armor stand
        // marcador de cada mob y desactiva la ventana de ingreso mientras
        // este menú (o cualquiera de sus submenús) esté en uso.
        enterAreaEditMode(area);

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

        inv.setItem(17, buildItem(Material.OAK_DOOR,
                ChatColor.AQUA + "Puertas (" + area.getDoorsById().size() + ")",
                List.of(ChatColor.GRAY + "Puerta de entrada: " + (area.getEntryDoorId() == null ? "(ninguna)" : area.getEntryDoorId()),
                        ChatColor.GRAY + "Puerta de salida: " + (area.getExitDoorId() == null ? "(ninguna)" : area.getExitDoorId()),
                        ChatColor.YELLOW + "Click para configurar"),
                "open_doors", area.getName(), null));

        inv.setItem(20, buildItem(Material.CLOCK,
                ChatColor.GOLD + "Ventana de ingreso: " + area.getJoinWindowSeconds() + "s",
                buildWindowLore(area), "window", area.getName(), null));

        long taggedCount = area.getMobs().stream().filter(m -> area.getName().equalsIgnoreCase(m.getTag())).count();
        inv.setItem(24, buildItem(Material.TARGET, ChatColor.GOLD + "Meta automática",
                List.of(ChatColor.GRAY + "Mobs con etiqueta '" + area.getName() + "': " + taggedCount,
                        ChatColor.GRAY + "Click agrega el objetivo (matarlos",
                        ChatColor.GRAY + "a todos) a Comandos de arranque."),
                "auto_goal", area.getName(), null));

        inv.setItem(22, buildItem(Material.GLOWSTONE, ChatColor.YELLOW + "Modo edición: ACTIVO",
                List.of(ChatColor.GRAY + "Mientras este menú (o sus submenús)",
                        ChatColor.GRAY + "estén abiertos, esta área NO arranca",
                        ChatColor.GRAY + "el evento aunque camines adentro.",
                        ChatColor.GRAY + "Los mobs se ven como armor stands.",
                        ChatColor.GRAY + "Salí con Guardar o Cancelar abajo."),
                "noop", null, null));

        boolean deleteArmed = ("area:" + area.getName()).equalsIgnoreCase(pendingConfirm.get(player.getUniqueId()));
        inv.setItem(31, buildItem(
                deleteArmed ? Material.TNT : Material.BARRIER,
                deleteArmed ? ChatColor.RED + "¿SEGURO? Click de nuevo" : ChatColor.RED + "Eliminar área",
                List.of(ChatColor.GRAY + (deleteArmed ? "Este click SÍ borra el área" : "Click 2 veces para eliminar"),
                        ChatColor.GRAY + "(no se puede deshacer)"),
                "delete", area.getName(), null));

        inv.setItem(27, buildItem(Material.ARROW, ChatColor.WHITE + "« Volver",
                List.of(ChatColor.GRAY + "(seguís en modo edición,",
                        ChatColor.GRAY + "los marcadores no se van)"),
                "back_main", area.getName(), null));

        inv.setItem(33, buildItem(Material.REDSTONE, ChatColor.GOLD + "Cancelar edición",
                List.of(ChatColor.GRAY + "Saca los armor stands y sale.",
                        ChatColor.GRAY + "(los cambios que ya hiciste",
                        ChatColor.GRAY + "quedan guardados igual)"),
                "cancel_edit", area.getName(), null));

        inv.setItem(35, buildItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "Guardar y salir",
                List.of(ChatColor.GRAY + "Termina la edición: saca los",
                        ChatColor.GRAY + "armor stands, el área queda lista"),
                "save_edit", area.getName(), null));

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
        pendingConfirm.remove(player.getUniqueId());
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
        lore.add(ChatColor.YELLOW + "Click derecho para clonar");
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

    // ----------------------------------------------------------------
    // Armor stands "modo edición" — uno por CADA mob del área, visibles
    // mientras el área esté en modo edición (hasta Guardar/Cancelar).
    // ----------------------------------------------------------------

    /** Cabeza real (no huevo) para el marcador, cuando existe una para ese tipo. */
    private Material markerHeadFor(EntityType type) {
        return switch (type) {
            case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED -> Material.ZOMBIE_HEAD;
            case SKELETON, STRAY -> Material.SKELETON_SKULL;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            case PIGLIN, PIGLIN_BRUTE, ZOMBIFIED_PIGLIN -> Material.PIGLIN_HEAD;
            case PLAYER -> Material.PLAYER_HEAD;
            default -> null;
        };
    }

    private void applyMarkerLook(ArmorStand as, MobSpawnDefinition mob) {
        as.setCustomName(ChatColor.YELLOW + "[" + mob.getId() + "] "
                + ChatColor.WHITE + (mob.getDisplayName() != null ? mob.getDisplayName() : mob.getBaseType().name()));
        as.setCustomNameVisible(true);

        EntityEquipment equipment = as.getEquipment();
        if (equipment == null) return;
        ItemStack head = mob.getEquipment().get(EquipmentSlot.HEAD);
        if (head != null) {
            equipment.setHelmet(head.clone());
        } else {
            Material fallback = markerHeadFor(mob.getBaseType());
            if (fallback == null) fallback = entityIcon(mob.getBaseType());
            if (fallback.isItem()) equipment.setHelmet(new ItemStack(fallback));
        }
        ItemStack chest = mob.getEquipment().get(EquipmentSlot.CHEST);
        equipment.setChestplate(chest != null ? chest.clone() : null);
        ItemStack legs = mob.getEquipment().get(EquipmentSlot.LEGS);
        equipment.setLeggings(legs != null ? legs.clone() : null);
        ItemStack feet = mob.getEquipment().get(EquipmentSlot.FEET);
        equipment.setBoots(feet != null ? feet.clone() : null);
        ItemStack hand = mob.getEquipment().get(EquipmentSlot.HAND);
        equipment.setItemInMainHand(hand != null ? hand.clone() : null);
        ItemStack offhand = mob.getEquipment().get(EquipmentSlot.OFF_HAND);
        equipment.setItemInOffHand(offhand != null ? offhand.clone() : null);
    }

    /** Entra en modo edición (si no lo estaba ya) y asegura un marcador por cada mob existente. */
    public void enterAreaEditMode(DungeonArea area) {
        area.setEditMode(true);
        for (MobSpawnDefinition mob : area.getMobs()) {
            ensureMarker(area, mob);
        }
    }

    /** Sale del modo edición: borra TODOS los armor stands marcadores del área. */
    public void exitAreaEditMode(DungeonArea area) {
        for (UUID id : new ArrayList<>(area.getMarkerEntities().values())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        area.getMarkerEntities().clear();
        area.setEditMode(false);
    }

    /** Si el mob no tiene marcador vivo todavía, le crea uno. No toca los que ya existen. */
    private void ensureMarker(DungeonArea area, MobSpawnDefinition mob) {
        UUID existing = area.getMarkerEntities().get(mob.getId());
        if (existing != null && Bukkit.getEntity(existing) != null) {
            return;
        }
        spawnOrRefreshMarker(area, mob);
    }

    /** Crea (reemplazando cualquier anterior) el armor stand marcador de este mob puntual. */
    public void spawnOrRefreshMarker(DungeonArea area, MobSpawnDefinition mob) {
        removeMarker(area, mob.getId());
        Location loc = mob.getSpawnLocation();
        if (loc == null) {
            return; // sin posición todavía, no hay dónde ponerlo
        }
        ArmorStand stand = loc.getWorld().spawn(loc.clone().add(0, 0.05, 0), ArmorStand.class, as -> {
            as.setInvulnerable(true);
            as.setGravity(false);
            as.setBasePlate(false);
            as.setCollidable(false);
            as.setPersistent(false);
            as.getPersistentDataContainer().set(markerAreaKey, PersistentDataType.STRING, area.getName());
            as.getPersistentDataContainer().set(markerMobKey, PersistentDataType.STRING, mob.getId());
            applyMarkerLook(as, mob);
        });
        area.getMarkerEntities().put(mob.getId(), stand.getUniqueId());
    }

    public void removeMarker(DungeonArea area, String mobId) {
        UUID id = area.getMarkerEntities().remove(mobId);
        if (id != null) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    /** Relee el equipo actual del armor stand y lo guarda en la definición del mob (usado al clickearlo con un ítem). */
    public void syncMarkerEquipmentFromStand(String areaName, String mobId, ArmorStand stand) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) return;
        MobSpawnDefinition mob = area.getMob(mobId);
        if (mob == null) return;

        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) return;
        putOrRemove(mob, EquipmentSlot.HEAD, equipment.getHelmet());
        putOrRemove(mob, EquipmentSlot.CHEST, equipment.getChestplate());
        putOrRemove(mob, EquipmentSlot.LEGS, equipment.getLeggings());
        putOrRemove(mob, EquipmentSlot.FEET, equipment.getBoots());
        putOrRemove(mob, EquipmentSlot.HAND, equipment.getItemInMainHand());
        putOrRemove(mob, EquipmentSlot.OFF_HAND, equipment.getItemInOffHand());
        areaManager.save();
    }

    private void putOrRemove(MobSpawnDefinition mob, EquipmentSlot slot, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            mob.getEquipment().remove(slot);
        } else {
            mob.getEquipment().put(slot, item.clone());
        }
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

        // El área ya está en modo edición desde que se abrió su menú: esto
        // solo asegura que el marcador de este mob puntual esté actualizado
        // (por si se le cambió posición, nombre o equipo recién).
        spawnOrRefreshMarker(area, mob);

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
        inv.setItem(29, buildToggle(Material.WHITE_WOOL, "Silencioso", mob.isSilent(), "toggle_silent", mob.getId()));
        inv.setItem(30, buildToggle(Material.SHIELD, "Invulnerable", mob.isInvulnerable(), "toggle_invulnerable", mob.getId()));
        inv.setItem(31, buildToggle(Material.GLOWSTONE_DUST, "Brillante", mob.isGlowing(), "toggle_glow", mob.getId()));
        inv.setItem(32, buildToggle(Material.EGG, "Bebé", mob.isBaby(), "toggle_baby", mob.getId()));

        boolean deleteMobArmed = ("mob:" + area.getName() + ":" + mob.getId()).equalsIgnoreCase(pendingConfirm.get(player.getUniqueId()));
        inv.setItem(49, buildItem(
                deleteMobArmed ? Material.TNT : Material.BARRIER,
                deleteMobArmed ? ChatColor.RED + "¿SEGURO? Click de nuevo" : ChatColor.RED + "Eliminar mob",
                List.of(ChatColor.GRAY + (deleteMobArmed ? "Este click SÍ borra el mob" : "Click 2 veces para eliminar"),
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
    // Menú de puertas de entrada/salida de un área
    // ----------------------------------------------------------------

    public void openDoorMenu(Player player, String areaName) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }

        Inventory inv = Bukkit.createInventory(new DoorMenuHolder(area.getName()), 27,
                ChatColor.DARK_PURPLE + "Puertas: " + area.getName());
        ((DoorMenuHolder) inv.getHolder()).setInventory(inv);

        inv.setItem(10, buildItem(Material.LIME_DYE,
                ChatColor.GREEN + "Entrada: " + (area.getEntryDoorId() == null ? "(ninguna)" : area.getEntryDoorId()),
                List.of(ChatColor.GRAY + "Se cierra sola al arrancar el evento.",
                        ChatColor.YELLOW + "Click izq/der: elegir puerta"),
                "cycle_entry", area.getName(), null));
        inv.setItem(11, buildItem(Material.IRON_DOOR, ChatColor.AQUA + "Abrir/cerrar entrada",
                List.of(ChatColor.YELLOW + "Click izq: abrir   Click der: cerrar"),
                "toggle_entry", area.getName(), null));

        inv.setItem(15, buildItem(Material.ORANGE_DYE,
                ChatColor.GOLD + "Salida: " + (area.getExitDoorId() == null ? "(ninguna)" : area.getExitDoorId()),
                List.of(ChatColor.GRAY + "La abrís vos desde el on-complete",
                        ChatColor.GRAY + "del objetivo (dem area door openexit).",
                        ChatColor.YELLOW + "Click izq/der: elegir puerta"),
                "cycle_exit", area.getName(), null));
        inv.setItem(16, buildItem(Material.IRON_DOOR, ChatColor.AQUA + "Abrir/cerrar salida",
                List.of(ChatColor.YELLOW + "Click izq: abrir   Click der: cerrar"),
                "toggle_exit", area.getName(), null));

        List<String> allDoorsLore = new ArrayList<>();
        if (area.getDoorsById().isEmpty()) {
            allDoorsLore.add(ChatColor.GRAY + "(esta área todavía no tiene puertas)");
        } else {
            for (DoorDefinition door : area.getDoorsById().values()) {
                allDoorsLore.add(ChatColor.GRAY + " - " + door.getId() + " (" + door.getBlockCount() + " bloques)");
            }
        }
        inv.setItem(13, buildItem(Material.BOOK, ChatColor.WHITE + "Puertas de esta área", allDoorsLore, "noop", null, null));

        inv.setItem(22, buildItem(Material.EMERALD, ChatColor.GREEN + "Crear puerta nueva",
                List.of(ChatColor.GRAY + "Marcá 2 esquinas con la varita",
                        ChatColor.GRAY + "sobre una puerta ya construida",
                        ChatColor.GRAY + "(eso queda como su estado 'cerrado').",
                        ChatColor.YELLOW + "Click para empezar"),
                "create_door", area.getName(), null));

        inv.setItem(18, buildItem(Material.ARROW, ChatColor.WHITE + "« Volver", List.of(), "back_area", area.getName(), null));

        player.openInventory(inv);
    }

    /** Ciclo circular entre "ninguna" y cada puerta de la área, para elegir entrada/salida sin escribir nada. */
    private String cycleDoorId(DungeonArea area, String current, boolean forward) {
        List<String> options = new ArrayList<>();
        options.add(null); // "ninguna"
        for (DoorDefinition door : area.getDoorsById().values()) {
            options.add(door.getId());
        }
        int idx = options.indexOf(current);
        if (idx < 0) idx = 0;
        int next = forward ? (idx + 1) % options.size() : (idx - 1 + options.size()) % options.size();
        return options.get(next);
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
            handleMobListAction(player, h.getAreaName(), action, itemMobId, event);
        } else if (holder instanceof MobTypePickerHolder h) {
            handleMobTypePickerAction(player, h.getAreaName(), action, index, itemMobId);
        } else if (holder instanceof MobEditorMenuHolder h) {
            handleMobEditorAction(player, h.getAreaName(), h.getMobId(), action, event);
        } else if (holder instanceof MobEquipMenuHolder h) {
            handleMobEquipAction(player, h.getAreaName(), h.getMobId(), action);
        } else if (holder instanceof DoorMenuHolder h) {
            handleDoorMenuAction(player, h.getAreaName(), action, event);
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
            case "open_doors" -> openDoorMenu(player, areaName);
            case "window" -> {
                int delta = event.isShiftClick() ? 30 : 5;
                if (event.isRightClick()) delta = -delta;
                area.setJoinWindowSeconds(area.getJoinWindowSeconds() + delta);
                areaManager.save();
                openAreaMenu(player, areaName);
            }
            case "auto_goal" -> {
                long count = area.getMobs().stream().filter(m -> area.getName().equalsIgnoreCase(m.getTag())).count();
                if (count == 0) {
                    player.sendMessage(ChatColor.RED + "Ningún mob tiene la etiqueta '" + area.getName()
                            + "' todavía (se pone sola al crearlos desde este menú).");
                } else {
                    String cmd = "dem objective watch " + area.getName() + " " + count
                            + " broadcast &a¡" + area.getName() + " superada!";
                    area.getStartCommands().add(cmd);
                    areaManager.save();
                    player.sendMessage(ChatColor.GREEN + "Agregado a Comandos de arranque: " + cmd);
                    player.sendMessage(ChatColor.GRAY + "Editalo ahí (Comandos de arranque) si querés sumarle "
                            + "abrir una puerta u otro efecto al completarse.");
                }
                openAreaMenu(player, areaName);
            }
            case "delete" -> {
                if (confirmOrArm(player, "area:" + area.getName())) {
                    exitAreaEditMode(area);
                    areaManager.removeArea(area.getName());
                    player.sendMessage(ChatColor.YELLOW + "Área '" + area.getName() + "' eliminada.");
                    openMainMenu(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Click de nuevo para confirmar: se va a eliminar '"
                            + area.getName() + "'.");
                    openAreaMenu(player, areaName);
                }
            }
            case "save_edit" -> {
                exitAreaEditMode(area);
                player.sendMessage(ChatColor.GREEN + "'" + area.getName() + "' guardada. Modo edición desactivado.");
                openMainMenu(player);
            }
            case "cancel_edit" -> {
                exitAreaEditMode(area);
                player.sendMessage(ChatColor.YELLOW + "Modo edición de '" + area.getName()
                        + "' cancelado (los cambios que ya hiciste quedan aplicados).");
                openMainMenu(player);
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

    private void handleMobListAction(Player player, String areaName, String action, String mobId,
                                      InventoryClickEvent event) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }

        switch (action) {
            case "back_area" -> openAreaMenu(player, areaName);
            case "open_mob" -> {
                if (event.isRightClick()) {
                    pendingInputs.put(player.getUniqueId(), PendingInput.cloneMob(areaName, mobId));
                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "Parate en la posición donde va la copia y escribí "
                            + "el id para '" + mobId + "' en el chat, o 'cancelar'.");
                } else {
                    openMobEditorMenu(player, areaName, mobId);
                }
            }
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
                createMobInstant(player, area, type);
            }
            default -> { }
        }
    }

    private String nextMobId(DungeonArea area) {
        int i = 1;
        while (area.getMob("mob" + i) != null) {
            i++;
        }
        return "mob" + i;
    }

    /**
     * Crea el mob al toque: sin pedir nada por chat. Usa la posición ACTUAL
     * del jugador como spawn point y un id autogenerado (después se le puede
     * poner un nombre lindo con el botón "Nombre" del editor). Abre el
     * editor directo, con su armor stand ya puesto.
     */
    private void createMobInstant(Player player, DungeonArea area, EntityType type) {
        String id = nextMobId(area);
        MobSpawnDefinition mob = new MobSpawnDefinition(id, type);
        mob.setSpawnLocation(player.getLocation());
        mob.setTag(area.getName()); // etiqueta = nombre del área, para automatizar el objetivo
        area.addMob(mob);
        areaManager.save();
        spawnOrRefreshMarker(area, mob);
        player.sendMessage(ChatColor.GREEN + "Mob '" + id + "' (" + prettyName(type)
                + ") creado en tu posición actual, con etiqueta '" + area.getName() + "'.");
        openMobEditorMenu(player, area.getName(), id);
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
                if (confirmOrArm(player, "mob:" + areaName + ":" + mob.getId())) {
                    removeMarker(area, mob.getId());
                    area.removeMob(mob.getId());
                    areaManager.save();
                    player.sendMessage(ChatColor.YELLOW + "Mob '" + mob.getId() + "' eliminado.");
                    openMobListMenu(player, areaName);
                } else {
                    player.sendMessage(ChatColor.RED + "Click de nuevo para confirmar: se va a eliminar '"
                            + mob.getId() + "'.");
                    openMobEditorMenu(player, areaName, mobId);
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

    private void handleDoorMenuAction(Player player, String areaName, String action, InventoryClickEvent event) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            openMainMenu(player);
            return;
        }

        boolean forward = event.isLeftClick();

        switch (action) {
            case "back_area" -> openAreaMenu(player, areaName);
            case "cycle_entry" -> {
                area.setEntryDoorId(cycleDoorId(area, area.getEntryDoorId(), forward));
                areaManager.save();
                openDoorMenu(player, areaName);
            }
            case "cycle_exit" -> {
                area.setExitDoorId(cycleDoorId(area, area.getExitDoorId(), forward));
                areaManager.save();
                openDoorMenu(player, areaName);
            }
            case "toggle_entry" -> {
                DoorDefinition door = area.getEntryDoor();
                if (door == null) {
                    player.sendMessage(ChatColor.RED + "'" + area.getName() + "' no tiene puerta de entrada asignada.");
                } else if (event.isRightClick()) {
                    door.close();
                    player.sendMessage(ChatColor.GREEN + "Puerta de entrada (" + door.getId() + ") cerrada.");
                } else {
                    door.open();
                    player.sendMessage(ChatColor.GREEN + "Puerta de entrada (" + door.getId() + ") abierta.");
                }
            }
            case "toggle_exit" -> {
                DoorDefinition door = area.getExitDoor();
                if (door == null) {
                    player.sendMessage(ChatColor.RED + "'" + area.getName() + "' no tiene puerta de salida asignada.");
                } else if (event.isRightClick()) {
                    door.close();
                    player.sendMessage(ChatColor.GREEN + "Puerta de salida (" + door.getId() + ") cerrada.");
                } else {
                    door.open();
                    player.sendMessage(ChatColor.GREEN + "Puerta de salida (" + door.getId() + ") abierta.");
                }
            }
            case "create_door" -> handleCreateDoorButton(player, areaName);
            default -> { }
        }
    }

    private void handleCreateDoorButton(Player player, String areaName) {
        Location pos1 = selectionListener.getPos1(player.getUniqueId());
        Location pos2 = selectionListener.getPos2(player.getUniqueId());

        if (pos1 == null || pos2 == null) {
            player.getInventory().addItem(selectionListener.createWand());
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Marcá las 2 esquinas de la puerta (ya construida) con la "
                    + "varita y abrí el menú de nuevo para ponerle nombre.");
            return;
        }
        if (pos1.getWorld() == null || !pos1.getWorld().equals(pos2.getWorld())) {
            player.sendMessage(ChatColor.RED + "Las dos esquinas deben estar en el mismo mundo.");
            return;
        }

        pendingInputs.put(player.getUniqueId(), PendingInput.createDoor(areaName));
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Escribí en el chat el id para la puerta nueva, o 'cancelar'.");
    }

    private void createDoorFromChat(Player player, String areaName, String id) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            return;
        }
        if (id.isEmpty()) {
            player.sendMessage(ChatColor.RED + "El id no puede estar vacío.");
            openDoorMenu(player, areaName);
            return;
        }
        if (area.getDoor(id) != null) {
            player.sendMessage(ChatColor.RED + "Ya existe una puerta '" + id + "' en esta área.");
            openDoorMenu(player, areaName);
            return;
        }

        Location pos1 = selectionListener.getPos1(player.getUniqueId());
        Location pos2 = selectionListener.getPos2(player.getUniqueId());
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || !pos1.getWorld().equals(pos2.getWorld())) {
            player.sendMessage(ChatColor.RED + "Perdiste la selección de esquinas, marcá de nuevo con la varita.");
            openDoorMenu(player, areaName);
            return;
        }

        World world = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        List<DoorDefinition.CapturedBlock> blocks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    blocks.add(new DoorDefinition.CapturedBlock(x, y, z, block.getBlockData().getAsString()));
                }
            }
        }

        DoorDefinition door = new DoorDefinition(id, world.getName(), minX, minY, minZ, maxX, maxY, maxZ, blocks);
        area.addDoor(door);
        areaManager.save();
        player.sendMessage(ChatColor.GREEN + "Puerta '" + id + "' creada (" + blocks.size()
                + " bloques capturados como estado cerrado).");
        openDoorMenu(player, areaName);
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
            case CLONE_MOB -> cloneMobFromChat(player, pending.getAreaName(), pending.getMobId(), message.trim());
            case SET_MOB_NAME -> setMobNameFromChat(player, pending.getAreaName(), pending.getMobId(), message);
            case SET_MOB_TAG -> setMobTagFromChat(player, pending.getAreaName(), pending.getMobId(), message.trim());
            case SET_MOB_LOOT -> setMobLootFromChat(player, pending.getAreaName(), pending.getMobId(), message.trim());
            case ADD_MOB_EFFECT -> addMobEffectFromChat(player, pending.getAreaName(), pending.getMobId(), message.trim());
            case CREATE_DOOR -> createDoorFromChat(player, pending.getAreaName(), message.trim());
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

    private void cloneMobFromChat(Player player, String areaName, String sourceMobId, String newId) {
        DungeonArea area = areaManager.getArea(areaName);
        if (area == null) {
            player.sendMessage(ChatColor.RED + "Esa área ya no existe.");
            return;
        }
        MobSpawnDefinition source = area.getMob(sourceMobId);
        if (source == null) {
            player.sendMessage(ChatColor.RED + "El mob original ya no existe.");
            openMobListMenu(player, areaName);
            return;
        }
        String id = newId.split("\\s+")[0];
        if (id.isEmpty() || area.getMob(id) != null) {
            player.sendMessage(ChatColor.RED + "Id inválido o ya usado en esta área.");
            openMobListMenu(player, areaName);
            return;
        }

        MobSpawnDefinition clone = source.copyWithNewId(id);
        clone.setSpawnLocation(player.getLocation()); // se clona parado en la posición nueva
        area.addMob(clone);
        areaManager.save();
        spawnOrRefreshMarker(area, clone);
        player.sendMessage(ChatColor.GREEN + "Mob '" + id + "' creado como copia de '" + sourceMobId
                + "' (mismo equipo, vida y efectos) en tu posición actual.");
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
