package de.tecca.endOverworld.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Objects;

/**
 * Represents a single block within a structure with contextual information
 * Used for intelligent corruption analysis and placement decisions
 */
public class StructureBlock {

    public final Location location;
    public final Material material;
    private final BlockType blockType;
    private final StructuralRole role;

    /**
     * Block type categories for corruption logic
     */
    public enum BlockType {
        STRUCTURAL,    // Core building blocks (purpur blocks, end stone bricks)
        DECORATIVE,    // Aesthetic elements (end rods, glass panes)
        FUNCTIONAL,    // Interactive blocks (chests, spawners)
        SUPPORT,       // Foundation/support blocks
        CONNECTOR      // Stairs, slabs connecting levels
    }

    /**
     * Structural role within the building
     */
    public enum StructuralRole {
        FOUNDATION,    // Ground level support
        WALL,          // Vertical structure elements
        FLOOR,         // Horizontal platform elements
        ROOF,          // Top covering elements
        PILLAR,        // Vertical support columns
        DECORATION,    // Non-structural aesthetic elements
        UNKNOWN        // Cannot determine role
    }

    public StructureBlock(Location location, Material material) {
        this.location = location.clone();
        this.material = material;
        this.blockType = determineBlockType(material);
        this.role = determineStructuralRole(location, material);
    }

    /**
     * Determines the functional type of the block
     */
    private BlockType determineBlockType(Material material) {
        String name = material.name();

        // Structural blocks - main building materials
        if (material == Material.PURPUR_BLOCK ||
                material == Material.PURPUR_PILLAR ||
                material == Material.END_STONE_BRICKS ||
                name.contains("_BRICKS")) {
            return BlockType.STRUCTURAL;
        }

        // Decorative blocks
        if (material == Material.END_ROD ||
                name.contains("GLASS") ||
                name.contains("PANE") ||
                material == Material.ITEM_FRAME ||
                material == Material.PAINTING) {
            return BlockType.DECORATIVE;
        }

        // Functional blocks
        if (material == Material.CHEST ||
                material == Material.ENDER_CHEST ||
                material == Material.SPAWNER ||
                material == Material.BREWING_STAND ||
                name.contains("DOOR") ||
                name.contains("TRAPDOOR")) {
            return BlockType.FUNCTIONAL;
        }

        // Connector blocks
        if (name.contains("STAIRS") ||
                name.contains("SLAB") ||
                material == Material.LADDER ||
                material == Material.VINE) {
            return BlockType.CONNECTOR;
        }

        // Default to structural if solid
        return material.isSolid() ? BlockType.STRUCTURAL : BlockType.DECORATIVE;
    }

    /**
     * Analyzes the block's structural role based on position and context
     */
    private StructuralRole determineStructuralRole(Location loc, Material material) {
        Block block = loc.getBlock();

        // Check blocks above and below for context
        Block above = block.getRelative(BlockFace.UP);
        Block below = block.getRelative(BlockFace.DOWN);

        // Foundation detection - solid block with air or structure above
        if (below.getType() == Material.AIR || below.getType() == Material.VOID_AIR) {
            return StructuralRole.FOUNDATION;
        }

        // Roof detection - air above, structure below
        if (above.getType() == Material.AIR && below.getType().isSolid()) {
            return StructuralRole.ROOF;
        }

        // Pillar detection - vertical alignment of same material
        if (above.getType() == material && below.getType() == material) {
            return StructuralRole.PILLAR;
        }

        // Wall detection - has solid blocks on horizontal sides
        int solidSides = 0;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (block.getRelative(face).getType().isSolid()) {
                solidSides++;
            }
        }

        if (solidSides >= 2) {
            return StructuralRole.WALL;
        }

        // Floor detection - air above, solid below, horizontal context
        if (above.getType() == Material.AIR && below.getType().isSolid()) {
            return StructuralRole.FLOOR;
        }

        // Decoration if non-solid or special materials
        if (!material.isSolid() || blockType == BlockType.DECORATIVE) {
            return StructuralRole.DECORATION;
        }

