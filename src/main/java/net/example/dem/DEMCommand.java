package net.example.dem;

import net.example.dem.area.AreaManager;
import net.example.dem.area.AreaModule;
import net.example.dem.gui.GuiManager;
import net.example.dem.loot.LootManager;
import net.example.dem.loot.LootModule;
import net.example.dem.objective.ObjectiveModule;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Punto de entrada único del plugin: /dem <modulo> <accion> ...
 *   /dem area ...
 *   /dem objective ...
 *   /dem loot ...
 *   /dem gui
 *   /dem reload
 */
public class DEMCommand implements CommandExecutor, TabCompleter {

    private final AreaManager areaManager;
    private final LootManager lootManager;
    private final AreaModule areaModule;
    private final ObjectiveModule objectiveModule;
    private final LootModule lootModule;
    private final GuiManager guiManager;

    public DEMCommand(AreaManager areaManager, LootManager lootManager,
                       AreaModule areaModule, ObjectiveModule objectiveModule, LootModule lootModule,
                       GuiManager guiManager) {
        this.areaManager = areaManager;
        this.lootManager = lootManager;
        this.areaModule = areaModule;
        this.objectiveModule = objectiveModule;
        this.lootModule = lootModule;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (args[0].toLowerCase()) {
            case "area":
                return areaModule.handle(sender, rest);
            case "objective":
                return objectiveModule.handle(sender, rest);
            case "loot":
                return lootModule.handle(sender, rest);
            case "gui":
            case "menu":
                return handleGui(sender);
            case "reload":
                return handleReload(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede abrir el menú.");
            return true;
        }
        guiManager.openMainMenu(player);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Uso:");
        sender.sendMessage(ChatColor.RED + "/dem area <wand|create|addenter|addleave|remove|list|info>");
        sender.sendMessage(ChatColor.RED + "/dem objective <watch|reset|status>");
        sender.sendMessage(ChatColor.RED + "/dem loot <create|addentry|removeentry|list|roll>");
        sender.sendMessage(ChatColor.RED + "/dem gui  (menú de configuración por inventario)");
        sender.sendMessage(ChatColor.RED + "/dem reload");
    }

    private boolean handleReload(CommandSender sender) {
        areaManager.reload();
        lootManager.reload();
        sender.sendMessage(ChatColor.GREEN + "DEM recargado: " + areaManager.getAreas().size()
                + " área(s) y " + lootManager.getTables().size() + " tabla(s) de loot cargadas desde disco.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("area", "objective", "loot", "gui", "reload"), args[0]);
        }

        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase()) {
            case "area":
                return areaModule.tabComplete(sender, rest);
            case "objective":
                return objectiveModule.tabComplete(sender, rest);
            case "loot":
                return lootModule.tabComplete(sender, rest);
            default:
                return new ArrayList<>();
        }
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
