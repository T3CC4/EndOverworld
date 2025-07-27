package de.tecca.endOverworld.world;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.Random;

/**
 * Post-processing End generator - lets Minecraft/datapacks generate, then applies our changes
 */
public class EndOverworldGenerator extends ChunkGenerator {

    private SimplexOctaveGenerator corruptionNoise;
    private SimplexOctaveGenerator enhancementNoise;
    private EndOverworld plugin;

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        // Let Minecraft/datapacks do their work first - return empty chunk for now
        ChunkData chunk = createChunkData(world);

        // Initialize noise if needed
        if (corruptionNoise == null) {
            initializeNoiseGenerators(world.getSeed());
        }

        // Schedule post-processing after chunk is populated
        schedulePostProcessing(world, chunkX, chunkZ);

        return chunk; // Empty chunk - let vanilla/datapacks fill it
    }

    private void initializeNoiseGenerators(long seed) {
        Random noiseRandom = new Random(seed);

        // Corruption zones
        corruptionNoise = new SimplexOctaveGenerator(new Random(noiseRandom.nextLong()), 4);
        corruptionNoise.setScale(0.008D);

        // Enhancement zones
        enhancementNoise = new SimplexOctaveGenerator(new Random(noiseRandom.nextLong()), 3);
        enhancementNoise.setScale(0.02D);
    }

    private void schedulePostProcessing(World world, int chunkX, int chunkZ) {
        // Use Bukkit scheduler to process chunk after it's fully populated
        Bukkit.getScheduler().runTaskLater(
                plugin != null ? plugin : getPluginFromWorld(world),
                () -> postProcessChunk(world, chunkX, chunkZ),
                5L // Wait 5 ticks for population to complete
        );
    }

    /**
     * Fallback method to get plugin instance from world if not set
     */
    private EndOverworld getPluginFromWorld(World world) {
        // Try to get our plugin instance
        return (EndOverworld) Bukkit.getPluginManager().getPlugin("EndOverworld");
    }

    /**
     * Post-process the chunk after vanilla/datapack generation is complete
     */
    private void postProcessChunk(World world, int chunkX, int chunkZ) {
        // Skip if chunk is unloaded
        if (!world.isChunkLoaded(chunkX, chunkZ)) return;

        // Skip main dragon area
        if (isNearMainIsland(chunkX, chunkZ)) return;

        org.bukkit.Chunk chunk = world.getChunkAt(chunkX, chunkZ);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Calculate our enhancement values
                double corruption = corruptionNoise.noise(worldX, worldZ, 0.5D, 0.5D);
                double enhancement = enhancementNoise.noise(worldX, worldZ, 0.5D, 0.5D);

                // Normalize to 0-1
                corruption = (corruption + 1.0) / 2.0;
                enhancement = (enhancement + 1.0) / 2.0;

                // Process this column
                processExistingColumn(chunk, x, z, corruption, enhancement);
            }
        }
    }

    private boolean isNearMainIsland(int chunkX, int chunkZ) {
        int worldX = chunkX * 16;
        int worldZ = chunkZ * 16;
        double distance = Math.sqrt(worldX * worldX + worldZ * worldZ);
        return distance < 1000;
    }

    /**
     * Process existing terrain in the column
     */
    private void processExistingColumn(org.bukkit.Chunk chunk, int x, int z, double corruption, double enhancement) {
        // Scan the column for existing blocks
        for (int y = 40; y <= 100; y++) {
            org.bukkit.block.Block block = chunk.getBlock(x, y, z);
            Material current = block.getType();

            // Skip air blocks
            if (current == Material.AIR) continue;

            // Apply corruption to existing blocks
            if (corruption > 0.4) {
                Material corrupted = applyCorruption(current, corruption);
                if (corrupted != null && corrupted != current) {
                    block.setType(corrupted);
                }
            }

            // Add surface enhancements
            if (current.isSolid() && enhancement > 0.6) {
                addSurfaceEnhancements(chunk, x, y, z, enhancement);
            }
        }
    }

    /**
     * Apply corruption to existing materials
     */
    private Material applyCorruption(Material original, double intensity) {
        // Only corrupt with reasonable probability
        if (Math.random() > intensity * 0.4) return null;

        // Ancient corruption progression
        switch (original) {
            // Purpur corruption progression
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

            // End Stone corruption progression
            case END_STONE_BRICKS:
                if (intensity > 0.8) return Material.SCULK;
                if (intensity > 0.6) return Material.DEEPSLATE_BRICKS;
                return Material.COBBLED_DEEPSLATE;

            case END_STONE_BRICK_STAIRS:
                return Material.DEEPSLATE_BRICK_STAIRS;

            case END_STONE_BRICK_SLAB:
                return Material.DEEPSLATE_BRICK_SLAB;

            case END_STONE_BRICK_WALL:
                return Material.DEEPSLATE_BRICK_WALL;

            case END_STONE:
                if (intensity > 0.9) return Material.SCULK;
                if (intensity > 0.7) return Material.DEEPSLATE;
                if (intensity > 0.5) return Material.COBBLED_DEEPSLATE;
                return null;

            // Other materials
            case OBSIDIAN:
                return intensity > 0.85 ? Material.SCULK_CATALYST : null;

            case STONE:
                return intensity > 0.8 ? Material.DEEPSLATE : null;

            case COBBLESTONE:
                return intensity > 0.7 ? Material.COBBLED_DEEPSLATE : null;

            default:
                return null;
        }
    }

    /**
     * Add surface enhancements to existing terrain
     */
    private void addSurfaceEnhancements(org.bukkit.Chunk chunk, int x, int y, int z, double enhancement) {
        org.bukkit.block.Block above = chunk.getBlock(x, y + 1, z);

        // Only add features if there's air above
        if (above.getType() != Material.AIR || y + 1 >= 256) return;

        Random random = new Random((long)x * 341873128712L + (long)z * 132897987541L + y);

        // Determine what to add based on enhancement strength
        if (enhancement > 0.9 && random.nextDouble() < 0.05) {
            // Rare building foundations
            addBuildingFoundation(chunk, x, y + 1, z);
        } else if (enhancement > 0.8 && random.nextDouble() < 0.08) {
            // Sculk growth
            addSculkGrowth(chunk, x, y + 1, z, random);
        } else if (enhancement > 0.7 && random.nextDouble() < 0.12) {
            // Vegetation
            addVegetation(chunk, x, y + 1, z, random);
        } else if (enhancement > 0.6 && random.nextDouble() < 0.03) {
            // Overworld debris
            addOverworldDebris(chunk, x, y + 1, z, random);
        }
    }

    private void addBuildingFoundation(org.bukkit.Chunk chunk, int x, int y, int z) {
        // Simple foundation marker
        chunk.getBlock(x, y, z).setType(Material.END_STONE_BRICKS);

        // Small 3x3 platform occasionally
        if (Math.random() < 0.3) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (x + dx >= 0 && x + dx < 16 && z + dz >= 0 && z + dz < 16) {
                        org.bukkit.block.Block platformBlock = chunk.getBlock(x + dx, y, z + dz);
                        if (platformBlock.getType() == Material.AIR) {
                            platformBlock.setType(Material.END_STONE_BRICKS);
                        }
                    }
                }
            }
        }
    }

    private void addSculkGrowth(org.bukkit.Chunk chunk, int x, int y, int z, Random random) {
        double growthType = random.nextDouble();

        if (growthType < 0.1) {
            chunk.getBlock(x, y, z).setType(Material.SCULK_CATALYST);
        } else if (growthType < 0.3) {
            Material sculkType = random.nextBoolean() ? Material.SCULK_SENSOR : Material.SCULK_SHRIEKER;
            chunk.getBlock(x, y, z).setType(sculkType);
        } else if (growthType < 0.7) {
            chunk.getBlock(x, y, z).setType(Material.SCULK);
        } else {
            chunk.getBlock(x, y, z).setType(Material.SCULK_VEIN);
        }

        // Occasionally spread to adjacent blocks
        if (random.nextDouble() < 0.4) {
            spreadSculk(chunk, x, y, z, random);
        }
    }

    private void spreadSculk(org.bukkit.Chunk chunk, int centerX, int centerY, int centerZ, Random random) {
        int[] offsets = {-1, 0, 1};

        for (int dx : offsets) {
            for (int dz : offsets) {
                if (dx == 0 && dz == 0) continue;

                int newX = centerX + dx;
                int newZ = centerZ + dz;

                if (newX >= 0 && newX < 16 && newZ >= 0 && newZ < 16 && random.nextDouble() < 0.3) {
                    org.bukkit.block.Block spreadBlock = chunk.getBlock(newX, centerY, newZ);
                    if (spreadBlock.getType() == Material.AIR) {
                        spreadBlock.setType(Material.SCULK_VEIN);
                    }
                }
            }
        }
    }

    private void addVegetation(org.bukkit.Chunk chunk, int x, int y, int z, Random random) {
        double vegType = random.nextDouble();

        if (vegType < 0.4) {
            // Chorus plant
            chunk.getBlock(x, y, z).setType(Material.CHORUS_PLANT);
            if (y + 1 < 256 && random.nextBoolean()) {
                chunk.getBlock(x, y + 1, z).setType(Material.CHORUS_FLOWER);
            }
        } else if (vegType < 0.7) {
            chunk.getBlock(x, y, z).setType(Material.CHORUS_FLOWER);
        } else {
            chunk.getBlock(x, y, z).setType(Material.DEAD_BUSH);
        }
    }

    private void addOverworldDebris(org.bukkit.Chunk chunk, int x, int y, int z, Random random) {
        Material[] debris = {
                Material.DIRT, Material.GRASS_BLOCK, Material.STONE,
                Material.OAK_LOG, Material.COBBLESTONE, Material.SAND
        };

        Material debrisType = debris[random.nextInt(debris.length)];
        chunk.getBlock(x, y, z).setType(debrisType);

        // Add vegetation on grass blocks
        if (debrisType == Material.GRASS_BLOCK && y + 1 < 256 && random.nextBoolean()) {
            Material vegetation = random.nextBoolean() ? Material.SHORT_GRASS : Material.DANDELION;
            chunk.getBlock(x, y + 1, z).setType(vegetation);
        }
    }

    @Override
    public boolean shouldGenerateStructures() {
        return true; // Let vanilla/datapacks handle structures
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true; // Let vanilla/datapacks handle mobs
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return true; // Let vanilla/datapacks handle decorations
    }

    public String getGeneratorInfo() {
        return "Post-Processing EndOverworldGenerator v7.0 - Applies changes after vanilla/datapack generation";
    }
}