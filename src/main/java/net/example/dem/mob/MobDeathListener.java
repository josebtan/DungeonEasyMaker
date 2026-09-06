package net.example.dem.mob;

import net.example.dem.DEMPlugin;
import net.example.dem.loot.LootEntry;
import net.example.dem.loot.LootManager;
import net.example.dem.loot.LootTable;
import net.example.dem.util.CommandRunner;
import net.example.dem.util.PlaceholderContext;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Cuando un mob personalizado (creado con /dem area mob) tiene una tabla de
 * loot asignada y lo mata un jugador, rolea esa tabla y ejecuta el comando
 * ganador con el jugador que dio el golpe final como contexto.
 */
public class MobDeathListener implements Listener {

    private final DEMPlugin plugin;
    private final LootManager lootManager;

    public MobDeathListener(DEMPlugin plugin, LootManager lootManager) {
        this.plugin = plugin;
        this.lootManager = lootManager;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        String tableName = entity.getPersistentDataContainer()
                .get(MobSpawner.lootTableKey(plugin), PersistentDataType.STRING);
        if (tableName == null || tableName.isEmpty()) {
            return;
        }

        Player killer = entity.getKiller();
        if (killer == null) {
            return; // sin un jugador destinatario no hay a quién darle el loot
        }

        LootTable table = lootManager.getTable(tableName);
        if (table == null) {
            return;
        }
        LootEntry entry = table.roll();
        if (entry == null) {
            return;
        }

        Location loc = killer.getLocation();
        PlaceholderContext context = new PlaceholderContext(killer.getName(), loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), killer);
        CommandRunner.runAll(plugin, List.of(entry.getCommand()), context);
    }
}
