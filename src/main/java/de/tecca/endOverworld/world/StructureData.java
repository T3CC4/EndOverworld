package de.tecca.endOverworld.world;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Analyzes chunk data to detect End City structures
 * Used during chunk scanning to determine if Ancient Site generation should occur
 */
public class StructureData {

    private final List<Location> endCityBlocks = new ArrayList<>();
    private final List<Location> solidBlocks = new ArrayList<>();
    private final Set<Material> detectedMaterials = new HashSet<>();

    private static final int MIN_END_CITY_BLOCKS = 25;
    private static final double MIN_STRUCTURE_RATIO = 0.15; // 15% of blocks should be End City materials

    /**
     * Adds an End City structure block to the analysis
     */
    public void addEndCityBlock(Location location) {
        endCityBlocks.add(location.clone());
        detectedMaterials.add(location.getBlock().getType());
    }

    /**
     * Adds a solid block (non-End City) to the analysis
     */
    public void addSolidBlock(Location location) {
        solidBlocks.add(location.clone());
        detectedMaterials.add(location.getBlock().getType());
    }

    /**
     * Determines if this represents a valid End City structure
     */
    public boolean isValidEndCity() {
        if (endCityBlocks.size() < MIN_END_CITY_BLOCKS) return false;

        int totalBlocks = endCityBlocks.size() + solidBlocks.size();
        if (totalBlocks == 0) return false;

        double structureRatio = (double) endCityBlocks.size() / totalBlocks;

        return structureRatio >= MIN_STRUCTURE_RATIO &&
                hasValidStructurePattern() &&
                hasStructuralDiversity();
    }

    /**
     * Checks for valid End City structure patterns
     */
    private boolean hasValidStructurePattern() {
        if (endCityBlocks.size() < 10) return false;

        // Check for vertical distribution (End Cities have towers)
        double minY = endCityBlocks.stream().mapToDouble(Location::getY).min().orElse(0);
        double maxY = endCityBlocks.stream().mapToDouble(Location::getY).max().orElse(0);
        double verticalSpread = maxY - minY;

        // End Cities should have at least 8 blocks of vertical spread
        if (verticalSpread < 8) return false;

        // Check for horizontal clustering (End Cities are compact)
        Location center = calculateCenter();
        if (center == null) return false;

        double averageDistance = endCityBlocks.stream()
                .mapToDouble(loc -> center.distance(loc))
                .average().orElse(0);

        // Average distance should be reasonable for a structure
        return averageDistance > 3 && averageDistance < 25;
    }

    /**
     * Checks if structure has sufficient material diversity
     */
    private boolean hasStructuralDiversity() {
        // Count unique End City materials
        long endCityMaterials = detectedMaterials.stream()
                .filter(this::isEndCityMaterial)
                .count();

        // Should have at least 2 different End City materials (e.g., purpur blocks + end rods)
        return endCityMaterials >= 2;
    }

    /**
     * Calculates the center point of the detected structure
     */
    public Location calculateCenter() {
        if (endCityBlocks.isEmpty()) return null;

        double avgX = endCityBlocks.stream().mapToDouble(Location::getX).average().orElse(0);
        double avgY = endCityBlocks.stream().mapToDouble(Location::getY).average().orElse(60);
        double avgZ = endCityBlocks.stream().mapToDouble(Location::getZ).average().orElse(0);

        return new Location(endCityBlocks.get(0).getWorld(), avgX, avgY, avgZ);
    }

    /**
     * Gets the estimated size/radius of the structure
     */
    public int getEstimatedRadius() {
        if (endCityBlocks.isEmpty()) return 0;

        Location center = calculateCenter();
        if (center == null) return 0;

        double maxDistance = endCityBlocks.stream()
                .mapToDouble(loc -> center.distance(loc))
                .max().orElse(0);

        return (int) Math.ceil(maxDistance);
    }

    /**
     * Gets structure density (blocks per cubic volume)
     */
    public double getStructureDensity() {
        int radius = getEstimatedRadius();
        if (radius == 0) return 0;

        double volume = (4.0/3.0) * Math.PI * Math.pow(radius, 3);
        return endCityBlocks.size() / volume;
    }

