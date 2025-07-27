package de.tecca.endOverworld.world;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Targeted post-processing system that only affects End Cities and their immediate surroundings
 * Much more controlled and performance-friendly than the previous version
 */
public class EndPostProcessor implements Listener {

    private final EndOverworld plugin;
    private final Set<String> processedChunks = ConcurrentHashMap.newKeySet();

    // Detection parameters
    private static final int END_CITY_MIN_BLOCKS = 20;
    private static final int CORRUPTION_RADIUS = 12; // Only corrupt within 12 blocks of End City
    private static final double CORRUPTION_CHANCE = 0.25; // 25% chance to corrupt valid blocks

    public EndPostProcessor(EndOverworld plugin) {
        this.plugin = plugin;
    }

    /**
     * Process chunks AFTER they are fully populated - but only if they contain End Cities
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

        // Schedule post-processing after population is complete
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (chunk.isLoaded()) {
                processEndCityChunk(chunk);
            }
            processedChunks.add(chunkKey);
        }, 5L); // Wait 5 ticks after population
    }

    private boolean isNearMainIsland(int chunkX, int chunkZ) {
        int worldX = chunkX * 16;
        int worldZ = chunkZ * 16;
        double distance = Math.sqrt(worldX * worldX + worldZ * worldZ);
        return distance < 1000;
    }

    /**
     * Only process chunks that actually contain End City structures
     */
    private void processEndCityChunk(org.bukkit.Chunk chunk) {
        // First, detect if this chunk contains End City structures
        List<Location> endCityBlocks = detectEndCityBlocks(chunk);

        if (endCityBlocks.size() < END_CITY_MIN_BLOCKS) {
            // No significant End City presence - skip processing
            return;
        }

        plugin.getLogger().info("Processing End City in chunk " + chunk.getX() + ", " + chunk.getZ() +
                " with " + endCityBlocks.size() + " structure blocks");

        // Apply targeted corruption around End City blocks
        applyTargetedCorruption(chunk, endCityBlocks);
    }

