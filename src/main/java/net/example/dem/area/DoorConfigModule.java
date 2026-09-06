package net.example.dem.area;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Subcomandos de /dem area door ... — crear puertas físicas capturando los
 * bloques ya construidos (su estado "cerrado"), y abrirlas/cerrarlas por
 * comando en vez de escribir setblock/fill a mano.
 */
public class DoorConfigModule {

    private final AreaManager areaManager;
    private final SelectionListener selectionListener;

    public DoorConfigModule(AreaManager areaManager, SelectionListener selectionListener) {
        this.areaManager = areaManager;
        this.selectionListener = selectionListener;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create":
                return handleCreate(sender, args);
            case "open":
                return withDoor(sender, args, door -> {
                    door.open();
                    return "Puerta '" + door.getId() + "' abierta.";
                });
            case "close":
                return withDoor(sender, args, door -> {
                    door.close();
                    return "Puerta '" + door.getId() + "' cerrada.";
                });
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender, args);
            case "info":
                return handleInfo(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un jugador puede crear una puerta (necesita la varita marcada).");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area door create <área> <id>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;

        String id = args[2];
        if (area.getDoor(id) != null) {
            sender.sendMessage(ChatColor.RED + "Ya existe una puerta '" + id + "' en '" + area.getName() + "'.");
            return true;
        }

        Location pos1 = selectionListener.getPos1(player.getUniqueId());
        Location pos2 = selectionListener.getPos2(player.getUniqueId());
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || !pos1.getWorld().equals(pos2.getWorld())) {
            sender.sendMessage(ChatColor.RED + "Primero marcá las 2 esquinas de la puerta con la varita "
                    + "(/dem area wand), en el mismo mundo.");
            return true;
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
        sender.sendMessage(ChatColor.GREEN + "Puerta '" + id + "' creada en '" + area.getName() + "' ("
                + blocks.size() + " bloques capturados como estado cerrado). Usá /dem area door open/close "
                + area.getName() + " " + id + " para manejarla.");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area door remove <área> <id>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        if (!area.removeDoor(args[2])) {
            sender.sendMessage(ChatColor.RED + "No existe la puerta '" + args[2] + "' en '" + area.getName() + "'.");
            return true;
        }
        areaManager.save();
        sender.sendMessage(ChatColor.YELLOW + "Puerta '" + args[2] + "' eliminada de '" + area.getName() + "'.");
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area door list <área>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        if (area.getDoorsById().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "'" + area.getName() + "' todavía no tiene puertas.");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "Puertas de '" + area.getName() + "':");
        for (DoorDefinition door : area.getDoorsById().values()) {
            sender.sendMessage(ChatColor.GRAY + " - " + door.getId() + " (" + door.getBlockCount() + " bloques)");
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area door info <área> <id>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        DoorDefinition door = area.getDoor(args[2]);
        if (door == null) {
            sender.sendMessage(ChatColor.RED + "No existe la puerta '" + args[2] + "' en '" + area.getName() + "'.");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "=== Puerta '" + door.getId() + "' (" + area.getName() + ") ===");
        sender.sendMessage(ChatColor.GRAY + "Mundo: " + door.getWorld());
        sender.sendMessage(ChatColor.GRAY + "Cubo: (" + door.getMinX() + "," + door.getMinY() + "," + door.getMinZ()
                + ") a (" + door.getMaxX() + "," + door.getMaxY() + "," + door.getMaxZ() + ")");
        sender.sendMessage(ChatColor.GRAY + "Bloques capturados: " + door.getBlockCount());
        return true;
    }

    // ----------------------------------------------------------------

    private interface DoorAction {
        String apply(DoorDefinition door);
    }

    private boolean withDoor(CommandSender sender, String[] args, DoorAction action) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /dem area door " + args[0] + " <área> <id>");
            return true;
        }
        DungeonArea area = resolveArea(sender, args[1]);
        if (area == null) return true;
        DoorDefinition door = area.getDoor(args[2]);
        if (door == null) {
            sender.sendMessage(ChatColor.RED + "No existe la puerta '" + args[2] + "' en '" + area.getName() + "'.");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + action.apply(door));
        return true;
    }

    private DungeonArea resolveArea(CommandSender sender, String name) {
        DungeonArea area = areaManager.getArea(name);
        if (area == null) {
            sender.sendMessage(ChatColor.RED + "No existe el área '" + name + "'.");
        }
        return area;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Uso: /dem area door <accion> <área> [id]");
        sender.sendMessage(ChatColor.GRAY + "create <área> <id>  (marcá 2 esquinas con la varita antes; captura lo ya construido)");
        sender.sendMessage(ChatColor.GRAY + "open|close <área> <id>");
        sender.sendMessage(ChatColor.GRAY + "remove|list|info <área> [id]");
    }
}
