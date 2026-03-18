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

    public String getSchematicName(String variant) {
        return plugin.getConfigManager().getStructureVariantSchematic(variant);
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

            int surfaceY = world.getHighestBlockYAt(targetX, targetZ,
                    org.bukkit.HeightMap.WORLD_SURFACE);
            if (surfaceY < minY) continue;

            // Cek apakah seluruh footprint struktur punya solid ground
            if (!isGroundSolidUnderFootprint(world, targetX, surfaceY, targetZ, footR)) continue;

            // Cek ruang udara di atas cukup untuk bangunan
            if (!hasAirAbove(world, targetX, surfaceY, targetZ, footR, clearH)) continue;

            Location candidate = new Location(world, targetX, surfaceY + 1, targetZ);
            if (!structureTracker.isClearOfStructures(candidate, minDist)) continue;

            return candidate;
        }
        return null;
    }

    /**
     * Cek apakah semua blok dalam footprint radius punya solid ground
     * di Y yang sama (toleransi maxHeightDiff blok).
     *
     * Cara kerja:
     * 1. Ambil surfaceY di tiap titik footprint
     * 2. Pastikan block di sana solid (bukan air/liquid/tanaman)
     * 3. Pastikan selisih ketinggian antar titik tidak melebihi maxHeightDiff
     *
     * Ini prevent struktur melayang di sisi karena terrain tidak rata.
     */
    private boolean isGroundSolidUnderFootprint(World world, int cx, int surfaceY,
                                                int cz, int footprintRadius) {
        ConfigManager cfg      = plugin.getConfigManager();
        int           maxDiff  = cfg.getStructureFlatMaxHeightDiff();
        int           minH     = surfaceY;
        int           maxH     = surfaceY;

        // Grid check setiap 2 blok supaya tidak terlalu berat tapi tetap akurat
        int step = Math.max(1, footprintRadius / 3);

        for (int dx = -footprintRadius; dx <= footprintRadius; dx += step) {
            for (int dz = -footprintRadius; dz <= footprintRadius; dz += step) {
                int bx = cx + dx;
                int bz = cz + dz;
                int h  = world.getHighestBlockYAt(bx, bz, org.bukkit.HeightMap.WORLD_SURFACE);
                Block top = world.getBlockAt(bx, h, bz);

                // Block di titik ini tidak boleh jadi permukaan yang tidak aman
                if (isUnsafeSurface(top.getType())) return false;

                // Tracking min/max untuk cek kemiringan
                if (h < minH) minH = h;
                if (h > maxH) maxH = h;

                // Kalau sudah terlalu miring, langsung reject
                if ((maxH - minH) > maxDiff) return false;
            }
        }

        return true;
    }

    /**
     * Cek apakah ada ruang udara bebas di atas footprint struktur.
     * Ini prevent spawn di bawah lereng, gua, atau pohon besar.
     */
    private boolean hasAirAbove(World world, int cx, int surfaceY,
                                int cz, int footprintRadius, int clearHeight) {
        int step = Math.max(1, footprintRadius / 2);

        for (int dx = -footprintRadius; dx <= footprintRadius; dx += step) {
            for (int dz = -footprintRadius; dz <= footprintRadius; dz += step) {
                for (int dy = 1; dy <= clearHeight; dy++) {
                    Block b = world.getBlockAt(cx + dx, surfaceY + dy, cz + dz);
                    if (isSolidBlocking(b.getType())) return false;
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

        int foundationDepth = cfg.getStructureVariantFoundationDepth(variant);
        int yOffset         = cfg.getStructureVariantPasteYOffset(variant);
        loc.setY(loc.getY() - foundationDepth + yOffset);

        structureTracker.reserve(loc, variant);
        return new StructurePlacement(variant, getSchematicName(variant), loc);
    }

    public static final class StructurePlacement {
        public final String   variant;
        public final String   schematicFile;
        public final Location location;

        public StructurePlacement(String variant, String schematicFile, Location location) {
            this.variant       = variant;
            this.schematicFile = schematicFile;
            this.location      = location;
        }

        @Override
        public String toString() {
            return "StructurePlacement{variant='" + variant + "', schem='" + schematicFile
                    + "', loc=" + String.format("%.0f,%.0f,%.0f", location.getX(), location.getY(), location.getZ()) + "}";
        }
    }
}