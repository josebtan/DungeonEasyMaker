package net.example.dem;

import net.example.dem.area.AreaManager;
import net.example.dem.loot.LootManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DEMCommand implements CommandExecutor, TabCompleter {

    private final AreaManager areaManager;
    private final LootManager lootManager;

    public DEMCommand(AreaManager areaManager, LootManager lootManager) {
        this.areaManager = areaManager;
        this.lootManager = lootManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            areaManager.reload();
            lootManager.reload();
            sender.sendMessage(ChatColor.GREEN + "DEM recargado: " + areaManager.getAreas().size()
                    + " área(s) y " + lootManager.getTables().size() + " tabla(s) de loot cargadas desde disco.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Uso: /dem reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String option : Arrays.asList("reload")) {
                if (option.startsWith(args[0].toLowerCase())) {
                    result.add(option);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }
}
