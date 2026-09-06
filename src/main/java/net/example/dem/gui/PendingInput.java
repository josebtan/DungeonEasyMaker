package net.example.dem.gui;

/**
 * Algunas acciones del GUI necesitan texto libre (nombre de área, un comando,
 * nombre/etiqueta/loot de un mob) que el inventario de Minecraft no puede
 * pedir directamente: cerramos el menú y capturamos el próximo mensaje de
 * chat del jugador para usarlo.
 */
public class PendingInput {

    public enum Kind {
        CREATE_AREA,
        ADD_COMMAND,
        CREATE_MOB,
        SET_MOB_NAME,
        SET_MOB_TAG,
        SET_MOB_LOOT,
        ADD_MOB_EFFECT
    }

    private final Kind kind;
    private final String areaName;
    private final CommandListType listType; // solo para ADD_COMMAND
    private final String mobId;             // solo para las acciones de mob

    private PendingInput(Kind kind, String areaName, CommandListType listType, String mobId) {
        this.kind = kind;
        this.areaName = areaName;
        this.listType = listType;
        this.mobId = mobId;
    }

    public static PendingInput createArea() {
        return new PendingInput(Kind.CREATE_AREA, null, null, null);
    }

    public static PendingInput addCommand(String areaName, CommandListType listType) {
        return new PendingInput(Kind.ADD_COMMAND, areaName, listType, null);
    }

    public static PendingInput createMob(String areaName) {
        return new PendingInput(Kind.CREATE_MOB, areaName, null, null);
    }

    public static PendingInput mobField(Kind kind, String areaName, String mobId) {
        return new PendingInput(kind, areaName, null, mobId);
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
}
