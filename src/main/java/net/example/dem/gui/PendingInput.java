package net.example.dem.gui;

/**
 * Algunas acciones del GUI necesitan texto libre (nombre de área, un comando)
 * que el inventario de Minecraft no puede pedir directamente: cerramos el
 * menú y capturamos el próximo mensaje de chat del jugador para usarlo.
 */
public class PendingInput {

    public enum Kind {
        CREATE_AREA,
        ADD_COMMAND
    }

    private final Kind kind;
    private final String areaName;       // null para CREATE_AREA
    private final CommandListType listType; // null para CREATE_AREA

    private PendingInput(Kind kind, String areaName, CommandListType listType) {
        this.kind = kind;
        this.areaName = areaName;
        this.listType = listType;
    }

    public static PendingInput createArea() {
        return new PendingInput(Kind.CREATE_AREA, null, null);
    }

    public static PendingInput addCommand(String areaName, CommandListType listType) {
        return new PendingInput(Kind.ADD_COMMAND, areaName, listType);
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
}
