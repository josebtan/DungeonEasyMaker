package net.example.dem.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Agrupa una secuencia de áreas (etapas, en orden) como UNA sola corrida.
 * La primera etapa de la lista es la "entrada": su puerta es la que se cierra
 * al arrancar la corrida y no se vuelve a abrir hasta que se libera el
 * dungeon completo (todas las etapas superadas, o todos los participantes
 * afuera de cualquier etapa).
 */
public class Dungeon {

    private final String id;
    private final List<String> stages = new ArrayList<>();

    // Punto donde se manda a alguien que muere adentro, o que queda afuera
    // cuando se libera el dungeon.
    private String kickWorld;
    private Double kickX;
    private Double kickY;
    private Double kickZ;
    private float kickYaw;
    private float kickPitch;

    // Estado en vivo (no se persiste): quién arrancó esta corrida y si sigue activa.
    private final transient Set<UUID> participants = new HashSet<>();
    private transient boolean inProgress = false;

    public Dungeon(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public List<String> getStages() {
        return stages;
    }

    public boolean hasKickPoint() {
        return kickWorld != null && kickX != null && kickY != null && kickZ != null;
    }

    public void setKickPoint(Location loc) {
        this.kickWorld = loc.getWorld().getName();
        this.kickX = loc.getX();
        this.kickY = loc.getY();
        this.kickZ = loc.getZ();
        this.kickYaw = loc.getYaw();
        this.kickPitch = loc.getPitch();
    }

    /** Usado solo por el manager al cargar desde disco. */
    public void loadRawKickPoint(String world, Double x, Double y, Double z, float yaw, float pitch) {
        this.kickWorld = world;
        this.kickX = x;
        this.kickY = y;
        this.kickZ = z;
        this.kickYaw = yaw;
        this.kickPitch = pitch;
    }

    public String getKickWorld() {
        return kickWorld;
    }

    public Double getKickX() {
        return kickX;
    }

    public Double getKickY() {
        return kickY;
    }

    public Double getKickZ() {
        return kickZ;
    }

    public float getKickYaw() {
        return kickYaw;
    }

    public float getKickPitch() {
        return kickPitch;
    }

    public Location getKickLocation() {
        if (!hasKickPoint()) {
            return null;
        }
        World w = Bukkit.getWorld(kickWorld);
        if (w == null) {
            return null;
        }
        return new Location(w, kickX, kickY, kickZ, kickYaw, kickPitch);
    }

    public Set<UUID> getParticipants() {
        return participants;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }
}
