package net.example.dem.dungeon;

import net.example.dem.area.AreaManager;
import net.example.dem.area.DungeonArea;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Si morís dentro de la etapa de un dungeon, quedás afuera (te manda al punto
 * de expulsión al reaparecer) hasta que el dungeon se libere de nuevo. La
 * corrida sigue para el resto del grupo; solo vos quedás eliminado.
 */
public class DungeonDeathListener implements Listener {

    private final AreaManager areaManager;
    private final DungeonManager dungeonManager;
    private final Map<UUID, Location> pendingKick = new HashMap<>();

    public DungeonDeathListener(AreaManager areaManager, DungeonManager dungeonManager) {
        this.areaManager = areaManager;
        this.dungeonManager = dungeonManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        for (DungeonArea area : areaManager.getAreas().values()) {
            if (!area.getJoiners().contains(player.getUniqueId())) {
                continue;
            }

            Dungeon dungeon = dungeonManager.findDungeonByArea(area.getName());
            area.getJoiners().remove(player.getUniqueId());

            if (dungeon != null) {
                Location kickLoc = dungeon.getKickLocation();
                if (kickLoc != null) {
                    pendingKick.put(player.getUniqueId(), kickLoc);
                }
                if (dungeon.isInProgress() && !dungeonManager.hasAnyoneInside(dungeon)) {
                    dungeonManager.release(dungeon);
                }
            }
            break; // un jugador solo puede estar "dentro" de una etapa a la vez
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location kickLoc = pendingKick.remove(event.getPlayer().getUniqueId());
        if (kickLoc != null) {
            event.setRespawnLocation(kickLoc);
        }
    }
}