    /**
     * Detect End City blocks in the chunk
     */
    private List<Location> detectEndCityBlocks(org.bukkit.Chunk chunk) {
        List<Location> endCityBlocks = new ArrayList<>();
        World world = chunk.getWorld();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 30; y <= 100; y++) {
                    Location loc = new Location(world, chunk.getX() * 16 + x, y, chunk.getZ() * 16 + z);
                    Material material = loc.getBlock().getType();

                    if (isEndCityMaterial(material)) {
                        endCityBlocks.add(loc);
                    }
                }
            }
        }

        return endCityBlocks;
    }

    /**
     * Apply corruption only around actual End City structures
     */
    private void applyTargetedCorruption(org.bukkit.Chunk chunk, List<Location> endCityBlocks) {
        int corruptedBlocks = 0;
        int maxCorruptions = 50; // Limit corruptions per chunk for performance

        for (Location endCityBlock : endCityBlocks) {
            if (corruptedBlocks >= maxCorruptions) break;

            // Apply corruption in a small radius around each End City block
            corruptedBlocks += corruptAroundLocation(endCityBlock, CORRUPTION_RADIUS);
        }

        if (corruptedBlocks > 0) {
            plugin.getLogger().info("Applied " + corruptedBlocks + " corruptions to End City in chunk " +
                    chunk.getX() + ", " + chunk.getZ());
        }
    }

    /**
     * Apply corruption in a controlled radius around a specific location
     */
    private int corruptAroundLocation(Location center, int radius) {
        int corrupted = 0;
        int maxCorruptionsPerCenter = 15; // Limit per center point

        for (int x = -radius; x <= radius && corrupted < maxCorruptionsPerCenter; x++) {
            for (int y = -radius; y <= radius && corrupted < maxCorruptionsPerCenter; y++) {
                for (int z = -radius; z <= radius && corrupted < maxCorruptionsPerCenter; z++) {
                    Location target = center.clone().add(x, y, z);
                    double distance = center.distance(target);

                    // Only corrupt within radius and with decreasing probability by distance
                    if (distance <= radius) {
                        double corruptionChance = CORRUPTION_CHANCE * (1.0 - distance / radius);

                        if (Math.random() < corruptionChance && shouldCorruptBlock(target)) {
                            Material corruption = getAppropriateCorruption(target.getBlock().getType(), distance / radius);
                            if (corruption != null) {
                                target.getBlock().setType(corruption);
                                corrupted++;
                            }
                        }
                    }
                }
            }
        }

        return corrupted;
    }

    /**
     * Check if a block should be corrupted (avoid important blocks)
     */
    private boolean shouldCorruptBlock(Location location) {
        Material material = location.getBlock().getType();

        // Don't corrupt air
        if (!material.isSolid()) return false;

        // Don't corrupt important functional blocks
        if (material == Material.CHEST ||
                material == Material.ENDER_CHEST ||
                material == Material.SPAWNER ||
                material == Material.END_PORTAL ||
                material == Material.END_PORTAL_FRAME) {
            return false;
        }

        // Don't corrupt blocks that are already corrupted
        if (isAlreadyCorrupted(material)) return false;

        // Don't corrupt if there are players very close (avoid disrupting active areas)
        boolean playersNearby = location.getWorld().getPlayers().stream()
                .anyMatch(player -> player.getLocation().distance(location) < 5);
        if (playersNearby) return false;

        return true;
    }

    /**
     * Get appropriate corruption material based on original material and intensity
     */
    private Material getAppropriateCorruption(Material original, double intensity) {
        // High intensity corruption (close to End City)
        if (intensity > 0.7) {
            switch (original) {
                case PURPUR_BLOCK:
                case PURPUR_PILLAR:
                    return Math.random() < 0.6 ? Material.SCULK : Material.DEEPSLATE_BRICKS;
                case END_STONE_BRICKS:
                    return Math.random() < 0.7 ? Material.SCULK : Material.DEEPSLATE_BRICKS;
                case END_STONE:
                    return Math.random() < 0.5 ? Material.SCULK : Material.DEEPSLATE;
                case PURPUR_STAIRS:
                    return Material.DEEPSLATE_BRICK_STAIRS;
                case PURPUR_SLAB:
                    return Material.DEEPSLATE_BRICK_SLAB;
                case END_STONE_BRICK_STAIRS:
                    return Material.DEEPSLATE_BRICK_STAIRS;
                case END_STONE_BRICK_SLAB:
                    return Material.DEEPSLATE_BRICK_SLAB;
                default:
                    return Material.SCULK_VEIN;
            }
        }

        // Medium intensity corruption
        else if (intensity > 0.4) {
            switch (original) {
                case PURPUR_BLOCK:
                case PURPUR_PILLAR:
                case END_STONE_BRICKS:
                    return Math.random() < 0.5 ? Material.DEEPSLATE_BRICKS : Material.COBBLED_DEEPSLATE;
                case END_STONE:
                    return Material.COBBLED_DEEPSLATE;
                case PURPUR_STAIRS:
                case END_STONE_BRICK_STAIRS:
                    return Material.DEEPSLATE_BRICK_STAIRS;
                case PURPUR_SLAB:
                case END_STONE_BRICK_SLAB:
                    return Material.DEEPSLATE_BRICK_SLAB;
                default:
                    return Math.random() < 0.3 ? Material.SCULK_VEIN : null;
            }
        }

        // Light corruption (far from End City)
        else {
            if (isEndCityMaterial(original)) {
                return Math.random() < 0.3 ? Material.SCULK_VEIN : Material.COBBLED_DEEPSLATE;
            }
            return Math.random() < 0.1 ? Material.SCULK_VEIN : null;
        }
    }

    /**
     * Check if material is End City related
     */
    private boolean isEndCityMaterial(Material material) {
        return material == Material.PURPUR_BLOCK ||
                material == Material.PURPUR_PILLAR ||
                material == Material.PURPUR_STAIRS ||
                material == Material.PURPUR_SLAB ||
                material == Material.END_STONE_BRICKS ||
                material == Material.END_STONE_BRICK_STAIRS ||
                material == Material.END_STONE_BRICK_SLAB ||
                material == Material.END_STONE_BRICK_WALL ||
                material == Material.END_ROD ||
                material == Material.MAGENTA_STAINED_GLASS ||
                material == Material.MAGENTA_STAINED_GLASS_PANE;
    }

    /**
     * Check if material is already corrupted
     */
    private boolean isAlreadyCorrupted(Material material) {
        return material == Material.SCULK ||
                material == Material.SCULK_VEIN ||
                material == Material.SCULK_CATALYST ||
                material == Material.SCULK_SHRIEKER ||
                material == Material.SCULK_SENSOR ||
                material == Material.DEEPSLATE_BRICKS ||
                material == Material.DEEPSLATE_TILES ||
                material == Material.POLISHED_DEEPSLATE ||
                material == Material.COBBLED_DEEPSLATE ||
                material == Material.DEEPSLATE ||
                material == Material.REINFORCED_DEEPSLATE;
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
        plugin.getLogger().info("Cleared processed chunks cache");
    }

    /**
     * Manually process a specific chunk (for admin commands)
     */
    public void manuallyProcessChunk(org.bukkit.Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        processedChunks.remove(chunkKey); // Allow reprocessing
        processEndCityChunk(chunk);
    }
}