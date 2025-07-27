package de.tecca.endOverworld.managers;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.*;

/**
 * Manages world operations without custom generation
 * Now focuses on world access and utility functions
 */
public class WorldManager {

    private final EndOverworld plugin;
    private World endWorld;
    private World netherWorld;

    public WorldManager(EndOverworld plugin) {
        this.plugin = plugin;
        initializeWorlds();
    }

    private void initializeWorlds() {
        // Get existing End world (don't create custom one)
        endWorld = getOrCreateEndWorld();

        // Ensure Nether world exists for portal travel
        netherWorld = getOrCreateNetherWorld();

        plugin.getLogger().info("Initialized worlds - End: " + endWorld.getName() + ", Nether: " + netherWorld.getName());
        plugin.getLogger().info("Using vanilla End generation with post-processing for End Cities");
    }

    private World getOrCreateEndWorld() {
        // Get the existing End world (vanilla generation)
        World world = Bukkit.getWorld("world_the_end");

        if (world == null) {
            plugin.getLogger().info("Creating vanilla End world...");
            world = Bukkit.createWorld(new WorldCreator("world_the_end")
                    .environment(World.Environment.THE_END)
                    .generateStructures(true)); // Keep vanilla structures
        }

        if (world != null) {
            configureWorld(world);
            plugin.getLogger().info("Using vanilla End world with post-processing");
        } else {
            plugin.getLogger().severe("Failed to get/create End world!");
        }

        return world;
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
     * Forces a chunk to regenerate (vanilla generation)
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

            // Regenerate chunk with vanilla generation
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
        info.append("Generation Type: Vanilla + Post-Processing\n");

        if (endWorld != null) {
            info.append("End Spawn: ").append(endWorld.getSpawnLocation()).append("\n");
            info.append("End Generator: ").append(endWorld.getGenerator() != null ?
                    endWorld.getGenerator().getClass().getSimpleName() : "default").append("\n");
            info.append("Structures Enabled: ").append(endWorld.canGenerateStructures()).append("\n");
            info.append("Keep Spawn in Memory: ").append(endWorld.getKeepSpawnInMemory()).append("\n");
            info.append("Loaded Chunks: ").append(endWorld.getLoadedChunks().length).append("\n");
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