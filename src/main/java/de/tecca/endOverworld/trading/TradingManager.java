package de.tecca.endOverworld.trading;

import de.tecca.endOverworld.EndOverworld;
import de.tecca.endOverworld.entities.TradingEnderman;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages all trading operations and trader instances
 */
public class TradingManager {

    private final EndOverworld plugin;
    private final Map<String, TradingEnderman> traders;
    private final FileConfiguration config;

    public TradingManager(EndOverworld plugin) {
        this.plugin = plugin;
        this.traders = new HashMap<>();
        this.config = plugin.getTraderConfig();
    }

    /**
     * Creates a new Trading Enderman
     */
    public TradingEnderman createTradingEnderman(Enderman enderman) {
        TradingEnderman trader = new TradingEnderman(plugin, enderman);
        traders.put(trader.getTraderID(), trader);
        return trader;
    }

    /**
     * Gets a Trading Enderman by ID
     */
    public TradingEnderman getTradingEnderman(String traderID) {
        return traders.get(traderID);
    }

    /**
     * Gets a Trading Enderman by Enderman entity
     */
    public TradingEnderman getTradingEnderman(Enderman enderman) {
        String traderID = TradingEnderman.getTradingEndermanID(enderman);
        return traderID != null ? traders.get(traderID) : null;
    }

    /**
     * Removes a trader (when they die or are removed)
     */
    public void removeTrader(String traderID) {
        traders.remove(traderID);
    }

    /**
     * Removes a trader by Enderman entity
     */
    public void removeTrader(Enderman enderman) {
        String traderID = TradingEnderman.getTradingEndermanID(enderman);
        if (traderID != null) {
            removeTrader(traderID);
        }
    }

    /**
     * Processes a trade click from a player
     */
    public boolean processTrade(Player player, ItemStack tradeItem, String traderID) {
        TradingEnderman trader = getTradingEnderman(traderID);
        if (trader != null) {
            return trader.processTrade(player, tradeItem);
        }
        return false;
    }

    /**
     * Opens trading menu for a player
     */
    public void openTradingMenu(Player player, Enderman enderman) {
        TradingEnderman trader = getTradingEnderman(enderman);
        if (trader != null) {
            trader.openTradingMenu(player);
        }
    }

    /**
     * Handles trader being attacked
     */
    public void handleTraderAttacked(Enderman enderman, Player attacker) {
        TradingEnderman trader = getTradingEnderman(enderman);
        if (trader != null) {
            trader.defendAgainstAttacker(attacker);
        }
    }

    /**
     * Handles trader death
     */
    public void handleTraderDeath(Enderman enderman) {
        TradingEnderman trader = getTradingEnderman(enderman);
        if (trader != null) {
            int dropCount = config.getInt("trader_settings.death_drops", 3);
            trader.dropRandomShopItems(dropCount);
            removeTrader(trader.getTraderID());
        }
    }

    /**
     * Gets the spawn chance for Trading Endermen
     */
    public double getSpawnChance() {
        return config.getDouble("trader_settings.spawn_chance", 0.03);
    }

    /**
     * Gets all active traders
     */
    public Map<String, TradingEnderman> getAllTraders() {
        return new HashMap<>(traders);
    }

    /**
     * Gets the number of active traders
     */
    public int getActiveTraderCount() {
        return traders.size();
    }

    /**
     * Creates a test TradeOffer for validation
     */
    public TradeOffer createTestOffer(String inputMaterial, int inputAmount,
                                      String outputMaterial, int outputAmount,
                                      String displayName, String rarity, int level) {
        try {
            return new TradeOffer(
                    org.bukkit.Material.valueOf(inputMaterial.toUpperCase()),
                    inputAmount,
                    org.bukkit.Material.valueOf(outputMaterial.toUpperCase()),
                    outputAmount,
                    displayName,
                    rarity,
                    level,
                    config
            );
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material in trade offer: " + e.getMessage());
            return null;
        }
    }

