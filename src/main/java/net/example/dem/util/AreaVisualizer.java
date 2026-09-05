package net.example.dem.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Muestra visualmente un área SOLO para el jugador que la está creando/revisando:
 *  - showCorner/revertCorner: bloque fantasma (client-side, como GriefPrevention)
 *    usando Player#sendBlockChange, que NO modifica el mundo real.
 *  - showOutline: dibuja las 12 aristas del cuboide con partículas, visibles
 *    solo para ese jugador (Player#spawnParticle no se transmite a otros).
 */
public class AreaVisualizer {

    private static final Material CORNER_MATERIAL = Material.SEA_LANTERN;

    public static void showCorner(Player player, Location loc) {
        player.sendBlockChange(loc, CORNER_MATERIAL.createBlockData());
    }

    public static void revertCorner(Player player, Location loc) {
        // Le devolvemos al cliente el bloque real que hay ahí (no tocamos el mundo).
        player.sendBlockChange(loc, loc.getBlock().getBlockData());
    }

    /**
     * Dibuja el contorno del cuboide con partículas durante durationTicks,
     * refrescando cada 10 ticks. Se cancela solo si el jugador se desconecta.
     */
    public static void showOutline(Plugin plugin, Player player, String worldName,
                                    int x1, int y1, int z1, int x2, int y2, int z2,
                                    int durationTicks) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }

        double minX = Math.min(x1, x2);
        double minY = Math.min(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxX = Math.max(x1, x2) + 1.0;
        double maxY = Math.max(y1, y2) + 1.0;
        double maxZ = Math.max(z1, z2) + 1.0;

        double[][] corners = {
                {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ},
                {minX, maxY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}
        };
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0}, // aristas inferiores
                {4, 5}, {5, 6}, {6, 7}, {7, 4}, // aristas superiores
                {0, 4}, {1, 5}, {2, 6}, {3, 7}  // aristas verticales
        };

        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int elapsedTicks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || elapsedTicks >= durationTicks) {
                    if (holder[0] != null) {
                        holder[0].cancel();
                    }
                    return;
                }
                for (int[] edge : edges) {
                    drawEdge(world, player, corners[edge[0]], corners[edge[1]]);
                }
                elapsedTicks += 10;
            }
        }, 0L, 10L);
    }

    private static void drawEdge(World world, Player player, double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double dz = b[2] - a[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) (length / 0.5));

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = a[0] + dx * t;
            double y = a[1] + dy * t;
            double z = a[2] + dz * t;
            player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
        }
    }
}
