package de.tecca.endOverworld.managers;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages bed mechanics in the End dimension including healing and spawn point setting
 */
public class BedManager implements Listener {

    private final EndOverworld plugin;
    private final Map<UUID, Integer> healingTasks;

    // Healing configuration
    private static final int HEALING_INTERVAL = 40; // ticks (2 seconds)
    private static final int FOOD_INTERVAL = 60; // ticks (3 seconds)
    private static final int MAX_HEALING_TIME = 200; // ticks (10 seconds)
    private static final double HEALING_AMOUNT = 2.0; // 1 heart per interval
    private static final int FOOD_AMOUNT = 1; // 1 food level per interval

    public BedManager(EndOverworld plugin) {
        this.plugin = plugin;
        this.healingTasks = new HashMap<>();
    }

    /**
     * Handles player entering bed in the End
     */
    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        Location bedLocation = event.getBed().getLocation();

        // Only handle beds in the End dimension
        if (bedLocation.getWorld().getEnvironment() != org.bukkit.World.Environment.THE_END) {
            return;
        }

        // Check if bed has proper ground support
        if (!hasGroundSupport(bedLocation)) {
            player.sendMessage("§cYou cannot sleep here - the bed needs solid ground support within 3 blocks below!");
            event.setCancelled(true);
            return;
        }

        // Set spawn point and start healing
        player.setBedSpawnLocation(bedLocation, true);
        player.sendMessage("§aSpawn point set! You feel rested in this strange realm.");

