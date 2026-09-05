package net.example.dem.area;

import net.example.dem.util.CommandRunner;
import net.example.dem.util.PlaceholderContext;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Detecta transiciones de "afuera -> adentro" y "adentro -> afuera" de cada área,
 * de forma similar a como funcionan los Portales de CMI, pero sin depender de CMI.
 *
 * Además maneja la "ventana de ingreso" opcional (area.getJoinWindowSeconds() > 0):
 * al entrar el primer jugador arranca una cuenta regresiva para que se sumen más;
 * al terminar, el área se bloquea (no se puede entrar más) y los comandos de
 * "addstart" se ejecutan una vez POR CADA jugador que entró durante la ventana,
 * lo que en la práctica multiplica los spawns/loot/etc. según cuántos se unieron.
 */
public class AreaMoveListener implements org.bukkit.event.Listener {

    private final Plugin plugin;
    private final AreaManager areaManager;
    // playerUUID -> set de nombres de áreas en las que está actualmente
    private final Map<UUID, Set<String>> playersInside = new HashMap<>();

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
                if (area.getJoinWindowSeconds() > 0 && area.isLocked()) {
                    // La ventana ya cerró y la dungeon arrancó: no se puede entrar
                    // hasta que se libere (cuando todos salgan del área).
                    event.setTo(from);
                    player.sendMessage(ChatColor.RED + "'" + area.getName()
                            + "' ya comenzó. Espera a que termine para poder entrar.");
                    continue;
                }

                currentlyInside.add(area.getName());
                runCommands(area.getEnterCommands(), player, to);

                if (area.getJoinWindowSeconds() > 0) {
                    handleWindowJoin(area, player);
                }
            } else if (!isInsideNow && wasInsideBefore) {
                currentlyInside.remove(area.getName());
                runCommands(area.getLeaveCommands(), player, to);

                if (area.getJoinWindowSeconds() > 0) {
                    handleWindowLeave(area, player);
                }
            }
        }
    }

    /** Primer jugador que entra arranca la cuenta regresiva; los demás solo se suman. */
    private void handleWindowJoin(DungeonArea area, Player player) {
        area.getJoiners().add(player.getUniqueId());

        if (!area.isWindowOpen()) {
            area.setWindowOpen(true);
            int seconds = area.getJoinWindowSeconds();
            Bukkit.broadcastMessage(ChatColor.GOLD + "[DEM] '" + area.getName()
                    + "' comenzará en " + seconds + " segundos. ¡Únanse ahora!");

            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                    () -> closeWindow(area), seconds * 20L);
            area.setCountdownTask(task);
        }
    }

    private void handleWindowLeave(DungeonArea area, Player player) {
        area.getJoiners().remove(player.getUniqueId());

        // Si ya había arrancado y el área se quedó sin nadie adentro, se libera
        // automáticamente para que se pueda volver a intentar sin reload/reinicio.
        if (area.isLocked() && area.getJoiners().isEmpty()) {
            area.resetWindowState();
            Bukkit.broadcastMessage(ChatColor.GRAY + "[DEM] '" + area.getName()
                    + "' quedó vacía y se liberó para un nuevo intento.");
        }
    }

    /** Se dispara cuando termina la cuenta regresiva: cierra y ejecuta addstart x jugador. */
    private void closeWindow(DungeonArea area) {
        area.setWindowOpen(false);
        area.setLocked(true);
        area.setCountdownTask(null);

        Set<UUID> joiners = new HashSet<>(area.getJoiners());
        int count = joiners.size();

        if (count == 0) {
            // Todos salieron durante la cuenta regresiva: no hay nada que arrancar.
            area.resetWindowState();
            return;
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "[DEM] '" + area.getName()
                + "' comenzó con " + count + " jugador(es).");

        for (UUID uuid : joiners) {
            Player joined = Bukkit.getPlayer(uuid);
            if (joined == null || !joined.isOnline()) continue;
            Location loc = joined.getLocation();
            PlaceholderContext context = new PlaceholderContext(
                    joined.getName(), loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), joined);
            // Se ejecuta la lista completa de addstart por cada jugador: si el
            // comando spawnea 3 mobs y entraron 2 jugadores, terminan siendo 6.
            CommandRunner.runAll(plugin, area.getStartCommands(), context);
        }
    }

    private void runCommands(List<String> commands, Player player, Location loc) {
        PlaceholderContext context = new PlaceholderContext(
                player.getName(), loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), player);
        CommandRunner.runAll(plugin, commands, context);
    }
}