        return StructuralRole.UNKNOWN;
    }

    /**
     * Calculates corruption resistance based on block importance
     */
    public double getCorruptionResistance() {
        double baseResistance = 0.0;

        // Role-based resistance
        switch (role) {
            case FOUNDATION:
                baseResistance = 0.7; // Hard to corrupt foundations
                break;
            case PILLAR:
                baseResistance = 0.6; // Important structural elements
                break;
            case WALL:
                baseResistance = 0.4; // Moderate resistance
                break;
            case FLOOR:
            case ROOF:
                baseResistance = 0.3; // Easier to corrupt surfaces
                break;
            case DECORATION:
                baseResistance = 0.1; // Very low resistance
                break;
            default:
                baseResistance = 0.2;
        }

        // Type-based modifier
        switch (blockType) {
            case FUNCTIONAL:
                baseResistance += 0.3; // Protect functional blocks
                break;
            case STRUCTURAL:
                baseResistance += 0.1; // Slight bonus for structural
                break;
            case DECORATIVE:
                baseResistance -= 0.1; // Easier to corrupt decorations
                break;
        }

        return Math.max(0.0, Math.min(1.0, baseResistance));
    }

    /**
     * Gets corruption priority (higher = corrupt first)
     */
    public double getCorruptionPriority() {
        return 1.0 - getCorruptionResistance();
    }

    /**
     * Determines if this block should be preserved during corruption
     */
    public boolean shouldPreserve() {
        // Always try to preserve functional blocks
        if (blockType == BlockType.FUNCTIONAL) return true;

        // Preserve some structural elements for architectural integrity
        if (role == StructuralRole.FOUNDATION || role == StructuralRole.PILLAR) {
            return Math.random() < 0.4; // 40% chance to preserve
        }

        return Math.random() < getCorruptionResistance();
    }

    /**
     * Gets the ideal corruption material for this block
     */
    public Material getCorruptionMaterial(double corruptionIntensity) {
        // High intensity corruption
        if (corruptionIntensity > 0.8) {
            switch (role) {
                case FOUNDATION:
                case PILLAR:
                    return Material.SCULK_CATALYST; // Major corruption nodes
                case WALL:
                    return Material.SCULK;
                case FLOOR:
                case ROOF:
                    return Material.DEEPSLATE_BRICKS;
                default:
                    return Material.SCULK_VEIN;
            }
        }

        // Medium intensity corruption
        if (corruptionIntensity > 0.5) {
            switch (blockType) {
                case STRUCTURAL:
                    return Math.random() < 0.6 ? Material.SCULK : Material.COBBLED_DEEPSLATE;
                case DECORATIVE:
                    return Material.SCULK_VEIN;
                case CONNECTOR:
                    return Material.SCULK_VEIN;
                default:
                    return Material.SCULK;
            }
        }

        // Light corruption
        return Math.random() < 0.7 ? Material.SCULK_VEIN : Material.DEEPSLATE;
    }

    /**
     * Checks if this block can spread corruption to neighbors
     */
    public boolean canSpreadCorruption() {
        return blockType == BlockType.STRUCTURAL &&
                (role == StructuralRole.WALL || role == StructuralRole.FLOOR);
    }

    /**
     * Gets corruption spread radius from this block
     */
    public int getCorruptionSpreadRadius() {
        switch (role) {
            case FOUNDATION:
            case PILLAR:
                return 4; // Major spread points
            case WALL:
                return 2; // Moderate spread
            case FLOOR:
            case ROOF:
                return 3; // Good spread along surfaces
            default:
                return 1; // Minimal spread
        }
    }

    /**
     * Calculates compatibility with nearby corruption
     */
    public double getCorruptionCompatibility(Material corruptionMaterial) {
        if (material == corruptionMaterial) return 1.0;

        // Check material compatibility
        if (isSculkMaterial(corruptionMaterial)) {
            switch (blockType) {
                case STRUCTURAL:
                    return 0.8; // Good compatibility
                case DECORATIVE:
                    return 0.9; // Very good compatibility
                case FUNCTIONAL:
                    return 0.2; // Poor compatibility
                default:
                    return 0.6;
            }
        }

        if (isAncientMaterial(corruptionMaterial)) {
            return blockType == BlockType.STRUCTURAL ? 0.9 : 0.5;
        }

        return 0.5; // Default compatibility
    }

    /**
     * Gets adjacent block locations for spread calculations
     */
    public Location[] getAdjacentLocations() {
        return new Location[] {
                location.clone().add(1, 0, 0),
                location.clone().add(-1, 0, 0),
                location.clone().add(0, 1, 0),
                location.clone().add(0, -1, 0),
                location.clone().add(0, 0, 1),
                location.clone().add(0, 0, -1)
        };
    }

    private boolean isSculkMaterial(Material material) {
        return material == Material.SCULK ||
                material == Material.SCULK_VEIN ||
                material == Material.SCULK_CATALYST ||
                material == Material.SCULK_SHRIEKER ||
                material == Material.SCULK_SENSOR;
    }

    private boolean isAncientMaterial(Material material) {
        return material == Material.DEEPSLATE_BRICKS ||
                material == Material.DEEPSLATE_TILES ||
                material == Material.POLISHED_DEEPSLATE ||
                material == Material.COBBLED_DEEPSLATE ||
                material == Material.BLACKSTONE;
    }

    // Getters
    public BlockType getBlockType() { return blockType; }
    public StructuralRole getRole() { return role; }
    public Location getLocation() { return location.clone(); }
    public Material getMaterial() { return material; }

    /**
     * Checks if this is a critical structural element
     */
    public boolean isCritical() {
        return role == StructuralRole.FOUNDATION ||
                role == StructuralRole.PILLAR ||
                blockType == BlockType.FUNCTIONAL;
    }

    /**
     * Checks if this block is decorative only
     */
    public boolean isDecorative() {
        return blockType == BlockType.DECORATIVE ||
                role == StructuralRole.DECORATION;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        StructureBlock that = (StructureBlock) obj;
        return Objects.equals(location, that.location) &&
                material == that.material;
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, material);
    }

    @Override
    public String toString() {
        return "StructureBlock{" +
                "location=" + String.format("%.0f,%.0f,%.0f",
                location.getX(), location.getY(), location.getZ()) +
                ", material=" + material +
                ", type=" + blockType +
                ", role=" + role +
                ", resistance=" + String.format("%.2f", getCorruptionResistance()) +
                '}';
    }
}