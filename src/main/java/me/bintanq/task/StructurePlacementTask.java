package me.bintanq.task;

import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import me.bintanq.manager.EventWindowManager;
import me.bintanq.manager.StructureManager;
import me.bintanq.manager.StructureManager.StructurePlacement;
import me.bintanq.util.LootPopulator;
import me.bintanq.util.NbtPasteUtil;
import me.bintanq.util.StructureTracker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class StructurePlacementTask implements Listener {

    private static final int MAX_PASTES_PER_TICK = 1;

    private final EasterEventVisantara plugin;
    private final StructureManager     structureManager;
    private final StructureTracker     structureTracker;
    private final NbtPasteUtil         pasteUtil;
    private final EventWindowManager   eventWindow;
    private final LootPopulator        lootPopulator;

    private final Queue<StructurePlacement> pasteQueue     = new ConcurrentLinkedQueue<>();
    private final Set<String>              processedChunks = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean seeding         = new AtomicBoolean(false);
    private final AtomicBoolean seedDone        = new AtomicBoolean(false);
    private final AtomicBoolean pasteInProgress = new AtomicBoolean(false);

    private BukkitTask drainTask;

    public StructurePlacementTask(EasterEventVisantara plugin,
                                  StructureManager structureManager,
                                  StructureTracker structureTracker,
                                  NbtPasteUtil pasteUtil,
                                  EventWindowManager eventWindow,
                                  LootPopulator lootPopulator) {
        this.plugin           = plugin;
        this.structureManager = structureManager;
        this.structureTracker = structureTracker;
        this.pasteUtil        = pasteUtil;
        this.eventWindow      = eventWindow;
        this.lootPopulator    = lootPopulator;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        drainTask = new BukkitRunnable() {
            @Override public void run() { drainQueue(); }
        }.runTaskTimer(plugin, 20L, 1L);
        plugin.getLogger().info("[StructurePlacementTask] Started.");
    }

    public void stop() {
        if (drainTask != null && !drainTask.isCancelled()) drainTask.cancel();
        ChunkLoadEvent.getHandlerList().unregister(this);
        pasteQueue.clear();
        processedChunks.clear();
        seeding.set(false);
        seedDone.set(false);
        pasteInProgress.set(false);
        plugin.getLogger().info("[StructurePlacementTask] Stopped.");
    }

    public boolean isSeeding()  { return seeding.get(); }
    public boolean isSeedDone() { return seedDone.get(); }

    public void seedInitialStructures(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[StructurePlacementTask] World '" + worldName + "' tidak ditemukan.");
            return;
        }

        seeding.set(true);
        seedDone.set(false);
        // Wajib clear supaya chunk yang sama bisa di-proses ulang saat /easter world seed dipanggil berulang
        processedChunks.clear();
        pasteQueue.clear();

        ConfigManager cfg = plugin.getConfigManager();
        // Hitung radius dari border size: border 5000 = 2500 blok ke tiap arah = ~156 chunk
        // Dibatasi max 64 chunk agar tidak terlalu lama, bisa di-override via seed-chunk-radius: -1
        int configRadius = cfg.getSeedChunkRadius();
        int borderRadius = (int) (cfg.getWorldBorderSize() / 2.0 / 16);
        int radius = configRadius > 0 ? Math.min(configRadius, 64) : Math.min(borderRadius, 64);
        int total  = (radius * 2 + 1) * (radius * 2 + 1);

        plugin.getLogger().info("[StructurePlacementTask] Seed dimulai: '" + worldName
                + "' radius=" + radius + " (" + total + " chunks)...");

        AtomicInteger done = new AtomicInteger(0);

        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                final int fcx = cx, fcz = cz;
                String key = worldName + ":" + cx + ":" + cz;

                if (!processedChunks.add(key)) {
                    if (done.incrementAndGet() == total) onSeedComplete(worldName);
                    continue;
                }

                world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                    // Semua logika (biome check + resolveForChunk) dijalankan di main thread
                    // karena getHighestBlockYAt dan getBlockAt tidak thread-safe untuk world baru
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            processChunk(world, chunk, fcx, fcz, worldName);
                        } finally {
                            if (done.incrementAndGet() == total) onSeedComplete(worldName);
                        }
                    });
                });
            }
        }
    }

    // Dipanggil dari ChunkLoadEvent (sudah di main thread)
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;

        World world = event.getWorld();
        ConfigManager cfg = plugin.getConfigManager();

        if (!world.getName().equals(cfg.getResourceWorldName())) return;
        if (!eventWindow.isEventActive()) return;

        Chunk  chunk    = event.getChunk();
        int    chunkX   = chunk.getX();
        int    chunkZ   = chunk.getZ();
        String chunkKey = world.getName() + ":" + chunkX + ":" + chunkZ;

        if (!processedChunks.add(chunkKey)) return;

        // Sudah di main thread, langsung proses
        processChunk(world, chunk, chunkX, chunkZ, world.getName());
    }

    /**
     * Logika utama per chunk. HARUS dipanggil dari main thread.
     */
    private void processChunk(World world, Chunk chunk, int chunkX, int chunkZ, String worldName) {
        ConfigManager cfg = plugin.getConfigManager();

        if (!isBiomeAllowed(world, chunk, cfg)) return;

        StructurePlacement placement = structureManager.resolveForChunk(chunkX, chunkZ, worldName);
        if (placement == null) return;

        pasteQueue.offer(placement);

        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[StructurePlacementTask] Queued: " + placement
                    + " (queue=" + pasteQueue.size() + ")");
        }
    }

    private void onSeedComplete(String worldName) {
        seeding.set(false);
        seedDone.set(true);
        plugin.getLogger().info("[StructurePlacementTask] Scan selesai '" + worldName
                + "'. Struktur di queue: " + pasteQueue.size());
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.broadcastMessage(plugin.getConfigManager().getMsgWorldSeedDone()));
    }

    private void drainQueue() {
        // Tunggu paste sebelumnya selesai sebelum mulai yang baru
        // Ini mencegah banyak FAWE operation jalan paralel yang menyebabkan high memory
        if (pasteInProgress.get()) return;
        if (pasteQueue.isEmpty()) return;

        StructurePlacement placement = pasteQueue.poll();
        if (placement == null) return;

        pasteInProgress.set(true);
        executePaste(placement);
    }

    private void executePaste(StructurePlacement placement) {
        plugin.getLogger().info("[StructurePlacementTask] Paste " + placement.variant
                + " @ " + formatLoc(placement.location));

        pasteUtil.pasteAsync(placement.schematicFile, placement.location, true)
                .thenAccept(success -> {
                    if (!success) {
                        plugin.getLogger().warning("[StructurePlacementTask] Paste gagal: " + placement.variant);
                        structureTracker.cancelReserve(placement.location);
                        pasteInProgress.set(false);
                        return;
                    }
                    structureTracker.register(placement.location, placement.variant);
                    plugin.getLogger().info("[StructurePlacementTask] OK: " + placement.variant
                            + " @ " + formatLoc(placement.location));
                    // Delay 60 tick (3 detik) sebelum isi chest dan buka paste berikutnya
                    // Ini beri waktu FAWE flush semua block changes ke dunia
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        lootPopulator.populateNearbyChests(placement.location, placement.variant);
                        pasteInProgress.set(false);
                    }, 60L);
                });
    }

    private boolean isBiomeAllowed(World world, Chunk chunk, ConfigManager cfg) {
        java.util.List<String> whitelist = cfg.getStructureBiomeWhitelist();
        if (whitelist.isEmpty()) return true;

        int bx = (chunk.getX() << 4) + 8;
        int bz = (chunk.getZ() << 4) + 8;
        int by = world.getHighestBlockYAt(bx, bz, org.bukkit.HeightMap.WORLD_SURFACE);

        String biome = world.getBiome(bx, by, bz).getKey().toString();
        // Normalisasi: strip "minecraft:" prefix untuk perbandingan
        String biomeShort = biome.startsWith("minecraft:") ? biome.substring(10) : biome;

        for (String allowed : whitelist) {
            String allowedShort = allowed.startsWith("minecraft:") ? allowed.substring(10) : allowed;
            if (biomeShort.equalsIgnoreCase(allowedShort)) return true;
        }

        // Hanya log kalau debug ON — hindari spam
        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[StructurePlacementTask] Skip biome '" + biome
                    + "' chunk " + chunk.getX() + "," + chunk.getZ());
        }
        return false;
    }

    private String formatLoc(org.bukkit.Location loc) {
        return String.format("%.0f,%.0f,%.0f[%s]",
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "?");
    }
}