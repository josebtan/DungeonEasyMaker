package net.example.dem.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marcadores para identificar de forma confiable qué inventario personalizado
 * está mirando el jugador (en vez de parsear el título, que es frágil).
 */
public class GuiHolders {

    public static class MainMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class AreaMenuHolder implements InventoryHolder {
        private final String areaName;
        private Inventory inventory;

        public AreaMenuHolder(String areaName) {
            this.areaName = areaName;
        }

        public String getAreaName() {
            return areaName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class CommandListMenuHolder implements InventoryHolder {
        private final String areaName;
        private final CommandListType type;
        private Inventory inventory;

        public CommandListMenuHolder(String areaName, CommandListType type) {
            this.areaName = areaName;
            this.type = type;
        }

        public String getAreaName() {
            return areaName;
        }

        public CommandListType getType() {
            return type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class MobListMenuHolder implements InventoryHolder {
        private final String areaName;
        private Inventory inventory;

        public MobListMenuHolder(String areaName) {
            this.areaName = areaName;
        }

        public String getAreaName() {
            return areaName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    /** Selector paginado de tipo base (huevos de spawn) al crear un mob nuevo. */
    public static class MobTypePickerHolder implements InventoryHolder {
        private final String areaName;
        private final int page;
        private Inventory inventory;

        public MobTypePickerHolder(String areaName, int page) {
            this.areaName = areaName;
            this.page = page;
        }

        public String getAreaName() {
            return areaName;
        }

        public int getPage() {
            return page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class MobEditorMenuHolder implements InventoryHolder {
        private final String areaName;
        private final String mobId;
        private Inventory inventory;

        public MobEditorMenuHolder(String areaName, String mobId) {
            this.areaName = areaName;
            this.mobId = mobId;
        }

        public String getAreaName() {
            return areaName;
        }

        public String getMobId() {
            return mobId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    /** Único menú "editable de verdad": los 6 slots centrales son un muñeco de papel real. */
    public static class MobEquipMenuHolder implements InventoryHolder {
        private final String areaName;
        private final String mobId;
        private Inventory inventory;

        public MobEquipMenuHolder(String areaName, String mobId) {
            this.areaName = areaName;
            this.mobId = mobId;
        }

        public String getAreaName() {
            return areaName;
        }

        public String getMobId() {
            return mobId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    /** Submenú de puertas de entrada/salida de un área. */
    public static class DoorMenuHolder implements InventoryHolder {
        private final String areaName;
        private Inventory inventory;

        public DoorMenuHolder(String areaName) {
            this.areaName = areaName;
        }

        public String getAreaName() {
            return areaName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    /** Menú de puertas de entrada/salida de un área. */
    public static class DoorMenuHolder implements InventoryHolder {
        private final String areaName;
        private Inventory inventory;

        public DoorMenuHolder(String areaName) {
            this.areaName = areaName;
        }

        public String getAreaName() {
            return areaName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }
}
