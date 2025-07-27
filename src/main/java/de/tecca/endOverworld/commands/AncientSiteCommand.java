package de.tecca.endOverworld.commands;

import de.tecca.endOverworld.EndOverworld;
import de.tecca.endOverworld.managers.AncientEndManager;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Command handler for Ancient Site teleportation and management
 * Usage: /ancientsite <teleport|list|info|stats> [args]
 */
public class AncientSiteCommand implements CommandExecutor, TabCompleter {

    private final EndOverworld plugin;
    private final AncientEndManager ancientManager;

    public AncientSiteCommand(EndOverworld plugin) {
        this.plugin = plugin;
        this.ancientManager = plugin.getAncientEndManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Basic permission check
        if (!sender.hasPermission("endoverworld.ancientsite")) {
            sender.sendMessage("§c❌ You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "teleport":
            case "tp":
                return handleTeleport(sender, args);

            case "list":
                return handleList(sender, args);

            case "info":
                return handleInfo(sender, args);

            case "stats":
                return handleStats(sender);

            case "nearest":
                return handleNearest(sender);

            case "random":
                return handleRandom(sender);

            case "help":
            default:
                sendHelpMessage(sender);
                return true;
        }
    }

    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c❌ Only players can teleport to Ancient Sites!");
            return true;
        }

        Player player = (Player) sender;

