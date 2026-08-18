package com.maya4pa.gatedungeon;

import com.maya4pa.gatedungeon.command.GateDungeonCommand;
import com.maya4pa.gatedungeon.command.GateDungeonTabCompleter;
import com.maya4pa.gatedungeon.config.ConfigManager;
import com.maya4pa.gatedungeon.database.DatabaseManager;
import com.maya4pa.gatedungeon.gate.GateManager;
import com.maya4pa.gatedungeon.hologram.HologramManager;
import com.maya4pa.gatedungeon.instance.InstanceManager;
import com.maya4pa.gatedungeon.listener.GateInteractionListener;
import com.maya4pa.gatedungeon.listener.RegionSelectionListener;
import com.maya4pa.gatedungeon.template.RegionVisualizer;
import com.maya4pa.gatedungeon.template.TemplateManager;
import com.maya4pa.gatedungeon.util.MessageUtils;
import com.maya4pa.gatedungeon.world.WorldManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class GateDungeonPlugin extends JavaPlugin {

    private static GateDungeonPlugin instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private WorldManager worldManager;
    private TemplateManager templateManager;
    private GateManager gateManager;
    private InstanceManager instanceManager;
    private HologramManager hologramManager;
    private RegionSelectionListener regionSelectionListener;
    private RegionVisualizer regionVisualizer;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("mobs.yml", false);

        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        MessageUtils.init(this);

        getLogger().info("=== GateDungeon " + getDescription().getVersion() + " Starting ===");

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        worldManager = new WorldManager(this);
        worldManager.loadExistingWorlds();

        templateManager = new TemplateManager(this);
        templateManager.loadFromDatabase();

        gateManager = new GateManager(this);
        gateManager.loadFromDatabase();

        instanceManager = new InstanceManager(this);
        hologramManager = new HologramManager(this);

        var cmd = getCommand("gatedungeon");
        if (cmd != null) {
            cmd.setExecutor(new GateDungeonCommand(this));
            cmd.setTabCompleter(new GateDungeonTabCompleter(this));
        } else {
            getLogger().severe("Command 'gatedungeon' missing from plugin.yml");
        }

        getServer().getPluginManager().registerEvents(new GateInteractionListener(this), this);
        regionSelectionListener = new RegionSelectionListener(this);
        getServer().getPluginManager().registerEvents(regionSelectionListener, this);

        regionVisualizer = new RegionVisualizer(this);
        regionVisualizer.start();

        instanceManager.startCleanupTask();

        getLogger().info("GateDungeon v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (regionVisualizer != null) {
            regionVisualizer.stop();
        }
        if (instanceManager != null) {
            instanceManager.cleanupAll();
        }
        if (gateManager != null) {
            gateManager.onDisable();
        }
        if (worldManager != null) {
            worldManager.cleanup();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("GateDungeon disabled.");
    }

    public static GateDungeonPlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public TemplateManager getTemplateManager() { return templateManager; }
    public GateManager getGateManager() { return gateManager; }
    public InstanceManager getInstanceManager() { return instanceManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public RegionSelectionListener getRegionSelectionListener() { return regionSelectionListener; }
    public RegionVisualizer getRegionVisualizer() { return regionVisualizer; }
}