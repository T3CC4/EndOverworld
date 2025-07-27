package de.tecca.endOverworld.world;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the 3D outline and analysis of an End City structure
 * Used for intelligent corruption placement and density calculations
 */
public class StructureOutline {

    private final List<StructureBlock> structureBlocks = new ArrayList<>();
    private final List<StructureBlock> solidBlocks = new ArrayList<>();
    private Location minBounds, maxBounds;
    private Location center;

    public void addStructureBlock(Location location, Material material) {
        structureBlocks.add(new StructureBlock(location.clone(), material));
    }

    public void addSolidBlock(Location location, Material material) {
        solidBlocks.add(new StructureBlock(location.clone(), material));
    }

    public void calculateBounds() {
        if (structureBlocks.isEmpty()) return;

        double minX = structureBlocks.stream().mapToDouble(b -> b.location.getX()).min().orElse(0);
        double maxX = structureBlocks.stream().mapToDouble(b -> b.location.getX()).max().orElse(0);
        double minY = structureBlocks.stream().mapToDouble(b -> b.location.getY()).min().orElse(0);
        double maxY = structureBlocks.stream().mapToDouble(b -> b.location.getY()).max().orElse(0);
        double minZ = structureBlocks.stream().mapToDouble(b -> b.location.getZ()).min().orElse(0);
        double maxZ = structureBlocks.stream().mapToDouble(b -> b.location.getZ()).max().orElse(0);

        minBounds = new Location(structureBlocks.get(0).location.getWorld(), minX, minY, minZ);
        maxBounds = new Location(structureBlocks.get(0).location.getWorld(), maxX, maxY, maxZ);

        // Calculate center
        center = new Location(
                structureBlocks.get(0).location.getWorld(),
                (minX + maxX) / 2.0,
                (minY + maxY) / 2.0,
                (minZ + maxZ) / 2.0
        );
    }

    /**
     * Gets the effective radius of the structure
     */
    public int getRadius() {
        if (minBounds == null || maxBounds == null) return 20;

        double width = maxBounds.getX() - minBounds.getX();
        double height = maxBounds.getY() - minBounds.getY();
        double depth = maxBounds.getZ() - minBounds.getZ();

        return (int) Math.max(width, Math.max(height, depth)) / 2;
    }

    /**
     * Calculates proximity factor to nearest structure block (0.0 to 1.0)
     */
    public double getProximityToStructure(Location location) {
        if (structureBlocks.isEmpty()) return 0.0;

        double minDistance = structureBlocks.stream()
                .mapToDouble(block -> location.distance(block.location))
                .min().orElse(Double.MAX_VALUE);

        // Convert distance to proximity (closer = higher value)
        return Math.max(0.0, 1.0 / (1.0 + minDistance * 0.1));
    }

    /**
     * Calculates local structure density within given radius
     */
    public double getLocalDensity(Location center, int radius) {
        long nearbyBlocks = structureBlocks.stream()
                .filter(block -> block.location.distance(center) <= radius)
                .count();

        // Calculate theoretical maximum blocks in sphere
        double sphereVolume = (4.0/3.0) * Math.PI * Math.pow(radius, 3);
        double maxPossibleBlocks = sphereVolume * 0.5; // Assume 50% fill rate for structures

        return Math.min(1.0, nearbyBlocks / maxPossibleBlocks);
    }

    /**
     * Finds the closest structure block to given location
     */
    public StructureBlock getClosestStructureBlock(Location location) {
        return structureBlocks.stream()
                .min((b1, b2) -> Double.compare(
                        location.distance(b1.location),
                        location.distance(b2.location)
                ))
                .orElse(null);
    }

