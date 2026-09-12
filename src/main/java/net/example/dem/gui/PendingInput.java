package net.example.dem.gui;

import org.bukkit.entity.EntityType;

/**
 * Algunas acciones del GUI necesitan texto libre (nombre de área, un comando,
 * el id de un mob nuevo, nombre/etiqueta/loot de un mob) que el inventario de
 * Minecraft no puede pedir directamente: cerramos el menú y capturamos el
 * próximo mensaje de chat del jugador para usarlo.
 */
public class PendingInput {

    public enum Kind {
        CREATE_AREA,
        ADD_COMMAND,
        CREATE_MOB,
        SET_MOB_NAME,
        SET_MOB_TAG,
        SET_MOB_LOOT,
        ADD_MOB_EFFECT,
        CREATE_DOOR
    }

    private final Kind kind;
    private final String areaName;
    private final CommandListType listType; // solo para ADD_COMMAND
    private final String mobId;             // solo para las acciones de mob existente
    private final EntityType mobType;       // solo para CREATE_MOB (elegido en el selector de huevos)

    private PendingInput(Kind kind, String areaName, CommandListType listType, String mobId, EntityType mobType) {
        this.kind = kind;
        this.areaName = areaName;
        this.listType = listType;
        this.mobId = mobId;
        this.mobType = mobType;
    }

    public static PendingInput createArea() {
        return new PendingInput(Kind.CREATE_AREA, null, null, null, null);
    }

    public static PendingInput addCommand(String areaName, CommandListType listType) {
        return new PendingInput(Kind.ADD_COMMAND, areaName, listType, null, null);
    }

    public static PendingInput createMob(String areaName, EntityType mobType) {
        return new PendingInput(Kind.CREATE_MOB, areaName, null, null, mobType);
    }

    public static PendingInput mobField(Kind kind, String areaName, String mobId) {
        return new PendingInput(kind, areaName, null, mobId, null);
    }

    public static PendingInput createDoor(String areaName) {
        return new PendingInput(Kind.CREATE_DOOR, areaName, null, null, null);
    }

    public Kind getKind() {
        return kind;
    }

    public String getAreaName() {
        return areaName;
    }

    public CommandListType getListType() {
        return listType;
    }

    public String getMobId() {
        return mobId;
    }

    public EntityType getMobType() {
        return mobType;
    }
}
