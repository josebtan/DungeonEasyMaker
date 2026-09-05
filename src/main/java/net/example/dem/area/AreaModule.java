package net.example.dem.area;

import net.example.dem.util.AreaVisualizer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lógica del módulo de áreas. Se invoca desde /dem area <accion> ...
 * args[] aquí NO incluye "area", empieza directo en la acción (wand, create, etc).
 */
public class AreaModule {

    private static final int SHOW_OUTLINE_TICKS = 5 * 20; // 5 segundos

    private final Plugin plugin;
    private final AreaManager areaManager;
    private final SelectionListener selectionListener;

    public AreaModule(Plugin plugin, AreaManager areaManager, SelectionListener selectionListener) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.selectionListener = selectionListener;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "wand":
                return handleWand(sender);
            case "create":
                return handleCreate(sender, args);
            case "addenter":
                return handleAddCommand(sender, args, true);
            case "addleave":
                return handleAddCommand(sender, args, false);
            case "addenterhere":
                return handleAddCommandHere(sender, args, true);
            case "addleavehere":
                return handleAddCommandHere(sender, args, false);
            case "setwindow":
                return handleSetWindow(sender, args);
            case "addstart":
                return handleAddStart(sender, args);
            case "addstarthere":
                return handleAddStartHere(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender);
            case "info":
                return handleInfo(sender, args);
            case "show":
                return handleShow(sender, args);
            case "select":
                return handleSelect(sender, args);
            case "unselect":
                return handleUnselect(sender);
            case "here":
                return handleHere(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Uso:");
        sender.sendMessage(ChatColor.RED + "/dem area wand");
        sender.sendMessage(ChatColor.RED + "/dem area create <nombre>");
        sender.sendMessage(ChatColor.RED + "/dem area addenter <nombre> <comando>");
        sender.sendMessage(ChatColor.RED + "/dem area addleave <nombre> <comando>");
        sender.sendMessage(ChatColor.RED + "/dem area addenterhere <comando>  (usa el área seleccionada o donde estás parado)");
        sender.sendMessage(ChatColor.RED + "/dem area addleavehere <comando>");
        sender.sendMessage(ChatColor.RED + "/dem area setwindow <nombre> <segundos>  (0 = desactivar)");
        sender.sendMessage(ChatColor.RED + "/dem area addstart <nombre> <comando>  (corre 1 vez al cerrar la ventana)");
        sender.sendMessage(ChatColor.RED + "/dem area addstarthere <comando>");
        sender.sendMessage(ChatColor.RED + "/dem area select <nombre>");
        sender.sendMessage(ChatColor.RED + "/dem area unselect");
        sender.sendMessage(ChatColor.RED + "/dem area here");
        sender.sendMessage(ChatColor.RED + "/dem area show <nombre>");
        sender.sendMessage(ChatColor.RED + "/dem area remove <nombre>");
        sender.sendMessage(ChatColor.RED + "/dem area list");
        sender.sendMessage(ChatColor.RED + "/dem area info <nombre>");
        sender.sendMessage(ChatColor.GRAY + "Placeholders disponibles en los comandos: [player] [world] [x] [y] [z] (+ PlaceholderAPI si está instalado)");
        sender.sendMessage(ChatColor.GRAY + "Delay opcional: \"delay:<segundos>|<comando>\"");
        sender.sendMessage(ChatColor.GRAY + "Ventana de ingreso: al entrar el 1er jugador arranca la cuenta regresiva "
                + "(setwindow); al cerrarse, addstart corre 1 sola vez tal como está escrito "
                + "y el área se bloquea hasta que quede vacía.");
    }

