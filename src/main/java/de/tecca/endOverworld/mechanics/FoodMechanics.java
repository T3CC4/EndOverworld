package de.tecca.endOverworld.mechanics;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles food-related mechanics, particularly Chorus Fruit enhancements
 */
public class FoodMechanics implements Listener {

    private final EndOverworld plugin;

    // Enhanced chorus fruit values
    private static final int BONUS_HUNGER = 2;  // Additional hunger points
    private static final float BONUS_SATURATION = 1.5f;  // Additional saturation

    public FoodMechanics(EndOverworld plugin) {
        this.plugin = plugin;
    }

    /**
     * Enhances Chorus Fruit nutrition when consumed
     */
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        // Check if player just consumed chorus fruit
        if (isConsumingChorusFruit(player)) {
            enhanceChorusFruit(player);
        }
    }

    private boolean isConsumingChorusFruit(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        return (mainHand.getType() == Material.CHORUS_FRUIT) ||
                (offHand.getType() == Material.CHORUS_FRUIT);
    }

    private void enhanceChorusFruit(Player player) {
        // Schedule enhancement for next tick (after vanilla consumption)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyChorusFruitBonuses(player);
        }, 1L);
    }

    private void applyChorusFruitBonuses(Player player) {
        // Add bonus hunger
        int currentFood = player.getFoodLevel();
        int newFood = Math.min(20, currentFood + BONUS_HUNGER);
        player.setFoodLevel(newFood);

        // Add bonus saturation
        float currentSaturation = player.getSaturation();
        float newSaturation = Math.min(20.0f, currentSaturation + BONUS_SATURATION);
        player.setSaturation(newSaturation);

        // Optional: Add subtle particle effect
        spawnChorusFruitParticles(player);

        plugin.getLogger().fine("Enhanced chorus fruit consumption for " + player.getName() +
                " - Hunger: +" + BONUS_HUNGER + ", Saturation: +" + BONUS_SATURATION);
    }

    private void spawnChorusFruitParticles(Player player) {
        try {
            // Spawn subtle end rod particles to indicate enhancement
            player.getWorld().spawnParticle(
                    org.bukkit.Particle.END_ROD,
                    player.getLocation().add(0, 1, 0),
                    3,
                    0.3, 0.3, 0.3,
                    0.01
            );
        } catch (Exception e) {
            // Particle spawning failed, but that's not critical
            plugin.getLogger().finest("Failed to spawn chorus fruit particles: " + e.getMessage());
        }
    }

    /**
     * Gets the enhanced nutrition values for Chorus Fruit
     */
    public int getTotalHunger() {
        return 4 + BONUS_HUNGER; // Vanilla (4) + Bonus (2) = 6 total
    }

    public float getTotalSaturation() {
        return 2.4f + BONUS_SATURATION; // Vanilla (2.4) + Bonus (1.5) = 3.9 total
    }

    /**
     * Gets bonus nutrition values
     */
    public int getBonusHunger() {
        return BONUS_HUNGER;
    }

    public float getBonusSaturation() {
        return BONUS_SATURATION;
    }

    /**
     * Checks if a food item is enhanced by this plugin
     */
    public boolean isEnhancedFood(Material material) {
        return material == Material.CHORUS_FRUIT;
    }

    /**
     * Gets the enhancement factor for a food item
     */
    public double getFoodEnhancementFactor(Material material) {
        if (material == Material.CHORUS_FRUIT) {
            return (double) getTotalHunger() / 4.0; // 6/4 = 1.5x enhancement
        }
        return 1.0; // No enhancement
    }

    /**
     * Manually applies chorus fruit bonuses (for testing or special cases)
     */
    public void applyChorusFruitBonus(Player player) {
        applyChorusFruitBonuses(player);
        player.sendMessage("§dYou feel nourished by the mystical chorus fruit!");
    }

    /**
     * Gets information about food mechanics for debugging
     */
    public String getFoodMechanicsInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Food Mechanics Information ===\n");
        info.append("Enhanced Foods: Chorus Fruit\n");
        info.append("Chorus Fruit Nutrition:\n");
        info.append("  - Total Hunger: ").append(getTotalHunger()).append(" (vanilla: 4)\n");
        info.append("  - Total Saturation: ").append(getTotalSaturation()).append(" (vanilla: 2.4)\n");
        info.append("  - Bonus Hunger: +").append(getBonusHunger()).append("\n");
        info.append("  - Bonus Saturation: +").append(getBonusSaturation()).append("\n");
        info.append("  - Enhancement Factor: ").append(getFoodEnhancementFactor(Material.CHORUS_FRUIT)).append("x\n");

        return info.toString();
    }

    /**
     * Validates food enhancement is working
     */
    public boolean validateFoodEnhancement() {
        return BONUS_HUNGER > 0 && BONUS_SATURATION > 0 &&
                getTotalHunger() > 4 && getTotalSaturation() > 2.4f;
    }

    /**
     * Gets all enhanced food types
     */
    public Material[] getEnhancedFoods() {
        return new Material[] { Material.CHORUS_FRUIT };
    }

    /**
     * Calculates effective nutrition for a food item
     */
    public int getEffectiveHunger(Material food) {
        switch (food) {
            case CHORUS_FRUIT:
                return getTotalHunger();
            default:
                return 0; // Not handled by this system
        }
    }

    public float getEffectiveSaturation(Material food) {
        switch (food) {
            case CHORUS_FRUIT:
                return getTotalSaturation();
            default:
                return 0.0f; // Not handled by this system
        }
    }
}