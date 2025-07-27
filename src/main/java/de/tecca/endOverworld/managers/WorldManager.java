package de.tecca.endOverworld.managers;

import de.tecca.endOverworld.EndOverworld;
import de.tecca.endOverworld.world.EndOverworldGenerator;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

/**
 * Manages world creation and world-related operations
 */
public class WorldManager {

    private final EndOverworld plugin;
    private World endWorld;
    private World netherWorld;
    private EndOverworldGenerator customGenerator;

    public WorldManager(EndOverworld plugin) {
        this.plugin = plugin;
        this.customGenerator = new EndOverworldGenerator();
        initializeWorlds();
    }

    private void initializeWorlds() {
        // Create/get End world with custom generator
        endWorld = getOrCreateEndWorld();

        // Ensure Nether world exists for portal travel
        netherWorld = getOrCreateNetherWorld();

        plugin.getLogger().info("Initialized worlds - End: " + endWorld.getName() + ", Nether: " + netherWorld.getName());

        // Log generator information
        if (endWorld.getGenerator() instanceof EndOverworldGenerator) {
            plugin.getLogger().info("Using custom EndOverworldGenerator: " + customGenerator.getGeneratorInfo());
        } else {
            plugin.getLogger().warning("End world is not using custom generator - some features may not work properly");
        }
    }

    private World getOrCreateEndWorld() {
        World world = Bukkit.getWorld("world_the_end");
        /*
        if (world == null) {
            // Create new world with custom generator
            plugin.getLogger().info("Creating End world with custom generator...");
            world = createCustomEndWorld();
        } else {
            // Check if existing world uses our generator
            ChunkGenerator generator = world.getGenerator();
            if (!(generator instanceof EndOverworldGenerator)) {
                plugin.getLogger().warning("Existing End world found without custom generator!");

                // Option 1: Try to recreate with custom generator
                if (plugin.getConfig().getBoolean("world.force_custom_generator", true)) {
                    plugin.getLogger().info("Attempting to recreate End world with custom generator...");
                    world = recreateEndWorldWithGenerator();
                } else {
                    plugin.getLogger().warning("Custom generator disabled in config - using existing world");
                    plugin.getLogger().warning("Some features may not work properly without custom terrain generation");
                }
            } else {
                plugin.getLogger().info("Found existing End world with custom generator");
            }
        }
        */
        return world;
    }

    private World createCustomEndWorld() {
        WorldCreator creator = new WorldCreator("world_the_end")
                .environment(World.Environment.THE_END)
                .generator(customGenerator)
                .generateStructures(true);

        World world = Bukkit.createWorld(creator);

        if (world != null) {
            configureWorld(world);
            plugin.getLogger().info("Successfully created End world with custom generator");
        } else {
            plugin.getLogger().severe("Failed to create End world!");
        }

        return world;
    }

