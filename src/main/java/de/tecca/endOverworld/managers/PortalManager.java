package de.tecca.endOverworld.managers;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles portal mechanics including Nether portal lighting in the End
 * Supports variable portal sizes from 4x5 to 23x23
 */
public class PortalManager implements Listener {

    private final EndOverworld plugin;
    private final WorldManager worldManager;
    private final Map<String, Long> cooldowns = new HashMap<>();

    // Nether coordinates are 8x smaller than End coordinates
    private static final double COORDINATE_SCALE = 8.0;
    private static final long PORTAL_COOLDOWN = 2000L;

    // Portal size limits
    private static final int MIN_PORTAL_WIDTH = 4;
    private static final int MIN_PORTAL_HEIGHT = 5;
    private static final int MAX_PORTAL_SIZE = 23;

    public PortalManager(EndOverworld plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Location from = event.getFrom();
        World fromWorld = from.getWorld();

        if (worldManager.isEndWorld(fromWorld)) {
            handleEndToNether(event, from);
        } else if (worldManager.isNetherWorld(fromWorld)) {
            handleNetherToEnd(event, from);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();

        if (block.getWorld().getEnvironment() != World.Environment.THE_END) return;
        if (!isLightingTool(item)) return;

        if (isOnCooldown(player)) {
            player.sendMessage("§cPlease wait before lighting another portal!");
            return;
        }

        PortalFrame frame = findPortalFrame(block.getLocation());
        if (frame != null && lightPortal(frame, player)) {
            event.setCancelled(true);
            addCooldown(player);
            consumeItem(item, player);
        }
    }

    private PortalFrame findPortalFrame(Location start) {
        // Search in a reasonable area around the clicked block
        for (int dx = -MAX_PORTAL_SIZE; dx <= MAX_PORTAL_SIZE; dx++) {
            for (int dy = -MAX_PORTAL_SIZE; dy <= MAX_PORTAL_SIZE; dy++) {
                for (int dz = -MAX_PORTAL_SIZE; dz <= MAX_PORTAL_SIZE; dz++) {
                    Location test = start.clone().add(dx, dy, dz);

                    // Try both orientations (X-axis and Z-axis)
                    PortalFrame frameX = detectPortalFrame(test, true);  // X-axis (North-South)
                    if (frameX != null) return frameX;

                    PortalFrame frameZ = detectPortalFrame(test, false); // Z-axis (East-West)
                    if (frameZ != null) return frameZ;
                }
            }
        }
        return null;
    }

    private PortalFrame detectPortalFrame(Location corner, boolean isXAxis) {
        if (corner.getBlock().getType() != Material.OBSIDIAN) return null;

        // Try different portal sizes
        for (int width = MIN_PORTAL_WIDTH; width <= MAX_PORTAL_SIZE; width++) {
            for (int height = MIN_PORTAL_HEIGHT; height <= MAX_PORTAL_SIZE; height++) {
                if (isValidPortalFrame(corner, width, height, isXAxis)) {
                    return new PortalFrame(corner, width, height, isXAxis);
                }
            }
        }
        return null;
    }

    private boolean isValidPortalFrame(Location corner, int width, int height, boolean isXAxis) {
        // Check the frame structure
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                Location loc = isXAxis ?
                        corner.clone().add(w, h, 0) :
                        corner.clone().add(0, h, w);

                boolean isFrameBlock = (w == 0 || w == width - 1 || h == 0 || h == height - 1);
                Material blockType = loc.getBlock().getType();

                if (isFrameBlock) {
                    // Frame must be obsidian
                    if (blockType != Material.OBSIDIAN) return false;
                } else {
                    // Interior must be air or already portal
                    if (blockType != Material.AIR && blockType != Material.NETHER_PORTAL) return false;
                }
            }
        }
        return true;
    }

    private boolean lightPortal(PortalFrame frame, Player player) {
        try {
            // Check if already lit
            if (isPortalLit(frame)) {
                player.sendMessage("§cThis portal is already lit!");
                return false;
            }

            // Fill interior with portal blocks
            for (int w = 1; w < frame.width - 1; w++) {
                for (int h = 1; h < frame.height - 1; h++) {
                    Location loc = frame.isXAxis ?
                            frame.corner.clone().add(w, h, 0) :
                            frame.corner.clone().add(0, h, w);

                    Block block = loc.getBlock();
                    block.setType(Material.NETHER_PORTAL);

                    // Set correct orientation
                    if (block.getBlockData() instanceof org.bukkit.block.data.Orientable) {
                        org.bukkit.block.data.Orientable orientable =
                                (org.bukkit.block.data.Orientable) block.getBlockData();
                        orientable.setAxis(frame.isXAxis ? org.bukkit.Axis.X : org.bukkit.Axis.Z);
                        block.setBlockData(orientable);
                    }
                }
            }

            player.sendMessage("§aPortal lit successfully! Size: " +
                    (frame.width - 2) + "x" + (frame.height - 2));
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.0f);
            spawnLightingEffect(frame.getCenter());
            return true;

        } catch (Exception e) {
            player.sendMessage("§cFailed to light portal!");
            return false;
        }
    }

    private boolean isPortalLit(PortalFrame frame) {
        // Check if any interior block is already a portal
        for (int w = 1; w < frame.width - 1; w++) {
            for (int h = 1; h < frame.height - 1; h++) {
                Location loc = frame.isXAxis ?
                        frame.corner.clone().add(w, h, 0) :
                        frame.corner.clone().add(0, h, w);
                if (loc.getBlock().getType() == Material.NETHER_PORTAL) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleEndToNether(PlayerPortalEvent event, Location from) {
        World nether = worldManager.getNetherWorld();

        // End coordinates are 8x larger than Nether coordinates
        double netherX = from.getX() / COORDINATE_SCALE;
        double netherZ = from.getZ() / COORDINATE_SCALE;
        double netherY = Math.max(1, Math.min(128, from.getY()));

        Location target = new Location(nether, netherX, netherY, netherZ);
        event.setTo(findSafeLocation(target));

        plugin.getLogger().info("Portal travel: End (" + formatCoords(from) +
                ") -> Nether (" + formatCoords(event.getTo()) + ")");
    }

    private void handleNetherToEnd(PlayerPortalEvent event, Location from) {
        World end = worldManager.getEndWorld();

        // Nether coordinates are 8x smaller than End coordinates
        double endX = from.getX() * COORDINATE_SCALE;
        double endZ = from.getZ() * COORDINATE_SCALE;
        double endY = Math.max(40, Math.min(100, from.getY()));

        Location target = new Location(end, endX, endY, endZ);
        event.setTo(findSafeLocation(target));

        plugin.getLogger().info("Portal travel: Nether (" + formatCoords(from) +
                ") -> End (" + formatCoords(event.getTo()) + ")");
    }

    private Location findSafeLocation(Location target) {
        World world = target.getWorld();
        int x = target.getBlockX();
        int z = target.getBlockZ();

        // Search for safe landing spot
        for (int y = Math.min(120, target.getBlockY() + 10); y >= 10; y--) {
            Location check = new Location(world, x, y, z);
            if (isSafeSpot(check)) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }

        // Create emergency platform
        return createPlatform(world, x, z);
    }

    private boolean isSafeSpot(Location loc) {
        return loc.getBlock().getType().isSolid() &&
                loc.clone().add(0, 1, 0).getBlock().getType().isAir() &&
                loc.clone().add(0, 2, 0).getBlock().getType().isAir();
    }

    private Location createPlatform(World world, int x, int z) {
        int y = world.getEnvironment() == World.Environment.NETHER ? 64 : 70;
        Material platform = world.getEnvironment() == World.Environment.NETHER ?
                Material.OBSIDIAN : Material.END_STONE;

        // Create 3x3 platform
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                new Location(world, x + dx, y, z + dz).getBlock().setType(platform);
                // Clear air above
                for (int dy = 1; dy <= 3; dy++) {
                    new Location(world, x + dx, y + dy, z + dz).getBlock().setType(Material.AIR);
                }
            }
        }

        plugin.getLogger().info("Created emergency platform at " + x + ", " + y + ", " + z);
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    private boolean isLightingTool(ItemStack item) {
        return item != null && (item.getType() == Material.FLINT_AND_STEEL ||
                item.getType() == Material.FIRE_CHARGE);
    }

    private void consumeItem(ItemStack item, Player player) {
        if (item.getType() == Material.FIRE_CHARGE) {
            item.setAmount(item.getAmount() - 1);
        } else if (item.getType() == Material.FLINT_AND_STEEL) {
            item.setDurability((short) (item.getDurability() + 1));
            if (item.getDurability() >= item.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }
        }
    }

    private void spawnLightingEffect(Location location) {
        World world = location.getWorld();
        for (int i = 0; i < 25; i++) {
            Location particle = location.clone().add(
                    (Math.random() - 0.5) * 6,
                    (Math.random() - 0.5) * 4,
                    (Math.random() - 0.5) * 6);
            world.spawnParticle(Particle.FLAME, particle, 1);
            world.spawnParticle(Particle.PORTAL, particle, 3);
        }
    }

    private boolean isOnCooldown(Player player) {
        String key = player.getUniqueId().toString();
        Long last = cooldowns.get(key);
        return last != null && (System.currentTimeMillis() - last) < PORTAL_COOLDOWN;
    }

    private void addCooldown(Player player) {
        cooldowns.put(player.getUniqueId().toString(), System.currentTimeMillis());
    }

    private String formatCoords(Location loc) {
        return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    // Public API methods
    public Location netherToEndCoords(Location netherLocation) {
        World end = worldManager.getEndWorld();
        return new Location(end,
                netherLocation.getX() * COORDINATE_SCALE,
                Math.max(40, Math.min(100, netherLocation.getY())),
                netherLocation.getZ() * COORDINATE_SCALE);
    }

    public Location endToNetherCoords(Location endLocation) {
        World nether = worldManager.getNetherWorld();
        return new Location(nether,
                endLocation.getX() / COORDINATE_SCALE,
                Math.max(1, Math.min(128, endLocation.getY())),
                endLocation.getZ() / COORDINATE_SCALE);
    }

    public double getCoordinateScale() {
        return COORDINATE_SCALE;
    }

    /**
     * Helper class to represent a portal frame structure
     */
    private static class PortalFrame {
        final Location corner;
        final int width;
        final int height;
        final boolean isXAxis;

        PortalFrame(Location corner, int width, int height, boolean isXAxis) {
            this.corner = corner.clone();
            this.width = width;
            this.height = height;
            this.isXAxis = isXAxis;
        }

        Location getCenter() {
            double centerW = (width - 1) / 2.0;
            double centerH = (height - 1) / 2.0;

            return isXAxis ?
                    corner.clone().add(centerW, centerH, 0) :
                    corner.clone().add(0, centerH, centerW);
        }
    }
}