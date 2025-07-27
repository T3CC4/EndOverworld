package de.tecca.endOverworld.managers;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages all custom recipes for the End Overworld
 */
public class RecipeManager {

    private final EndOverworld plugin;
    private final List<NamespacedKey> registeredRecipes;

    public RecipeManager(EndOverworld plugin) {
        this.plugin = plugin;
        this.registeredRecipes = new ArrayList<>();
    }

    /**
     * Registers all custom recipes
     */
    public void registerAllRecipes() {
        registerChorusFlowerRecipe();
        registerChorusPlantRecipe();

        plugin.getLogger().info("Registered " + registeredRecipes.size() + " custom recipes");
    }

    private void registerChorusFlowerRecipe() {
        try {
            NamespacedKey key = new NamespacedKey(plugin, "chorus_flower_to_cherry_plank");
            ShapedRecipe recipe = new ShapedRecipe(key, new ItemStack(Material.CHERRY_PLANKS, 1));
            recipe.shape("CC", "CC");
            recipe.setIngredient('C', Material.CHORUS_FLOWER);

            if (Bukkit.addRecipe(recipe)) {
                registeredRecipes.add(key);
                plugin.getLogger().info("Added recipe: 4 Chorus Flowers -> Cherry Planks");
            } else {
                plugin.getLogger().warning("Failed to add chorus flower recipe");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error registering chorus flower recipe: " + e.getMessage());
        }
    }

    private void registerChorusPlantRecipe() {
        try {
            NamespacedKey key = new NamespacedKey(plugin, "chorus_plant_to_cherry_plank");
            ShapedRecipe recipe = new ShapedRecipe(key, new ItemStack(Material.CHERRY_PLANKS, 1));
            recipe.shape("CC", "CC");
            recipe.setIngredient('C', Material.CHORUS_PLANT);

            if (Bukkit.addRecipe(recipe)) {
                registeredRecipes.add(key);
                plugin.getLogger().info("Added recipe: 4 Chorus Plants -> Cherry Planks");
            } else {
                plugin.getLogger().warning("Failed to add chorus plant recipe");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error registering chorus plant recipe: " + e.getMessage());
        }
    }

    /**
     * Removes all registered recipes (for plugin disable)
     */
    public void unregisterAllRecipes() {
        for (NamespacedKey key : registeredRecipes) {
            try {
                Bukkit.removeRecipe(key);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to remove recipe " + key + ": " + e.getMessage());
            }
        }
        registeredRecipes.clear();
        plugin.getLogger().info("Unregistered all custom recipes");
    }

    /**
     * Checks if a recipe is registered by this plugin
     */
    public boolean isCustomRecipe(NamespacedKey key) {
        return registeredRecipes.contains(key);
    }

    /**
     * Gets all registered recipe keys
     */
    public List<NamespacedKey> getRegisteredRecipes() {
        return new ArrayList<>(registeredRecipes);
    }

    /**
     * Gets count of registered recipes
     */
    public int getRecipeCount() {
        return registeredRecipes.size();
    }

    /**
     * Adds a custom recipe dynamically
     */
    public boolean addCustomRecipe(String name, Material output, int outputAmount, String pattern, char ingredient, Material ingredientMaterial) {
        try {
            NamespacedKey key = new NamespacedKey(plugin, name);
            ShapedRecipe recipe = new ShapedRecipe(key, new ItemStack(output, outputAmount));
            recipe.shape(pattern);
            recipe.setIngredient(ingredient, ingredientMaterial);

            if (Bukkit.addRecipe(recipe)) {
                registeredRecipes.add(key);
                plugin.getLogger().info("Added custom recipe: " + name);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to add custom recipe " + name + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Removes a specific recipe
     */
    public boolean removeRecipe(String name) {
        NamespacedKey key = new NamespacedKey(plugin, name);
        if (Bukkit.removeRecipe(key)) {
            registeredRecipes.remove(key);
            plugin.getLogger().info("Removed recipe: " + name);
            return true;
        }
        return false;
    }

    /**
     * Gets recipe information for debugging
     */
    public String getRecipeInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Recipe Information ===\n");
        info.append("Total Recipes: ").append(registeredRecipes.size()).append("\n");

        for (NamespacedKey key : registeredRecipes) {
            info.append("- ").append(key.getKey()).append("\n");
        }

        return info.toString();
    }
}