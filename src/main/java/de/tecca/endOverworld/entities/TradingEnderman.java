package de.tecca.endOverworld.entities;

import de.tecca.endOverworld.EndOverworld;
import de.tecca.endOverworld.trading.TraderData;
import de.tecca.endOverworld.trading.TradeOffer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class TradingEnderman {

    private final EndOverworld plugin;
    private final Enderman enderman;
    private final String traderID;
    private final TraderData traderData;

    public TradingEnderman(EndOverworld plugin, Enderman enderman) {
        this.plugin = plugin;
        this.enderman = enderman;
        this.traderID = "trader_" + System.currentTimeMillis() + "_" + Math.random();
        this.traderData = new TraderData(traderID, plugin.getTraderConfig());

        setupEnderman();
        startParticleEffects();
    }

    private void setupEnderman() {
        enderman.setCustomName("§5Trader");
        enderman.setCustomNameVisible(false);
        enderman.setAI(true);

        // Store trader ID and data in enderman metadata
        enderman.setMetadata("traderID", new FixedMetadataValue(plugin, traderID));
        enderman.setMetadata("isTradingEnderman", new FixedMetadataValue(plugin, true));

        plugin.getLogger().info("Created Trading Enderman with ID: " + traderID);
    }

    private void startParticleEffects() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (enderman.isDead() || !enderman.isValid()) {
                return;
            }

            spawnParticles();
        }, 0L, 10L); // Every 0.5 seconds
    }

    private void spawnParticles() {
        Location center = enderman.getLocation();
        World world = center.getWorld();
        Random random = new Random();

        // Spawn particles around the enderman
        for (int i = 0; i < 8; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 1.5;
            double offsetY = random.nextDouble() * 2.5;
            double offsetZ = (random.nextDouble() - 0.5) * 1.5;

            Location particleLoc = center.clone().add(offsetX, offsetY, offsetZ);

            if (i % 2 == 0) {
                world.spawnParticle(Particle.ENCHANT,
                        particleLoc, 1, 0.1, 0.1, 0.1, 0.01);
            } else {
                world.spawnParticle(Particle.END_ROD,
                        particleLoc, 1, 0.05, 0.05, 0.05, 0.005);
            }
        }
    }

    public void openTradingMenu(Player player) {
        org.bukkit.inventory.Inventory tradeMenu = Bukkit.createInventory(null, 54,
                "§5Trading Enderman - Level " + traderData.getLevel());

        List<TradeOffer> availableOffers = traderData.getAvailableOffers();

        // Place available trades
        for (int i = 0; i < availableOffers.size() && i < 45; i++) {
            TradeOffer offer = availableOffers.get(i);
            ItemStack tradeItem = createTradeItem(offer);
            tradeMenu.setItem(i + 9, tradeItem);
        }

        // Info item
        ItemStack info = createInfoItem();
        tradeMenu.setItem(4, info);

        // Store trader ID in player metadata for trade processing
        player.setMetadata("currentTraderID", new FixedMetadataValue(plugin, traderID));

        player.openInventory(tradeMenu);
        player.sendMessage("§5The Trading Enderman shows you their wares (Level " + traderData.getLevel() + ")...");
    }

    private ItemStack createTradeItem(TradeOffer offer) {
        ItemStack tradeItem = new ItemStack(offer.getOutputMaterial());
        org.bukkit.inventory.meta.ItemMeta meta = tradeItem.getItemMeta();
        meta.setDisplayName(offer.getDisplayName());
        meta.setLore(Arrays.asList(
                "§7Cost: §e" + offer.getInputAmount() + " " + formatMaterialName(offer.getInputMaterial()),
                "§7Gives: §a" + offer.getOutputAmount() + " " + formatMaterialName(offer.getOutputMaterial()),
                "§7Rarity: " + offer.getRarityColor() + offer.getRarity(),
                "§6Click to trade!"
        ));
        tradeItem.setItemMeta(meta);
        return tradeItem;
    }

    private ItemStack createInfoItem() {
        ItemStack info = new ItemStack(Material.BOOK);
        org.bukkit.inventory.meta.ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§5Trader Level " + traderData.getLevel());
        infoMeta.setLore(Arrays.asList(
                "§7This trader has " + traderData.getAvailableOffers().size() + " items available",
                "§7Complete trades to unlock more items!",
                "§7Trades completed: " + traderData.getTradesCompleted(),
                "§7Next level: " + getNextLevelProgress()
        ));
        info.setItemMeta(infoMeta);
        return info;
    }

    private String getNextLevelProgress() {
        if (traderData.getLevel() >= traderData.getMaxLevel()) {
            return "§6MAX LEVEL";
        }

        int required = traderData.getTradesRequiredForNextLevel();
        int current = traderData.getTradesCompleted();
        int needed = required - current;

        return "§e" + needed + " more trades needed";
    }

    private String formatMaterialName(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }

    public boolean processTrade(Player player, ItemStack tradeItem) {
        org.bukkit.inventory.meta.ItemMeta meta = tradeItem.getItemMeta();
        if (meta == null || meta.getLore() == null) return false;

        try {
            String costLine = meta.getLore().get(0);
            String[] costParts = costLine.replace("§7Cost: §e", "").split(" ");
            int requiredAmount = Integer.parseInt(costParts[0]);

            if (player.getInventory().contains(Material.CHORUS_FRUIT, requiredAmount)) {
                // Remove payment
                player.getInventory().removeItem(new ItemStack(Material.CHORUS_FRUIT, requiredAmount));

                // Give reward
                String givesLine = meta.getLore().get(1);
                String[] givesParts = givesLine.replace("§7Gives: §a", "").split(" ");
                int giveAmount = Integer.parseInt(givesParts[0]);

                ItemStack reward = new ItemStack(tradeItem.getType(), giveAmount);
                player.getInventory().addItem(reward);

                // Update trader progress
                traderData.completeTrade();

                player.sendMessage("§aTrade successful! Trader experience increased.");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

                // Check for level up
                checkLevelUp(player);

                return true;
            } else {
                player.sendMessage("§cYou need " + requiredAmount + " Chorus Fruit!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return false;
            }
        } catch (Exception e) {
            player.sendMessage("§cTrade failed! Please try again.");
            return false;
        }
    }

    private void checkLevelUp(Player player) {
        if (traderData.shouldLevelUp()) {
            traderData.levelUp();
            player.sendMessage("§6The Trading Enderman has leveled up! New items available!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            // Add level up particle effects
            for (int i = 0; i < 20; i++) {
                Location particleLoc = enderman.getLocation().add(
                        (Math.random() - 0.5) * 2,
                        Math.random() * 3,
                        (Math.random() - 0.5) * 2
                );
                enderman.getWorld().spawnParticle(Particle.FIREWORK, particleLoc, 1);
            }

            // Refresh the menu after a short delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                openTradingMenu(player);
            }, 5L);
        }
    }

    public void defendAgainstAttacker(Player attacker) {
        // Apply debuff effects
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 1));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 300, 1));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 400, 1));

        attacker.sendMessage("§cThe Trading Enderman is displeased with your aggression!");
        enderman.getWorld().playSound(enderman.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 2.0f, 0.5f);

        // Spawn angry particles
        for (int i = 0; i < 15; i++) {
            Location particleLoc = enderman.getLocation().add(
                    (Math.random() - 0.5) * 2,
                    Math.random() * 3,
                    (Math.random() - 0.5) * 2
            );
            enderman.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, particleLoc, 1);
        }
    }

    public void dropRandomShopItems(int itemCount) {
        List<TradeOffer> availableOffers = traderData.getAvailableOffers();
        Location dropLocation = enderman.getLocation();

        for (int i = 0; i < itemCount && !availableOffers.isEmpty(); i++) {
            TradeOffer randomOffer = availableOffers.get((int)(Math.random() * availableOffers.size()));
            ItemStack dropItem = new ItemStack(randomOffer.getOutputMaterial(), randomOffer.getOutputAmount());
            dropLocation.getWorld().dropItemNaturally(dropLocation, dropItem);
        }

        dropLocation.getWorld().playSound(dropLocation, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        plugin.getLogger().info("Trading Enderman " + traderID + " dropped " + itemCount + " random items");
    }

    // Getters
    public String getTraderID() { return traderID; }
    public TraderData getTraderData() { return traderData; }
    public Enderman getEnderman() { return enderman; }

    // Static helper methods
    public static boolean isTradingEnderman(Enderman enderman) {
        return enderman.hasMetadata("isTradingEnderman") &&
                enderman.getMetadata("isTradingEnderman").get(0).asBoolean();
    }

    public static String getTradingEndermanID(Enderman enderman) {
        if (enderman.hasMetadata("traderID")) {
            return enderman.getMetadata("traderID").get(0).asString();
        }
        return null;
    }
}