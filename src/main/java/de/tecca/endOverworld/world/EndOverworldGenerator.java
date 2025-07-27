package de.tecca.endOverworld.world;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.Random;

/**
 * Custom chunk generator for End with enhanced terrain generation
 * Compatible with Nullscape and other End modifications
 */
public class EndOverworldGenerator extends ChunkGenerator {

    private static final int MAIN_ISLAND_RADIUS = 1000;
    private static final int MIN_ISLAND_HEIGHT = 55;
    private static final int MAX_ISLAND_HEIGHT = 75;

    // Materials for overworld debris
    private static final Material[] OVERWORLD_MATERIALS = {
            Material.GRASS_BLOCK, Material.DIRT, Material.STONE,
            Material.OAK_LOG, Material.COBBLESTONE, Material.SAND
    };

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        ChunkData chunk = createChunkData(world);

        // Check if we should generate terrain here
        if (shouldGenerateInChunk(chunkX, chunkZ)) {
            generateEndTerrain(chunk, random, chunkX, chunkZ);

            // Add overworld debris occasionally
            if (random.nextInt(100) < 3) { // 3% chance
                generateOverworldDebris(chunk, random);
            }
        }

        return chunk;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return true; // Allow End cities and other structures
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true; // Allow mob spawning
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return true; // Allow decorations like chorus plants
    }

    /**
     * Determines if we should generate terrain in this chunk
     */
    private boolean shouldGenerateInChunk(int chunkX, int chunkZ) {
        int worldX = chunkX * 16;
        int worldZ = chunkZ * 16;

        // Don't generate too close to main dragon island (0,0)
        double distanceFromCenter = Math.sqrt(worldX * worldX + worldZ * worldZ);
        return distanceFromCenter > MAIN_ISLAND_RADIUS;
    }

    /**
     * Generates End terrain with islands and flat areas
     */
    private void generateEndTerrain(ChunkData chunk, Random random, int chunkX, int chunkZ) {
        SimplexOctaveGenerator generator = new SimplexOctaveGenerator(new Random(chunkX * 341873128712L + chunkZ * 132897987541L), 4);
        generator.setScale(0.005D);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Generate noise for island formation
                double noise = generator.noise(worldX, worldZ, 0.5D, 0.5D);
                double heightNoise = generator.noise(worldX, worldZ, 0.8D, 0.8D);

                // Create islands based on noise
                if (noise > 0.25D) {
                    int baseHeight = MIN_ISLAND_HEIGHT + (int)(heightNoise * (MAX_ISLAND_HEIGHT - MIN_ISLAND_HEIGHT));
                    generateIslandAt(chunk, x, z, baseHeight, random, noise);
                }
            }
        }
    }

    /**
     * Generates an island at the specified location
     */
    private void generateIslandAt(ChunkData chunk, int x, int z, int height, Random random, double density) {
        // Calculate island thickness based on density
        int thickness = 3 + (int)(density * 8);

        // Generate the main island structure
        for (int y = Math.max(1, height - thickness); y <= height; y++) {
            // Make edges more jagged and natural
            double edgeNoise = random.nextGaussian() * 0.1;
            if (density + edgeNoise > 0.3) {
                chunk.setBlock(x, y, z, Material.END_STONE);
            }
        }

        // Add supporting pillars to prevent floating islands
        if (density > 0.4 && random.nextDouble() < 0.7) {
            generateSupportPillar(chunk, x, z, height - thickness, random);
        }

        // Create flat areas suitable for building and beds
        if (density > 0.6 && random.nextInt(20) == 0) {
            createBuildingPlatform(chunk, x, z, height + 1, random);
        }

        // Add chorus plants occasionally
        if (random.nextInt(15) == 0 && height < MAX_ISLAND_HEIGHT - 5) {
            addChorusPlant(chunk, x, z, height + 1, random);
        }
    }

    /**
     * Generates a support pillar to make islands look more natural
     */
    private void generateSupportPillar(ChunkData chunk, int x, int z, int startHeight, Random random) {
        int pillarHeight = 5 + random.nextInt(15);

        for (int y = Math.max(1, startHeight - pillarHeight); y < startHeight; y++) {
            if (random.nextDouble() < 0.8) { // 80% chance for each block
                chunk.setBlock(x, y, z, Material.END_STONE);
            }
        }
    }

    /**
     * Creates a flat platform suitable for building
     */
    private void createBuildingPlatform(ChunkData chunk, int centerX, int centerZ, int height, Random random) {
        int size = 3 + random.nextInt(4); // 3-6 blocks
        int radius = size / 2;

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                if (isValidChunkCoordinate(x, z)) {
                    // Ensure solid ground
                    chunk.setBlock(x, height - 1, z, Material.END_STONE);

                    // Clear space above for building
                    for (int y = height; y <= height + 3; y++) {
                        chunk.setBlock(x, y, z, Material.AIR);
                    }

                    // Occasionally add End stone bricks for a more finished look
                    if (random.nextInt(8) == 0) {
                        chunk.setBlock(x, height - 1, z, Material.END_STONE_BRICKS);
                    }
                }
            }
        }
    }

    /**
     * Adds chorus plants to the terrain
     */
    private void addChorusPlant(ChunkData chunk, int x, int z, int height, Random random) {
        if (!isValidChunkCoordinate(x, z)) return;

        // Place chorus flower
        chunk.setBlock(x, height, z, Material.CHORUS_FLOWER);

        // Occasionally add a small chorus plant structure
        if (random.nextInt(3) == 0) {
            int plantHeight = 2 + random.nextInt(3);
            for (int y = 1; y <= plantHeight; y++) {
                if (height + y < 120) { // Don't build too high
                    chunk.setBlock(x, height + y, z, Material.CHORUS_PLANT);
                }
            }

            // Add flower on top
            if (height + plantHeight + 1 < 120) {
                chunk.setBlock(x, height + plantHeight + 1, z, Material.CHORUS_FLOWER);
            }
        }
    }

    /**
     * Generates small patches of overworld materials as "debris"
     */
    private void generateOverworldDebris(ChunkData chunk, Random random) {
        int debrisX = random.nextInt(16);
        int debrisZ = random.nextInt(16);
        int debrisY = MIN_ISLAND_HEIGHT + random.nextInt(20);

        Material debrisMaterial = OVERWORLD_MATERIALS[random.nextInt(OVERWORLD_MATERIALS.length)];
        int debrisSize = 1 + random.nextInt(3); // 1-3 block radius

        for (int x = -debrisSize; x <= debrisSize; x++) {
            for (int z = -debrisSize; z <= debrisSize; z++) {
                for (int y = 0; y <= debrisSize; y++) {
                    int blockX = debrisX + x;
                    int blockZ = debrisZ + z;
                    int blockY = debrisY + y;

                    if (isValidChunkCoordinate(blockX, blockZ) && blockY > 0 && blockY < 256) {
                        // Use distance to create a more natural debris cluster
                        double distance = Math.sqrt(x * x + z * z + y * y);
                        if (distance <= debrisSize && random.nextDouble() < 0.6) {
                            chunk.setBlock(blockX, blockY, blockZ, debrisMaterial);

                            // Add some grass or flowers on grass blocks
                            if (debrisMaterial == Material.GRASS_BLOCK && y == debrisSize && random.nextBoolean()) {
                                if (blockY + 1 < 256) {
                                    Material decoration = random.nextBoolean() ? Material.SHORT_GRASS : Material.DANDELION;
                                    chunk.setBlock(blockX, blockY + 1, blockZ, decoration);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if coordinates are valid within a chunk
     */
    private boolean isValidChunkCoordinate(int x, int z) {
        return x >= 0 && x < 16 && z >= 0 && z < 16;
    }

    /**
     * Gets information about this generator for debugging
     */
    public String getGeneratorInfo() {
        return "EndOverworldGenerator v1.0 - Enhanced End terrain with building platforms and overworld debris";
    }
}