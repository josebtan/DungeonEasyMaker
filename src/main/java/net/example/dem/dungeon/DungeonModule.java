package net.example.dem.dungeon;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;

/**
 * /dem dungeon create <id> <etapa1> <etapa2> ... <etapaN>
 * /dem dungeon setentrance <id> <área> <idPuerta>
 * /dem dungeon setkickpoint <id>          (usa tu posición actual)
 * /dem dungeon complete <id>              (libera todo; usalo en el on-complete de la última etapa)
 * /dem dungeon release <id>               (liberación manual/forzada)
 * /dem dungeon remove|list|info <id>
 */
public class DungeonModule {

    private final DungeonManager dungeonManager;

    public DungeonModule(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create":
                return handleCreate(sender, args);
            case "setentrance":
                return handleSetEntrance(sender, args);
            case "setkickpoint":
                return handleSetKickPoint(sender, args);
            case "complete":
            case "release":
                return handleRelease(sender, args);
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

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem dungeon create <id> <etapa1> <etapa2> ... <etapaN>");
            return true;
        }
        String id = args[1];
        if (dungeonManager.getDungeon(id) != null) {
            sender.sendMessage(ChatColor.RED + "Ya existe un dungeon '" + id + "'.");
            return true;
        }
        Dungeon dungeon = new Dungeon(id);
        for (String stage : Arrays.copyOfRange(args, 2, args.length)) {
            dungeon.getStages().add(stage);
        }
        dungeonManager.addDungeon(dungeon);
        dungeonManager.save();
        sender.sendMessage(ChatColor.GREEN + "Dungeon '" + id + "' creado con " + dungeon.getStages().size()
                + " etapa(s): " + String.join(" -> ", dungeon.getStages()));
        sender.sendMessage(ChatColor.GRAY + "La primera etapa (" + dungeon.getStages().get(0)
                + ") es la entrada. Configurale la puerta con /dem dungeon setentrance.");
        return true;
    }

    private boolean handleSetEntrance(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem dungeon setentrance <id> <área> <idPuerta>");
            return true;
        }
        Dungeon dungeon = resolveDungeon(sender, args[1]);
        if (dungeon == null) return true;

        dungeon.setEntranceDoorArea(args[2]);
        dungeon.setEntranceDoorId(args[3]);
        dungeonManager.save();
        sender.sendMessage(ChatColor.GREEN + "Puerta de entrada de '" + dungeon.getId() + "' establecida en "
                + args[2] + "/" + args[3] + ". Se va a cerrar sola al arrancar la corrida.");
        return true;
    }

    private boolean handleSetKickPoint(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede fijar el punto de expulsión.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem dungeon setkickpoint <id>");
            return true;
        }
        Dungeon dungeon = resolveDungeon(sender, args[1]);
        if (dungeon == null) return true;

        dungeon.setKickPoint(player.getLocation());
        dungeonManager.save();
        sender.sendMessage(ChatColor.GREEN + "Punto de expulsión de '" + dungeon.getId() + "' fijado en tu ubicación. "
                + "Ahí van a aparecer los que mueran adentro o cuando se libere el dungeon.");
        return true;
    }

    private boolean handleRelease(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem dungeon " + args[0] + " <id>");
            return true;
        }
        Dungeon dungeon = resolveDungeon(sender, args[1]);
        if (dungeon == null) return true;

        dungeonManager.release(dungeon);
        sender.sendMessage(ChatColor.GREEN + "Dungeon '" + dungeon.getId() + "' liberado: entrada reabierta, "
                + "todas las etapas reseteadas.");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem dungeon remove <id>");
            return true;
        }
        if (!dungeonManager.removeDungeon(args[1])) {
            sender.sendMessage(ChatColor.RED + "No existe el dungeon '" + args[1] + "'.");
            return true;
        }
        dungeonManager.save();
        sender.sendMessage(ChatColor.YELLOW + "Dungeon '" + args[1] + "' eliminado (las áreas y sus puertas siguen existiendo).");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (dungeonManager.getDungeons().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Todavía no hay dungeons creados.");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "Dungeons:");
        for (Dungeon d : dungeonManager.getDungeons().values()) {
            sender.sendMessage(ChatColor.GRAY + " - " + d.getId() + " (" + d.getStages().size() + " etapas) "
                    + (d.isInProgress() ? ChatColor.RED + "[en curso]" : ChatColor.GREEN + "[libre]"));
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem dungeon info <id>");
            return true;
        }
        Dungeon dungeon = resolveDungeon(sender, args[1]);
        if (dungeon == null) return true;

        sender.sendMessage(ChatColor.AQUA + "=== Dungeon '" + dungeon.getId() + "' ===");
        sender.sendMessage(ChatColor.GRAY + "Etapas: " + String.join(" -> ", dungeon.getStages()));
        sender.sendMessage(ChatColor.GRAY + "Entrada: " + (dungeon.getEntranceDoorArea() == null ? "(sin configurar)"
                : dungeon.getEntranceDoorArea() + "/" + dungeon.getEntranceDoorId()));
        sender.sendMessage(ChatColor.GRAY + "Punto de expulsión: " + (dungeon.hasKickPoint() ? "configurado" : "(sin configurar)"));
        sender.sendMessage(ChatColor.GRAY + "Estado: " + (dungeon.isInProgress()
                ? ChatColor.RED + "en curso (" + dungeon.getParticipants().size() + " participante(s))"
                : ChatColor.GREEN + "libre"));
        return true;
    }

    private Dungeon resolveDungeon(CommandSender sender, String id) {
        Dungeon dungeon = dungeonManager.getDungeon(id);
        if (dungeon == null) {
            sender.sendMessage(ChatColor.RED + "No existe el dungeon '" + id + "'.");
        }
        return dungeon;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Uso: /dem dungeon <accion> ...");
        sender.sendMessage(ChatColor.GRAY + "create <id> <etapa1> <etapa2> ... <etapaN>");
        sender.sendMessage(ChatColor.GRAY + "setentrance <id> <área> <idPuerta>");
        sender.sendMessage(ChatColor.GRAY + "setkickpoint <id>  (usa tu posición actual)");
        sender.sendMessage(ChatColor.GRAY + "complete|release <id>");
        sender.sendMessage(ChatColor.GRAY + "remove|list|info <id>");
    }
}
