package net.example.dem.objective;

import net.example.dem.DEMPlugin;
import net.example.dem.util.CommandRunner;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Map;

public class GuardDeathListener implements Listener {

    private final DEMPlugin plugin;
    private final ObjectiveManager objectiveManager;

    public GuardDeathListener(DEMPlugin plugin, ObjectiveManager objectiveManager) {
        this.plugin = plugin;
        this.objectiveManager = objectiveManager;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Map<String, GroupState> activeGroups = objectiveManager.getActiveGroups();

        for (String tag : entity.getScoreboardTags()) {
            GroupState state = activeGroups.get(tag);
            if (state == null) {
                continue;
            }

            int remaining = state.decrementAndGet();
            plugin.getLogger().info("Objetivo con tag '" + tag + "' actualizado. Quedan: " + remaining);

            if (remaining <= 0) {
                // Los comandos ya vienen con los placeholders resueltos desde que se
                // creó el objetivo (via /dem objective watch), así que no hace falta
                // un PlaceholderContext aquí. Igual soporta "delay:<segundos>|comando".
                CommandRunner.runAll(plugin, state.getOnCompleteCommands(), null);
                activeGroups.remove(tag);
            }
            break;
        }
    }
}
