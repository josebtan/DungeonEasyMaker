package net.example.dem.loot;

import net.example.dem.DEMPlugin;
import net.example.dem.util.CommandRunner;
import net.example.dem.util.PlaceholderContext;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lógica del módulo de loot. Se invoca desde /dem loot <accion> ...
 */
public class LootModule {

    private final DEMPlugin plugin;
    private final LootManager lootManager;

    public LootModule(DEMPlugin plugin, LootManager lootManager) {
        this.plugin = plugin;
        this.lootManager = lootManager;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                return handleCreate(sender, args);
            case "addentry":
                return handleAddEntry(sender, args);
            case "removeentry":
                return handleRemoveEntry(sender, args);
            case "list":
                return handleList(sender, args);
            case "roll":
                return handleRoll(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Uso:");
        sender.sendMessage(ChatColor.RED + "/dem loot create <tabla>");
        sender.sendMessage(ChatColor.RED + "/dem loot addentry <tabla> <peso> <comando>");
        sender.sendMessage(ChatColor.RED + "/dem loot removeentry <tabla> <indice>");
        sender.sendMessage(ChatColor.RED + "/dem loot list <tabla>");
        sender.sendMessage(ChatColor.RED + "/dem loot roll <tabla> <jugador>");
        sender.sendMessage(ChatColor.GRAY + "Placeholders: [player] [world] [x] [y] [z] | Delay: \"delay:<segundos>|<comando>\"");
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem loot create <tabla>");
            return true;
        }
        if (lootManager.getTable(args[1]) != null) {
            sender.sendMessage(ChatColor.YELLOW + "Ya existe una tabla llamada '" + args[1] + "'.");
            return true;
        }
        lootManager.createTable(args[1]);
        sender.sendMessage(ChatColor.GREEN + "Tabla de loot '" + args[1] + "' creada.");
        return true;
    }

    // /dem loot addentry <tabla> <peso> <comando...>
    // El comando puede usar [player]/%player%, [world], [x], [y], [z], y opcionalmente
    // un prefijo "delay:<segundos>|" para retrasar su ejecución.
    private boolean handleAddEntry(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem loot addentry <tabla> <peso> <comando>");
            return true;
        }
        LootTable table = lootManager.getTable(args[1]);
        if (table == null) {
            sender.sendMessage(ChatColor.RED + "No existe la tabla '" + args[1] + "'.");
            return true;
        }
        int weight;
        try {
            weight = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "El peso debe ser un número entero.");
            return true;
        }
        String commandText = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        table.addEntry(weight, commandText);
        lootManager.save();
        sender.sendMessage(ChatColor.GREEN + "Recompensa agregada a '" + args[1] + "' (peso " + weight
                + "): " + commandText);
        return true;
    }

    private boolean handleRemoveEntry(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem loot removeentry <tabla> <indice>");
            return true;
        }
        LootTable table = lootManager.getTable(args[1]);
        if (table == null) {
            sender.sendMessage(ChatColor.RED + "No existe la tabla '" + args[1] + "'.");
            return true;
        }
        int index;
        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "El índice debe ser un número entero.");
            return true;
        }
        table.removeEntry(index);
        lootManager.save();
        sender.sendMessage(ChatColor.GREEN + "Entrada #" + index + " eliminada de '" + args[1] + "'.");
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem loot list <tabla>");
            return true;
        }
        LootTable table = lootManager.getTable(args[1]);
        if (table == null) {
            sender.sendMessage(ChatColor.RED + "No existe la tabla '" + args[1] + "'.");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "Tabla '" + table.getName() + "':");
        List<LootEntry> entries = table.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            LootEntry entry = entries.get(i);
            sender.sendMessage(ChatColor.GRAY + " [" + i + "] peso " + entry.getWeight()
                    + " -> " + entry.getCommand());
        }
        return true;
    }

    // /dem loot roll <tabla> <jugador>
    private boolean handleRoll(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem loot roll <tabla> <jugador>");
            return true;
        }
        LootTable table = lootManager.getTable(args[1]);
        if (table == null) {
            sender.sendMessage(ChatColor.RED + "No existe la tabla '" + args[1] + "'.");
            return true;
        }
        String playerName = args[2];
        LootEntry result = table.roll();
        if (result == null) {
            sender.sendMessage(ChatColor.YELLOW + "La tabla '" + args[1] + "' no tiene entradas.");
            return true;
        }

        PlaceholderContext context = buildContext(playerName);
        CommandRunner.runAll(plugin, List.of(result.getCommand()), context);
        plugin.getLogger().info("Loot roll '" + table.getName() + "' para " + playerName
                + " -> " + result.getCommand());
        return true;
    }

    private PlaceholderContext buildContext(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            Location loc = online.getLocation();
            return new PlaceholderContext(playerName, loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), online);
        }
        return PlaceholderContext.ofPlayerOnly(playerName);
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("create", "addentry", "removeentry", "list", "roll"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            return filter(new ArrayList<>(lootManager.getTables().keySet()), args[1]);
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
