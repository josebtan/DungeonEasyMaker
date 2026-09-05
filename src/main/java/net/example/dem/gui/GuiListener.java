package net.example.dem.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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
        // Siempre cancelamos: es un menú de solo-lectura/botones, nadie debe
        // poder sacar o mover los items que arma el GUI.
        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return; // click en el inventario del jugador, no en el menú
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        guiManager.handleClick(player, holder, event);
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
