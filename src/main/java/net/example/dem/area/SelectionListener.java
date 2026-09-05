package net.example.dem.area;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maneja la "varita" de selección: un item especial que al hacer
 * click izquierdo/derecho en un bloque guarda la esquina 1 y 2 de un área.
 */
public class SelectionListener implements Listener {

    private static final String WAND_KEY = "dungeoncore_wand";

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

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
        UUID uuid = event.getPlayer().getUniqueId();
        Location loc = event.getClickedBlock().getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(uuid, loc);
            event.getPlayer().sendMessage(ChatColor.GREEN + "Posición 1 establecida: "
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pos2.put(uuid, loc);
            event.getPlayer().sendMessage(ChatColor.GREEN + "Posición 2 establecida: "
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        }
    }

    public Location getPos1(UUID uuid) {
        return pos1.get(uuid);
    }

    public Location getPos2(UUID uuid) {
        return pos2.get(uuid);
    }
}
