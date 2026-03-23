package me.bintanq.task;

import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import me.bintanq.manager.EventWindowManager;
import me.bintanq.manager.StructureManager;
import me.bintanq.manager.StructureManager.StructurePlacement;
import me.bintanq.util.LootPopulator;
import me.bintanq.util.NbtStructureUtil;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class StructurePlacementTask implements Listener {

    private static final int BATCH_SIZE        = 10;
    private static final long BATCH_DELAY_TICKS = 2L;

    private final EasterEventVisantara plugin;
    private final StructureManager     structureManager;
    private final StructureTracker     structureTracker;
    private final NbtStructureUtil     nbtStructureUtil;
    private final EventWindowManager   eventWindow;
    private final LootPopulator        lootPopulator;
    private final Random               rng = new Random();

    private final Queue<StructurePlacement> pasteQueue     = new ConcurrentLinkedQueue<>();
    private final Set<String>              processedChunks = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean seeding         = new AtomicBoolean(false);
    private final AtomicBoolean seedDone        = new AtomicBoolean(false);
    private final AtomicBoolean pasteInProgress = new AtomicBoolean(false);
    private final AtomicInteger seedProgress    = new AtomicInteger(0);
    private final AtomicInteger seedTotal       = new AtomicInteger(0);

    private BukkitTask drainTask;

    public StructurePlacementTask(EasterEventVisantara plugin,
                                  StructureManager structureManager,
                                  StructureTracker structureTracker,
                                  NbtStructureUtil nbtStructureUtil,
                                  EventWindowManager eventWindow,
                                  LootPopulator lootPopulator) {
        this.plugin           = plugin;
        this.structureManager = structureManager;
        this.structureTracker = structureTracker;
        this.nbtStructureUtil = nbtStructureUtil;
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
        seedProgress.set(0);
        seedTotal.set(0);
        plugin.getLogger().info("[StructurePlacementTask] Stopped.");
    }

    public boolean isSeeding()       { return seeding.get(); }
    public boolean isSeedDone()      { return seedDone.get(); }
    public int     getSeedProgress() { return seedProgress.get(); }
    public int     getSeedTotal()    { return seedTotal.get(); }

    public void seedInitialStructures(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[StructurePlacementTask] World '" + worldName + "' tidak ditemukan.");
            return;
        }

        seeding.set(true);
        seedDone.set(false);
        seedProgress.set(0);
        processedChunks.clear();
        pasteQueue.clear();
        pasteInProgress.set(false);

        ConfigManager cfg           = plugin.getConfigManager();
        int           maxStructures = cfg.getStructureMaxTotal();
        int           attemptsPerSlot = cfg.getAttemptsPerSlot();
        int           totalAttempts = maxStructures * attemptsPerSlot;
        double        half          = cfg.getWorldBorderSize() / 2.0;

        seedTotal.set(totalAttempts);

        plugin.getLogger().info("[StructurePlacementTask] Seed dimulai: '" + worldName
                + "' target=" + maxStructures + " struktur, "
                + totalAttempts + " attempt, batch=" + BATCH_SIZE + "...");

        List<int[]> coords = new ArrayList<>(totalAttempts);
        for (int i = 0; i < totalAttempts; i++) {
            int rx = (int) ((-half + rng.nextDouble() * cfg.getWorldBorderSize()) / 16);
            int rz = (int) ((-half + rng.nextDouble() * cfg.getWorldBorderSize()) / 16);
            coords.add(new int[]{rx, rz});
        }

        scheduleBatch(world, worldName, coords, 0, new AtomicInteger(0), maxStructures);
    }

    private void scheduleBatch(World world, String worldName, List<int[]> coords,
                               int offset, AtomicInteger done, int maxStructures) {
        if (offset >= coords.size()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int end = Math.min(offset + BATCH_SIZE, coords.size());
            int remaining = end - offset;
            AtomicInteger batchDone = new AtomicInteger(0);

            for (int i = offset; i < end; i++) {
                final int cx = coords.get(i)[0];
                final int cz = coords.get(i)[1];

                world.getChunkAtAsync(cx, cz).thenAccept(chunk ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                int current = structureTracker.getEntriesInWorld(worldName).size();
                                if (current < maxStructures) {
                                    tryPlaceAt(world, chunk, cx, cz, worldName);
                                }
                            } finally {
                                seedProgress.incrementAndGet();
                                int total = coords.size();
                                if (done.incrementAndGet() == total) {
                                    onSeedComplete(worldName);
                                } else if (batchDone.incrementAndGet() == remaining) {
                                    scheduleBatch(world, worldName, coords, end, done, maxStructures);
                                }
                            }
                        })
                );
            }
        }, BATCH_DELAY_TICKS);
    }

    private void tryPlaceAt(World world, Chunk chunk, int chunkX, int chunkZ, String worldName) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!isBiomeAllowed(world, chunk, cfg)) return;

        StructurePlacement placement = structureManager.resolveCandidate(world, chunkX, chunkZ, worldName);
        if (placement == null) return;

        pasteQueue.offer(placement);

        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[StructurePlacementTask] Queued: " + placement
                    + " (queue=" + pasteQueue.size() + ")");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;

        World world = event.getWorld();
        ConfigManager cfg = plugin.getConfigManager();

        if (!world.getName().equals(cfg.getResourceWorldName())) return;
        if (!eventWindow.isEventActive()) return;
        if (isSeeding()) return;

        Chunk  chunk    = event.getChunk();
        int    chunkX   = chunk.getX();
        int    chunkZ   = chunk.getZ();
        String chunkKey = world.getName() + ":" + chunkX + ":" + chunkZ;

        if (!processedChunks.add(chunkKey)) return;
        if (structureTracker.getEntriesInWorld(world.getName()).size() >= cfg.getStructureMaxTotal()) return;

        tryPlaceAt(world, chunk, chunkX, chunkZ, world.getName());
    }

    private void onSeedComplete(String worldName) {
        seeding.set(false);
        seedDone.set(true);
        int placed = structureTracker.getEntriesInWorld(worldName).size();
        plugin.getLogger().info("[StructurePlacementTask] Seed selesai '" + worldName
                + "'. Queue: " + pasteQueue.size() + " | Reserved: " + placed);
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.broadcastMessage(plugin.getConfigManager().getMsgWorldSeedDone()));
    }

    private void drainQueue() {
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

        nbtStructureUtil.pasteAsync(placement.nbtFile, placement.location, true)
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
        String biome      = world.getBiome(bx, by, bz).getKey().toString();
        String biomeShort = biome.startsWith("minecraft:") ? biome.substring(10) : biome;

        for (String allowed : whitelist) {
            String allowedShort = allowed.startsWith("minecraft:") ? allowed.substring(10) : allowed;
            if (biomeShort.equalsIgnoreCase(allowedShort)) return true;
        }

        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[StructurePlacementTask] Skip biome '" + biome + "'");
        }
        return false;
    }

    private String formatLoc(org.bukkit.Location loc) {
        return String.format("%.0f,%.0f,%.0f[%s]",
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "?");
    }
}