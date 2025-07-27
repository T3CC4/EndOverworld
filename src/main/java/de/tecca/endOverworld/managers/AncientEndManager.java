package de.tecca.endOverworld.managers;

import de.tecca.endOverworld.EndOverworld;
import de.tecca.endOverworld.world.StructureOutline;
import de.tecca.endOverworld.world.StructureData;
import de.tecca.endOverworld.world.StructureBlock;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enhanced Ancient End Manager with updated material detection and corruption system
 */
public class AncientEndManager implements Listener {

    private final EndOverworld plugin;
    private final Map<String, Location> ancientSites = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> wardenTasks = new ConcurrentHashMap<>();
    private final Set<String> processedChunks = ConcurrentHashMap.newKeySet();

    private BukkitTask particleTask;
    private BukkitTask cleanupTask;

    private static final long ENTRY_COOLDOWN = 30_000L;
    private static final double SPAWN_CHANCE = 0.25;
    private static final int DETECTION_RADIUS = 40;
    private static final int MIN_STRUCTURE_BLOCKS = 25;

    // Updated material arrays for new system
    private static final Material[] END_CITY_MATERIALS = {
            Material.PURPUR_BLOCK, Material.PURPUR_PILLAR, Material.PURPUR_STAIRS, Material.PURPUR_SLAB,
            Material.END_STONE_BRICKS, Material.END_STONE_BRICK_STAIRS, Material.END_STONE_BRICK_SLAB, Material.END_STONE_BRICK_WALL,
            Material.END_ROD, Material.MAGENTA_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS_PANE,
            // Include already-corrupted variants
            Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES, Material.POLISHED_DEEPSLATE, Material.COBBLED_DEEPSLATE,
            Material.DEEPSLATE_BRICK_STAIRS, Material.DEEPSLATE_BRICK_SLAB, Material.DEEPSLATE_BRICK_WALL,
            Material.REINFORCED_DEEPSLATE
    };

    private static final Material[] SCULK_MATERIALS = {
            Material.SCULK, Material.SCULK_VEIN, Material.SCULK_CATALYST,
            Material.SCULK_SHRIEKER, Material.SCULK_SENSOR
    };

    private static final Material[] ANCIENT_CORRUPTION_MATERIALS = {
            Material.DEEPSLATE, Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES,
            Material.POLISHED_DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.CHISELED_DEEPSLATE,
            Material.REINFORCED_DEEPSLATE, Material.BLACKSTONE, Material.POLISHED_BLACKSTONE
    };

    public AncientEndManager(EndOverworld plugin) {
        this.plugin = plugin;
        startBackgroundTasks();
    }

