package de.tecca.endOverworld.trading;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

public class TradeOffer {

    private final Material inputMaterial;
    private final int inputAmount;
    private final Material outputMaterial;
    private final int outputAmount;
    private final String displayName;
    private final String rarity;
    private final int requiredLevel;
    private final String rarityColor;

    public TradeOffer(Material inputMaterial, int inputAmount, Material outputMaterial, int outputAmount,
                      String displayName, String rarity, int requiredLevel, FileConfiguration config) {
        this.inputMaterial = inputMaterial;
        this.inputAmount = inputAmount;
        this.outputMaterial = outputMaterial;
        this.outputAmount = outputAmount;
        this.displayName = displayName;
        this.rarity = rarity;
        this.requiredLevel = requiredLevel;
        this.rarityColor = config.getString("rarity_colors." + rarity, "§7");
    }

    // Alternative constructor for direct creation
    public TradeOffer(Material inputMaterial, int inputAmount, Material outputMaterial, int outputAmount,
                      String displayName, String rarity, int requiredLevel) {
        this.inputMaterial = inputMaterial;
        this.inputAmount = inputAmount;
        this.outputMaterial = outputMaterial;
        this.outputAmount = outputAmount;
        this.displayName = displayName;
        this.rarity = rarity;
        this.requiredLevel = requiredLevel;
        this.rarityColor = getDefaultRarityColor(rarity);
    }

    private String getDefaultRarityColor(String rarity) {
        switch (rarity.toLowerCase()) {
            case "common": return "§f";
            case "uncommon": return "§a";
            case "rare": return "§9";
            case "epic": return "§5";
            case "legendary": return "§6";
            default: return "§7";
        }
    }

    // Create ItemStack for input (what player needs to pay)
    public ItemStack createInputItemStack() {
        return new ItemStack(inputMaterial, inputAmount);
    }

    // Create ItemStack for output (what player receives)
    public ItemStack createOutputItemStack() {
        return new ItemStack(outputMaterial, outputAmount);
    }

    // Check if player has enough resources for this trade
    public boolean canAfford(org.bukkit.inventory.PlayerInventory inventory) {
        return inventory.contains(inputMaterial, inputAmount);
    }

    // Execute the trade (remove input, add output)
    public boolean executeTrade(org.bukkit.inventory.PlayerInventory inventory) {
        if (!canAfford(inventory)) {
            return false;
        }

        // Remove input items
        inventory.removeItem(createInputItemStack());

        // Add output items
        inventory.addItem(createOutputItemStack());

        return true;
    }

    // Get formatted cost string for display
    public String getCostString() {
        return inputAmount + " " + formatMaterialName(inputMaterial);
    }

    // Get formatted reward string for display
    public String getRewardString() {
        return outputAmount + " " + formatMaterialName(outputMaterial);
    }

    private String formatMaterialName(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }

    // Check if this is a rare/valuable trade
    public boolean isRare() {
        return rarity.equalsIgnoreCase("rare") ||
                rarity.equalsIgnoreCase("epic") ||
                rarity.equalsIgnoreCase("legendary");
    }

    // Get trade value (for sorting/comparison)
    public int getTradeValue() {
        switch (rarity.toLowerCase()) {
            case "common": return 1;
            case "uncommon": return 2;
            case "rare": return 3;
            case "epic": return 4;
            case "legendary": return 5;
            default: return 0;
        }
    }

    // Check if offer is valid (materials exist)
    public boolean isValid() {
        return inputMaterial != null && outputMaterial != null &&
                inputAmount > 0 && outputAmount > 0 && requiredLevel > 0;
    }

    // Create a copy of this offer with modified amounts
    public TradeOffer withModifiedAmounts(int newInputAmount, int newOutputAmount) {
        return new TradeOffer(inputMaterial, newInputAmount, outputMaterial, newOutputAmount,
                displayName, rarity, requiredLevel);
    }

    // Create a copy of this offer with modified level requirement
    public TradeOffer withRequiredLevel(int newRequiredLevel) {
        return new TradeOffer(inputMaterial, inputAmount, outputMaterial, outputAmount,
                displayName, rarity, newRequiredLevel);
    }

    // Getters
    public Material getInputMaterial() { return inputMaterial; }
    public int getInputAmount() { return inputAmount; }
    public Material getOutputMaterial() { return outputMaterial; }
    public int getOutputAmount() { return outputAmount; }
    public String getDisplayName() { return displayName; }
    public String getRarity() { return rarity; }
    public String getRarityColor() { return rarityColor; }
    public int getRequiredLevel() { return requiredLevel; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        TradeOffer that = (TradeOffer) obj;
        return inputAmount == that.inputAmount &&
                outputAmount == that.outputAmount &&
                requiredLevel == that.requiredLevel &&
                inputMaterial == that.inputMaterial &&
                outputMaterial == that.outputMaterial &&
                rarity.equals(that.rarity);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(inputMaterial, inputAmount, outputMaterial, outputAmount, rarity, requiredLevel);
    }

    @Override
    public String toString() {
        return "TradeOffer{" +
                "input=" + inputAmount + "x" + inputMaterial +
                ", output=" + outputAmount + "x" + outputMaterial +
                ", rarity='" + rarity + '\'' +
                ", level=" + requiredLevel +
                '}';
    }
}