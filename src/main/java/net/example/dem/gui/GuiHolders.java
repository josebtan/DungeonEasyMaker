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
}
