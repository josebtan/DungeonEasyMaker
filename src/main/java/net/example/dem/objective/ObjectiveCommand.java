package net.example.dem.objective;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ObjectiveCommand implements CommandExecutor, TabCompleter {

    private final ObjectiveManager objectiveManager;

    public ObjectiveCommand(ObjectiveManager objectiveManager) {
        this.objectiveManager = objectiveManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "watch":
                return handleWatch(sender, args);
            case "reset":
                return handleReset(sender, args);
            case "status":
                return handleStatus(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Uso:");
        sender.sendMessage(ChatColor.RED + "/dungeon watch <tag> <cantidad> <comando1>~~<comando2>~~...");
        sender.sendMessage(ChatColor.RED + "/dungeon reset <tag>");
        sender.sendMessage(ChatColor.RED + "/dungeon status <tag>");
    }

    private boolean handleWatch(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /dungeon watch <tag> <cantidad> <comando1>~~<comando2>~~...");
            return true;
        }

        String tag = args[1];
        int count;
        try {
            count = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "La cantidad debe ser un número entero.");
            return true;
        }

        String joinedCommands = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        List<String> onCompleteCommands = Arrays.asList(joinedCommands.split("~~"));

        objectiveManager.getActiveGroups().put(tag, new GroupState(count, onCompleteCommands));
        sender.sendMessage(ChatColor.GREEN + "Grupo '" + tag + "' iniciado con " + count
                + " objetivos y " + onCompleteCommands.size() + " comando(s) al completarse.");
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dungeon reset <tag>");
            return true;
        }
        String tag = args[1];
        if (objectiveManager.getActiveGroups().remove(tag) != null) {
            sender.sendMessage(ChatColor.GREEN + "Grupo '" + tag + "' reiniciado.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "El grupo '" + tag + "' no estaba activo.");
        }
        return true;
    }

    private boolean handleStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dungeon status <tag>");
            return true;
        }
        String tag = args[1];
        GroupState state = objectiveManager.getActiveGroups().get(tag);
        if (state == null) {
            sender.sendMessage(ChatColor.YELLOW + "El grupo '" + tag + "' no está activo.");
        } else {
            sender.sendMessage(ChatColor.AQUA + "Grupo '" + tag + "': " + state.getRemaining()
                    + " objetivos restantes.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("watch", "reset", "status"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("watch")) {
            return filter(new ArrayList<>(objectiveManager.getActiveGroups().keySet()), args[1]);
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
