package net.example.dem.loot;

import net.example.dem.DEMPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LootCommand implements CommandExecutor, TabCompleter {

    private final DEMPlugin plugin;
    private final LootManager lootManager;

    public LootCommand(DEMPlugin plugin, LootManager lootManager) {
        this.plugin = plugin;
        this.lootManager = lootManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
            case "reload":
                return handleReload(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        lootManager.reload();
        sender.sendMessage(ChatColor.GREEN + "loot.yml recargado desde disco. Tablas cargadas: "
                + lootManager.getTables().size());
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Uso:");
        sender.sendMessage(ChatColor.RED + "/dungeonloot create <tabla>");
        sender.sendMessage(ChatColor.RED + "/dungeonloot addentry <tabla> <peso> <comando>");
        sender.sendMessage(ChatColor.RED + "/dungeonloot removeentry <tabla> <indice>");
        sender.sendMessage(ChatColor.RED + "/dungeonloot list <tabla>");
        sender.sendMessage(ChatColor.RED + "/dungeonloot roll <tabla> <jugador>");
        sender.sendMessage(ChatColor.RED + "/dungeonloot reload");
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dungeonloot create <tabla>");
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

    // /dungeonloot addentry <tabla> <peso> <comando...>
    // El comando puede usar %player% que se reemplaza al momento de dar la recompensa.
    private boolean handleAddEntry(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /dungeonloot addentry <tabla> <peso> <comando>");
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
            sender.sendMessage(ChatColor.RED + "Uso: /dungeonloot removeentry <tabla> <indice>");
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
            sender.sendMessage(ChatColor.RED + "Uso: /dungeonloot list <tabla>");
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

    // /dungeonloot roll <tabla> <jugador>
    private boolean handleRoll(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dungeonloot roll <tabla> <jugador>");
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
        String finalCommand = result.getCommand().replace("%player%", playerName);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        plugin.getLogger().info("Loot roll '" + table.getName() + "' para " + playerName
                + " -> " + finalCommand);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("create", "addentry", "removeentry", "list", "roll", "reload"), args[0]);
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