        startBedHealing(player);
    }

    /**
     * Handles player leaving bed in the End
     */
    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();

        // Only handle beds in the End dimension
        if (player.getLocation().getWorld().getEnvironment() != org.bukkit.World.Environment.THE_END) {
            return;
        }

        stopBedHealing(player);
        completeBedHealing(player);
    }

    private boolean hasGroundSupport(Location bedLocation) {
        // Check for solid ground within 3 blocks below the bed
        for (int y = 1; y <= 3; y++) {
            Location checkLoc = bedLocation.clone().subtract(0, y, 0);
            Material blockBelow = checkLoc.getBlock().getType();

            if (blockBelow.isSolid() && blockBelow != Material.AIR) {
                return true;
            }
        }
        return false;
    }

    private void startBedHealing(Player player) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing healing task
        stopBedHealing(player);

        // Start new healing task
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;

            @Override
            public void run() {
                // Check if player is still in bed and valid
                if (!player.isOnline() || !player.isSleeping()) {
                    stopBedHealing(player);
                    return;
                }

                ticks++;

                // Heal 1 heart every 2 seconds
                if (ticks % HEALING_INTERVAL == 0 && player.getHealth() < player.getMaxHealth()) {
                    double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + HEALING_AMOUNT);
                    player.setHealth(newHealth);

                    // Add regeneration effect
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.REGENERATION, 60, 0, true, false));

                    // Spawn healing particles
                    spawnHealingParticles(player);
                }

                // Restore food every 3 seconds
                if (ticks % FOOD_INTERVAL == 0 && player.getFoodLevel() < 20) {
                    int newFood = Math.min(20, player.getFoodLevel() + FOOD_AMOUNT);
                    player.setFoodLevel(newFood);
                }

                // Stop after maximum healing time
                if (ticks >= MAX_HEALING_TIME) {
                    stopBedHealing(player);
                }
            }
        }, 0L, 1L).getTaskId();

        healingTasks.put(playerId, taskId);
        plugin.getLogger().fine("Started bed healing for " + player.getName());
    }

    private void stopBedHealing(Player player) {
        UUID playerId = player.getUniqueId();
        Integer taskId = healingTasks.remove(playerId);

        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
            plugin.getLogger().fine("Stopped bed healing for " + player.getName());
        }
    }

    private void completeBedHealing(Player player) {
        // Final healing boost when leaving bed
        if (player.getHealth() < player.getMaxHealth()) {
            double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + 4.0);
            player.setHealth(newHealth);
        }

        // Restore full food and saturation
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        // Add temporary well-rested effects
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.REGENERATION, 300, 1, true, false)); // 15 seconds Regen II
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SATURATION, 300, 0, true, false)); // 15 seconds Saturation

        // Spawn completion particles
        spawnCompletionParticles(player);

        player.sendMessage("§aYou feel fully rested and healed! The End's strange energy has restored you.");
        plugin.getLogger().fine("Completed bed healing for " + player.getName());
    }

    private void spawnHealingParticles(Player player) {
        try {
            // Gentle healing particles
            player.getWorld().spawnParticle(
                    org.bukkit.Particle.HEART,
                    player.getLocation().add(0, 0.5, 0),
                    2,
                    0.5, 0.2, 0.5,
                    0.1
            );
        } catch (Exception e) {
            plugin.getLogger().finest("Failed to spawn healing particles: " + e.getMessage());
        }
    }

    private void spawnCompletionParticles(Player player) {
        try {
            // More impressive completion particles
            Location loc = player.getLocation();

            // Hearts
            player.getWorld().spawnParticle(
                    org.bukkit.Particle.HEART,
                    loc.add(0, 1, 0),
                    8,
                    1.0, 0.5, 1.0,
                    0.1
            );

            // End rod sparkles
            player.getWorld().spawnParticle(
                    org.bukkit.Particle.END_ROD,
                    loc,
                    15,
                    1.5, 1.0, 1.5,
                    0.05
            );
        } catch (Exception e) {
            plugin.getLogger().finest("Failed to spawn completion particles: " + e.getMessage());
        }
    }

    /**
     * Manually heals a player as if they used a bed
     */
    public void healPlayer(Player player) {
        completeBedHealing(player);
        player.sendMessage("§aYou have been magically healed by the End's power!");
    }

    /**
     * Checks if a player is currently being healed by a bed
     */
    public boolean isPlayerBeingHealed(Player player) {
        return healingTasks.containsKey(player.getUniqueId());
    }

    /**
     * Forces stop healing for a player
     */
    public void forceStopHealing(Player player) {
        stopBedHealing(player);
    }

    /**
     * Checks if a location has adequate bed support
     */
    public boolean isValidBedLocation(Location location) {
        return location.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END &&
                hasGroundSupport(location);
    }

    /**
     * Gets healing configuration info
     */
    public String getHealingInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Bed Healing Information ===\n");
        info.append("Healing Interval: ").append(HEALING_INTERVAL).append(" ticks (").append(HEALING_INTERVAL/20.0).append("s)\n");
        info.append("Food Interval: ").append(FOOD_INTERVAL).append(" ticks (").append(FOOD_INTERVAL/20.0).append("s)\n");
        info.append("Max Healing Time: ").append(MAX_HEALING_TIME).append(" ticks (").append(MAX_HEALING_TIME/20.0).append("s)\n");
        info.append("Healing per Interval: ").append(HEALING_AMOUNT).append(" health\n");
        info.append("Food per Interval: ").append(FOOD_AMOUNT).append(" hunger\n");
        info.append("Active Healing Players: ").append(healingTasks.size()).append("\n");
        info.append("Ground Support Required: true\n");

        return info.toString();
    }

    /**
     * Gets all players currently being healed
     */
    public java.util.Set<UUID> getHealingPlayers() {
        return new java.util.HashSet<>(healingTasks.keySet());
    }

    /**
     * Cleanup method - removes invalid healing tasks
     */
    public void cleanup() {
        healingTasks.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || !player.isSleeping()) {
                Bukkit.getScheduler().cancelTask(entry.getValue());
                return true;
            }
            return false;
        });
    }

    /**
     * Gets bed healing statistics
     */
    public int getActiveHealingCount() {
        return healingTasks.size();
    }
}