    /**
     * Gets structure blocks within specified distance of location
     */
    public List<StructureBlock> getStructureBlocksWithin(Location location, double distance) {
        return structureBlocks.stream()
                .filter(block -> block.location.distance(location) <= distance)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Calculates structure complexity based on material variety and distribution
     */
    public double getComplexity() {
        if (structureBlocks.isEmpty()) return 0.0;

        // Count unique materials
        long uniqueMaterials = structureBlocks.stream()
                .map(block -> block.material)
                .distinct()
                .count();

        // Calculate spread (how distributed the blocks are)
        double averageDistance = 0.0;
        if (center != null) {
            averageDistance = structureBlocks.stream()
                    .mapToDouble(block -> center.distance(block.location))
                    .average().orElse(0.0);
        }

        // Combine factors for complexity score
        double materialComplexity = Math.min(1.0, uniqueMaterials / 8.0); // Max 8 materials
        double spatialComplexity = Math.min(1.0, averageDistance / 30.0); // Max 30 block spread

        return (materialComplexity + spatialComplexity) / 2.0;
    }

    /**
     * Checks if location is within structure bounds
     */
    public boolean isWithinBounds(Location location) {
        if (minBounds == null || maxBounds == null) return false;

        return location.getX() >= minBounds.getX() && location.getX() <= maxBounds.getX() &&
                location.getY() >= minBounds.getY() && location.getY() <= maxBounds.getY() &&
                location.getZ() >= minBounds.getZ() && location.getZ() <= maxBounds.getZ();
    }

    /**
     * Gets the primary building material of the structure
     */
    public Material getPrimaryMaterial() {
        if (structureBlocks.isEmpty()) return Material.PURPUR_BLOCK;

        return structureBlocks.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        block -> block.material,
                        java.util.stream.Collectors.counting()
                ))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse(Material.PURPUR_BLOCK);
    }

    /**
     * Finds suitable corruption anchor points (high-density areas)
     */
    public List<Location> getCorruptionAnchors(int maxAnchors) {
        List<Location> anchors = new ArrayList<>();

        if (structureBlocks.isEmpty()) return anchors;

        // Use clustering to find high-density areas
        for (StructureBlock block : structureBlocks) {
            if (anchors.size() >= maxAnchors) break;

            // Check if this block is in a high-density area
            double localDensity = getLocalDensity(block.location, 5);
            if (localDensity > 0.3) {
                // Make sure we don't have anchors too close together
                boolean tooClose = anchors.stream()
                        .anyMatch(anchor -> anchor.distance(block.location) < 8);

                if (!tooClose) {
                    anchors.add(block.location.clone());
                }
            }
        }

        return anchors;
    }

    // Getters
    public List<StructureBlock> getStructureBlocks() {
        return new ArrayList<>(structureBlocks);
    }

    public List<StructureBlock> getSolidBlocks() {
        return new ArrayList<>(solidBlocks);
    }

    public int getBlockCount() {
        return structureBlocks.size();
    }

    public int getTotalBlocks() {
        return structureBlocks.size() + solidBlocks.size();
    }

    public Location getCenter() {
        return center != null ? center.clone() : null;
    }

    public Location getMinBounds() {
        return minBounds != null ? minBounds.clone() : null;
    }

    public Location getMaxBounds() {
        return maxBounds != null ? maxBounds.clone() : null;
    }

    /**
     * Gets dimensions of the structure
     */
    public int[] getDimensions() {
        if (minBounds == null || maxBounds == null) return new int[]{0, 0, 0};

        return new int[]{
                (int) (maxBounds.getX() - minBounds.getX()),
                (int) (maxBounds.getY() - minBounds.getY()),
                (int) (maxBounds.getZ() - minBounds.getZ())
        };
    }

    /**
     * Checks if this is a valid structure outline
     */
    public boolean isValid() {
        return !structureBlocks.isEmpty() &&
                minBounds != null &&
                maxBounds != null &&
                center != null;
    }

    @Override
    public String toString() {
        return "StructureOutline{" +
                "blocks=" + structureBlocks.size() +
                ", complexity=" + String.format("%.2f", getComplexity()) +
                ", radius=" + getRadius() +
                ", primaryMaterial=" + getPrimaryMaterial() +
                '}';
    }
}