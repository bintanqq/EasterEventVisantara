package me.bintanq.util;

import me.bintanq.EasterEventVisantara;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    private final ConcurrentHashMap<Long, StructureEntry> entries     = new ConcurrentHashMap<>();
    private final Set<Long>                               cooldownSet = ConcurrentHashMap.newKeySet();

    private EasterEventVisantara plugin;
    private File                 saveFile;

    public StructureTracker() {}

    public void init(EasterEventVisantara plugin) {
        this.plugin   = plugin;
        this.saveFile = new File(plugin.getDataFolder(), "structures.yml");
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveToFile() {
        if (saveFile == null) return;
        YamlConfiguration yml = new YamlConfiguration();
        int i = 0;
        for (StructureEntry e : entries.values()) {
            if (e.reserved) continue;
            String base = "structures." + i;
            yml.set(base + ".world",   e.worldName);
            yml.set(base + ".variant", e.variant);
            yml.set(base + ".x",       e.blockX);
            yml.set(base + ".y",       e.blockY);
            yml.set(base + ".z",       e.blockZ);
            i++;
        }
        try {
            yml.save(saveFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("[StructureTracker] Gagal save: " + ex.getMessage());
        }
    }

    public void loadFromFile() {
        if (saveFile == null || !saveFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(saveFile);
        var sec = yml.getConfigurationSection("structures");
        if (sec == null) return;
        int loaded = 0;
        for (String key : sec.getKeys(false)) {
            String base    = "structures." + key;
            String world   = yml.getString(base + ".world",   "");
            String variant = yml.getString(base + ".variant", "");
            int x = yml.getInt(base + ".x");
            int y = yml.getInt(base + ".y");
            int z = yml.getInt(base + ".z");
            if (world.isEmpty() || variant.isEmpty()) continue;
            long pk = pack(x, z);
            entries.put(pk, new StructureEntry(world, variant, x, y, z, false));
            loaded++;
        }
        if (loaded > 0)
            plugin.getLogger().info("[StructureTracker] Loaded " + loaded + " struktur dari file.");
    }


    public void reserve(Location loc, String variant) {
        long key = pack(loc.getBlockX(), loc.getBlockZ());
        entries.putIfAbsent(key, new StructureEntry(
                loc.getWorld().getName(), variant,
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), true));
    }

    public void register(Location loc, String variant) {
        long key = pack(loc.getBlockX(), loc.getBlockZ());
        entries.put(key, new StructureEntry(
                loc.getWorld().getName(), variant,
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), false));
        // Auto-save setiap kali struktur berhasil di-register
        if (plugin != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveToFile);
        }
    }

    public void cancelReserve(Location loc) {
        long key = pack(loc.getBlockX(), loc.getBlockZ());
        StructureEntry e = entries.get(key);
        if (e != null && e.reserved) entries.remove(key);
    }

    public void unregister(Location loc) {
        entries.remove(pack(loc.getBlockX(), loc.getBlockZ()));
        if (plugin != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveToFile);
        }
    }

    public void clearAll() {
        entries.clear();
        cooldownSet.clear();
        if (plugin != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveToFile);
        }
    }


    public void addCooldown(int chunkX, int chunkZ) {
        cooldownSet.add(pack(chunkX, chunkZ));
    }

    public boolean isOnCooldown(int chunkX, int chunkZ) {
        return cooldownSet.contains(pack(chunkX, chunkZ));
    }

    public void clearCooldowns() {
        cooldownSet.clear();
    }


    public boolean isClearOfStructures(Location loc, int minDistance) {
        int x        = loc.getBlockX();
        int z        = loc.getBlockZ();
        int md2      = minDistance * minDistance;
        String world = loc.getWorld().getName();

        for (StructureEntry e : entries.values()) {
            if (!e.worldName.equals(world)) continue;
            int dx = e.blockX - x;
            int dz = e.blockZ - z;
            if ((dx * dx + dz * dz) < md2) return false;
        }
        return true;
    }

    public StructureEntry getNearestEntry(Location loc, double radius) {
        double closest = radius * radius;
        StructureEntry result = null;
        String world = loc.getWorld().getName();
        double lx = loc.getX(), lz = loc.getZ();

        for (StructureEntry e : entries.values()) {
            if (!e.worldName.equals(world)) continue;
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

    private static long pack(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }
}