    private boolean handleWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede recibir la varita.");
            return true;
        }
        player.getInventory().addItem(selectionListener.createWand());
        player.sendMessage(ChatColor.GREEN + "Varita recibida. Click izquierdo = Posición 1, "
                + "click derecho = Posición 2. Vas a ver un bloque brillante marcando cada esquina.");
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede crear un área (necesita posiciones seleccionadas).");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area create <nombre>");
            return true;
        }

        String name = args[1];
        Location pos1 = selectionListener.getPos1(player.getUniqueId());
        Location pos2 = selectionListener.getPos2(player.getUniqueId());

        if (pos1 == null || pos2 == null) {
            sender.sendMessage(ChatColor.RED + "Primero selecciona las dos esquinas con la varita "
                    + "(/dem area wand).");
            return true;
        }
        if (pos1.getWorld() == null || !pos1.getWorld().equals(pos2.getWorld())) {
            sender.sendMessage(ChatColor.RED + "Las dos posiciones deben estar en el mismo mundo.");
            return true;
        }

        DungeonArea area = new DungeonArea(
                name, pos1.getWorld().getName(),
                pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ()
        );
        areaManager.addArea(area);
        selectionListener.setSelectedArea(player.getUniqueId(), name);
        sender.sendMessage(ChatColor.GREEN + "Área '" + name + "' creada y seleccionada. Ahora puedes usar "
                + "/dem area addenterhere <comando> sin repetir el nombre.");
        return true;
    }

    private boolean handleAddCommand(CommandSender sender, String[] args, boolean isEnter) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area " + args[0] + " <nombre> <comando>");
            return true;
        }
        DungeonArea area = areaManager.getArea(args[1]);
        if (area == null) {
            sender.sendMessage(ChatColor.RED + "No existe el área '" + args[1] + "'.");
            return true;
        }
        String commandText = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        addCommandToArea(sender, area, commandText, isEnter);
        return true;
    }

    // /dem area addenterhere|addleavehere <comando...>
    // Resuelve el área usando la seleccionada (/dem area select) o, si no hay
    // selección, el área en la que el jugador está parado (si es solo una).
    private boolean handleAddCommandHere(CommandSender sender, String[] args, boolean isEnter) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area " + args[0] + " <comando>");
            return true;
        }
        DungeonArea area = resolveArea(sender);
        if (area == null) {
            return true; // el mensaje de error ya lo mandó resolveArea()
        }
        String commandText = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        addCommandToArea(sender, area, commandText, isEnter);
        return true;
    }

    private void addCommandToArea(CommandSender sender, DungeonArea area, String commandText, boolean isEnter) {
        if (isEnter) {
            area.getEnterCommands().add(commandText);
        } else {
            area.getLeaveCommands().add(commandText);
        }
        areaManager.save();
        sender.sendMessage(ChatColor.GREEN + "Comando agregado a '" + area.getName() + "' ("
                + (isEnter ? "entrada" : "salida") + "): " + commandText);
    }

    private boolean handleSetWindow(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area setwindow <nombre> <segundos>");
            return true;
        }
        DungeonArea area = areaManager.getArea(args[1]);
        if (area == null) {
            sender.sendMessage(ChatColor.RED + "No existe el área '" + args[1] + "'.");
            return true;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "El tiempo debe ser un número entero de segundos.");
            return true;
        }
        area.setJoinWindowSeconds(seconds);
        areaManager.save();
        if (area.getJoinWindowSeconds() == 0) {
            sender.sendMessage(ChatColor.GREEN + "Ventana de ingreso desactivada en '" + area.getName()
                    + "'. Los comandos de entrada vuelven a correr normal, uno por jugador.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Ventana de ingreso de '" + area.getName() + "' establecida en "
                    + area.getJoinWindowSeconds() + " segundos. Al entrar el primer jugador arranca la cuenta "
                    + "regresiva; al terminar, se bloquea la entrada y los comandos de 'addstart' corren "
                    + "una sola vez, tal como están escritos.");
        }
        return true;
    }

    private boolean handleAddStart(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area addstart <nombre> <comando>");
            return true;
        }
        DungeonArea area = areaManager.getArea(args[1]);
        if (area == null) {
            sender.sendMessage(ChatColor.RED + "No existe el área '" + args[1] + "'.");
            return true;
        }
        String commandText = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        addStartCommandToArea(sender, area, commandText);
        return true;
    }

    // /dem area addstarthere <comando...> — igual que addenterhere pero para la
    // lista de comandos "de arranque" (se disparan al cerrarse la ventana).
    private boolean handleAddStartHere(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area addstarthere <comando>");
            return true;
        }
        DungeonArea area = resolveArea(sender);
        if (area == null) {
            return true; // el mensaje de error ya lo mandó resolveArea()
        }
        String commandText = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        addStartCommandToArea(sender, area, commandText);
        return true;
    }

    private void addStartCommandToArea(CommandSender sender, DungeonArea area, String commandText) {
        area.getStartCommands().add(commandText);
        areaManager.save();
        String windowNote = area.getJoinWindowSeconds() == 0
                ? " (ADVERTENCIA: esta área todavía no tiene ventana de ingreso configurada con /dem area setwindow, "
                    + "así que este comando nunca se va a ejecutar)"
                : "";
        sender.sendMessage(ChatColor.GREEN + "Comando de arranque agregado a '" + area.getName()
                + "' (corre 1 sola vez al cerrar la ventana): " + commandText + windowNote);
    }

    /**
     * Resuelve a qué área se refiere el jugador cuando no especifica un nombre:
     * 1. Si tiene una seleccionada con /dem area select, se usa esa.
     * 2. Si no, y está parado dentro de exactamente un área, se usa esa.
     * 3. Si no hay forma de saberlo (o hay ambigüedad), se le pide que aclare.
     */
    private DungeonArea resolveArea(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando requiere ser un jugador. "
                    + "Desde consola usa la variante con nombre explícito (addenter/addleave).");
            return null;
        }

        String selectedName = selectionListener.getSelectedArea(player.getUniqueId());
        if (selectedName != null) {
            DungeonArea area = areaManager.getArea(selectedName);
            if (area != null) {
                return area;
            }
            player.sendMessage(ChatColor.YELLOW + "Tenías seleccionada '" + selectedName
                    + "' pero ya no existe. Selecciona otra.");
        }

        List<DungeonArea> here = areaManager.getAreas().values().stream()
                .filter(a -> a.contains(player.getLocation()))
                .collect(Collectors.toList());

        if (here.size() == 1) {
            return here.get(0);
        }
        if (here.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No tienes un área seleccionada (/dem area select <nombre>) "
                    + "ni estás parado dentro de ninguna.");
        } else {
            String names = here.stream().map(DungeonArea::getName).collect(Collectors.joining(", "));
            player.sendMessage(ChatColor.RED + "Estás dentro de varias áreas (" + names
                    + "). Selecciona una con /dem area select <nombre>.");
        }
        return null;
    }

    private boolean handleSelect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede seleccionar un área.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area select <nombre>");
            return true;
        }
        DungeonArea area = areaManager.getArea(args[1]);
        if (area == null) {
            sender.sendMessage(ChatColor.RED + "No existe el área '" + args[1] + "'.");
            return true;
        }
        selectionListener.setSelectedArea(player.getUniqueId(), area.getName());
        sender.sendMessage(ChatColor.GREEN + "Área '" + area.getName() + "' seleccionada. "
                + "Ahora /dem area addenterhere y addleavehere la usarán automáticamente.");
        showOutline(player, area);
        return true;
    }

    private boolean handleUnselect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede tener una selección.");
            return true;
        }
        selectionListener.clearSelectedArea(player.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + "Selección de área limpiada.");
        return true;
    }

    private boolean handleHere(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando requiere ser un jugador.");
            return true;
        }
        String selected = selectionListener.getSelectedArea(player.getUniqueId());
        sender.sendMessage(ChatColor.AQUA + "Área seleccionada: "
                + (selected != null ? selected : ChatColor.GRAY + "(ninguna)"));

        List<String> here = areaManager.getAreas().values().stream()
                .filter(a -> a.contains(player.getLocation()))
                .map(DungeonArea::getName)
                .collect(Collectors.toList());
        sender.sendMessage(ChatColor.AQUA + "Parado dentro de: "
                + (here.isEmpty() ? ChatColor.GRAY + "(ningún área)" : String.join(", ", here)));
        return true;
    }

    private boolean handleShow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede ver la previsualización.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area show <nombre>");
            return true;
        }
        DungeonArea area = areaManager.getArea(args[1]);
        if (area == null) {
            sender.sendMessage(ChatColor.RED + "No existe el área '" + args[1] + "'.");
            return true;
        }
        showOutline(player, area);
        sender.sendMessage(ChatColor.GREEN + "Mostrando el contorno de '" + area.getName() + "' por 5 segundos.");
        return true;
    }

    private void showOutline(Player player, DungeonArea area) {
        AreaVisualizer.showOutline(plugin, player, area.getWorld(),
                area.getMinX(), area.getMinY(), area.getMinZ(),
                area.getMaxX(), area.getMaxY(), area.getMaxZ(),
                SHOW_OUTLINE_TICKS);
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area remove <nombre>");
            return true;
        }
        if (areaManager.removeArea(args[1])) {
            sender.sendMessage(ChatColor.GREEN + "Área '" + args[1] + "' eliminada.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "No existe el área '" + args[1] + "'.");
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (areaManager.getAreas().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No hay áreas creadas todavía.");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "Áreas: " + String.join(", ", areaManager.getAreas().keySet()));
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area info <nombre>");
            return true;
        }
        DungeonArea area = areaManager.getArea(args[1]);
        if (area == null) {
            sender.sendMessage(ChatColor.RED + "No existe el área '" + args[1] + "'.");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "Área: " + area.getName() + " | Mundo: " + area.getWorld());
        sender.sendMessage(ChatColor.AQUA + "De (" + area.getMinX() + "," + area.getMinY() + "," + area.getMinZ()
                + ") a (" + area.getMaxX() + "," + area.getMaxY() + "," + area.getMaxZ() + ")");
        sender.sendMessage(ChatColor.AQUA + "Comandos de entrada:");
        area.getEnterCommands().forEach(c -> sender.sendMessage(ChatColor.GRAY + " - " + c));
        sender.sendMessage(ChatColor.AQUA + "Comandos de salida:");
        area.getLeaveCommands().forEach(c -> sender.sendMessage(ChatColor.GRAY + " - " + c));

        if (area.getJoinWindowSeconds() > 0) {
            sender.sendMessage(ChatColor.AQUA + "Ventana de ingreso: " + area.getJoinWindowSeconds() + "s");
            sender.sendMessage(ChatColor.AQUA + "Comandos de arranque (corren 1 sola vez):");
            area.getStartCommands().forEach(c -> sender.sendMessage(ChatColor.GRAY + " - " + c));
            String estado = area.isLocked() ? ("bloqueada, " + area.getJoiners().size() + " jugador(es) dentro")
                    : area.isWindowOpen() ? ("cuenta regresiva activa, " + area.getJoiners().size() + " unido(s)")
                    : "libre";
            sender.sendMessage(ChatColor.AQUA + "Estado actual: " + estado);
        } else {
            sender.sendMessage(ChatColor.AQUA + "Ventana de ingreso: desactivada");
        }
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("wand", "create", "addenter", "addleave", "addenterhere",
                    "addleavehere", "setwindow", "addstart", "addstarthere",
                    "select", "unselect", "here", "show", "remove", "list", "info"), args[0]);
        }
        boolean needsAreaName = args.length == 2 && Arrays.asList(
                "addenter", "addleave", "addstart", "setwindow", "remove", "info", "select", "show")
                .contains(args[0].toLowerCase());
        if (needsAreaName) {
            return filter(new ArrayList<>(areaManager.getAreas().keySet()), args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.add(option);
            }
        }
        return result;
    }
}
