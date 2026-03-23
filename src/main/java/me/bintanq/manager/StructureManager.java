package me.bintanq.manager;

import me.bintanq.EasterEventVisantara;
import me.bintanq.util.StructureTracker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Random;

public class StructureManager {

    private final EasterEventVisantara plugin;
    private final StructureTracker structureTracker;
    private final Random rng = new Random();

    public StructureManager(EasterEventVisantara plugin, StructureTracker structureTracker) {
        this.plugin           = plugin;
        this.structureTracker = structureTracker;
    }

    public String pickVariant() {
        ConfigManager cfg      = plugin.getConfigManager();
        List<String>  variants = cfg.getStructureVariantNames();
        if (variants.isEmpty()) {
            plugin.getLogger().warning("[StructureManager] Tidak ada variant struktur di config.");
            return null;
        }
        int totalWeight = 0;
        for (String v : variants) totalWeight += cfg.getStructureVariantWeight(v);
        if (totalWeight <= 0) return null;

        int roll = rng.nextInt(totalWeight);
        int cumulative = 0;
        for (String v : variants) {
            cumulative += cfg.getStructureVariantWeight(v);
            if (roll < cumulative) return v;
        }
        return variants.get(variants.size() - 1);
    }

    public Location resolveCandidate(int chunkOriginX, int chunkOriginZ, String worldName) {
        ConfigManager cfg     = plugin.getConfigManager();
        int           minY    = cfg.getSpawnMinY();
        int           minDist = cfg.getStructureMinDistance();
        int           footR   = cfg.getStructureFlatCheckRadius();
        int           clearH  = cfg.getStructureClearHeightAbove();

        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) return null;

        for (int attempt = 0; attempt < 8; attempt++) {
            int offsetX = 4 + rng.nextInt(8);
            int offsetZ = 4 + rng.nextInt(8);
            int targetX = chunkOriginX + offsetX;
            int targetZ = chunkOriginZ + offsetZ;

            int centerY = world.getHighestBlockYAt(targetX, targetZ, org.bukkit.HeightMap.WORLD_SURFACE);
            if (centerY < minY) continue;

            FootprintResult fp = scanFootprint(world, targetX, centerY, targetZ, footR);
            if (!fp.valid) continue;
            if (fp.minY < minY) continue;

            if (!hasAirAbove(world, targetX, fp.minY, targetZ, footR, clearH)) continue;

            Location candidate = new Location(world, targetX, fp.minY + 1, targetZ);
            if (!structureTracker.isClearOfStructures(candidate, minDist)) continue;

            return candidate;
        }
        return null;
    }

    private static class FootprintResult {
        boolean valid;
        int     minY;
        FootprintResult(boolean valid, int minY) {
            this.valid = valid;
            this.minY  = minY;
        }
    }

    private FootprintResult scanFootprint(World world, int cx, int centerY, int cz, int radius) {
        int minH = centerY;
        int step = Math.max(2, radius / 2);

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int h     = world.getHighestBlockYAt(cx + dx, cz + dz, org.bukkit.HeightMap.WORLD_SURFACE);
                Block top = world.getBlockAt(cx + dx, h, cz + dz);

                if (isUnsafeSurface(top.getType())) {
                    return new FootprintResult(false, 0);
                }

                if (h < minH) minH = h;
            }
        }
        return new FootprintResult(true, minH);
    }

    private boolean hasAirAbove(World world, int cx, int surfaceY,
                                int cz, int radius, int clearHeight) {
        int step = Math.max(1, radius / 2);
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                for (int dy = 1; dy <= clearHeight; dy++) {
                    if (isSolidBlocking(world.getBlockAt(cx + dx, surfaceY + dy, cz + dz).getType()))
                        return false;
                }
            }
        }
        return true;
    }

    private boolean isUnsafeSurface(Material mat) {
        if (mat == Material.AIR || mat == Material.VOID_AIR || mat == Material.CAVE_AIR) return true;
        if (mat == Material.WATER || mat == Material.LAVA) return true;
        if (mat == Material.VINE || mat == Material.BAMBOO || mat == Material.SUGAR_CANE) return true;
        String name = mat.name();
        if (name.contains("LEAVES") || name.contains("LOG")) return true;
        if (name.equals("SHORT_GRASS") || name.equals("TALL_GRASS")) return true;
        if (name.equals("FERN") || name.equals("LARGE_FERN")) return true;
        if (name.contains("FLOWER") || name.contains("SAPLING")) return true;
        if (name.contains("MUSHROOM") || name.contains("BUSH")) return true;
        return false;
    }

    private boolean isSolidBlocking(Material mat) {
        if (mat == Material.AIR || mat == Material.VOID_AIR || mat == Material.CAVE_AIR) return false;
        if (mat == Material.WATER || mat == Material.LAVA) return false;
        if (mat == Material.VINE) return false;
        String name = mat.name();
        if (name.contains("LEAVES")) return false;
        if (name.equals("SHORT_GRASS") || name.equals("TALL_GRASS")) return false;
        if (name.equals("FERN") || name.equals("LARGE_FERN")) return false;
        return mat.isSolid();
    }

    public StructurePlacement resolveForChunk(int chunkX, int chunkZ, String worldName) {
        ConfigManager cfg = plugin.getConfigManager();
        if (rng.nextDouble() > cfg.getStructureSpawnChance()) return null;
        if (structureTracker.getEntriesInWorld(worldName).size() >= cfg.getStructureMaxTotal()) return null;

        String variant = pickVariant();
        if (variant == null) return null;

        Location loc = resolveCandidate(chunkX << 4, chunkZ << 4, worldName);
        if (loc == null) return null;

        int yOffset = cfg.getStructureVariantPasteYOffset(variant);
        if (yOffset != 0) loc.setY(loc.getY() + yOffset);

        structureTracker.reserve(loc, variant);
        return new StructurePlacement(variant, cfg.getStructureVariantNbtFile(variant), loc);
    }

    public StructurePlacement resolveCandidate(World world, int chunkX, int chunkZ, String worldName) {
        ConfigManager cfg = plugin.getConfigManager();
        if (structureTracker.getEntriesInWorld(worldName).size() >= cfg.getStructureMaxTotal()) return null;

        String variant = pickVariant();
        if (variant == null) return null;

        Location loc = resolveCandidate(chunkX << 4, chunkZ << 4, worldName);
        if (loc == null) return null;

        int yOffset = cfg.getStructureVariantPasteYOffset(variant);
        if (yOffset != 0) loc.setY(loc.getY() + yOffset);

        structureTracker.reserve(loc, variant);
        return new StructurePlacement(variant, cfg.getStructureVariantNbtFile(variant), loc);
    }

    public static final class StructurePlacement {
        public final String   variant;
        public final String   nbtFile;
        public final Location location;

        public StructurePlacement(String variant, String nbtFile, Location location) {
            this.variant  = variant;
            this.nbtFile  = nbtFile;
            this.location = location;
        }

        @Override
        public String toString() {
            return "StructurePlacement{variant='" + variant + "', nbt='" + nbtFile
                    + "', loc=" + String.format("%.0f,%.0f,%.0f", location.getX(), location.getY(), location.getZ()) + "}";
        }
    }
}