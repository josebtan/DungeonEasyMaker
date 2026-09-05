package net.example.dem.objective;

import net.example.dem.DEMPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
                runOnComplete(state);
                activeGroups.remove(tag);
            }
            break;
        }
    }

    private void runOnComplete(GroupState state) {
        for (String command : state.getOnCompleteCommands()) {
            String trimmed = command.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (trimmed.startsWith("broadcast ")) {
                    String message = trimmed.substring("broadcast ".length());
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed);
                }
            });
        }
    }
}
