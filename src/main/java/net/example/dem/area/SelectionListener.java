package net.example.dem.area;

import net.example.dem.util.AreaVisualizer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maneja la "varita" de selección: un item especial que al hacer
 * click izquierdo/derecho en un bloque guarda la esquina 1 y 2 de un área.
 *
 * También trackea, por jugador, cuál área tiene "seleccionada" para poder
 * usar /dem area addenterhere, addleavehere, etc. sin repetir el nombre.
 *
 * Al seleccionar posiciones, muestra un bloque fantasma (client-side, no
 * modifica el mundo real) en cada esquina, y cuando ambas están listas,
 * dibuja el contorno completo con partículas por unos segundos.
 */
public class SelectionListener implements Listener {

    private static final String WAND_KEY = "dungeoncore_wand";
    private static final int GHOST_BLOCK_TIMEOUT_TICKS = 30 * 20; // 30 segundos
    private static final int PREVIEW_OUTLINE_TICKS = 3 * 20; // 3 segundos

    private final Plugin plugin;

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final Map<UUID, String> selectedArea = new HashMap<>();

    // Para poder revertir el bloque fantasma anterior si el jugador reselecciona
    private final Map<UUID, Location> ghostPos1 = new HashMap<>();
    private final Map<UUID, Location> ghostPos2 = new HashMap<>();
    private final Map<UUID, BukkitTask> ghostRevertTasks = new HashMap<>();

    public SelectionListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack createWand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Selector de Dungeon");
        meta.getPersistentDataContainer().set(
                new NamespacedKey("dungeoncore", WAND_KEY), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer()
                .has(new NamespacedKey("dungeoncore", WAND_KEY), PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isWand(event.getItem()) || event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        org.bukkit.entity.Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location loc = event.getClickedBlock().getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            revertGhost(player, ghostPos1.get(uuid));
            pos1.put(uuid, loc);
            showGhost(player, uuid, loc, true);
            player.sendMessage(ChatColor.GREEN + "Posición 1 establecida: "
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            revertGhost(player, ghostPos2.get(uuid));
            pos2.put(uuid, loc);
            showGhost(player, uuid, loc, false);
            player.sendMessage(ChatColor.GREEN + "Posición 2 establecida: "
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        }

        Location p1 = pos1.get(uuid);
        Location p2 = pos2.get(uuid);
        if (p1 != null && p2 != null && p1.getWorld().equals(p2.getWorld())) {
            AreaVisualizer.showOutline(plugin, player, p1.getWorld().getName(),
                    p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                    p2.getBlockX(), p2.getBlockY(), p2.getBlockZ(),
                    PREVIEW_OUTLINE_TICKS);
        }

        scheduleGhostTimeout(player, uuid);
    }

    private void showGhost(org.bukkit.entity.Player player, UUID uuid, Location loc, boolean isPos1) {
        AreaVisualizer.showCorner(player, loc);
        if (isPos1) {
            ghostPos1.put(uuid, loc);
        } else {
            ghostPos2.put(uuid, loc);
        }
    }

    private void revertGhost(org.bukkit.entity.Player player, Location previous) {
        if (previous != null && player.isOnline()) {
            AreaVisualizer.revertCorner(player, previous);
        }
    }

    private void scheduleGhostTimeout(org.bukkit.entity.Player player, UUID uuid) {
        BukkitTask existing = ghostRevertTasks.get(uuid);
        if (existing != null) {
            existing.cancel();
        }
        BukkitTask task = org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            revertGhost(player, ghostPos1.remove(uuid));
            revertGhost(player, ghostPos2.remove(uuid));
        }, GHOST_BLOCK_TIMEOUT_TICKS);
        ghostRevertTasks.put(uuid, task);
    }

    public Location getPos1(UUID uuid) {
        return pos1.get(uuid);
    }

    public Location getPos2(UUID uuid) {
        return pos2.get(uuid);
    }

    public void setSelectedArea(UUID uuid, String areaName) {
        selectedArea.put(uuid, areaName);
    }

    public void clearSelectedArea(UUID uuid) {
        selectedArea.remove(uuid);
    }

    public String getSelectedArea(UUID uuid) {
        return selectedArea.get(uuid);
    }
}
