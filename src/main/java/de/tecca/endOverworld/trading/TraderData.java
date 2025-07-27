package de.tecca.endOverworld.trading;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public class TraderData {

    private final String id;
    private int level;
    private int tradesCompleted;
    private final int maxLevel;
    private final int tradesPerLevel;
    private final List<TradeOffer> allOffers;
    private final List<TradeOffer> unlockedOffers;

    public TraderData(String id, FileConfiguration config) {
        this.id = id;
        this.level = 1;
        this.tradesCompleted = 0;
        this.maxLevel = config.getInt("trader_settings.max_level", 5);
        this.tradesPerLevel = config.getInt("trader_settings.trades_per_level", 3);
        this.allOffers = loadOffersFromConfig(config);
        this.unlockedOffers = new ArrayList<>();
        updateAvailableOffers();
    }

    private List<TradeOffer> loadOffersFromConfig(FileConfiguration config) {
        List<TradeOffer> offers = new ArrayList<>();

        // Load main trade offers
        List<String> tradeStrings = config.getStringList("trade_offers");
        for (String tradeString : tradeStrings) {
            TradeOffer offer = parseTradeString(tradeString, config);
            if (offer != null) {
                offers.add(offer);
            }
        }

        // Load custom trade offers
        List<String> customTradeStrings = config.getStringList("custom_trades");
        for (String tradeString : customTradeStrings) {
            TradeOffer offer = parseTradeString(tradeString, config);
            if (offer != null) {
                offers.add(offer);
            }
        }

        return offers;
    }

    private TradeOffer parseTradeString(String tradeString, FileConfiguration config) {
        try {
            // Format: input_material:input_amount:output_material:output_amount:display_name:rarity:required_level
            String[] parts = tradeString.split(":");
            if (parts.length != 7) {
                return null;
            }

            Material inputMaterial = Material.valueOf(parts[0].toUpperCase());
            int inputAmount = Integer.parseInt(parts[1]);
            Material outputMaterial = Material.valueOf(parts[2].toUpperCase());
            int outputAmount = Integer.parseInt(parts[3]);
            String displayName = parts[4];
            String rarity = parts[5];
            int requiredLevel = Integer.parseInt(parts[6]);

            return new TradeOffer(inputMaterial, inputAmount, outputMaterial, outputAmount,
                    displayName, rarity, requiredLevel, config);

        } catch (Exception e) {
            // Invalid trade string, skip it
            return null;
        }
    }

    private void updateAvailableOffers() {
        unlockedOffers.clear();
        for (TradeOffer offer : allOffers) {
            if (offer.getRequiredLevel() <= level) {
                unlockedOffers.add(offer);
            }
        }
    }

    public void completeTrade() {
        tradesCompleted++;
    }

    public boolean shouldLevelUp() {
        if (level >= maxLevel) {
            return false;
        }

        int requiredTrades = getTradesRequiredForNextLevel();
        return tradesCompleted >= requiredTrades;
    }

    public void levelUp() {
        if (level < maxLevel) {
            level++;
            updateAvailableOffers();
        }
    }

    public int getTradesRequiredForNextLevel() {
        return level * tradesPerLevel;
    }

    // Getters
    public String getId() { return id; }
    public int getLevel() { return level; }
    public int getMaxLevel() { return maxLevel; }
    public int getTradesCompleted() { return tradesCompleted; }
    public List<TradeOffer> getAvailableOffers() { return new ArrayList<>(unlockedOffers); }
    public List<TradeOffer> getAllOffers() { return new ArrayList<>(allOffers); }

    // Setters (for loading saved data)
    public void setLevel(int level) {
        this.level = Math.min(level, maxLevel);
        updateAvailableOffers();
    }

    public void setTradesCompleted(int tradesCompleted) {
        this.tradesCompleted = tradesCompleted;
    }

    // Utility methods
    public boolean hasOffer(Material outputMaterial) {
        return unlockedOffers.stream().anyMatch(offer -> offer.getOutputMaterial() == outputMaterial);
    }

    public TradeOffer getOffer(Material outputMaterial) {
        return unlockedOffers.stream()
                .filter(offer -> offer.getOutputMaterial() == outputMaterial)
                .findFirst()
                .orElse(null);
    }

    public List<TradeOffer> getOffersByRarity(String rarity) {
        return unlockedOffers.stream()
                .filter(offer -> offer.getRarity().equalsIgnoreCase(rarity))
                .collect(java.util.stream.Collectors.toList());
    }

    public int getTotalOfferCount() {
        return allOffers.size();
    }

    public int getUnlockedOfferCount() {
        return unlockedOffers.size();
    }

    @Override
    public String toString() {
        return "TraderData{" +
                "id='" + id + '\'' +
                ", level=" + level +
                ", tradesCompleted=" + tradesCompleted +
                ", unlockedOffers=" + unlockedOffers.size() +
                '}';
    }
}