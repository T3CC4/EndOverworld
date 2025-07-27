package de.tecca.endOverworld;

import de.tecca.endOverworld.commands.AncientSiteCommand;
import de.tecca.endOverworld.managers.*;
import de.tecca.endOverworld.mechanics.*;
import de.tecca.endOverworld.trading.TradingManager;
import de.tecca.endOverworld.world.EndPostProcessor;
import de.tecca.endOverworld.world.StructureManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class EndOverworld extends JavaPlugin {

    // Managers
    private SpawnManager spawnManager;
    private PortalManager portalManager;
    private WorldManager worldManager;
    private BedManager bedManager;
    private RecipeManager recipeManager;
    private TradingManager tradingManager;
    private StructureManager structureManager;
    private AncientEndManager ancientEndManager;

    // Mechanics
    private BlockMechanics blockMechanics;
    private FoodMechanics foodMechanics;
    private MobBehavior mobBehavior;

    // Post Processing
    private EndPostProcessor postProcessor;

    // Configuration
    private FileConfiguration traderConfig;

    @Override
    public void onEnable() {
        // Load configurations
        loadConfigurations();

        // Initialize managers
        initializeManagers();

        // Initialize mechanics
        initializeMechanics();

        // Initialize post-processor
        initializePostProcessor();

        // Register all event listeners
        registerEventListeners();

        // Register commands
        registerCommands();

        // Validate configurations
        validateConfigurations();

        getLogger().info("EndOverworld plugin enabled!");
        getLogger().info("Features: End City Villagers, Trading Endermen, Chorus Sprites, Enhanced Mechanics, Ancient End Sites");
        getLogger().info("Using vanilla End generation with targeted End City post-processing");
    }

    @Override
    public void onDisable() {
        // Cleanup all managers
        if (tradingManager != null) {
            tradingManager.cleanup();
        }

        if (ancientEndManager != null) {
            ancientEndManager.cleanup();
        }

        if (worldManager != null) {
            worldManager.cleanup();
        }

        getLogger().info("EndOverworld plugin disabled!");
    }

    private void loadConfigurations() {
        // Save default configs
        saveDefaultConfig();

        // Load trader config
        File traderConfigFile = new File(getDataFolder(), "trader_config.yml");
        if (!traderConfigFile.exists()) {
            saveResource("trader_config.yml", false);
        }
        traderConfig = YamlConfiguration.loadConfiguration(traderConfigFile);
    }

    private void initializeManagers() {
        worldManager = new WorldManager(this);
        spawnManager = new SpawnManager(this, worldManager);
        portalManager = new PortalManager(this, worldManager);
        bedManager = new BedManager(this);
        recipeManager = new RecipeManager(this);
        tradingManager = new TradingManager(this);
        structureManager = new StructureManager(this);
        ancientEndManager = new AncientEndManager(this);
    }

    private void initializeMechanics() {
        blockMechanics = new BlockMechanics(this);
        foodMechanics = new FoodMechanics(this);
        mobBehavior = new MobBehavior(this, tradingManager);
    }

    private void initializePostProcessor() {
        postProcessor = new EndPostProcessor(this);
        getServer().getPluginManager().registerEvents(postProcessor, this);
        getLogger().info("End City post-processor initialized - will only affect End Cities");
    }

    private void registerEventListeners() {
        getServer().getPluginManager().registerEvents(spawnManager, this);
        getServer().getPluginManager().registerEvents(portalManager, this);
        getServer().getPluginManager().registerEvents(bedManager, this);
        getServer().getPluginManager().registerEvents(blockMechanics, this);
        getServer().getPluginManager().registerEvents(foodMechanics, this);
        getServer().getPluginManager().registerEvents(mobBehavior, this);
        getServer().getPluginManager().registerEvents(structureManager, this);
        getServer().getPluginManager().registerEvents(ancientEndManager, this);
    }

    private void registerCommands() {
        // Register Ancient Site command
        AncientSiteCommand ancientSiteCommand = new AncientSiteCommand(this);
        getCommand("ancientsite").setExecutor(ancientSiteCommand);
        getCommand("ancientsite").setTabCompleter(ancientSiteCommand);
    }

    private void validateConfigurations() {
        tradingManager.validateTradeOffers();
        recipeManager.registerAllRecipes();

        // Log feature status
        getLogger().info("=== EndOverworld Features ===");
        getLogger().info("End City Villagers: " + (structureManager != null ? "ENABLED" : "DISABLED"));
        getLogger().info("Trading Endermen: " + (tradingManager != null ? "ENABLED" : "DISABLED"));
        getLogger().info("Block Mechanics: " + (blockMechanics != null ? "ENABLED" : "DISABLED"));
        getLogger().info("Enhanced Food: " + (foodMechanics != null ? "ENABLED" : "DISABLED"));
        getLogger().info("Ancient End Sites: " + (ancientEndManager != null ? "ENABLED" : "DISABLED"));
        getLogger().info("End City Post-Processing: " + (postProcessor != null ? "ENABLED" : "DISABLED"));
    }

    // Getters for managers (for cross-manager communication)
    public SpawnManager getSpawnManager() { return spawnManager; }
    public PortalManager getPortalManager() { return portalManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public BedManager getBedManager() { return bedManager; }
    public RecipeManager getRecipeManager() { return recipeManager; }
    public TradingManager getTradingManager() { return tradingManager; }
    public StructureManager getStructureManager() { return structureManager; }
    public AncientEndManager getAncientEndManager() { return ancientEndManager; }

    public BlockMechanics getBlockMechanics() { return blockMechanics; }
    public FoodMechanics getFoodMechanics() { return foodMechanics; }
    public MobBehavior getMobBehavior() { return mobBehavior; }

    public EndPostProcessor getPostProcessor() { return postProcessor; }

    public FileConfiguration getTraderConfig() { return traderConfig; }
}