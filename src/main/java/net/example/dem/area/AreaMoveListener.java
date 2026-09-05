package net.example.dem.area;

import net.example.dem.util.CommandRunner;
import net.example.dem.util.PlaceholderContext;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detecta transiciones de "afuera -> adentro" y "adentro -> afuera" de cada área,
 * de forma similar a como funcionan los Portales de CMI, pero sin depender de CMI.
 */
public class AreaMoveListener implements org.bukkit.event.Listener {

    private final Plugin plugin;
    private final AreaManager areaManager;
    // playerUUID -> set de nombres de áreas en las que está actualmente
    private final Map<java.util.UUID, Set<String>> playersInside = new HashMap<>();

    public AreaMoveListener(Plugin plugin, AreaManager areaManager) {
        this.plugin = plugin;
        this.areaManager = areaManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Optimización: solo revisar si cambió de bloque, no en cada micro-movimiento
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Set<String> currentlyInside = playersInside.computeIfAbsent(
                player.getUniqueId(), k -> new HashSet<>());

        for (DungeonArea area : areaManager.getAreas().values()) {
            boolean isInsideNow = area.contains(to);
            boolean wasInsideBefore = currentlyInside.contains(area.getName());

            if (isInsideNow && !wasInsideBefore) {
                currentlyInside.add(area.getName());
                runCommands(area.getEnterCommands(), player, to);
            } else if (!isInsideNow && wasInsideBefore) {
                currentlyInside.remove(area.getName());
                runCommands(area.getLeaveCommands(), player, to);
            }
        }
    }

    private void runCommands(List<String> commands, Player player, Location loc) {
        PlaceholderContext context = new PlaceholderContext(
                player.getName(), loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        CommandRunner.runAll(plugin, commands, context);
    }
}
