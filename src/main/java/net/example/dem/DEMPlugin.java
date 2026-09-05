package net.example.dem;

import net.example.dem.area.AreaCommand;
import net.example.dem.area.AreaManager;
import net.example.dem.area.AreaMoveListener;
import net.example.dem.area.SelectionListener;
import net.example.dem.loot.LootCommand;
import net.example.dem.loot.LootManager;
import net.example.dem.objective.GuardDeathListener;
import net.example.dem.objective.ObjectiveCommand;
import net.example.dem.objective.ObjectiveManager;
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
        SelectionListener selectionListener = new SelectionListener();
        getServer().getPluginManager().registerEvents(selectionListener, this);
        getServer().getPluginManager().registerEvents(new AreaMoveListener(areaManager), this);
        AreaCommand areaCommand = new AreaCommand(this, areaManager, selectionListener);
        getCommand("dungeonarea").setExecutor(areaCommand);
        getCommand("dungeonarea").setTabCompleter(areaCommand);

        // --- Módulo de Objetivos ---
        objectiveManager = new ObjectiveManager();
        getServer().getPluginManager().registerEvents(new GuardDeathListener(this, objectiveManager), this);
        ObjectiveCommand objectiveCommand = new ObjectiveCommand(objectiveManager);
        getCommand("dungeon").setExecutor(objectiveCommand);
        getCommand("dungeon").setTabCompleter(objectiveCommand);

        // --- Módulo de Loot ---
        lootManager = new LootManager(this);
        lootManager.load();
        LootCommand lootCommand = new LootCommand(this, lootManager);
        getCommand("dungeonloot").setExecutor(lootCommand);
        getCommand("dungeonloot").setTabCompleter(lootCommand);

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
