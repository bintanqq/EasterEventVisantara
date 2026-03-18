package me.bintanq.util;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class StructureTracker {

    public static final class StructureEntry {
        public final String  worldName;
        public final String  variant;
        public final int     blockX;
        public final int     blockY;
        public final int     blockZ;
        public final boolean reserved;

        private volatile int robbitCount = 0;

        public StructureEntry(String worldName, String variant, int bx, int by, int bz, boolean reserved) {
            this.worldName = worldName;
            this.variant   = variant;
            this.blockX    = bx;
            this.blockY    = by;
            this.blockZ    = bz;
            this.reserved  = reserved;
        }

        public synchronized int  getRobbitCount()       { return robbitCount; }
        public synchronized void incrementRobbitCount() { robbitCount++; }
        public synchronized void decrementRobbitCount() { robbitCount = Math.max(0, robbitCount - 1); }

        public Location toLocation(org.bukkit.World world) {
            return new Location(world, blockX, blockY, blockZ);
        }

        @Override
        public String toString() {
            return "StructureEntry{world='" + worldName + "', variant='" + variant
                    + "', pos=" + blockX + "," + blockY + "," + blockZ
                    + ", robbits=" + robbitCount
                    + (reserved ? ", RESERVED" : "") + "}";
        }
    }

    private final ConcurrentHashMap<Long, StructureEntry> entries = new ConcurrentHashMap<>();

    /**
     * Reserve lokasi sebelum paste dimulai.
     * Ini mencegah chunk lain yang selesai async bersamaan mengisi lokasi
     * yang terlalu berdekatan sebelum paste selesai di-register.
     */
    public void reserve(Location loc, String variant) {
        long key = pack(loc.getBlockX(), loc.getBlockZ());
        entries.putIfAbsent(key, new StructureEntry(
                loc.getWorld().getName(), variant,
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                true));
    }

    /**
     * Konfirmasi reservasi setelah paste berhasil (ubah reserved=false).
     */
    public void register(Location loc, String variant) {
        long key = pack(loc.getBlockX(), loc.getBlockZ());
        entries.put(key, new StructureEntry(
                loc.getWorld().getName(), variant,
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                false));
    }

    /**
     * Batalkan reservasi jika paste gagal.
     */
    public void cancelReserve(Location loc) {
        long key = pack(loc.getBlockX(), loc.getBlockZ());
        StructureEntry e = entries.get(key);
        if (e != null && e.reserved) entries.remove(key);
    }

    public void unregister(Location loc) {
        entries.remove(pack(loc.getBlockX(), loc.getBlockZ()));
    }

    public boolean isClearOfStructures(Location loc, int minDistance) {
        int x   = loc.getBlockX();
        int z   = loc.getBlockZ();
        int md2 = minDistance * minDistance;
        String worldName = loc.getWorld().getName();

        for (StructureEntry e : entries.values()) {
            if (!e.worldName.equals(worldName)) continue;
            int dx = e.blockX - x;
            int dz = e.blockZ - z;
            if ((dx * dx + dz * dz) < md2) return false;
        }
        return true;
    }

    public StructureEntry getNearestEntry(Location loc, double radius) {
        double closest = radius * radius;
        StructureEntry result = null;
        String worldName = loc.getWorld().getName();
        double lx = loc.getX(), lz = loc.getZ();

        for (StructureEntry e : entries.values()) {
            if (!e.worldName.equals(worldName)) continue;
            if (e.reserved) continue;
            double dx = e.blockX - lx;
            double dz = e.blockZ - lz;
            double d2 = dx * dx + dz * dz;
            if (d2 <= closest) { closest = d2; result = e; }
        }
        return result;
    }

    public List<StructureEntry> getEntriesInWorld(String worldName) {
        List<StructureEntry> list = new ArrayList<>();
        for (StructureEntry e : entries.values()) {
            if (e.worldName.equals(worldName) && !e.reserved) list.add(e);
        }
        return list;
    }

    public int getTotalCount() { return entries.size(); }

    public void clearAll() { entries.clear(); }

    private static long pack(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }
}