    /**
     * Gets the primary End City material detected
     */
    public Material getPrimaryEndCityMaterial() {
        return endCityBlocks.stream()
                .map(loc -> loc.getBlock().getType())
                .filter(this::isEndCityMaterial)
                .collect(java.util.stream.Collectors.groupingBy(
                        material -> material,
                        java.util.stream.Collectors.counting()
                ))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse(Material.PURPUR_BLOCK);
    }

    /**
     * Gets the height range of the structure
     */
    public int[] getHeightRange() {
        if (endCityBlocks.isEmpty()) return new int[]{0, 0};

        int minY = (int) endCityBlocks.stream().mapToDouble(Location::getY).min().orElse(0);
        int maxY = (int) endCityBlocks.stream().mapToDouble(Location::getY).max().orElse(0);

        return new int[]{minY, maxY};
    }

    /**
     * Checks if structure spans multiple levels (indicating a tower/complex structure)
     */
    public boolean hasMultipleLevels() {
        int[] heightRange = getHeightRange();
        return (heightRange[1] - heightRange[0]) >= 12; // At least 12 blocks tall
    }

    /**
     * Gets confidence score for End City detection (0.0 to 1.0)
     */
    public double getConfidenceScore() {
        if (!isValidEndCity()) return 0.0;

        double sizeScore = Math.min(1.0, endCityBlocks.size() / 100.0); // More blocks = higher confidence
        double ratioScore = Math.min(1.0, (double) endCityBlocks.size() / (endCityBlocks.size() + solidBlocks.size()));
        double diversityScore = Math.min(1.0, detectedMaterials.size() / 8.0); // More materials = higher confidence
        double heightScore = hasMultipleLevels() ? 1.0 : 0.5;

        return (sizeScore + ratioScore + diversityScore + heightScore) / 4.0;
    }

    /**
     * Gets locations that would be good anchor points for corruption
     */
    public List<Location> getCorruptionAnchorCandidates() {
        List<Location> anchors = new ArrayList<>();

        if (endCityBlocks.isEmpty()) return anchors;

        Location center = calculateCenter();
        if (center == null) return anchors;

        // Find blocks at different distances from center for varied corruption
        for (double distanceRatio : new double[]{0.3, 0.6, 0.9}) {
            double targetDistance = getEstimatedRadius() * distanceRatio;

            Location closest = endCityBlocks.stream()
                    .min((loc1, loc2) -> Double.compare(
                            Math.abs(center.distance(loc1) - targetDistance),
                            Math.abs(center.distance(loc2) - targetDistance)
                    ))
                    .orElse(null);

            if (closest != null && !anchors.contains(closest)) {
                anchors.add(closest);
            }
        }

        return anchors;
    }

    /**
     * Checks if material is End City related
     */
    private boolean isEndCityMaterial(Material material) {
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
                material.name().contains("PURPUR") ||
                material.name().contains("END_STONE_BRICK");
    }

    // Getters for basic data
    public List<Location> getEndCityBlocks() {
        return new ArrayList<>(endCityBlocks);
    }

    public List<Location> getSolidBlocks() {
        return new ArrayList<>(solidBlocks);
    }

    public int getEndCityBlockCount() {
        return endCityBlocks.size();
    }

    public int getSolidBlockCount() {
        return solidBlocks.size();
    }

    public int getTotalBlockCount() {
        return endCityBlocks.size() + solidBlocks.size();
    }

    public Set<Material> getDetectedMaterials() {
        return new HashSet<>(detectedMaterials);
    }

    /**
     * Checks if structure is large enough for Ancient Site generation
     */
    public boolean isSuitableForAncientSite() {
        return isValidEndCity() &&
                getConfidenceScore() > 0.6 &&
                getEndCityBlockCount() > 40;
    }

    @Override
    public String toString() {
        return "StructureData{" +
                "endCityBlocks=" + endCityBlocks.size() +
                ", solidBlocks=" + solidBlocks.size() +
                ", confidence=" + String.format("%.2f", getConfidenceScore()) +
                ", radius=" + getEstimatedRadius() +
                ", materials=" + detectedMaterials.size() +
                ", valid=" + isValidEndCity() +
                '}';
    }
}