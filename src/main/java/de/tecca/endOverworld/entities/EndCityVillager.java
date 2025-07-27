package de.tecca.endOverworld.entities;

import de.tecca.endOverworld.EndOverworld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages End City Villagers - special villagers that live in End Cities
 */
public class EndCityVillager {

    private static final String METADATA_KEY = "isEndCityVillager";
    private static final String ID_METADATA_KEY = "endVillagerID";
    private static final String SPAWN_METADATA_KEY = "spawnLocation";
    private static final String CREATION_METADATA_KEY = "creationTime";

    private static final Material[] SAFE_GROUND_MATERIALS = {
            Material.PURPUR_BLOCK, Material.PURPUR_PILLAR, Material.END_STONE,
            Material.END_STONE_BRICKS, Material.OBSIDIAN
    };

    private final EndOverworld plugin;
    private final Villager villager;
    private final String villagerID;
    private final Location spawnLocation;

    public EndCityVillager(EndOverworld plugin, Location spawnLocation) {
        this.plugin = plugin;
        this.spawnLocation = spawnLocation.clone();
        this.villagerID = generateUniqueID();
        this.villager = createVillager();
    }

    public static List<EndCityVillager> createVillagersNear(EndOverworld plugin, Location center, int count) {
        return findSafeLocationsNear(center, count + 2).stream()
                .limit(count)
                .map(location -> new EndCityVillager(plugin, location))
                .collect(Collectors.toList());
    }

    public static boolean isEndCityVillager(Villager villager) {
        return villager.hasMetadata(METADATA_KEY) &&
                villager.getMetadata(METADATA_KEY).get(0).asBoolean();
    }

    public static String getEndCityVillagerID(Villager villager) {
        return villager.hasMetadata(ID_METADATA_KEY) ?
                villager.getMetadata(ID_METADATA_KEY).get(0).asString() : null;
    }

    public void returnToSpawn() {
        if (!isValid()) return;

        int maxDistance = plugin.getConfig().getInt("villagers.max-wander-distance", 15);
        if (villager.getLocation().distance(spawnLocation) > maxDistance) {
            villager.teleport(spawnLocation);
        }
    }

    public String getGreetingMessage() {
        String profession = villager.getProfession().name().toLowerCase();

        if (profession.contains("librar")) return "§5Welcome, seeker of knowledge. The void holds many secrets...";
        if (profession.contains("cleric") || profession.contains("priest")) return "§5May the End's blessing be upon you, traveler.";
        if (profession.contains("cartograph")) return "§5Lost in the void? I can guide your way...";
        if (profession.contains("tool") || profession.contains("smith")) return "§5I craft with materials touched by the void itself.";

        return "§5Greetings, stranger. Welcome to our End sanctuary.";
    }

    public boolean isValid() {
        return villager != null && villager.isValid() && !villager.isDead();
    }

    public void remove() {
        if (isValid()) villager.remove();
    }

    private Villager createVillager() {
        Villager v = (Villager) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);

        setupProperties(v);
        setupMetadata(v);
        setupBehavior(v);

