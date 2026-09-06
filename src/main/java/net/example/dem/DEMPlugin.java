package net.example.dem;

import net.example.dem.area.AreaManager;
import net.example.dem.area.AreaModule;
import net.example.dem.area.AreaMoveListener;
import net.example.dem.area.SelectionListener;
import net.example.dem.dungeon.DungeonDeathListener;
import net.example.dem.dungeon.DungeonManager;
import net.example.dem.dungeon.DungeonModule;
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
    private DungeonManager dungeonManager;

    @Override
    public void onEnable() {
        // --- Módulo de Áreas ---
        areaManager = new AreaManager(this);
        areaManager.load();
        SelectionListener selectionListener = new SelectionListener(this);
        getServer().getPluginManager().registerEvents(selectionListener, this);
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

        // --- Módulo de Dungeons (cadena de etapas, entrada, expulsión) ---
        dungeonManager = new DungeonManager(this, areaManager);
        dungeonManager.load();
        DungeonModule dungeonModule = new DungeonModule(dungeonManager);
        getServer().getPluginManager().registerEvents(new DungeonDeathListener(areaManager, dungeonManager), this);

        // El listener de movimiento de áreas necesita el DungeonManager para
        // saber cuándo una etapa es la entrada de un dungeon encadenado.
        getServer().getPluginManager().registerEvents(new AreaMoveListener(this, areaManager, dungeonManager), this);

        // --- GUI de configuración (inventario) ---
        GuiManager guiManager = new GuiManager(this, areaManager, selectionListener);
        getServer().getPluginManager().registerEvents(new GuiListener(guiManager), this);

        // --- Comando único: /dem area|objective|loot|dungeon|gui|reload ---
        DEMCommand demCommand = new DEMCommand(areaManager, lootManager, areaModule, objectiveModule,
                lootModule, guiManager, dungeonModule, dungeonManager);
        getCommand("dem").setExecutor(demCommand);
        getCommand("dem").setTabCompleter(demCommand);

        getLogger().info("DungeonEasyMaker habilitado. Áreas: " + areaManager.getAreas().size()
                + " | Tablas de loot: " + lootManager.getTables().size()
                + " | Dungeons: " + dungeonManager.getDungeons().size());
    }

    @Override
    public void onDisable() {
        if (areaManager != null) {
            areaManager.save();
        }
        if (lootManager != null) {
            lootManager.save();
        }
        if (dungeonManager != null) {
            dungeonManager.save();
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

    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }
}
