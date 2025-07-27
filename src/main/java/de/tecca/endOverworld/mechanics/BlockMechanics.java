package de.tecca.endOverworld.mechanics;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Handles block-related mechanics like End Stone breaking and crafting substitution
 */
public class BlockMechanics implements Listener {

    private final EndOverworld plugin;

    public BlockMechanics(EndOverworld plugin) {
        this.plugin = plugin;
    }

    /**
     * Makes End Stone breakable by hand and ensures it drops itself
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.END_STONE) return;

        Player player = event.getPlayer();

        // Skip drop logic for creative mode players
        if (player.getGameMode() == GameMode.CREATIVE) return;

        // Ensure End Stone always drops itself (even when broken by hand)
        event.setDropItems(true);

        // Add extra drop to guarantee it drops
        event.getBlock().getWorld().dropItemNaturally(
                event.getBlock().getLocation(),
                new ItemStack(Material.END_STONE, 1)
        );
    }

    /**
     * Allows End Stone to substitute for Cobblestone in all recipes
     */
    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        // Skip recipe substitution for creative mode players
        if (isCreativePlayer(event)) return;

        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        // Check if recipe uses End Stone but no Cobblestone
        boolean hasEndStone = false;
        boolean hasCobblestone = false;

        for (ItemStack item : matrix) {
            if (item != null) {
                if (item.getType() == Material.END_STONE) {
                    hasEndStone = true;
                } else if (item.getType() == Material.COBBLESTONE) {
                    hasCobblestone = true;
                }
            }
        }

        // If we have End Stone but no Cobblestone, try substitution
        if (hasEndStone && !hasCobblestone) {
            processEndStoneSubstitution(event, matrix);
        }
    }

    /**
     * Checks if the crafting event involves a creative mode player
     */
    private boolean isCreativePlayer(PrepareItemCraftEvent event) {
        return event.getViewers().stream()
                .filter(viewer -> viewer instanceof Player)
                .map(viewer -> (Player) viewer)
                .anyMatch(player -> player.getGameMode() == GameMode.CREATIVE);
    }

    private void processEndStoneSubstitution(PrepareItemCraftEvent event, ItemStack[] originalMatrix) {
        // Create modified matrix with End Stone replaced by Cobblestone
        ItemStack[] modifiedMatrix = new ItemStack[originalMatrix.length];

        for (int i = 0; i < originalMatrix.length; i++) {
            if (originalMatrix[i] != null && originalMatrix[i].getType() == Material.END_STONE) {
                modifiedMatrix[i] = new ItemStack(Material.COBBLESTONE, originalMatrix[i].getAmount());
            } else {
                modifiedMatrix[i] = originalMatrix[i];
            }
        }

        // Try to find a recipe with the modified matrix
        try {
            Recipe recipe = Bukkit.getCraftingRecipe(modifiedMatrix, event.getInventory().getLocation().getWorld());
            if (recipe != null) {
                event.getInventory().setResult(recipe.getResult());
                plugin.getLogger().fine("End Stone substitution successful for recipe: " + recipe.getResult().getType());
            }
        } catch (Exception e) {
            // Recipe lookup failed, which is normal for non-matching patterns
            plugin.getLogger().finest("Recipe lookup failed for End Stone substitution: " + e.getMessage());
        }
    }

    /**
     * Checks if End Stone can be used as a substitute for a given material
     */
    public boolean canSubstitute(Material original, Material substitute) {
        return original == Material.COBBLESTONE && substitute == Material.END_STONE;
    }

    /**
     * Gets the substitute material for a given material
     */
    public Material getSubstitute(Material original) {
        if (original == Material.COBBLESTONE) {
            return Material.END_STONE;
        }
        return original;
    }

    /**
     * Checks if a material is a valid substitute
     */
    public boolean isValidSubstitute(Material material) {
        return material == Material.END_STONE;
    }

    /**
     * Gets all materials that can be substituted
     */
    public Material[] getSubstitutableMaterials() {
        return new Material[] { Material.COBBLESTONE };
    }

    /**
     * Validates End Stone breaking mechanics
     */
    public boolean validateEndStoneBreaking() {
        // This could be expanded to test End Stone breaking mechanics
        return Material.END_STONE.isBlock() && Material.END_STONE.isSolid();
    }

    /**
     * Checks if creative mode mechanics should be bypassed for a player
     */
    public boolean shouldBypassCreativeMechanics(Player player) {
        return player.getGameMode() == GameMode.CREATIVE;
    }

    /**
     * Gets information about block mechanics for debugging
     */
    public String getMechanicsInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Block Mechanics Information ===\n");
        info.append("End Stone Hand-Breakable: true (survival only)\n");
        info.append("End Stone Drops Self: true (survival only)\n");
        info.append("Cobblestone Substitution: enabled (survival only)\n");
        info.append("Creative Mode Bypass: enabled\n");
        info.append("Substitutable Materials: ");

        for (Material material : getSubstitutableMaterials()) {
            info.append(material.name()).append(" ");
        }
        info.append("\n");

        return info.toString();
    }

    /**
     * Tests recipe substitution for a specific recipe
     */
    public boolean testRecipeSubstitution(ItemStack[] originalRecipe) {
        if (originalRecipe == null) return false;

        boolean hasEndStone = false;
        boolean hasCobblestone = false;

        for (ItemStack item : originalRecipe) {
            if (item != null) {
                if (item.getType() == Material.END_STONE) hasEndStone = true;
                if (item.getType() == Material.COBBLESTONE) hasCobblestone = true;
            }
        }

        return hasEndStone && !hasCobblestone;
    }
}