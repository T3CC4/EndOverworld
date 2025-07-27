package de.tecca.endOverworld.mechanics;

import de.tecca.endOverworld.EndOverworld;
import de.tecca.endOverworld.entities.TradingEnderman;
import de.tecca.endOverworld.trading.TradingManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles mob behavior modifications and trading interactions
 */
public class MobBehavior implements Listener {

    private final EndOverworld plugin;
    private final TradingManager tradingManager;

    public MobBehavior(EndOverworld plugin, TradingManager tradingManager) {
        this.plugin = plugin;
        this.tradingManager = tradingManager;
    }

    // === ENDERMAN BEHAVIOR ===

    /**
     * Spawns Trading Endermen with configured chance
     */
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Enderman) {
            Enderman enderman = (Enderman) event.getEntity();

            // Check spawn chance for Trading Enderman
            double spawnChance = tradingManager.getSpawnChance();
            if (Math.random() < spawnChance) {
                tradingManager.createTradingEnderman(enderman);
            }
        }
    }

    /**
     * Removes eye contact aggression from Endermen
     */
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof Enderman)) return;

        // Skip for creative mode players
        if (event.getTarget() instanceof Player) {
            Player player = (Player) event.getTarget();
            if (player.getGameMode() == GameMode.CREATIVE) return;
        }

        // Only allow targeting if directly attacked
        if (event.getReason() != EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles Trading Enderman interactions
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Enderman)) return;

        Enderman enderman = (Enderman) event.getRightClicked();
        Player player = event.getPlayer();

        if (TradingEnderman.isTradingEnderman(enderman)) {
            event.setCancelled(true);
            tradingManager.openTradingMenu(player, enderman);
        }
    }

    /**
     * Handles Trading Enderman defense when attacked
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Enderman && event.getDamager() instanceof Player)) return;

        Enderman enderman = (Enderman) event.getEntity();
        Player player = (Player) event.getDamager();

        // Skip defense mechanics for creative mode players
        if (player.getGameMode() == GameMode.CREATIVE) return;

        if (TradingEnderman.isTradingEnderman(enderman)) {
            tradingManager.handleTraderAttacked(enderman, player);
        }
    }

    /**
     * Handles Trading Enderman death drops
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Enderman)) return;

        Enderman enderman = (Enderman) event.getEntity();

        if (TradingEnderman.isTradingEnderman(enderman)) {
            // Clear default drops to prevent duplication
            event.getDrops().clear();
            event.setDroppedExp(0);

            // Handle through trading manager
            tradingManager.handleTraderDeath(enderman);
        }
    }

    // === VILLAGER PROTECTION ===

    /**
     * Prevents all hostile mobs from targeting villagers
     */
    @EventHandler
    public void onMobTargetVillager(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Villager)) return;

        Entity attacker = event.getEntity();

        // Cancel targeting if it's a hostile mob
        if (isHostileMob(attacker)) {
            event.setCancelled(true);
            plugin.getLogger().finest("Prevented " + attacker.getType() + " from targeting villager");
        }
    }

    private boolean isHostileMob(Entity entity) {
        return entity instanceof Zombie || entity instanceof Skeleton || entity instanceof Creeper ||
                entity instanceof Spider || entity instanceof Witch || entity instanceof Pillager ||
                entity instanceof Vindicator || entity instanceof Evoker || entity instanceof Phantom ||
                entity instanceof Blaze || entity instanceof Ghast || entity instanceof Wither ||
                entity instanceof WitherSkeleton || entity instanceof Husk || entity instanceof Stray ||
                entity instanceof Drowned || entity instanceof Slime || entity instanceof MagmaCube ||
                entity instanceof Endermite || entity instanceof Silverfish || entity instanceof Guardian ||
                entity instanceof ElderGuardian || entity instanceof Shulker || entity instanceof Vex ||
                entity instanceof Ravager || entity instanceof Piglin || entity instanceof PiglinBrute ||
                entity instanceof Hoglin || entity instanceof Zoglin;
    }

    // === END CITY GUARDS ===

    /**
     * Handles chest opening in End cities - makes guards aggressive
     */
    @EventHandler
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK ||
                event.getClickedBlock() == null ||
                event.getClickedBlock().getType() != Material.CHEST) return;

        Player player = event.getPlayer();

        // Skip guard activation for creative mode players
        if (player.getGameMode() == GameMode.CREATIVE) return;

        Location chestLoc = event.getClickedBlock().getLocation();

        if (isInEndCity(chestLoc)) {
            activateEndCityGuards(player, chestLoc);
        }
    }

    private boolean isInEndCity(Location location) {
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    Location checkLoc = location.clone().add(x, y, z);
                    Material block = checkLoc.getBlock().getType();
                    if (block == Material.PURPUR_BLOCK ||
                            block == Material.PURPUR_PILLAR ||
                            block == Material.END_ROD) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void activateEndCityGuards(Player player, Location chestLocation) {
        // Find nearby Shulkers and Endermen to make them aggressive
        for (Entity entity : chestLocation.getWorld().getNearbyEntities(chestLocation, 15, 10, 15)) {
            if (entity instanceof Shulker ||
                    (entity instanceof Enderman && !TradingEnderman.isTradingEnderman((Enderman) entity))) {

                if (entity instanceof Creature) {
                    ((Creature) entity).setTarget(player);
                }
            }
        }

        // Play warning sound
        player.getWorld().playSound(chestLocation, Sound.ENTITY_SHULKER_AMBIENT, 1.0f, 0.8f);
        player.sendMessage("§cThe End city guardians stir as you disturb their treasures...");

        plugin.getLogger().info("Activated End city guards for " + player.getName() + " at " + chestLocation);
    }

    // === TRADING SYSTEM INTEGRATION ===

    /**
     * Handles trading menu clicks
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("§5Trading Enderman")) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null ||
                event.getCurrentItem().getType() == Material.AIR ||
                event.getCurrentItem().getType() == Material.BOOK) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String traderID = tradingManager.getPlayerCurrentTraderID(player);

        if (traderID != null) {
            // Allow trading for all game modes
            tradingManager.processTrade(player, event.getCurrentItem(), traderID);
        }
    }

    // === UTILITY METHODS ===

    /**
     * Checks if player should bypass mob mechanics due to creative mode
     */
    public boolean shouldBypassMobMechanics(Player player) {
        return player.getGameMode() == GameMode.CREATIVE;
    }

    /**
     * Gets statistics about mob behavior
     */
    public String getMobBehaviorInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Mob Behavior Information ===\n");
        info.append("Enderman Eye Contact Aggression: disabled (survival only)\n");
        info.append("Villager Protection: enabled\n");
        info.append("End City Guards: enabled (survival only)\n");
        info.append("Trading Endermen: enabled (all game modes)\n");
        info.append("Creative Mode Bypass: partial (trading allowed)\n");
        info.append("Trading Endermen Spawn Rate: ").append(tradingManager.getSpawnChance() * 100).append("%\n");
        info.append("Active Trading Endermen: ").append(tradingManager.getActiveTraderCount()).append("\n");

        return info.toString();
    }

    /**
     * Checks if villager protection is working
     */
    public boolean isVillagerProtectionActive() {
        return true;
    }

    /**
     * Gets all protected entity types
     */
    public Class<?>[] getProtectedEntityTypes() {
        return new Class<?>[] { Villager.class };
    }

    /**
     * Gets all hostile mob types that are restricted
     */
    public Class<?>[] getHostileMobTypes() {
        return new Class<?>[] {
                Zombie.class, Skeleton.class, Creeper.class, Spider.class,
                Witch.class, Pillager.class, Vindicator.class, Evoker.class,
                Phantom.class, Blaze.class, Ghast.class, Wither.class,
                WitherSkeleton.class, Husk.class, Stray.class, Drowned.class,
                Slime.class, MagmaCube.class, Endermite.class, Silverfish.class,
                Guardian.class, ElderGuardian.class, Shulker.class, Vex.class,
                Ravager.class, Piglin.class, PiglinBrute.class, Hoglin.class, Zoglin.class
        };
    }

    /**
     * Manually activates End city guards at a specific location (for testing)
     */
    public void forceActivateEndCityGuards(Player player, Location location) {
        if (shouldBypassMobMechanics(player)) return;
        activateEndCityGuards(player, location);
    }

    /**
     * Forces cleanup of invalid traders
     */
    public void cleanupInvalidTraders() {
        tradingManager.cleanup();
    }
}