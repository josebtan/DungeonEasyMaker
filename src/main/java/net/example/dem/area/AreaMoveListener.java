package net.example.dem.area;

import net.example.dem.dungeon.Dungeon;
import net.example.dem.dungeon.DungeonManager;
import net.example.dem.mob.MobSpawner;
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

import java.util.ArrayList;
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
 * "addstart" se ejecutan UNA sola vez, tal como están escritos (no se multiplican
 * por la cantidad de jugadores que entraron). Los mobs personalizados de la
 * área (ver net.example.dem.mob) se spawnean en ese mismo momento, cada uno
 * respetando su propio delay, y se despawnean solos cuando el área se libera.
 */
public class AreaMoveListener implements org.bukkit.event.Listener {

    private final Plugin plugin;
    private final AreaManager areaManager;
    private final DungeonManager dungeonManager;
    // playerUUID -> set de nombres de áreas en las que está actualmente
    private final Map<UUID, Set<String>> playersInside = new HashMap<>();

    public AreaMoveListener(Plugin plugin, AreaManager areaManager, DungeonManager dungeonManager) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.dungeonManager = dungeonManager;
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

        List<DungeonArea> entered = new ArrayList<>();
        List<DungeonArea> left = new ArrayList<>();
        for (DungeonArea area : areaManager.getAreas().values()) {
            boolean isInsideNow = area.contains(to);
            boolean wasInsideBefore = currentlyInside.contains(area.getName());
            if (isInsideNow && !wasInsideBefore) {
                entered.add(area);
            } else if (!isInsideNow && wasInsideBefore) {
                left.add(area);
            }
        }

        // IMPORTANTE: procesamos primero TODAS las entradas y recién después
        // las salidas. Si alguien camina directo de una etapa a la siguiente
        // en el mismo tick (ej: cruza la puerta de etapa1 a etapa2), así la
        // etapa nueva ya queda registrada como ocupada ANTES de evaluar si el
        // dungeon quedó vacío por la salida de la etapa vieja. Si lo
        // hiciéramos en el orden inverso, un jugador que avanza de etapa se
        // contaría como "salió del dungeon" por un instante y se liberaría
        // (reabriendo la entrada y sin poder disparar los mobs de la próxima
        // etapa) aunque en los hechos nunca dejó de estar adentro.
        for (DungeonArea area : entered) {
            handleAreaEnter(event, player, currentlyInside, area, from, to);
        }
        for (DungeonArea area : left) {
            handleAreaLeave(player, currentlyInside, area, to);
        }
    }

    private void handleAreaEnter(PlayerMoveEvent event, Player player, Set<String> currentlyInside,
                                  DungeonArea area, Location from, Location to) {
        if (area.getJoinWindowSeconds() > 0 && area.isLocked()) {
            // La ventana ya cerró: no se puede entrar hasta que se libere.
            // Si "from" también está adentro (llegó por /tp u otro comando en
            // vez de caminar), no alcanza con cancelar el movimiento: hay que
            // expulsarlo a un punto seguro afuera.
            Location safeSpot = area.contains(from) ? resolveKickSpot(area, player) : from;
            event.setTo(safeSpot);
            player.sendMessage(ChatColor.RED + "'" + area.getName()
                    + "' ya comenzó. Espera a que termine para poder entrar.");
            return;
        }

        currentlyInside.add(area.getName());
        runCommands(area.getEnterCommands(), player, to);

        if (area.getJoinWindowSeconds() > 0) {
            handleWindowJoin(area, player);
        }
    }

    private void handleAreaLeave(Player player, Set<String> currentlyInside, DungeonArea area, Location to) {
        currentlyInside.remove(area.getName());
        runCommands(area.getLeaveCommands(), player, to);

        if (area.getJoinWindowSeconds() > 0) {
            handleWindowLeave(area, player);
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

    /** A dónde mandar a alguien que apareció adentro de un área bloqueada sin haber caminado hasta ahí. */
    private Location resolveKickSpot(DungeonArea area, Player player) {
        Dungeon dungeon = dungeonManager.findDungeonByArea(area.getName());
        if (dungeon != null && dungeon.hasKickPoint()) {
            Location kick = dungeon.getKickLocation();
            if (kick != null) {
                return kick;
            }
        }
        return player.getWorld().getSpawnLocation();
    }

    private void handleWindowLeave(DungeonArea area, Player player) {
        area.getJoiners().remove(player.getUniqueId());
        if (!area.isLocked()) {
            return;
        }

        Dungeon dungeon = dungeonManager.findDungeonByArea(area.getName());
        if (dungeon != null) {
            // Esta área es una etapa de un dungeon encadenado: no se libera sola
            // (es normal que se vacíe cuando el grupo avanza a la siguiente
            // etapa). Solo se libera el dungeon ENTERO cuando no queda nadie en
            // NINGUNA de sus etapas.
            if (dungeon.isInProgress() && !dungeonManager.hasAnyoneInside(dungeon)) {
                dungeonManager.release(dungeon);
                Bukkit.broadcastMessage(ChatColor.GRAY + "[DEM] El dungeon '" + dungeon.getId()
                        + "' quedó vacío y se liberó (entrada reabierta).");
            }
            return;
        }

        // Área independiente (no pertenece a ningún dungeon): comportamiento de siempre.
        if (area.getJoiners().isEmpty()) {
            area.resetWindowState();
            Bukkit.broadcastMessage(ChatColor.GRAY + "[DEM] '" + area.getName()
                    + "' quedó vacía y se liberó para un nuevo intento.");
        }
    }

    /** Se dispara cuando termina la cuenta regresiva: cierra la ventana y ejecuta addstart. */
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

        // Los comandos de "addstart" corren UNA sola vez, tal como están escritos
        // (no se multiplican por la cantidad de jugadores que entraron).
        Player reference = joiners.stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .findFirst()
                .orElse(null);

        PlaceholderContext context;
        if (reference != null) {
            Location loc = reference.getLocation();
            context = new PlaceholderContext(reference.getName(), loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), reference);
        } else {
            context = PlaceholderContext.ofPlayerOnly("");
        }
        CommandRunner.runAll(plugin, area.getStartCommands(), context);

        // Los mobs configurados para esta área (posición fija, delay propio)
        // se disparan junto con los comandos de arranque.
        MobSpawner.spawnAllForArea(plugin, area);

        // Cierra sola la puerta de entrada de ESTA etapa (si tiene una
        // asignada con /dem area setentrydoor), para que nadie más pueda
        // entrar a esta sala en particular una vez que arrancó el evento.
        DoorDefinition entryDoor = area.getEntryDoor();
        if (entryDoor != null) {
            entryDoor.close();
        }

        // Si esta área es la ENTRADA de un dungeon encadenado, arranca la
        // corrida completa (marca participantes; la puerta ya se cerró arriba).
        Dungeon dungeon = dungeonManager.findDungeonByArea(area.getName());
        if (dungeon != null && dungeonManager.isEntranceStage(dungeon, area.getName())) {
            dungeonManager.startDungeon(dungeon, joiners);
        }
    }

    private void runCommands(List<String> commands, Player player, Location loc) {
        PlaceholderContext context = new PlaceholderContext(
                player.getName(), loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), player);
        CommandRunner.runAll(plugin, commands, context);
    }
}
