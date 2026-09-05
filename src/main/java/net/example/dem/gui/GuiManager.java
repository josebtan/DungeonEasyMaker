package net.example.dem.gui;

import net.example.dem.area.AreaManager;
import net.example.dem.area.DungeonArea;
import net.example.dem.area.SelectionListener;
import net.example.dem.gui.GuiHolders.AreaMenuHolder;
import net.example.dem.gui.GuiHolders.CommandListMenuHolder;
import net.example.dem.gui.GuiHolders.MainMenuHolder;
import net.example.dem.util.AreaVisualizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private final Plugin plugin;
    private final AreaManager areaManager;
    private final SelectionListener selectionListener;

    private final NamespacedKey actionKey;
    private final NamespacedKey areaKey;
    private final NamespacedKey indexKey;

    private final Map<UUID, PendingInput> pendingInputs = new HashMap<>();

    public GuiManager(Plugin plugin, AreaManager areaManager, SelectionListener selectionListener) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.selectionListener = selectionListener;
        this.actionKey = new NamespacedKey(plugin, NAMESPACE + "_gui_action");
        this.areaKey = new NamespacedKey(plugin, NAMESPACE + "_gui_area");
        this.indexKey = new NamespacedKey(plugin, NAMESPACE + "_gui_index");
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public boolean isDemGui(InventoryHolder holder) {
        return holder instanceof MainMenuHolder
                || holder instanceof AreaMenuHolder
                || holder instanceof CommandListMenuHolder;
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

        if (holder instanceof MainMenuHolder) {
            String areaName = meta.getPersistentDataContainer().get(areaKey, PersistentDataType.STRING);
            handleMainMenuAction(player, action, areaName);
        } else if (holder instanceof AreaMenuHolder h) {
            handleAreaMenuAction(player, h.getAreaName(), action, event);
        } else if (holder instanceof CommandListMenuHolder h) {
            handleCommandListAction(player, h.getAreaName(), h.getType(), action, index);
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

        if (pending.getKind() == PendingInput.Kind.CREATE_AREA) {
            createAreaFromChat(player, message.trim());
        } else {
            addCommandFromChat(player, pending.getAreaName(), pending.getListType(), message);
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

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private ItemStack buildItem(Material material, String name, List<String> lore,
                                 String action, String areaName, Integer index) {
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
        item.setItemMeta(meta);
        return item;
    }
}
