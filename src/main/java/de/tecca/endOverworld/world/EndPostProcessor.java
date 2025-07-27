package de.tecca.endOverworld.world;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Post-processing system that applies changes AFTER world generation
 * Does not override any world generation - purely additive
 */
public class EndPostProcessor implements Listener {

    private final EndOverworld plugin;
    private final Set<String> processedChunks = ConcurrentHashMap.newKeySet();

    private SimplexOctaveGenerator corruptionNoise;
    private SimplexOctaveGenerator enhancementNoise;

    public EndPostProcessor(EndOverworld plugin) {
        this.plugin = plugin;
        initializeNoiseGenerators(System.currentTimeMillis()); // Use system time as seed initially
    }

    private void initializeNoiseGenerators(long seed) {
        Random noiseRandom = new Random(seed);

        // Corruption zones for Ancient Site preparation
        corruptionNoise = new SimplexOctaveGenerator(new Random(noiseRandom.nextLong()), 4);
        corruptionNoise.setScale(0.008D);

        // Enhancement zones for feature placement
        enhancementNoise = new SimplexOctaveGenerator(new Random(noiseRandom.nextLong()), 3);
        enhancementNoise.setScale(0.02D);
    }

    /**
     * Process chunks AFTER they are fully populated by vanilla/datapacks
     */
    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        // Only process End chunks
        if (event.getWorld().getEnvironment() != World.Environment.THE_END) return;

        org.bukkit.Chunk chunk = event.getChunk();
        String chunkKey = getChunkKey(chunk);

        // Skip if already processed
        if (processedChunks.contains(chunkKey)) return;

        // Skip main dragon area
        if (isNearMainIsland(chunk.getX(), chunk.getZ())) return;

        // Re-initialize noise with world seed if needed
        if (corruptionNoise == null) {
            initializeNoiseGenerators(event.getWorld().getSeed());
        }