    /**
     * Validates all trade offers in config
     */
    public void validateTradeOffers() {
        List<String> tradeStrings = config.getStringList("trade_offers");
        List<String> customTrades = config.getStringList("custom_trades");

        int validOffers = 0;
        int invalidOffers = 0;

        // Validate main offers
        for (String tradeString : tradeStrings) {
            if (validateTradeString(tradeString)) {
                validOffers++;
            } else {
                invalidOffers++;
                plugin.getLogger().warning("Invalid trade offer: " + tradeString);
            }
        }

        // Validate custom offers
        for (String tradeString : customTrades) {
            if (validateTradeString(tradeString)) {
                validOffers++;
            } else {
                invalidOffers++;
                plugin.getLogger().warning("Invalid custom trade: " + tradeString);
            }
        }

        plugin.getLogger().info("Validated " + validOffers + " trade offers (" + invalidOffers + " invalid)");
    }

    private boolean validateTradeString(String tradeString) {
        try {
            String[] parts = tradeString.split(":");
            if (parts.length != 7) return false;

            // Validate materials
            org.bukkit.Material.valueOf(parts[0].toUpperCase());
            org.bukkit.Material.valueOf(parts[2].toUpperCase());

            // Validate numbers
            int inputAmount = Integer.parseInt(parts[1]);
            int outputAmount = Integer.parseInt(parts[3]);
            int level = Integer.parseInt(parts[6]);

            return inputAmount > 0 && outputAmount > 0 && level > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets player's current trader ID from metadata
     */
    public String getPlayerCurrentTraderID(Player player) {
        if (player.hasMetadata("currentTraderID")) {
            return player.getMetadata("currentTraderID").get(0).asString();
        }
        return null;
    }

    /**
     * Clears player's trader metadata
     */
    public void clearPlayerTraderData(Player player) {
        if (player.hasMetadata("currentTraderID")) {
            player.removeMetadata("currentTraderID", plugin);
        }
    }

    /**
     * Cleanup method - removes invalid traders
     */
    public void cleanup() {
        traders.entrySet().removeIf(entry -> {
            TradingEnderman trader = entry.getValue();
            return trader.getEnderman().isDead() || !trader.getEnderman().isValid();
        });
    }

    /**
     * Gets statistics about all traders
     */
    public TradingStats getStatistics() {
        int totalTraders = traders.size();
        int totalTrades = 0;
        int maxLevel = 0;
        int totalOffers = 0;

        for (TradingEnderman trader : traders.values()) {
            TraderData data = trader.getTraderData();
            totalTrades += data.getTradesCompleted();
            maxLevel = Math.max(maxLevel, data.getLevel());
            totalOffers += data.getUnlockedOfferCount();
        }

        return new TradingStats(totalTraders, totalTrades, maxLevel, totalOffers);
    }

    /**
     * Gets traders by level
     */
    public Map<Integer, Integer> getTradersByLevel() {
        Map<Integer, Integer> levelCounts = new HashMap<>();

        for (TradingEnderman trader : traders.values()) {
            int level = trader.getTraderData().getLevel();
            levelCounts.put(level, levelCounts.getOrDefault(level, 0) + 1);
        }

        return levelCounts;
    }

    /**
     * Checks if a material is valid for trading
     */
    public boolean isValidTradeMaterial(String materialName) {
        try {
            org.bukkit.Material.valueOf(materialName.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Gets all valid rarity types from config
     */
    public List<String> getValidRarities() {
        return List.of(
                config.getConfigurationSection("rarity_colors").getKeys(false).toArray(new String[0])
        );
    }

    /**
     * Statistics holder class
     */
    public static class TradingStats {
        private final int totalTraders;
        private final int totalTrades;
        private final int highestLevel;
        private final int totalOffers;

        public TradingStats(int totalTraders, int totalTrades, int highestLevel, int totalOffers) {
            this.totalTraders = totalTraders;
            this.totalTrades = totalTrades;
            this.highestLevel = highestLevel;
            this.totalOffers = totalOffers;
        }

        public int getTotalTraders() { return totalTraders; }
        public int getTotalTrades() { return totalTrades; }
        public int getHighestLevel() { return highestLevel; }
        public int getTotalOffers() { return totalOffers; }

        @Override
        public String toString() {
            return "TradingStats{" +
                    "traders=" + totalTraders +
                    ", trades=" + totalTrades +
                    ", maxLevel=" + highestLevel +
                    ", offers=" + totalOffers +
                    '}';
        }
    }
}