        return v;
    }

    private void setupProperties(Villager v) {
        v.setProfession(getRandomProfession());
        v.setVillagerType(Villager.Type.SWAMP);
        v.setAdult();
        v.setCustomName(getNameForProfession(v.getProfession()));
        v.setCustomNameVisible(true);
        v.setPersistent(true);
        v.setRemoveWhenFarAway(false);
    }

    private void setupMetadata(Villager v) {
        v.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, true));
        v.setMetadata(ID_METADATA_KEY, new FixedMetadataValue(plugin, villagerID));
        v.setMetadata(SPAWN_METADATA_KEY, new FixedMetadataValue(plugin, spawnLocation));
        v.setMetadata(CREATION_METADATA_KEY, new FixedMetadataValue(plugin, System.currentTimeMillis()));
    }

    private void setupBehavior(Villager v) {
        v.setAI(false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> v.setAI(true), 100L);
    }

    private Villager.Profession getRandomProfession() {
        Villager.Profession[] professions = getEndProfessions();
        return professions[(int) (Math.random() * professions.length)];
    }

    private Villager.Profession[] getEndProfessions() {
        return Arrays.stream(Villager.Profession.values())
                .filter(p -> !p.name().equals("NONE"))
                .filter(this::isEndAppropriate)
                .toArray(Villager.Profession[]::new);
    }

    private boolean isEndAppropriate(Villager.Profession profession) {
        String name = profession.name().toLowerCase();
        return name.contains("librar") || name.contains("cleric") || name.contains("priest") ||
                name.contains("cartograph") || name.contains("tool") || name.contains("smith");
    }

    private String getNameForProfession(Villager.Profession profession) {
        String configKey = "villagers.names." + profession.name().toLowerCase();
        List<String> names = plugin.getConfig().getStringList(configKey);

        if (names.isEmpty()) {
            names = plugin.getConfig().getStringList("villagers.names.generic");
        }

        return names.isEmpty() ? getFallbackName(profession) : getRandomElement(names);
    }

    private String getFallbackName(Villager.Profession profession) {
        String name = profession.name().toLowerCase();

        if (name.contains("librar")) return "§5End Scholar";
        if (name.contains("cleric") || name.contains("priest")) return "§5Void Priest";
        if (name.contains("cartograph")) return "§5Portal Sage";
        if (name.contains("tool") || name.contains("smith")) return "§5Shadow Merchant";

        return "§5End Dweller";
    }

    private static List<Location> findSafeLocationsNear(Location center, int maxLocations) {
        List<Location> locations = new ArrayList<>();

        // First verify we're actually in an End City structure
        if (!isValidEndCityStructure(center)) {
            return locations; // Return empty list if not in End City
        }

        // Search in expanding radius with better spacing
        for (int radius = 2; radius <= 20 && locations.size() < maxLocations; radius += 3) {
            for (int x = -radius; x <= radius && locations.size() < maxLocations; x += 2) {
                for (int z = -radius; z <= radius && locations.size() < maxLocations; z += 2) {
                    // Skip center area to avoid overcrowding
                    if (Math.abs(x) < 3 && Math.abs(z) < 3) continue;

                    // Search vertically for valid spawn points
                    for (int y = -10; y <= 15 && locations.size() < maxLocations; y++) {
                        Location candidate = center.clone().add(x, y, z);

                        if (isSafeSpawnLocation(candidate)) {
                            // Add small offset to center villager on block
                            locations.add(candidate.add(0.5, 0.1, 0.5));
                        }
                    }
                }
            }
        }

        return locations;
    }

    /**
     * Enhanced End City structure detection
     */
    private static boolean isValidEndCityStructure(Location location) {
        int endCityBlocks = 0;
        int totalChecked = 0;

        // Check larger area for End City characteristics
        for (int x = -15; x <= 15; x += 3) {
            for (int y = -15; y <= 15; y += 3) {
                for (int z = -15; z <= 15; z += 3) {
                    Location check = location.clone().add(x, y, z);
                    Material block = check.getBlock().getType();
                    totalChecked++;

                    if (isEndCityMaterial(block)) {
                        endCityBlocks++;
                    }
                }
            }
        }

        // At least 30% of checked blocks should be End City materials
        double ratio = (double) endCityBlocks / totalChecked;
        return ratio >= 0.30;
    }

    /**
     * Checks if a material belongs to End City structures
     */
    private static boolean isEndCityMaterial(Material material) {
        String name = material.name();
        return material == Material.PURPUR_BLOCK ||
                material == Material.PURPUR_PILLAR ||
                material == Material.PURPUR_STAIRS ||
                material == Material.PURPUR_SLAB ||
                material == Material.END_STONE_BRICKS ||
                material == Material.END_STONE_BRICK_STAIRS ||
                material == Material.END_STONE_BRICK_SLAB ||
                material == Material.END_STONE_BRICK_WALL ||
                material == Material.END_ROD ||
                material == Material.MAGENTA_STAINED_GLASS ||
                material == Material.MAGENTA_STAINED_GLASS_PANE ||
                name.contains("PURPUR") ||
                name.contains("END_STONE_BRICK");
    }

    /**
     * Comprehensive safe location checking
     */
    private static boolean isSafeSpawnLocation(Location location) {
        if (!hasValidSpawnSpace(location)) return false;
        if (!hasStableGround(location)) return false;
        if (isInsideBlock(location)) return false;
        if (isTooCloseToVoid(location)) return false;
        if (!isOnEndCityStructure(location)) return false;

        return true;
    }

    /**
     * Checks if location has 2 blocks of air space for villager
     */
    private static boolean hasValidSpawnSpace(Location location) {
        // Check the spawn location and 2 blocks above
        for (int y = 0; y <= 2; y++) {
            Location check = location.clone().add(0, y, 0);
            Material material = check.getBlock().getType();

            // Must be air or passable
            if (material != Material.AIR &&
                    material != Material.CAVE_AIR &&
                    material != Material.VOID_AIR &&
                    !isPassableMaterial(material)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if material is passable for spawning
     */
    private static boolean isPassableMaterial(Material material) {
        return material == Material.SHORT_GRASS ||
                material == Material.TALL_GRASS ||
                material == Material.DEAD_BUSH ||
                material == Material.VINE ||
                material.name().contains("CARPET") ||
                material.name().contains("PRESSURE_PLATE");
    }

    /**
     * Checks if location has stable ground beneath
     */
    private static boolean hasStableGround(Location location) {
        Location groundCheck = location.clone().add(0, -1, 0);
        Material ground = groundCheck.getBlock().getType();

        // Must be solid and preferably End City material
        if (!ground.isSolid()) return false;

        // Prefer End City materials but allow other solid blocks
        return isEndCityMaterial(ground) ||
                ground == Material.END_STONE ||
                ground == Material.OBSIDIAN ||
                ground.name().contains("PLANKS") ||
                ground.name().contains("STONE");
    }

    /**
     * Checks if location is inside a solid block
     */
    private static boolean isInsideBlock(Location location) {
        Material material = location.getBlock().getType();
        return material.isSolid() &&
                material != Material.SHORT_GRASS &&
                material != Material.TALL_GRASS &&
                !isPassableMaterial(material);
    }

    /**
     * Enhanced void proximity checking
     */
    private static boolean isTooCloseToVoid(Location location) {
        // Check 3x3 area around location
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Location check = location.clone().add(x, -1, z);

                if (check.getBlock().getType() == Material.AIR) {
                    // Check if there's solid ground within 10 blocks below
                    if (!hasGroundBelow(check, 10)) {
                        return true; // Too close to void
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if location is actually on an End City structure
     */
    private static boolean isOnEndCityStructure(Location location) {
        // Check ground and surrounding area for End City materials
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -2; y <= 0; y++) {
                    Location check = location.clone().add(x, y, z);
                    if (isEndCityMaterial(check.getBlock().getType())) {
                        return true;
                    }
                }
            }
        }

        // Also check for End City materials in wider area
        int endCityBlocksNearby = 0;
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    Location check = location.clone().add(x, y, z);
                    if (isEndCityMaterial(check.getBlock().getType())) {
                        endCityBlocksNearby++;
                        if (endCityBlocksNearby >= 10) return true; // Found enough End City blocks
                    }
                }
            }
        }

        return false;
    }

    private static boolean hasGroundBelow(Location location, int depth) {
        for (int y = -2; y >= -depth; y--) {
            if (location.clone().add(0, y, 0).getBlock().getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    private String generateUniqueID() {
        return "end_villager_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    private <T> T getRandomElement(List<T> list) {
        return list.get((int)(Math.random() * list.size()));
    }

    // Getters
    public Villager getVillager() { return villager; }
    public String getVillagerID() { return villagerID; }
    public Location getSpawnLocation() { return spawnLocation; }
}