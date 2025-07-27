package de.tecca.endOverworld.managers;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enhanced Ancient End Manager with performance optimizations and particle effects
 */
public class AncientEndManager implements Listener {

    private final EndOverworld plugin;
    private final Map<String, Long> processedChunks = new ConcurrentHashMap<>();
    private final Map<String, Location> ancientSites = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> wardenTasks = new ConcurrentHashMap<>();
    private final Set<String> loadedChunks = ConcurrentHashMap.newKeySet();

    private BukkitTask particleTask;
    private BukkitTask cleanupTask;

    private static final long ENTRY_COOLDOWN = 30_000L;
    private static final long CHUNK_CLEANUP_INTERVAL = 300_000L; // 5 minutes
    private static final double SPAWN_CHANCE_DEFAULT = 0.25;
    private static final int SITE_RADIUS = 15;

    private static final Material[] SCULK_MATERIALS = {
            Material.SCULK, Material.SCULK_VEIN, Material.SCULK_CATALYST,
            Material.SCULK_SHRIEKER, Material.SCULK_SENSOR
    };

    private static final Material[] ANCIENT_BLOCKS = {
            Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES,
            Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE
    };

    public AncientEndManager(EndOverworld plugin) {
        this.plugin = plugin;
        startBackgroundTasks();
    }

    private void startBackgroundTasks() {
        // Particle effects task
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateParticleEffects, 100L, 20L);