    // === CHUNK PROCESSING ===

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.getWorld().getEnvironment() != World.Environment.THE_END) return;

        String chunkKey = getChunkKey(event.getChunk());
        if (processedChunks.contains(chunkKey)) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            StructureData structureData = scanChunkForStructures(event.getChunk());
            if (structureData.isValidEndCity()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        processAncientSite(structureData.calculateCenter())
                );
            }
            processedChunks.add(chunkKey);
        });
    }

    private StructureData scanChunkForStructures(Chunk chunk) {
        StructureData data = new StructureData();
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int y = 30; y <= 100; y += 2) {
            for (int x = 0; x < 16; x += 2) {
                for (int z = 0; z < 16; z += 2) {
                    Location loc = new Location(world, baseX + x, y, baseZ + z);
                    Material material = loc.getBlock().getType();

                    if (isStructureMaterial(material)) {
                        data.addEndCityBlock(loc);
                    }
                }
            }
        }
        return data;
    }

    private void processAncientSite(Location center) {
        if (center == null) return;
        if (ThreadLocalRandom.current().nextDouble() > SPAWN_CHANCE) return;

        String siteKey = getSiteKey(center);
        if (ancientSites.containsKey(siteKey)) return;

        createAncientSite(center);
    }

    // === ANCIENT SITE CREATION ===

    private void createAncientSite(Location center) {
        String siteKey = getSiteKey(center);
        ancientSites.put(siteKey, center);

        StructureOutline structure = analyzeStructure(center);
        applyIntelligentCorruption(center, structure);
        placeSculkInfrastructure(center, structure);
        addAncientLoot(center, structure);
        scheduleWardenSpawn(center);

        plugin.getLogger().info("Ancient Site created at " + formatLocation(center) +
                " affecting " + structure.getBlockCount() + " blocks");
    }

    private StructureOutline analyzeStructure(Location center) {
        StructureOutline outline = new StructureOutline();

        for (int x = -DETECTION_RADIUS; x <= DETECTION_RADIUS; x++) {
            for (int y = -DETECTION_RADIUS/2; y <= DETECTION_RADIUS/2; y++) {
                for (int z = -DETECTION_RADIUS; z <= DETECTION_RADIUS; z++) {
                    Location loc = center.clone().add(x, y, z);
                    Material material = loc.getBlock().getType();

                    if (isStructureMaterial(material)) {
                        outline.addStructureBlock(loc, material);
                    } else if (material.isSolid()) {
                        outline.addSolidBlock(loc, material);
                    }
                }
            }
        }

        outline.calculateBounds();
        return outline;
    }

    private void applyIntelligentCorruption(Location center, StructureOutline structure) {
        // Primary corruption around structure blocks - respects existing corruption
        for (StructureBlock block : structure.getStructureBlocks()) {
            applyContextualCorruption(block, structure);
        }

        // Secondary corruption connections
        createCorruptionNetwork(structure);

        // Atmospheric corruption in sparse areas
        fillAtmosphericAreas(center, structure);
    }

    private void applyContextualCorruption(StructureBlock sourceBlock, StructureOutline structure) {
        Location center = sourceBlock.location;
        int radius = 4 + ThreadLocalRandom.current().nextInt(3);
        Material sourceMaterial = sourceBlock.material;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location target = center.clone().add(x, y, z);
                    double distance = center.distance(target);

                    if (distance <= radius && shouldCorrupt(target, sourceBlock, structure, distance, radius)) {
                        applyContextualCorruption(target, distance / radius, sourceMaterial);
                    }
                }
            }
        }
    }

    private boolean shouldCorrupt(Location target, StructureBlock source, StructureOutline structure,
                                  double distance, int radius) {
        Block block = target.getBlock();
        Material material = block.getType();

        // Don't corrupt air or already corrupted blocks
        if (!material.isSolid() || isFullyCorrupted(material)) return false;

        // Respect blocks that are already partially corrupted from generator
        if (isPartiallyCorrupted(material) && ThreadLocalRandom.current().nextDouble() < 0.7) return false;

        // Protect important End City blocks based on context
        if (isEndCityMaterial(material) && ThreadLocalRandom.current().nextDouble() < 0.4) return false;

        double proximityBonus = structure.getProximityToStructure(target);
        double corruptionChance = (1.0 - distance / radius) * (0.6 + proximityBonus * 0.4);

        return ThreadLocalRandom.current().nextDouble() < corruptionChance;
    }

    private void applyContextualCorruption(Location location, double intensity, Material sourceContext) {
        Block block = location.getBlock();
        Material currentMaterial = block.getType();
        Material corruption = selectContextualCorruption(currentMaterial, intensity, sourceContext);

        if (corruption != null && corruption != currentMaterial) {
            block.setType(corruption);
        }
    }

    private Material selectContextualCorruption(Material current, double intensity, Material context) {
        // Respect existing corruption progression
        if (isFullyCorrupted(current)) return null;

        // Smart corruption based on current material and context
        if (isEndCityMaterial(current)) {
            return getEndCityCorruption(current, intensity);
        } else if (current == Material.END_STONE) {
            return getEndStoneCorruption(intensity);
        } else if (isPartiallyCorrupted(current)) {
            return advanceCorruption(current, intensity);
        } else {
            return getGenericCorruption(intensity);
        }
    }

    private Material getEndCityCorruption(Material original, double intensity) {
        // Purpur progression: Purpur → Deepslate variants → Sculk
        if (original == Material.PURPUR_BLOCK) {
            if (intensity > 0.8) return Material.SCULK;
            if (intensity > 0.6) return Material.DEEPSLATE_BRICKS;
            if (intensity > 0.4) return Material.DEEPSLATE_TILES;
            return Material.COBBLED_DEEPSLATE;
        }

        if (original == Material.PURPUR_PILLAR) {
            return intensity > 0.7 ? Material.REINFORCED_DEEPSLATE : Material.DEEPSLATE_TILES;
        }

        // End Stone Brick progression
        if (original == Material.END_STONE_BRICKS) {
            if (intensity > 0.8) return Material.SCULK;
            if (intensity > 0.5) return Material.DEEPSLATE_BRICKS;
            return Material.COBBLED_DEEPSLATE;
        }

        // Maintain structural variants
        if (original == Material.PURPUR_STAIRS) return Material.DEEPSLATE_BRICK_STAIRS;
        if (original == Material.PURPUR_SLAB) return Material.DEEPSLATE_BRICK_SLAB;
        if (original == Material.END_STONE_BRICK_STAIRS) return Material.DEEPSLATE_BRICK_STAIRS;
        if (original == Material.END_STONE_BRICK_SLAB) return Material.DEEPSLATE_BRICK_SLAB;
        if (original == Material.END_STONE_BRICK_WALL) return Material.DEEPSLATE_BRICK_WALL;

        return null;
    }

    private Material getEndStoneCorruption(double intensity) {
        if (intensity > 0.9) return Material.SCULK;
        if (intensity > 0.6) return Material.DEEPSLATE;
        return Material.COBBLED_DEEPSLATE;
    }

    private Material advanceCorruption(Material current, double intensity) {
        // Advance partially corrupted blocks further
        if (current == Material.COBBLED_DEEPSLATE && intensity > 0.7) {
            return Material.DEEPSLATE_BRICKS;
        }
        if (current == Material.DEEPSLATE_BRICKS && intensity > 0.8) {
            return Material.SCULK;
        }
        if (current == Material.DEEPSLATE && intensity > 0.8) {
            return Material.SCULK;
        }
        return null;
    }

    private Material getGenericCorruption(double intensity) {
        if (intensity > 0.8) return Material.SCULK;
        if (intensity > 0.5) return Material.SCULK_VEIN;
        return null;
    }

    private void createCorruptionNetwork(StructureOutline structure) {
        List<StructureBlock> blocks = structure.getStructureBlocks();

        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < Math.min(blocks.size(), i + 4); j++) {
                StructureBlock block1 = blocks.get(i);
                StructureBlock block2 = blocks.get(j);

                double distance = block1.location.distance(block2.location);
                if (distance > 4 && distance < 12) {
                    createSculkTendril(block1.location, block2.location);
                }
            }
        }
    }

    private void createSculkTendril(Location start, Location end) {
        double distance = start.distance(end);
        int steps = (int) (distance * 1.5);

        for (int i = 0; i <= steps; i++) {
            double progress = (double) i / steps;
            Location current = interpolate(start, end, progress);

            if (ThreadLocalRandom.current().nextDouble() < 0.4) {
                Block block = current.getBlock();
                if (block.getType().isSolid() && !isFullyCorrupted(block.getType()) &&
                        !isImportantStructureBlock(block.getType())) {
                    block.setType(Material.SCULK_VEIN);
                }
            }
        }
    }

    private void fillAtmosphericAreas(Location center, StructureOutline structure) {
        int samples = 15; // Reduced from 20
        int radius = DETECTION_RADIUS;

        for (int i = 0; i < samples; i++) {
            Location sample = center.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * radius * 2,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * radius,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * radius * 2
            );

            if (structure.getLocalDensity(sample, 6) < 0.15) {
                createAtmosphericCorruption(sample);
            }
        }
    }

    private void createAtmosphericCorruption(Location center) {
        int size = 2 + ThreadLocalRandom.current().nextInt(2); // Smaller patches

        for (int x = -size; x <= size; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -size; z <= size; z++) {
                    if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                        Location loc = center.clone().add(x, y, z);
                        Block block = loc.getBlock();

                        if (block.getType().isSolid() && !isFullyCorrupted(block.getType()) &&
                                !isImportantStructureBlock(block.getType())) {

                            Material corruption = ThreadLocalRandom.current().nextBoolean() ?
                                    Material.SCULK_VEIN : Material.COBBLED_DEEPSLATE;
                            block.setType(corruption);
                        }
                    }
                }
            }
        }
    }

    // === IMPROVED MATERIAL DETECTION ===

    private boolean isStructureMaterial(Material material) {
        return Arrays.stream(END_CITY_MATERIALS).anyMatch(m -> m == material);
    }

    private boolean isEndCityMaterial(Material material) {
        return material == Material.PURPUR_BLOCK || material == Material.PURPUR_PILLAR ||
                material == Material.END_STONE_BRICKS || material == Material.END_ROD ||
                material.name().contains("PURPUR") || material.name().contains("END_STONE_BRICK");
    }

    private boolean isSculkMaterial(Material material) {
        return Arrays.stream(SCULK_MATERIALS).anyMatch(m -> m == material);
    }

    private boolean isPartiallyCorrupted(Material material) {
        return Arrays.stream(ANCIENT_CORRUPTION_MATERIALS).anyMatch(m -> m == material);
    }

    private boolean isFullyCorrupted(Material material) {
        return isSculkMaterial(material) || material == Material.REINFORCED_DEEPSLATE;
    }

    private boolean isImportantStructureBlock(Material material) {
        return material == Material.END_ROD ||
                material == Material.MAGENTA_STAINED_GLASS ||
                material == Material.MAGENTA_STAINED_GLASS_PANE ||
                material == Material.CHEST ||
                material == Material.SPAWNER;
    }

    // === SCULK INFRASTRUCTURE (Updated) ===

    private void placeSculkInfrastructure(Location center, StructureOutline structure) {
        placeSculkSensors(center, structure);
        placeSculkShriekers(center, structure);
        placeSculkCatalysts(center, structure);
    }

    private void placeSculkSensors(Location center, StructureOutline structure) {
        int count = 4 + ThreadLocalRandom.current().nextInt(3); // Reduced from 6

        for (int i = 0; i < count; i++) {
            Location placement = findStructureBasedPlacement(center, structure, 15);
            if (placement != null) {
                placement.getBlock().setType(Material.SCULK_SENSOR);
                addSculkSupport(placement);
            }
        }
    }

    private void placeSculkShriekers(Location center, StructureOutline structure) {
        int count = 2 + ThreadLocalRandom.current().nextInt(2); // Reduced from 3

        for (int i = 0; i < count; i++) {
            Location placement = findStructureBasedPlacement(center, structure, 12);
            if (placement != null) {
                placement.getBlock().setType(Material.SCULK_SHRIEKER);
                createSculkPlatform(placement);
            }
        }
    }

    private void placeSculkCatalysts(Location center, StructureOutline structure) {
        int count = 2 + ThreadLocalRandom.current().nextInt(2); // Reduced from 3

        for (int i = 0; i < count; i++) {
            Location placement = findStructureBasedPlacement(center, structure, 20);
            if (placement != null) {
                placement.getBlock().setType(Material.SCULK_CATALYST);
                createCatalystSpread(placement);
            }
        }
    }

    private Location findStructureBasedPlacement(Location center, StructureOutline structure, int radius) {
        for (int attempt = 0; attempt < 8; attempt++) { // Reduced attempts
            Location candidate = center.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * radius * 2,
                    ThreadLocalRandom.current().nextInt(radius) - radius/2,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * radius * 2
            );

            if (structure.getProximityToStructure(candidate) > 0.1 && isSuitablePlacement(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isSuitablePlacement(Location location) {
        Block block = location.getBlock();
        Block above = location.clone().add(0, 1, 0).getBlock();

        return block.getType().isSolid() &&
                !isImportantStructureBlock(block.getType()) &&
                above.getType().isAir();
    }

    private void addSculkSupport(Location location) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;

                Location support = location.clone().add(x, 0, z);
                Block supportBlock = support.getBlock();

                if (supportBlock.getType().isSolid() &&
                        !isImportantStructureBlock(supportBlock.getType()) &&
                        ThreadLocalRandom.current().nextDouble() < 0.5) {
                    supportBlock.setType(Material.SCULK_VEIN);
                }
            }
        }
    }

    private void createSculkPlatform(Location center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                double distance = Math.sqrt(x*x + z*z);
                if (distance <= 2.0 && ThreadLocalRandom.current().nextDouble() < 0.6) {
                    Location platform = center.clone().add(x, -1, z);
                    Block platformBlock = platform.getBlock();

                    if (platformBlock.getType().isSolid() &&
                            !isImportantStructureBlock(platformBlock.getType())) {
                        platformBlock.setType(Material.SCULK);
                    }
                }
            }
        }
    }

    private void createCatalystSpread(Location center) {
        int radius = 2; // Reduced from 3

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt(x*x + y*y + z*z);
                    if (distance <= radius) {
                        Location spread = center.clone().add(x, y, z);
                        Block block = spread.getBlock();

                        if (block.getType().isSolid() &&
                                !isFullyCorrupted(block.getType()) &&
                                !isImportantStructureBlock(block.getType())) {

                            double chance = 0.4 * (1.0 - distance / radius);
                            if (ThreadLocalRandom.current().nextDouble() < chance) {
                                Material corruption = ThreadLocalRandom.current().nextBoolean() ?
                                        Material.SCULK : Material.SCULK_VEIN;
                                block.setType(corruption);
                            }
                        }
                    }
                }
            }
        }
    }

    // === LOOT PLACEMENT ===

    private void addAncientLoot(Location center, StructureOutline structure) {
        int chestCount = 1 + ThreadLocalRandom.current().nextInt(2); // Reduced from 2+

        for (int i = 0; i < chestCount; i++) {
            Location chestLoc = findStructureBasedPlacement(center, structure, 18);
            if (chestLoc != null) {
                createLootChest(chestLoc);
            }
        }
    }

    private void createLootChest(Location location) {
        location.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) location.getBlock().getState();
        populateChest(chest);
    }

    private void populateChest(Chest chest) {
        List<ItemStack> loot = new ArrayList<>();

        loot.add(new ItemStack(Material.SCULK, 3 + ThreadLocalRandom.current().nextInt(5)));
        loot.add(new ItemStack(Material.ECHO_SHARD, 1 + ThreadLocalRandom.current().nextInt(2)));
        loot.add(new ItemStack(Material.DISC_FRAGMENT_5, 1));
        loot.add(new ItemStack(Material.DEEPSLATE_BRICKS, 8 + ThreadLocalRandom.current().nextInt(16)));

        if (ThreadLocalRandom.current().nextDouble() < 0.3) {
            loot.add(new ItemStack(Material.RECOVERY_COMPASS, 1));
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            loot.add(new ItemStack(Material.NETHERITE_INGOT, 1));
        }

        for (ItemStack item : loot) {
            int slot = ThreadLocalRandom.current().nextInt(27);
            while (chest.getInventory().getItem(slot) != null && slot < 27) {
                slot = (slot + 1) % 27;
            }
            chest.getInventory().setItem(slot, item);
        }

        chest.update();
    }

    // === WARDEN MANAGEMENT ===

    private void scheduleWardenSpawn(Location center) {
        String siteKey = getSiteKey(center);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (shouldSpawnWarden(center)) {
                spawnWarden(center);
            }
        }, 400L, 1200L);

        wardenTasks.put(siteKey, task);
    }

    private boolean shouldSpawnWarden(Location center) {
        boolean playersNearby = center.getWorld().getPlayers().stream()
                .anyMatch(p -> p.getLocation().distance(center) < 25);

        if (!playersNearby) return false;

        return center.getWorld().getNearbyEntities(center, 30, 30, 30).stream()
                .noneMatch(entity -> entity instanceof Warden);
    }

    private void spawnWarden(Location center) {
        Location spawnLoc = findSuitablePlacement(center, 15);
        if (spawnLoc == null) return;

        Warden warden = (Warden) center.getWorld().spawnEntity(spawnLoc, EntityType.WARDEN);
        warden.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 0));

        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.8f);
        notifyNearbyPlayers(center, "§4⚠ Ancient Guardian awakens...");
    }

    private Location findSuitablePlacement(Location center, int radius) {
        for (int attempt = 0; attempt < 10; attempt++) {
            Location candidate = center.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * radius * 2,
                    ThreadLocalRandom.current().nextInt(8) - 4,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * radius * 2
            );

            if (isSuitablePlacement(candidate)) {
                return candidate.add(0, 1, 0);
            }
        }
        return null;
    }

    // === EVENT HANDLERS ===

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null ||
                event.getTo().getWorld().getEnvironment() != World.Environment.THE_END) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (isOnCooldown(playerId)) return;

        for (Location site : ancientSites.values()) {
            if (event.getTo().distance(site) < 15) {
                triggerSiteEntry(player, site);
                addCooldown(playerId);
                break;
            }
        }
    }

    private void triggerSiteEntry(Player player, Location site) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0));
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.4f, 0.6f);
        player.sendMessage("§8§oAn ancient presence stirs...");

        spawnEntryParticles(player.getLocation());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getWorld().getEnvironment() != World.Environment.THE_END) return;

        if (isAncientMaterial(block.getType()) && ThreadLocalRandom.current().nextDouble() < 0.12) {
            ItemStack bonus = createBonusLoot(block.getType());
            if (bonus != null) {
                block.getWorld().dropItemNaturally(block.getLocation(), bonus);
                event.getPlayer().sendMessage("§6§oAncient energies yield their secrets...");
                spawnBonusParticles(block.getLocation());
            }
        }
    }

    private ItemStack createBonusLoot(Material blockType) {
        if (isSculkMaterial(blockType)) {
            return new ItemStack(Material.ECHO_SHARD, 1);
        } else if (isPartiallyCorrupted(blockType)) {
            return new ItemStack(Material.SCULK, 1 + ThreadLocalRandom.current().nextInt(2));
        }
        return null;
    }

    private boolean isAncientMaterial(Material material) {
        return isSculkMaterial(material) || isPartiallyCorrupted(material) || isEndCityMaterial(material);
    }

    // === BACKGROUND TASKS ===

    private void startBackgroundTasks() {
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateParticles, 0L, 60L);
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::performCleanup, 6000L, 6000L);
    }

    private void updateParticles() {
        for (Location site : ancientSites.values()) {
            if (hasNearbyPlayers(site, 40)) {
                spawnSiteParticles(site);
            }
        }
    }

    private void performCleanup() {
        long currentTime = System.currentTimeMillis();
        playerCooldowns.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > ENTRY_COOLDOWN * 2);
    }

    // === UTILITY METHODS ===

    private Location interpolate(Location start, Location end, double progress) {
        return start.clone().add(
                (end.getX() - start.getX()) * progress,
                (end.getY() - start.getY()) * progress,
                (end.getZ() - start.getZ()) * progress
        );
    }

    private boolean isOnCooldown(UUID playerId) {
        Long lastEntry = playerCooldowns.get(playerId);
        return lastEntry != null && (System.currentTimeMillis() - lastEntry) < ENTRY_COOLDOWN;
    }

    private void addCooldown(UUID playerId) {
        playerCooldowns.put(playerId, System.currentTimeMillis());
    }

    private boolean hasNearbyPlayers(Location location, int radius) {
        return location.getWorld().getPlayers().stream()
                .anyMatch(p -> p.getLocation().distance(location) < radius);
    }

    private void notifyNearbyPlayers(Location center, String message) {
        center.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(center) < 40)
                .forEach(p -> p.sendMessage(message));
    }

    private void spawnSiteParticles(Location location) {
        World world = location.getWorld();
        for (int i = 0; i < 2; i++) { // Reduced from 3
            Location particle = location.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 12,
                    ThreadLocalRandom.current().nextDouble() * 5,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 12
            );
            world.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, particle, 1);
        }
    }

    private void spawnEntryParticles(Location location) {
        location.getWorld().spawnParticle(Particle.SOUL, location, 10, 0.8, 0.8, 0.8, 0.05);
    }

    private void spawnBonusParticles(Location location) {
        location.getWorld().spawnParticle(Particle.ENCHANT, location, 6, 0.4, 0.4, 0.4, 0.08);
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }

    private String getSiteKey(Location location) {
        return location.getWorld().getName() + "_" +
                (int)(location.getX() / 50) + "_" +
                (int)(location.getZ() / 50);
    }

    private String formatLocation(Location loc) {
        return String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ());
    }

    // === PUBLIC API ===

    public Map<String, Location> getAncientSites() {
        return new HashMap<>(ancientSites);
    }

    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("ancient_sites", ancientSites.size());
        stats.put("processed_chunks", processedChunks.size());
        stats.put("active_wardens", countActiveWardens());
        return stats;
    }

    private int countActiveWardens() {
        return ancientSites.values().stream()
                .mapToInt(loc -> (int) loc.getWorld().getNearbyEntities(loc, 35, 35, 35).stream()
                        .filter(e -> e instanceof Warden).count())
                .sum();
    }

    public String getDebugInfo() {
        StringBuilder info = new StringBuilder("=== Ancient End Manager ===\n");
        getStatistics().forEach((key, value) ->
                info.append(key).append(": ").append(value).append("\n"));
        return info.toString();
    }

    public void cleanup() {
        if (particleTask != null) particleTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        wardenTasks.values().forEach(BukkitTask::cancel);

        ancientSites.clear();
        playerCooldowns.clear();
        wardenTasks.clear();
        processedChunks.clear();

        plugin.getLogger().info("Ancient End Manager cleanup complete");
    }
}