        // Schedule post-processing after population is complete
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            processPopulatedChunk(chunk);
            processedChunks.add(chunkKey);
        }, 3L); // Wait 3 ticks after population
    }

    private boolean isNearMainIsland(int chunkX, int chunkZ) {
        int worldX = chunkX * 16;
        int worldZ = chunkZ * 16;
        double distance = Math.sqrt(worldX * worldX + worldZ * worldZ);
        return distance < 1000;
    }

    /**
     * Process a chunk that has been fully populated
     */
    private void processPopulatedChunk(org.bukkit.Chunk chunk) {
        // Skip if chunk is no longer loaded
        if (!chunk.isLoaded()) return;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunk.getX() * 16 + x;
                int worldZ = chunk.getZ() * 16 + z;

                // Calculate enhancement values
                double corruption = corruptionNoise.noise(worldX, worldZ, 0.5D, 0.5D);
                double enhancement = enhancementNoise.noise(worldX, worldZ, 0.5D, 0.5D);

                // Normalize to 0-1
                corruption = (corruption + 1.0) / 2.0;
                enhancement = (enhancement + 1.0) / 2.0;

                // Apply our modifications to existing terrain
                applyModificationsToColumn(chunk, x, z, corruption, enhancement);
            }
        }
    }

    /**
     * Apply our modifications to existing terrain column
     */
    private void applyModificationsToColumn(org.bukkit.Chunk chunk, int x, int z, double corruption, double enhancement) {
        // Scan column for existing blocks to modify
        for (int y = 40; y <= 100; y++) {
            org.bukkit.block.Block block = chunk.getBlock(x, y, z);
            Material current = block.getType();

            // Skip air blocks
            if (current == Material.AIR) continue;

            // Apply corruption to existing solid blocks
            if (corruption > 0.5) {
                Material corrupted = getCorruptedVariant(current, corruption);
                if (corrupted != null && corrupted != current) {
                    block.setType(corrupted);
                }
            }

            // Add surface features on solid blocks
            if (current.isSolid() && enhancement > 0.65) {
                addSurfaceFeatures(chunk, x, y, z, enhancement);
            }
        }
    }

    /**
     * Get corrupted variant of existing material for Ancient Site preparation
     */
    private Material getCorruptedVariant(Material original, double intensity) {
        // Only apply corruption with reasonable probability
        if (Math.random() > intensity * 0.35) return null;

        // Ancient corruption progression for End City integration
        switch (original) {
            // Purpur corruption - prepares for Ancient Sites
            case PURPUR_BLOCK:
                if (intensity > 0.85) return Material.SCULK;
                if (intensity > 0.7) return Material.DEEPSLATE_BRICKS;
                if (intensity > 0.55) return Material.DEEPSLATE_TILES;
                return Material.COBBLED_DEEPSLATE;

            case PURPUR_PILLAR:
                return intensity > 0.75 ? Material.REINFORCED_DEEPSLATE : Material.DEEPSLATE_TILES;

            case PURPUR_STAIRS:
                return Material.DEEPSLATE_BRICK_STAIRS;

            case PURPUR_SLAB:
                return Material.DEEPSLATE_BRICK_SLAB;

            // End Stone corruption
            case END_STONE_BRICKS:
                if (intensity > 0.8) return Material.SCULK;
                if (intensity > 0.65) return Material.DEEPSLATE_BRICKS;
                return Material.COBBLED_DEEPSLATE;

            case END_STONE_BRICK_STAIRS:
                return Material.DEEPSLATE_BRICK_STAIRS;

            case END_STONE_BRICK_SLAB:
                return Material.DEEPSLATE_BRICK_SLAB;

            case END_STONE_BRICK_WALL:
                return Material.DEEPSLATE_BRICK_WALL;

            case END_STONE:
                if (intensity > 0.9) return Material.SCULK;
                if (intensity > 0.75) return Material.DEEPSLATE;
                if (intensity > 0.6) return Material.COBBLED_DEEPSLATE;
                return null;

            // Additional materials
            case OBSIDIAN:
                return intensity > 0.88 ? Material.SCULK_CATALYST : null;

            case STONE:
                return intensity > 0.82 ? Material.DEEPSLATE : null;

            default:
                return null;
        }
    }

    /**
     * Add surface features to existing terrain
     */
    private void addSurfaceFeatures(org.bukkit.Chunk chunk, int x, int y, int z, double enhancement) {
        // Only add features on top of blocks with air above
        if (y + 1 >= 256) return;

        org.bukkit.block.Block above = chunk.getBlock(x, y + 1, z);
        if (above.getType() != Material.AIR) return;

        Random random = new Random((long)x * 341873128712L + (long)z * 132897987541L + y);

        // Determine feature type based on enhancement strength
        if (enhancement > 0.9 && random.nextDouble() < 0.04) {
            // Building preparation - foundation markers
            addFoundationMarker(above);
        } else if (enhancement > 0.8 && random.nextDouble() < 0.06) {
            // Sculk growth for Ancient Site preparation
            addSculkFeature(chunk, x, y + 1, z, random);
        } else if (enhancement > 0.75 && random.nextDouble() < 0.08) {
            // Natural vegetation
            addVegetationFeature(above, random);
        } else if (enhancement > 0.7 && random.nextDouble() < 0.025) {
            // Overworld debris
            addDebrisFeature(above, random);
        }
    }

    private void addFoundationMarker(org.bukkit.block.Block block) {
        // Simple foundation marker for future building
        block.setType(Material.END_STONE_BRICKS);
    }

    private void addSculkFeature(org.bukkit.Chunk chunk, int x, int y, int z, Random random) {
        org.bukkit.block.Block block = chunk.getBlock(x, y, z);

        double featureType = random.nextDouble();

        if (featureType < 0.05) {
            // Rare sculk catalyst (like grass block spawning)
            block.setType(Material.SCULK_CATALYST);
        } else if (featureType < 0.2) {
            // Sculk sensors/shriekers
            Material sculkType = random.nextBoolean() ? Material.SCULK_SENSOR : Material.SCULK_SHRIEKER;
            block.setType(sculkType);
        } else if (featureType < 0.6) {
            // Regular sculk (like dirt block)
            block.setType(Material.SCULK);
        } else {
            // Sculk veins (like vegetation)
            block.setType(Material.SCULK_VEIN);
        }

        // Occasionally spread sculk to nearby air blocks
        if (random.nextDouble() < 0.3) {
            spreadSculkAround(chunk, x, y, z, random);
        }
    }

    private void spreadSculkAround(org.bukkit.Chunk chunk, int centerX, int centerY, int centerZ, Random random) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;

                int newX = centerX + dx;
                int newZ = centerZ + dz;

                if (newX >= 0 && newX < 16 && newZ >= 0 && newZ < 16 && random.nextDouble() < 0.25) {
                    org.bukkit.block.Block spreadBlock = chunk.getBlock(newX, centerY, newZ);
                    if (spreadBlock.getType() == Material.AIR) {
                        spreadBlock.setType(Material.SCULK_VEIN);
                    }
                }
            }
        }
    }

    private void addVegetationFeature(org.bukkit.block.Block block, Random random) {
        double vegType = random.nextDouble();

        if (vegType < 0.4) {
            // Small chorus plant
            block.setType(Material.CHORUS_PLANT);
            org.bukkit.block.Block above = block.getLocation().add(0, 1, 0).getBlock();
            if (above.getType() == Material.AIR && random.nextBoolean()) {
                above.setType(Material.CHORUS_FLOWER);
            }
        } else if (vegType < 0.7) {
            block.setType(Material.CHORUS_FLOWER);
        } else {
            block.setType(Material.DEAD_BUSH);
        }
    }

    private void addDebrisFeature(org.bukkit.block.Block block, Random random) {
        Material[] debris = {
                Material.DIRT, Material.GRASS_BLOCK, Material.STONE,
                Material.OAK_LOG, Material.COBBLESTONE, Material.SAND
        };

        Material debrisType = debris[random.nextInt(debris.length)];
        block.setType(debrisType);

        // Add vegetation on grass blocks
        if (debrisType == Material.GRASS_BLOCK) {
            org.bukkit.block.Block above = block.getLocation().add(0, 1, 0).getBlock();
            if (above.getType() == Material.AIR && random.nextBoolean()) {
                Material vegetation = random.nextBoolean() ? Material.SHORT_GRASS : Material.DANDELION;
                above.setType(vegetation);
            }
        }
    }

    private String getChunkKey(org.bukkit.Chunk chunk) {
        return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }

    /**
     * Get statistics about post-processing
     */
    public int getProcessedChunkCount() {
        return processedChunks.size();
    }

    /**
     * Clear processed chunks (for debugging/reset)
     */
    public void clearProcessedChunks() {
        processedChunks.clear();
    }
}