    private World recreateEndWorldWithGenerator() {
        try {
            // Get the existing world
            World existingWorld = Bukkit.getWorld("world_the_end");
            if (existingWorld == null) {
                return createCustomEndWorld();
            }

            // Save important data
            Location spawn = existingWorld.getSpawnLocation();

            // Unload the existing world
            plugin.getLogger().info("Unloading existing End world...");
            Bukkit.unloadWorld(existingWorld, true);

            // Wait a moment for unloading to complete
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Create new world with custom generator
            plugin.getLogger().info("Creating new End world with custom generator...");
            WorldCreator creator = new WorldCreator("world_the_end")
                    .environment(World.Environment.THE_END)
                    .generator(customGenerator)
                    .generateStructures(true);

            World newWorld = Bukkit.createWorld(creator);

            if (newWorld != null) {
                configureWorld(newWorld);

                // Restore spawn location if reasonable
                if (spawn.getY() > 0 && spawn.getY() < 256) {
                    newWorld.setSpawnLocation(spawn);
                }

                plugin.getLogger().info("Successfully recreated End world with custom generator");
                return newWorld;
            } else {
                plugin.getLogger().severe("Failed to recreate End world - attempting fallback");
                return Bukkit.getWorld("world_the_end"); // Fallback to existing
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error recreating End world: " + e.getMessage());
            return Bukkit.getWorld("world_the_end"); // Fallback to existing
        }
    }

    private void configureWorld(World world) {
        world.setSpawnFlags(true, true); // Allow monsters and animals
        world.setKeepSpawnInMemory(true);
        world.setDifficulty(Difficulty.NORMAL);

        // Set reasonable spawn location for End
        if (world.getSpawnLocation().getY() < 50) {
            world.setSpawnLocation(new Location(world, 100, 70, 100));
        }
    }

    private World getOrCreateNetherWorld() {
        World world = Bukkit.getWorld("world_nether");
        if (world == null) {
            plugin.getLogger().info("Creating Nether world...");
            world = Bukkit.createWorld(new WorldCreator("world_nether")
                    .environment(World.Environment.NETHER)
                    .generateStructures(true)); // Enable Nether fortresses, bastions, etc.

            if (world != null) {
                world.setSpawnFlags(true, true);
                plugin.getLogger().info("Successfully created Nether world");
            }
        }
        return world;
    }

    /**
     * Gets the End world (main world for this plugin)
     */
    public World getEndWorld() {
        return endWorld;
    }

    /**
     * Gets the Nether world
     */
    public World getNetherWorld() {
        return netherWorld;
    }

    /**
     * Gets the custom End generator instance
     */
    public EndOverworldGenerator getCustomGenerator() {
        return customGenerator;
    }

    /**
     * Checks if a world is the End dimension
     */
    public boolean isEndWorld(World world) {
        return world != null && world.getEnvironment() == World.Environment.THE_END;
    }

    /**
     * Checks if a world is the Nether dimension
     */
    public boolean isNetherWorld(World world) {
        return world != null && world.getEnvironment() == World.Environment.NETHER;
    }

    /**
     * Checks if a world is using our custom End generator
     */
    public boolean isUsingCustomGenerator(World world) {
        return world != null && world.getGenerator() instanceof EndOverworldGenerator;
    }

    /**
     * Gets world by environment type
     */
    public World getWorldByEnvironment(World.Environment environment) {
        switch (environment) {
            case THE_END:
                return endWorld;
            case NETHER:
                return netherWorld;
            default:
                return null;
        }
    }

    /**
     * Checks if Nullscape or other End modifications are detected
     */
    public boolean hasCustomEndGeneration() {
        // Check for signs of custom terrain generation
        return checkForCustomDatapacks() || checkForCustomMaterials();
    }

    private boolean checkForCustomDatapacks() {
        try {
            if (endWorld != null) {
                java.io.File worldFolder = endWorld.getWorldFolder();
                java.io.File datapackFolder = new java.io.File(worldFolder, "datapacks");

                if (datapackFolder.exists() && datapackFolder.isDirectory()) {
                    java.io.File[] datapacks = datapackFolder.listFiles();
                    if (datapacks != null) {
                        for (java.io.File datapack : datapacks) {
                            String name = datapack.getName().toLowerCase();
                            if (name.startsWith("nullscape") ||
                                    name.contains("end") ||
                                    name.contains("outer") ||
                                    name.contains("incendium")) {
                                plugin.getLogger().info("Detected End modification datapack: " + datapack.getName());
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not check for datapacks: " + e.getMessage());
        }
        return false;
    }

    private boolean checkForCustomMaterials() {
        try {
            // Check for modded materials (non-minecraft namespace)
            for (org.bukkit.Material material : org.bukkit.Material.values()) {
                String key = material.getKey().toString();
                if (!key.startsWith("minecraft:")) {
                    plugin.getLogger().info("Detected custom materials - enhanced compatibility enabled");
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not check for custom materials: " + e.getMessage());
        }
        return false;
    }

    /**
     * Forces a chunk to regenerate with our custom generator
     */
    public boolean regenerateChunk(int chunkX, int chunkZ) {
        if (endWorld == null) {
            plugin.getLogger().warning("Cannot regenerate chunk - End world is null");
            return false;
        }

        try {
            // Unload chunk first
            if (endWorld.isChunkLoaded(chunkX, chunkZ)) {
                endWorld.unloadChunk(chunkX, chunkZ, false);
            }

            // Regenerate chunk
            endWorld.regenerateChunk(chunkX, chunkZ);
            plugin.getLogger().info("Regenerated chunk " + chunkX + ", " + chunkZ + " in End world");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to regenerate chunk " + chunkX + ", " + chunkZ + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets comprehensive world information for debugging
     */
    public String getWorldInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== World Information ===\n");
        info.append("End World: ").append(endWorld != null ? endWorld.getName() : "null").append("\n");
        info.append("Nether World: ").append(netherWorld != null ? netherWorld.getName() : "null").append("\n");
        info.append("Custom Generation: ").append(hasCustomEndGeneration()).append("\n");
        info.append("Using Custom Generator: ").append(isUsingCustomGenerator(endWorld)).append("\n");

        if (endWorld != null) {
            info.append("End Spawn: ").append(endWorld.getSpawnLocation()).append("\n");
            info.append("End Generator: ").append(endWorld.getGenerator() != null ?
                    endWorld.getGenerator().getClass().getSimpleName() : "default").append("\n");
            info.append("Structures Enabled: ").append(endWorld.canGenerateStructures()).append("\n");
            info.append("Keep Spawn in Memory: ").append(endWorld.getKeepSpawnInMemory()).append("\n");
            info.append("Loaded Chunks: ").append(endWorld.getLoadedChunks().length).append("\n");

            // Additional generator info
            if (customGenerator != null) {
                info.append("Generator Info: ").append(customGenerator.getGeneratorInfo()).append("\n");
            }
        }

        if (netherWorld != null) {
            info.append("Nether Spawn: ").append(netherWorld.getSpawnLocation()).append("\n");
            info.append("Nether Loaded Chunks: ").append(netherWorld.getLoadedChunks().length).append("\n");
        }

        return info.toString();
    }

    /**
     * Performs cleanup when the plugin is disabled
     */
    public void cleanup() {
        plugin.getLogger().info("WorldManager cleanup - saving worlds...");

        if (endWorld != null) {
            endWorld.save();
        }

        if (netherWorld != null) {
            netherWorld.save();
        }

        plugin.getLogger().info("WorldManager cleanup complete");
    }
}