package de.tecca.endOverworld.world;

import de.tecca.endOverworld.EndOverworld;
import de.tecca.endOverworld.entities.EndCityVillager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages End structures and spawns villagers in End cities
 */
public class StructureManager implements Listener {

    private final EndOverworld plugin;
    private final Map<String, List<EndCityVillager>> endCityVillagers;
    private final Map<String, Boolean> processedChunks;

    public StructureManager(EndOverworld plugin) {
        this.plugin = plugin;
        this.endCityVillagers = new HashMap<>();
        this.processedChunks = new HashMap<>();
    }

    /**
     * Handles chunk population to detect and populate End cities with villagers
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkPopulate(ChunkPopulateEvent event) {
        World world = event.getWorld();

        // Only process End chunks
        if (world.getEnvironment() == World.Environment.THE_END) {
            // Delay processing to allow structures to fully generate
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                processEndChunk(event.getChunk());
            }, 20L); // Wait 1 second
        }
    }

    private void processEndChunk(org.bukkit.Chunk chunk) {
        String chunkKey = getChunkKey(chunk);

        // Skip if already processed
        if (processedChunks.containsKey(chunkKey)) {
            return;
        }

        List<Location> shulkerLocations = findShulkersInChunk(chunk);

        for (Location shulkerLoc : shulkerLocations) {
            if (!isLocationAlreadyProcessed(shulkerLoc)) {
                processEndCityAtLocation(shulkerLoc);
                markLocationAsProcessed(shulkerLoc);
            }
        }

        // Mark chunk as processed
        processedChunks.put(chunkKey, true);
    }

    private List<Location> findShulkersInChunk(org.bukkit.Chunk chunk) {
        List<Location> shulkerLocations = new ArrayList<>();

        // Scan chunk for Shulkers (reliable End city indicator)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 30; y <= 100; y++) {
                    Location checkLoc = new Location(chunk.getWorld(),
                            chunk.getX() * 16 + x, y, chunk.getZ() * 16 + z);

                    // Look for Shulkers in nearby area
                    for (Entity entity : checkLoc.getWorld().getNearbyEntities(checkLoc, 3, 3, 3)) {
                        if (entity instanceof Shulker) {
                            // Only add unique locations (avoid duplicates)
                            boolean alreadyFound = false;
                            for (Location existing : shulkerLocations) {
                                if (existing.distance(entity.getLocation()) < 5) {
                                    alreadyFound = true;
                                    break;
                                }
                            }
                            if (!alreadyFound) {
                                shulkerLocations.add(entity.getLocation());
                            }
                        }
                    }
                }
            }
        }

        return shulkerLocations;
    }

    private void processEndCityAtLocation(Location shulkerLocation) {
        // Determine number of villagers to spawn (1-2 per End city/ship)
        int villagerCount = 1 + (int)(Math.random() * 2);

        // Create villagers near the Shulker location using EndCityVillager's method
        List<EndCityVillager> villagers = EndCityVillager.createVillagersNear(plugin, shulkerLocation, villagerCount);

        if (!villagers.isEmpty()) {
            String locationKey = getLocationKey(shulkerLocation);
            endCityVillagers.put(locationKey, villagers);

            plugin.getLogger().info("Populated End city with " + villagers.size() +
                    " villagers near Shulker at " + shulkerLocation);
        } else {
            plugin.getLogger().warning("Failed to spawn villagers near Shulker at " + shulkerLocation);
        }
    }

    private String getChunkKey(org.bukkit.Chunk chunk) {
        return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }

    private String getLocationKey(Location location) {
        return location.getWorld().getName() + "_" +
                (location.getBlockX() / 16) + "_" +
                (location.getBlockZ() / 16);
    }

    private boolean isLocationAlreadyProcessed(Location location) {
        String key = getLocationKey(location);
        return endCityVillagers.containsKey(key);
    }

    private void markLocationAsProcessed(Location location) {
        String key = getLocationKey(location);
        if (!endCityVillagers.containsKey(key)) {
            endCityVillagers.put(key, new ArrayList<>());
        }
    }

    /**
     * Manually spawns villagers at a specific End city location
     */
    public List<EndCityVillager> spawnVillagersAt(Location location, int count) {
        List<EndCityVillager> villagers = EndCityVillager.createVillagersNear(plugin, location, count);

        if (!villagers.isEmpty()) {
            String locationKey = getLocationKey(location);
            endCityVillagers.put(locationKey, villagers);
            plugin.getLogger().info("Manually spawned " + villagers.size() + " villagers at " + location);
        } else {
            plugin.getLogger().warning("Failed to manually spawn villagers at " + location);
        }

        return villagers;
    }

    /**
     * Gets all End city villagers
     */
    public List<EndCityVillager> getAllEndCityVillagers() {
        List<EndCityVillager> allVillagers = new ArrayList<>();
        for (List<EndCityVillager> villagerList : endCityVillagers.values()) {
            allVillagers.addAll(villagerList);
        }
        return allVillagers;
    }

    /**
     * Gets End city villagers near a specific location
     */
    public List<EndCityVillager> getVillagersNear(Location location, double radius) {
        List<EndCityVillager> nearbyVillagers = new ArrayList<>();

        for (EndCityVillager villager : getAllEndCityVillagers()) {
            if (villager.isValid() &&
                    villager.getVillager().getLocation().distance(location) <= radius) {
                nearbyVillagers.add(villager);
            }
        }

        return nearbyVillagers;
    }

    /**
     * Returns wandering villagers to their spawn locations
     */
    public void returnWanderingVillagers() {
        int returnedCount = 0;

        for (EndCityVillager villager : getAllEndCityVillagers()) {
            if (villager.isValid()) {
                double distance = villager.getVillager().getLocation().distance(villager.getSpawnLocation());
                if (distance > 15) { // Use same threshold as EndCityVillager.returnToSpawn()
                    villager.returnToSpawn();
                    returnedCount++;
                }
            }
        }

        if (returnedCount > 0) {
            plugin.getLogger().info("Returned " + returnedCount + " wandering villagers to their spawn locations");
        }
    }

    /**
     * Cleanup invalid villagers
     */
    public void cleanup() {
        AtomicInteger removedCount = new AtomicInteger();

        for (Map.Entry<String, List<EndCityVillager>> entry : endCityVillagers.entrySet()) {
            List<EndCityVillager> villagers = entry.getValue();
            villagers.removeIf(villager -> {
                if (!villager.isValid()) {
                    removedCount.getAndIncrement();
                    return true;
                }
                return false;
            });
        }

        // Remove empty entries
        endCityVillagers.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        if (removedCount.get() > 0) {
            plugin.getLogger().info("Cleaned up " + removedCount + " invalid villagers");
        }
    }

    /**
     * Gets statistics about managed villagers
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();

        int totalVillagers = 0;
        int validVillagers = 0;
        int endCities = 0;

        for (List<EndCityVillager> villagers : endCityVillagers.values()) {
            if (!villagers.isEmpty()) {
                endCities++;
                totalVillagers += villagers.size();

                for (EndCityVillager villager : villagers) {
                    if (villager.isValid()) {
                        validVillagers++;
                    }
                }
            }
        }

        stats.put("total_villagers", totalVillagers);
        stats.put("valid_villagers", validVillagers);
        stats.put("end_cities", endCities);
        stats.put("processed_chunks", processedChunks.size());

        return stats;
    }

    /**
     * Forces a recheck of a specific chunk
     */
    public void recheckChunk(org.bukkit.Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        processedChunks.remove(chunkKey);
        processEndChunk(chunk);
    }
}