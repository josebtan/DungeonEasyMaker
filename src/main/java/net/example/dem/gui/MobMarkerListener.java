package net.example.dem.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Los armor stands marcadores (modo edición de área) son interactivos:
 *  - Click derecho: abre el editor del mob correspondiente.
 *  - Click derecho con un arma/armadura en la mano: además la equipa en el
 *    stand (comportamiento vanilla) y eso se copia a la definición del mob.
 *  - Click izquierdo (golpe): no hace nada, solo evita que "duela"/empuje.
 */
public class MobMarkerListener implements Listener {

    private final GuiManager guiManager;

    public MobMarkerListener(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    private String areaOf(ArmorStand stand) {
        return stand.getPersistentDataContainer().get(guiManager.getMarkerAreaKey(), PersistentDataType.STRING);
    }

    private String mobOf(ArmorStand stand) {
        return stand.getPersistentDataContainer().get(guiManager.getMarkerMobKey(), PersistentDataType.STRING);
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) {
            return;
        }
        String areaName = areaOf(stand);
        String mobId = mobOf(stand);
        if (areaName == null || mobId == null) {
            return; // no es uno de nuestros marcadores
        }
        Player player = event.getPlayer();
        // No cancelamos: si tiene un ítem equipable en la mano, dejamos que
        // Bukkit dispare además el PlayerArmorStandManipulateEvent normal.
        Bukkit.getScheduler().runTask(guiManager.getPlugin(), () -> guiManager.openMobEditorMenu(player, areaName, mobId));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (event.isCancelled()) {
            return;
        }
        ArmorStand stand = event.getRightClicked();
        String areaName = areaOf(stand);
        String mobId = mobOf(stand);
        if (areaName == null || mobId == null) {
            return;
        }
        // 1 tick de delay: cuando este evento se dispara, Bukkit todavía no
        // aplicó el cambio de equipo sobre la entidad.
        Bukkit.getScheduler().runTask(guiManager.getPlugin(),
                () -> guiManager.syncMarkerEquipmentFromStand(areaName, mobId, stand));
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) {
            return;
        }
        if (areaOf(stand) != null) {
            event.setCancelled(true);
        }
    }
}