        // Cleanup task for memory management
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::performCleanup,
                CHUNK_CLEANUP_INTERVAL, CHUNK_CLEANUP_INTERVAL);
    }

    // === CHUNK MANAGEMENT ===

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.getWorld().getEnvironment() != World.Environment.THE_END) return;

        String chunkKey = getChunkKey(event.getChunk());
        loadedChunks.add(chunkKey);

        if (processedChunks.containsKey(chunkKey)) return;

        // Async structure detection
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (hasEndCityStructure(event.getChunk())) {
                // Schedule site creation on main thread
                Bukkit.getScheduler().runTask(plugin, () -> processAncientSite(event.getChunk()));
            }
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        String chunkKey = getChunkKey(event.getChunk());
        loadedChunks.remove(chunkKey);
    }

    private void processAncientSite(Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        processedChunks.put(chunkKey, System.currentTimeMillis());

        double spawnChance = plugin.getConfig().getDouble("ancient_end.spawn_chance", SPAWN_CHANCE_DEFAULT);
        if (ThreadLocalRandom.current().nextDouble() > spawnChance) return;

        Location center = findEndCityCenter(chunk);
        if (center != null) {
            createAncientSite(center);
        }
    }

    // === STRUCTURE DETECTION ===

    private boolean hasEndCityStructure(Chunk chunk) {
        int endCityBlocks = 0;
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        // Optimized scanning with early exit
        for (int y = 50; y <= 90 && endCityBlocks < 20; y++) {
            for (int x = 0; x < 16 && endCityBlocks < 20; x += 2) {
                for (int z = 0; z < 16 && endCityBlocks < 20; z += 2) {
                    Material block = world.getBlockAt(baseX + x, y, baseZ + z).getType();
                    if (isEndCityMaterial(block)) {
                        endCityBlocks++;
                    }
                }
            }
        }
        return endCityBlocks >= 20;
    }

    private boolean isEndCityMaterial(Material material) {
        return material == Material.PURPUR_BLOCK || material == Material.PURPUR_PILLAR ||
                material == Material.END_STONE_BRICKS || material == Material.END_ROD ||
                material.name().contains("PURPUR");
    }

    private Location findEndCityCenter(Chunk chunk) {
        List<Location> blocks = new ArrayList<>();
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int x = 0; x < 16; x += 2) {
            for (int z = 0; z < 16; z += 2) {
                for (int y = 50; y <= 90; y += 3) {
                    if (isEndCityMaterial(world.getBlockAt(baseX + x, y, baseZ + z).getType())) {
                        blocks.add(new Location(world, baseX + x, y, baseZ + z));
                    }
                }
            }
        }

        if (blocks.isEmpty()) return null;

        // Calculate center efficiently
        double avgX = blocks.stream().mapToDouble(Location::getX).average().orElse(0);
        double avgY = blocks.stream().mapToDouble(Location::getY).average().orElse(60);
        double avgZ = blocks.stream().mapToDouble(Location::getZ).average().orElse(0);

        return new Location(world, avgX, avgY, avgZ);
    }

    // === SITE CREATION ===

    private void createAncientSite(Location center) {
        String siteKey = getSiteKey(center);
        ancientSites.put(siteKey, center);

        addSculkPatches(center);
        addAncientStructures(center);
        addAncientLoot(center);
        scheduleWardenSpawn(center, siteKey);

        plugin.getLogger().info("Created Ancient Site: " + siteKey);
    }

    private void addSculkPatches(Location center) {
        int patchCount = 3 + ThreadLocalRandom.current().nextInt(5);

        for (int i = 0; i < patchCount; i++) {
            Location patchCenter = center.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 30,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 10,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 30
            );
            createSculkPatch(patchCenter);
        }
    }

    private void createSculkPatch(Location center) {
        int radius = 2 + ThreadLocalRandom.current().nextInt(4);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -2; y <= 2; y++) {
                    if (ThreadLocalRandom.current().nextDouble() > 0.6) continue;

                    Location loc = center.clone().add(x, y, z);
                    Block block = loc.getBlock();

                    if (block.getType().isSolid() &&
                            block.getType() != Material.CHEST &&
                            block.getType() != Material.SPAWNER) {
                        block.setType(getSculkMaterial(x, y, z, radius));
                    }
                }
            }
        }
    }

    private Material getSculkMaterial(int x, int y, int z, int radius) {
        double distance = Math.sqrt(x*x + y*y + z*z);
        double ratio = distance / radius;

        if (ratio < 0.3 && ThreadLocalRandom.current().nextDouble() < 0.1) {
            return ThreadLocalRandom.current().nextBoolean() ? Material.SCULK_CATALYST :
                    ThreadLocalRandom.current().nextBoolean() ? Material.SCULK_SHRIEKER : Material.SCULK_SENSOR;
        } else if (ratio < 0.7) {
            return Material.SCULK;
        } else {
            return Material.SCULK_VEIN;
        }
    }

    private void addAncientStructures(Location center) {
        int count = 1 + ThreadLocalRandom.current().nextInt(3);

        for (int i = 0; i < count; i++) {
            Location structureLoc = center.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 20,
                    ThreadLocalRandom.current().nextInt(5) - 2,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 20
            );
            createAncientStructure(structureLoc);
        }
    }

    private void createAncientStructure(Location center) {
        Material block = ANCIENT_BLOCKS[ThreadLocalRandom.current().nextInt(ANCIENT_BLOCKS.length)];

        // Create hollow 3x3x3 structure
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) {
                    boolean isEdge = Math.abs(x) == 1 || Math.abs(z) == 1 || y == 0 || y == 2;
                    if (isEdge) {
                        Location loc = center.clone().add(x, y, z);
                        if (loc.getBlock().getType() == Material.AIR) {
                            loc.getBlock().setType(block);
                        }
                    }
                }
            }
        }

        // Add entrance
        center.clone().add(0, 1, 1).getBlock().setType(Material.AIR);
    }

    private void addAncientLoot(Location center) {
        for (int i = 0; i < 2; i++) {
            Location chestLoc = findSuitableLocation(center, 15);
            if (chestLoc != null) {
                createLootChest(chestLoc);
            }
        }
    }

    private Location findSuitableLocation(Location center, int range) {
        for (int attempt = 0; attempt < 10; attempt++) {
            Location candidate = center.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * range,
                    ThreadLocalRandom.current().nextInt(5) - 2,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * range
            );

            if (candidate.getBlock().getType().isSolid() &&
                    candidate.clone().add(0, 1, 0).getBlock().getType() == Material.AIR) {
                return candidate.add(0, 1, 0);
            }
        }
        return null;
    }

    private void createLootChest(Location location) {
        location.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) location.getBlock().getState();
        populateChest(chest);
    }

    private void populateChest(Chest chest) {
        List<ItemStack> loot = Arrays.asList(
                new ItemStack(Material.SCULK, 3 + ThreadLocalRandom.current().nextInt(5)),
                new ItemStack(Material.ECHO_SHARD, 1 + ThreadLocalRandom.current().nextInt(2)),
                new ItemStack(Material.DISC_FRAGMENT_5, 1),
                new ItemStack(Material.DEEPSLATE_BRICKS, 8 + ThreadLocalRandom.current().nextInt(16))
        );

        // Rare items
        if (ThreadLocalRandom.current().nextDouble() < 0.3) {
            loot.add(new ItemStack(Material.RECOVERY_COMPASS, 1));
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            loot.add(new ItemStack(Material.NETHERITE_INGOT, 1));
        }

        // Distribute loot randomly
        loot.forEach(item -> {
            int slot = ThreadLocalRandom.current().nextInt(27);
            if (chest.getInventory().getItem(slot) == null) {
                chest.getInventory().setItem(slot, item);
            }
        });

        chest.update();
    }

    // === WARDEN MANAGEMENT ===

    private void scheduleWardenSpawn(Location center, String siteKey) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (shouldSpawnWarden(center)) {
                spawnWarden(center);
            }
        }, 200L, 600L);

        wardenTasks.put(siteKey, task);
    }

    private boolean shouldSpawnWarden(Location center) {
        boolean playersNearby = center.getWorld().getPlayers().stream()
                .anyMatch(p -> p.getLocation().distance(center) < 30);

        if (!playersNearby) return false;

        return center.getWorld().getNearbyEntities(center, 40, 40, 40).stream()
                .noneMatch(entity -> entity instanceof Warden);
    }

    private void spawnWarden(Location center) {
        Location spawnLoc = findSuitableLocation(center, 20);
        if (spawnLoc == null) return;

        Warden warden = (Warden) center.getWorld().spawnEntity(spawnLoc, EntityType.WARDEN);
        warden.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 0));

        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.8f);
        center.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(center) < 50)
                .forEach(p -> p.sendMessage("§4§l⚠ The Ancient Guardian awakens... ⚠"));
    }

    // === PARTICLE EFFECTS ===

    private void updateParticleEffects() {
        for (Location site : ancientSites.values()) {
            if (!loadedChunks.contains(getChunkKey(site.getChunk()))) continue;

            spawnSiteParticles(site);
        }
    }

    private void spawnSiteParticles(Location center) {
        World world = center.getWorld();

        // Sculk spores
        for (int i = 0; i < 3; i++) {
            Location particleLoc = center.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 20,
                    ThreadLocalRandom.current().nextDouble() * 8,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 20
            );

            world.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, particleLoc, 1, 0.2, 0.2, 0.2, 0.01);
        }

        // Dark particles near sculk
        Location darkParticle = center.clone().add(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 15,
                ThreadLocalRandom.current().nextDouble() * 5,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * 15
        );

        world.spawnParticle(Particle.SQUID_INK, darkParticle, 2, 0.5, 0.5, 0.5, 0.02);
    }

    // === EVENT HANDLERS ===

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null ||
                event.getTo().getWorld().getEnvironment() != World.Environment.THE_END) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        Long lastEntry = playerCooldowns.get(playerId);
        if (lastEntry != null && (currentTime - lastEntry) < ENTRY_COOLDOWN) return;

        Location playerLoc = event.getTo();
        for (Location siteLoc : ancientSites.values()) {
            if (playerLoc.distance(siteLoc) < SITE_RADIUS) {
                playerCooldowns.put(playerId, currentTime);
                triggerSiteEntry(player, siteLoc);
                break;
            }
        }
    }

    private void triggerSiteEntry(Player player, Location siteLoc) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.5f, 0.7f);
        player.sendMessage("§8§oYou feel an ancient presence watching you...");

        // Enhanced particle effect on entry
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation(), 20, 1, 1, 1, 0.1);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isAncientBlock(block.getType()) &&
                block.getWorld().getEnvironment() == World.Environment.THE_END &&
                ThreadLocalRandom.current().nextDouble() < 0.2) {

            ItemStack bonus = getBonusLoot(block.getType());
            if (bonus != null) {
                block.getWorld().dropItemNaturally(block.getLocation(), bonus);
                event.getPlayer().sendMessage("§6§oThe ancient block yields extra treasures...");

                // Particle effect for bonus loot
                block.getWorld().spawnParticle(Particle.ENCHANT,
                        block.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }

    private boolean isAncientBlock(Material material) {
        return Arrays.stream(SCULK_MATERIALS).anyMatch(m -> m == material) ||
                Arrays.stream(ANCIENT_BLOCKS).anyMatch(m -> m == material);
    }

    private ItemStack getBonusLoot(Material blockType) {
        switch (blockType) {
            case SCULK_CATALYST: return new ItemStack(Material.ECHO_SHARD, 1);
            case SCULK_SHRIEKER: return new ItemStack(Material.DISC_FRAGMENT_5, 1);
            case SCULK_SENSOR: return new ItemStack(Material.SCULK, 2 + ThreadLocalRandom.current().nextInt(3));
            default: return new ItemStack(Material.SCULK_VEIN, 1 + ThreadLocalRandom.current().nextInt(2));
        }
    }

    // === CLEANUP & UTILITIES ===

    private void performCleanup() {
        long currentTime = System.currentTimeMillis();

        // Clean old processed chunks
        processedChunks.entrySet().removeIf(entry ->
                !loadedChunks.contains(entry.getKey()) &&
                        (currentTime - entry.getValue()) > CHUNK_CLEANUP_INTERVAL);

        // Clean old player cooldowns
        playerCooldowns.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > ENTRY_COOLDOWN * 2);
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }

    private String getSiteKey(Location location) {
        return location.getWorld().getName() + "_" +
                (int)(location.getX() / 50) + "_" +
                (int)(location.getZ() / 50);
    }

    // === PUBLIC API ===

    public Map<String, Location> getAncientSites() {
        return new HashMap<>(ancientSites);
    }

    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("ancient_sites", ancientSites.size());
        stats.put("processed_chunks", processedChunks.size());
        stats.put("active_wardens", ancientSites.values().stream()
                .mapToInt(loc -> (int) loc.getWorld().getNearbyEntities(loc, 50, 50, 50).stream()
                        .filter(e -> e instanceof Warden).count())
                .sum());
        return stats;
    }

    public String getDebugInfo() {
        Map<String, Integer> stats = getStatistics();
        StringBuilder info = new StringBuilder("=== Ancient End Manager Debug ===\n");
        stats.forEach((key, value) -> info.append(key).append(": ").append(value).append("\n"));
        return info.toString();
    }

    public void cleanup() {
        // Cancel all tasks
        if (particleTask != null) particleTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        wardenTasks.values().forEach(BukkitTask::cancel);

        // Clear all data
        processedChunks.clear();
        ancientSites.clear();
        playerCooldowns.clear();
        wardenTasks.clear();
        loadedChunks.clear();

        plugin.getLogger().info("Ancient End Manager cleanup complete");
    }
}