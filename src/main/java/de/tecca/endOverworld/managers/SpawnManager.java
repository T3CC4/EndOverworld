package de.tecca.endOverworld.managers;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles player spawning and respawning in the End dimension
 */
public class SpawnManager implements Listener {

    private final EndOverworld plugin;
    private final WorldManager worldManager;

    public SpawnManager(EndOverworld plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Only teleport new players to the End
        if (!player.hasPlayedBefore()) {
            World endWorld = worldManager.getEndWorld();
            Location spawnLocation = findSafeEndSpawn(endWorld);
            player.teleport(spawnLocation);

            // Welcome message
            player.sendMessage("§5Welcome to the End Overworld!");
            player.sendMessage("§7This dimension is now your new home. Good luck!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // If player has a bed spawn in the End, use that
        if (event.getRespawnLocation() != null &&
                event.getRespawnLocation().getWorld().getEnvironment() == World.Environment.THE_END) {
            return;
        }

        // Otherwise, find a safe spawn location in the End
        World endWorld = worldManager.getEndWorld();
        Location spawnLocation = findSafeEndSpawn(endWorld);
        event.setRespawnLocation(spawnLocation);
    }

    /**
     * Finds a safe spawn location in the End dimension
     */
    public Location findSafeEndSpawn(World endWorld) {
        // Search outer islands (avoid dragon area)
        int[] distances = {1200, 1500, 1800, 2100, 2400};

        for (int distance : distances) {
            for (int angle = 0; angle < 360; angle += 30) {
                double radian = Math.toRadians(angle);
                int x = (int) (Math.cos(radian) * distance);
                int z = (int) (Math.sin(radian) * distance);

                Location safeLocation = findSafeLocationAt(endWorld, x, z);
                if (safeLocation != null) {
                    plugin.getLogger().info("Found safe spawn at " + x + ", " + z);
                    return safeLocation;
                }
            }
        }

        // Fallback: create emergency platform
        plugin.getLogger().warning("No safe spawn found - creating emergency platform");
        return createEmergencySpawnPlatform(endWorld);
    }

    private Location findSafeLocationAt(World world, int x, int z) {
        for (int y = 100; y >= 40; y--) {
            Location loc = new Location(world, x, y, z);
            if (world.getBlockAt(loc).getType() == Material.END_STONE) {
                if (isSafePlatform(world, loc)) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
        }
        return null;
    }

    private boolean isSafePlatform(World world, Location groundLevel) {
        // Check 3x3 platform with air above
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location ground = groundLevel.clone().add(x, 0, z);
                Location air1 = groundLevel.clone().add(x, 1, z);
                Location air2 = groundLevel.clone().add(x, 2, z);

                if (world.getBlockAt(ground).getType() != Material.END_STONE ||
                        world.getBlockAt(air1).getType() != Material.AIR ||
                        world.getBlockAt(air2).getType() != Material.AIR) {
                    return false;
                }
            }
        }

        // Check that platform isn't too small (5x5 area should have some End stone)
        int solidCount = 0;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Location check = groundLevel.clone().add(x, 0, z);
                if (world.getBlockAt(check).getType() == Material.END_STONE) {
                    solidCount++;
                }
            }
        }

        return solidCount >= 15; // At least 15 blocks in 5x5 area
    }

    private Location createEmergencySpawnPlatform(World endWorld) {
        int x = 1000, z = 1000, y = 70;

        // Create 7x7 platform for safety
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                Location platformLoc = new Location(endWorld, x + dx, y, z + dz);
                platformLoc.getBlock().setType(Material.END_STONE);

                // Clear air above
                for (int dy = 1; dy <= 3; dy++) {
                    Location airLoc = new Location(endWorld, x + dx, y + dy, z + dz);
                    airLoc.getBlock().setType(Material.AIR);
                }
            }
        }

        // Add some chorus flowers for resources
        new Location(endWorld, x - 2, y + 1, z - 2).getBlock().setType(Material.CHORUS_FLOWER);
        new Location(endWorld, x + 2, y + 1, z + 2).getBlock().setType(Material.CHORUS_FLOWER);

        plugin.getLogger().warning("Created emergency spawn platform at " + x + ", " + y + ", " + z);
        return new Location(endWorld, x + 0.5, y + 1, z + 0.5);
    }

    /**
     * Teleports a player to a safe End spawn location
     */
    public void teleportToSafeSpawn(Player player) {
        World endWorld = worldManager.getEndWorld();
        Location spawnLocation = findSafeEndSpawn(endWorld);
        player.teleport(spawnLocation);
    }

    /**
     * Checks if a location is safe for spawning
     */
    public boolean isSafeSpawnLocation(Location location) {
        return location.getWorld().getEnvironment() == World.Environment.THE_END &&
                isSafePlatform(location.getWorld(), location);
    }
}