        // Check if player is in the End
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) {
            player.sendMessage("§c❌ You must be in the End dimension to use this command!");
            return true;
        }

        Map<String, Location> ancientSites = ancientManager.getAncientSites();
        if (ancientSites.isEmpty()) {
            player.sendMessage("§e⚠ No Ancient Sites have been discovered yet!");
            player.sendMessage("§7💡 Explore End Cities to discover Ancient Sites naturally.");
            return true;
        }

        Location targetSite = null;

        if (args.length > 1) {
            // Teleport to specific site by index
            try {
                int index = Integer.parseInt(args[1]) - 1; // Convert to 0-based index
                List<Location> sites = new ArrayList<>(ancientSites.values());

                if (index < 0 || index >= sites.size()) {
                    player.sendMessage("§c❌ Invalid site number! Use /ancientsite list to see available sites.");
                    return true;
                }

                targetSite = sites.get(index);
            } catch (NumberFormatException e) {
                player.sendMessage("§c❌ Invalid number! Usage: /ancientsite tp <number>");
                return true;
            }
        } else {
            // Teleport to nearest site
            targetSite = findNearestAncientSite(player.getLocation(), ancientSites);
        }

        if (targetSite == null) {
            player.sendMessage("§c❌ Could not find a suitable Ancient Site!");
            return true;
        }

        // Perform teleportation with effects
        teleportToAncientSite(player, targetSite);
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        Map<String, Location> ancientSites = ancientManager.getAncientSites();

        if (ancientSites.isEmpty()) {
            sender.sendMessage("§e⚠ No Ancient Sites have been discovered yet!");
            return true;
        }

        sender.sendMessage("§6§l=== Ancient Sites ===");
        sender.sendMessage("§7Found §e" + ancientSites.size() + "§7 Ancient Sites:");

        int index = 1;
        for (Map.Entry<String, Location> entry : ancientSites.entrySet()) {
            Location loc = entry.getValue();
            String coords = String.format("§a%.0f§7, §a%.0f§7, §a%.0f",
                    loc.getX(), loc.getY(), loc.getZ());

            sender.sendMessage(String.format("§7%d. §6%s §7at %s",
                    index++, entry.getKey(), coords));
        }

        sender.sendMessage("§7💡 Use §e/ancientsite tp <number>§7 to teleport!");
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c❌ Only players can check Ancient Site info!");
            return true;
        }

        Player player = (Player) sender;
        Location nearestSite = findNearestAncientSite(player.getLocation(),
                ancientManager.getAncientSites());

        if (nearestSite == null) {
            player.sendMessage("§e⚠ No Ancient Sites found nearby!");
            return true;
        }

        double distance = player.getLocation().distance(nearestSite);
        boolean isNear = distance < 15;

        player.sendMessage("§6§l=== Nearest Ancient Site Info ===");
        player.sendMessage("§7Distance: §e" + String.format("%.1f", distance) + " blocks");
        player.sendMessage("§7Location: §a" + String.format("%.0f, %.0f, %.0f",
                nearestSite.getX(), nearestSite.getY(), nearestSite.getZ()));
        player.sendMessage("§7Status: " + (isNear ? "§a⚡ Within site area" : "§7🚶 Outside site area"));

        if (isNear) {
            player.sendMessage("§8§o⚠ An ancient presence watches you...");
        }

        return true;
    }

    private boolean handleStats(CommandSender sender) {
        Map<String, Integer> stats = ancientManager.getStatistics();

        sender.sendMessage("§6§l=== Ancient End Statistics ===");
        sender.sendMessage("§7Ancient Sites: §e" + stats.get("ancient_sites"));
        sender.sendMessage("§7Active Wardens: §c" + stats.get("active_wardens"));
        sender.sendMessage("§7Processed Chunks: §b" + stats.get("processed_chunks"));

        if (sender.hasPermission("endoverworld.admin")) {
            sender.sendMessage("§8" + ancientManager.getDebugInfo().replace("\n", "\n§8"));
        }

        return true;
    }

    private boolean handleNearest(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c❌ Only players can find nearest Ancient Sites!");
            return true;
        }

        Player player = (Player) sender;
        Location nearestSite = findNearestAncientSite(player.getLocation(),
                ancientManager.getAncientSites());

        if (nearestSite == null) {
            player.sendMessage("§e⚠ No Ancient Sites found!");
            return true;
        }

        double distance = player.getLocation().distance(nearestSite);
        player.sendMessage("§6🧭 Nearest Ancient Site:");
        player.sendMessage("§7Distance: §e" + String.format("%.1f", distance) + " blocks");
        player.sendMessage("§7Direction: " + getDirection(player.getLocation(), nearestSite));
        player.sendMessage("§7💡 Use §e/ancientsite tp§7 to teleport there!");

        return true;
    }

    private boolean handleRandom(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c❌ Only players can teleport to Ancient Sites!");
            return true;
        }

        Player player = (Player) sender;
        Map<String, Location> ancientSites = ancientManager.getAncientSites();

        if (ancientSites.isEmpty()) {
            player.sendMessage("§e⚠ No Ancient Sites have been discovered yet!");
            return true;
        }

        // Get random site
        List<Location> sites = new ArrayList<>(ancientSites.values());
        Location randomSite = sites.get((int) (Math.random() * sites.size()));

        teleportToAncientSite(player, randomSite);
        player.sendMessage("§6🎲 Teleported to a random Ancient Site!");

        return true;
    }

    private void teleportToAncientSite(Player player, Location targetSite) {
        // Find safe teleport location (above ground)
        Location safeLoc = findSafeTeleportLocation(targetSite);

        if (safeLoc == null) {
            player.sendMessage("§c❌ Could not find safe teleport location!");
            return;
        }

        // Pre-teleport effects
        player.sendMessage("§8§l⚡ The ancient forces pull you through space...");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

        // Add brief blindness for dramatic effect
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));

        // Teleport with delay for effect
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.teleport(safeLoc);

            // Post-teleport effects
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.7f, 0.5f);

            // Welcome message
            player.sendMessage("§6⚡ §lYou have arrived at an Ancient Site!");
            player.sendMessage("§8§o⚠ Beware... the ancient guardian may be watching...");

            // Apply darkness effect briefly
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));

        }, 20L); // 1 second delay
    }

    private Location findSafeTeleportLocation(Location center) {
        // Try to find a safe location within 10 blocks of the center
        for (int attempt = 0; attempt < 20; attempt++) {
            Location candidate = center.clone().add(
                    (Math.random() - 0.5) * 20, // ±10 blocks
                    Math.random() * 10 - 5,     // ±5 blocks
                    (Math.random() - 0.5) * 20
            );

            // Check if location is safe (solid ground, 2 blocks of air above)
            if (candidate.getBlock().getType().isSolid() &&
                    candidate.clone().add(0, 1, 0).getBlock().getType().isAir() &&
                    candidate.clone().add(0, 2, 0).getBlock().getType().isAir()) {

                return candidate.add(0, 1, 0); // Stand on the solid block
            }
        }

        // Fallback: just use the center location + some height
        return center.clone().add(0, 5, 0);
    }

    private Location findNearestAncientSite(Location playerLoc, Map<String, Location> ancientSites) {
        if (ancientSites.isEmpty()) return null;

        Location nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Location siteLoc : ancientSites.values()) {
            if (siteLoc.getWorld().equals(playerLoc.getWorld())) {
                double distance = playerLoc.distance(siteLoc);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = siteLoc;
                }
            }
        }

        return nearest;
    }

    private String getDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        double angle = Math.atan2(dz, dx) * 180 / Math.PI;
        angle = (angle + 360) % 360; // Normalize to 0-360

        if (angle >= 337.5 || angle < 22.5) return "§eEast →";
        else if (angle >= 22.5 && angle < 67.5) return "§aSoutheast ↘";
        else if (angle >= 67.5 && angle < 112.5) return "§6South ↓";
        else if (angle >= 112.5 && angle < 157.5) return "§cSouthwest ↙";
        else if (angle >= 157.5 && angle < 202.5) return "§9West ←";
        else if (angle >= 202.5 && angle < 247.5) return "§bNorthwest ↖";
        else if (angle >= 247.5 && angle < 292.5) return "§dNorth ↑";
        else return "§5Northeast ↗";
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6§l=== Ancient Site Commands ===");
        sender.sendMessage("§e/ancientsite tp [number]§7 - Teleport to nearest/specific Ancient Site");
        sender.sendMessage("§e/ancientsite list§7 - List all discovered Ancient Sites");
        sender.sendMessage("§e/ancientsite nearest§7 - Find direction to nearest site");
        sender.sendMessage("§e/ancientsite random§7 - Teleport to random Ancient Site");
        sender.sendMessage("§e/ancientsite info§7 - Get info about nearest site");
        sender.sendMessage("§e/ancientsite stats§7 - View Ancient Sites statistics");
        sender.sendMessage("§7💡 You must be in the End dimension to teleport!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommands
            List<String> subCommands = Arrays.asList("tp", "teleport", "list", "nearest", "random", "info", "stats", "help");
            String partial = args[0].toLowerCase();

            for (String subCmd : subCommands) {
                if (subCmd.startsWith(partial)) {
                    completions.add(subCmd);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("teleport"))) {
            // Second argument for teleport - site numbers
            int siteCount = ancientManager.getAncientSites().size();
            for (int i = 1; i <= siteCount; i++) {
                completions.add(String.valueOf(i));
            }
        }

        return completions;
    }
}