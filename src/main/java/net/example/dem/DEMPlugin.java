package net.example.dem;

import net.example.dem.area.AreaManager;
import net.example.dem.area.AreaModule;
import net.example.dem.area.AreaMoveListener;
import net.example.dem.area.SelectionListener;
import net.example.dem.gui.GuiListener;
import net.example.dem.gui.GuiManager;
import net.example.dem.loot.LootManager;
import net.example.dem.loot.LootModule;
import net.example.dem.mob.MobDeathListener;
import net.example.dem.objective.GuardDeathListener;
import net.example.dem.objective.ObjectiveManager;
import net.example.dem.objective.ObjectiveModule;
import org.bukkit.plugin.java.JavaPlugin;

public class DEMPlugin extends JavaPlugin {

    private AreaManager areaManager;
    private ObjectiveManager objectiveManager;
    private LootManager lootManager;

    @Override
    public void onEnable() {
        // --- Módulo de Áreas ---
        areaManager = new AreaManager(this);
        areaManager.load();
        SelectionListener selectionListener = new SelectionListener(this);
        getServer().getPluginManager().registerEvents(selectionListener, this);
        getServer().getPluginManager().registerEvents(new AreaMoveListener(this, areaManager), this);
        AreaModule areaModule = new AreaModule(this, areaManager, selectionListener);

        // --- Módulo de Objetivos ---
        objectiveManager = new ObjectiveManager();
        getServer().getPluginManager().registerEvents(new GuardDeathListener(this, objectiveManager), this);
        ObjectiveModule objectiveModule = new ObjectiveModule(objectiveManager);

        // --- Módulo de Loot ---
        lootManager = new LootManager(this);
        lootManager.load();
        LootModule lootModule = new LootModule(this, lootManager);
        getServer().getPluginManager().registerEvents(new MobDeathListener(this, lootManager), this);

        // --- GUI de configuración (inventario) ---
        GuiManager guiManager = new GuiManager(this, areaManager, selectionListener);
        getServer().getPluginManager().registerEvents(new GuiListener(guiManager), this);

        // --- Comando único: /dem area|objective|loot|gui|reload ---
        DEMCommand demCommand = new DEMCommand(areaManager, lootManager, areaModule, objectiveModule, lootModule, guiManager);
        getCommand("dem").setExecutor(demCommand);
        getCommand("dem").setTabCompleter(demCommand);

        getLogger().info("DungeonEasyMaker habilitado. Áreas: " + areaManager.getAreas().size()
                + " | Tablas de loot: " + lootManager.getTables().size());
    }

    @Override
    public void onDisable() {
        if (areaManager != null) {
            areaManager.save();
        }
        if (lootManager != null) {
            lootManager.save();
        }
    }

    public AreaManager getAreaManager() {
        return areaManager;
    }

    public ObjectiveManager getObjectiveManager() {
        return objectiveManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }
}
