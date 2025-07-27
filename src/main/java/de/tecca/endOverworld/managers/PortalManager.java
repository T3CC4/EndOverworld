package de.tecca.endOverworld.managers;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

/**
 * Handles portal redirection between End and Nether (8:1 ratio)
 */
public class PortalManager implements Listener {

    private final EndOverworld plugin;
    private final WorldManager worldManager;

    // Coordinate scaling factor (same as vanilla Overworld/Nether)
    private static final double COORDINATE_SCALE = 8.0;

    public PortalManager(EndOverworld plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Location from = event.getFrom();
        World fromWorld = from.getWorld();

        if (worldManager.isEndWorld(fromWorld)) {
            // End to Nether
            handleEndToNether(event, from);
        } else if (worldManager.isNetherWorld(fromWorld)) {
            // Nether to End
            handleNetherToEnd(event, from);
        }
        // Ignore other world types (like normal Overworld if it exists)
    }

    private void handleEndToNether(PlayerPortalEvent event, Location from) {
        World netherWorld = worldManager.getNetherWorld();

        // Convert End coordinates to Nether coordinates (divide by 8)
        double netherX = from.getX() / COORDINATE_SCALE;
        double netherZ = from.getZ() / COORDINATE_SCALE;
        double netherY = Math.max(1, Math.min(128, from.getY())); // Keep Y within Nether bounds

        Location netherLocation = new Location(netherWorld, netherX, netherY, netherZ);

        // Ensure safe arrival location
        netherLocation = findSafeNetherLocation(netherLocation);

        event.setTo(netherLocation);

        plugin.getLogger().info("Portal travel: End " + formatCoords(from) +
                " -> Nether " + formatCoords(netherLocation));
    }

    private void handleNetherToEnd(PlayerPortalEvent event, Location from) {
        World endWorld = worldManager.getEndWorld();

        // Convert Nether coordinates to End coordinates (multiply by 8)
        double endX = from.getX() * COORDINATE_SCALE;
        double endZ = from.getZ() * COORDINATE_SCALE;
        double endY = Math.max(40, Math.min(100, from.getY())); // Keep Y within reasonable End bounds

        Location endLocation = new Location(endWorld, endX, endY, endZ);

        // Ensure safe arrival location
        endLocation = findSafeEndLocation(endLocation);

        event.setTo(endLocation);

        plugin.getLogger().info("Portal travel: Nether " + formatCoords(from) +
                " -> End " + formatCoords(endLocation));
    }

    private Location findSafeNetherLocation(Location target) {
        World nether = target.getWorld();
        int x = target.getBlockX();
        int z = target.getBlockZ();

        // Search for safe location in Nether
        for (int y = 100; y >= 10; y--) {
            Location check = new Location(nether, x, y, z);

            if (nether.getBlockAt(check).getType().isSolid() &&
                    nether.getBlockAt(check.add(0, 1, 0)).getType().isAir() &&
                    nether.getBlockAt(check.add(0, 1, 0)).getType().isAir()) {

                return new Location(nether, x + 0.5, y + 1, z + 0.5);
            }
        }

        // Fallback: create small platform
        return createNetherPlatform(nether, x, z);
    }

    private Location findSafeEndLocation(Location target) {
        World end = target.getWorld();
        int x = target.getBlockX();
        int z = target.getBlockZ();

        // Search for safe location in End
        for (int y = 100; y >= 40; y--) {
            Location check = new Location(end, x, y, z);

            if (end.getBlockAt(check).getType() == org.bukkit.Material.END_STONE &&
                    end.getBlockAt(check.add(0, 1, 0)).getType().isAir() &&
                    end.getBlockAt(check.add(0, 1, 0)).getType().isAir()) {

                return new Location(end, x + 0.5, y + 1, z + 0.5);
            }
        }

        // Fallback: create small platform
        return createEndPlatform(end, x, z);
    }

    private Location createNetherPlatform(World nether, int x, int z) {
        int y = 64; // Safe middle height

        // Create 3x3 obsidian platform
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Location platformLoc = new Location(nether, x + dx, y, z + dz);
                platformLoc.getBlock().setType(org.bukkit.Material.OBSIDIAN);

                // Clear air above
                for (int dy = 1; dy <= 3; dy++) {
                    Location airLoc = new Location(nether, x + dx, y + dy, z + dz);
                    airLoc.getBlock().setType(org.bukkit.Material.AIR);
                }
            }
        }

        plugin.getLogger().info("Created emergency Nether platform at " + x + ", " + y + ", " + z);
        return new Location(nether, x + 0.5, y + 1, z + 0.5);
    }

    private Location createEndPlatform(World end, int x, int z) {
        int y = 70; // Safe middle height

        // Create 3x3 End stone platform
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Location platformLoc = new Location(end, x + dx, y, z + dz);
                platformLoc.getBlock().setType(org.bukkit.Material.END_STONE);

                // Clear air above
                for (int dy = 1; dy <= 3; dy++) {
                    Location airLoc = new Location(end, x + dx, y + dy, z + dz);
                    airLoc.getBlock().setType(org.bukkit.Material.AIR);
                }
            }
        }

        plugin.getLogger().info("Created emergency End platform at " + x + ", " + y + ", " + z);
        return new Location(end, x + 0.5, y + 1, z + 0.5);
    }

    private String formatCoords(Location loc) {
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Manually calculates End coordinates from Nether coordinates
     */
    public Location netherToEndCoords(Location netherLocation) {
        World endWorld = worldManager.getEndWorld();
        double endX = netherLocation.getX() * COORDINATE_SCALE;
        double endZ = netherLocation.getZ() * COORDINATE_SCALE;
        double endY = Math.max(40, Math.min(100, netherLocation.getY()));

        return new Location(endWorld, endX, endY, endZ);
    }

    /**
     * Manually calculates Nether coordinates from End coordinates
     */
    public Location endToNetherCoords(Location endLocation) {
        World netherWorld = worldManager.getNetherWorld();
        double netherX = endLocation.getX() / COORDINATE_SCALE;
        double netherZ = endLocation.getZ() / COORDINATE_SCALE;
        double netherY = Math.max(1, Math.min(128, endLocation.getY()));

        return new Location(netherWorld, netherX, netherY, netherZ);
    }

    /**
     * Gets the coordinate scaling factor
     */
    public double getCoordinateScale() {
        return COORDINATE_SCALE;
    }
}