package net.example.dem.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    private final GuiManager guiManager;

    public GuiListener(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!guiManager.isDemGui(holder)) {
            return;
        }

        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());

        // El editor de equipamiento es el único menú "editable de verdad": sus
        // 6 slots del muñeco de papel se dejan con comportamiento normal para
        // poder poner/sacar ítems (incluso shift-click desde el inventario del
        // jugador). Todo lo demás en el GUI se cancela como siempre.
        if (clickedTop && guiManager.isFreeInteractSlot(holder, event.getSlot())) {
            return;
        }
        if (!clickedTop && holder instanceof net.example.dem.gui.GuiHolders.MobEquipMenuHolder) {
            return; // deja que el jugador maneje su propio inventario con normalidad
        }

        event.setCancelled(true);

        if (!clickedTop) {
            return; // click en el inventario del jugador, no en el menú
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        guiManager.handleClick(player, holder, event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof net.example.dem.gui.GuiHolders.MobEquipMenuHolder
                && event.getPlayer() instanceof Player player) {
            guiManager.returnEquipItemsOnClose(player, event.getInventory());
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!guiManager.hasPendingInput(player.getUniqueId())) {
            return;
        }
        // Evita que el mensaje se vea en el chat público del servidor.
        event.setCancelled(true);
        String message = event.getMessage();
        // El evento es async: hay que volver al hilo principal para tocar
        // inventarios/Bukkit API dentro de processChatInput.
        Bukkit.getScheduler().runTask(guiManager.getPlugin(),
                () -> guiManager.processChatInput(player, message));
    }
}
