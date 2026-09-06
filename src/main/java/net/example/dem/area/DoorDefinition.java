package net.example.dem.area;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.List;

/**
 * Una puerta es un cubo de bloques capturado tal como estaba construido
 * (su estado "cerrado") que se puede abrir (rellenar con aire) o volver a
 * cerrar (restaurar exactamente los bloques originales) por comando.
 *
 * Se crea parándose en el mundo y marcando 2 esquinas con la varita de área,
 * igual que al crear un área: /dem area door create <área> <id> captura lo
 * que ya construiste ahí (una pared, una puerta de hierro, lo que sea) como
 * el estado "cerrado".
 */
public class DoorDefinition {

    public static class CapturedBlock {
        public final int x;
        public final int y;
        public final int z;
        public final String blockData;

        public CapturedBlock(int x, int y, int z, String blockData) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockData = blockData;
        }
    }

    private final String id;
    private final String world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final List<CapturedBlock> closedBlocks;

    public DoorDefinition(String id, String world, int minX, int minY, int minZ,
                           int maxX, int maxY, int maxZ, List<CapturedBlock> closedBlocks) {
        this.id = id;
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.closedBlocks = closedBlocks;
    }

    public String getId() {
        return id;
    }

    public String getWorld() {
        return world;
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public List<CapturedBlock> getClosedBlocks() {
        return closedBlocks;
    }

    public int getBlockCount() {
        return closedBlocks.size();
    }

    /** Rellena todo el cubo de la puerta con aire. */
    public void open() {
        World w = Bukkit.getWorld(world);
        if (w == null) return;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    /** Restaura exactamente los bloques que había cuando se creó la puerta. */
    public void close() {
        World w = Bukkit.getWorld(world);
        if (w == null) return;
        for (CapturedBlock cb : closedBlocks) {
            try {
                BlockData data = Bukkit.createBlockData(cb.blockData);
                w.getBlockAt(cb.x, cb.y, cb.z).setBlockData(data, false);
            } catch (IllegalArgumentException ignored) {
                // bloque con datos inválidos (raro, pero no debe tumbar el servidor)
            }
        }
    }
}
