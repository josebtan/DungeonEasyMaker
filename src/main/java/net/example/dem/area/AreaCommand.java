package net.example.dem.area;

import net.example.dem.DEMPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AreaCommand implements CommandExecutor, TabCompleter {

    private final DEMPlugin plugin;
    private final AreaManager areaManager;
    private final SelectionListener selectionListener;

    public AreaCommand(DEMPlugin plugin, AreaManager areaManager, SelectionListener selectionListener) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.selectionListener = selectionListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender);
            case "info":
                return handleInfo(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Uso:");
        sender.sendMessage(ChatColor.RED + "/dungeonarea wand");
        sender.sendMessage(ChatColor.RED + "/dungeonarea create <nombre>");
        sender.sendMessage(ChatColor.RED + "/dungeonarea addenter <nombre> <comando>");
        sender.sendMessage(ChatColor.RED + "/dungeonarea addleave <nombre> <comando>");
        sender.sendMessage(ChatColor.RED + "/dungeonarea remove <nombre>");
        sender.sendMessage(ChatColor.RED + "/dungeonarea list");
        sender.sendMessage(ChatColor.RED + "/dungeonarea info <nombre>");
    }

    private boolean handleWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede recibir la varita.");
            return true;
        }
        player.getInventory().addItem(selectionListener.createWand());
        player.sendMessage(ChatColor.GREEN + "Varita recibida. Click izquierdo = Posición 1, "
                + "click derecho = Posición 2.");
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede crear un área (necesita posiciones seleccionadas).");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dungeonarea create <nombre>");
            return true;
        }

        String name = args[1];
        Location pos1 = selectionListener.getPos1(player.getUniqueId());
        Location pos2 = selectionListener.getPos2(player.getUniqueId());

        if (pos1 == null || pos2 == null) {
            sender.sendMessage(ChatColor.RED + "Primero selecciona las dos esquinas con la varita "
                    + "(/dungeonarea wand).");
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
        sender.sendMessage(ChatColor.GREEN + "Área '" + name + "' creada. Ahora agrégale comandos con "
                + "/dungeonarea addenter " + name + " <comando>");
        return true;
    }

    private boolean handleAddCommand(CommandSender sender, String[] args, boolean isEnter) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dungeonarea " + args[0] + " <nombre> <comando>");
            return true;
        }
        DungeonArea area = areaManager.getArea(args[1]);
        if (area == null) {
            sender.sendMessage(ChatColor.RED + "No existe el área '" + args[1] + "'.");
            return true;
        }
        String commandText = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (isEnter) {
            area.getEnterCommands().add(commandText);
        } else {
            area.getLeaveCommands().add(commandText);
        }
        areaManager.save();
        sender.sendMessage(ChatColor.GREEN + "Comando agregado a '" + args[1] + "' ("
                + (isEnter ? "entrada" : "salida") + "): " + commandText);
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dungeonarea remove <nombre>");
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
            sender.sendMessage(ChatColor.RED + "Uso: /dungeonarea info <nombre>");
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
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("wand", "create", "addenter", "addleave", "remove", "list", "info"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("wand") && !args[0].equalsIgnoreCase("create")
                && !args[0].equalsIgnoreCase("list")) {
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
