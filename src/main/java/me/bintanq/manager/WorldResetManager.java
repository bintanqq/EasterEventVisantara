package me.bintanq.manager;

import me.bintanq.EasterEventVisantara;
import me.bintanq.task.StructurePlacementTask;
import me.bintanq.util.StructureTracker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.CreateWorldOptions;
import org.mvplugins.multiverse.core.world.options.DeleteWorldOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.stream.Stream;

public class WorldResetManager {

    private final EasterEventVisantara plugin;
    private final StructureTracker     structureTracker;

    private volatile boolean resetting = false;

    public WorldResetManager(EasterEventVisantara plugin, StructureTracker structureTracker) {
        this.plugin           = plugin;
        this.structureTracker = structureTracker;
    }

    public boolean isResetting() { return resetting; }

    public boolean isWorldReady() {
        StructurePlacementTask spt = plugin.getStructurePlacementTask();
        return spt == null || !spt.isSeeding();
    }

    public Location getFallbackLocation() {
        ConfigManager cfg = plugin.getConfigManager();
        World w = Bukkit.getWorld(cfg.getFallbackSpawnWorld());
        if (w != null) {
            return new Location(w,
                    cfg.getFallbackSpawnX(), cfg.getFallbackSpawnY(), cfg.getFallbackSpawnZ(),
                    cfg.getFallbackSpawnYaw(), cfg.getFallbackSpawnPitch());
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void beginReset() {
        if (resetting) { plugin.getLogger().warning("[WorldResetManager] Operasi sedang berjalan."); return; }
        resetting = true;
        plugin.getLogger().info("[WorldResetManager] Memulai reset world...");
        Bukkit.getScheduler().runTask(plugin, this::step1_evacuateAndDelete);
    }

    public void createFresh() {
        if (resetting) { plugin.getLogger().warning("[WorldResetManager] Operasi sedang berjalan."); return; }
        resetting = true;
        plugin.getLogger().info("[WorldResetManager] Membuat resource world baru...");
        Bukkit.getScheduler().runTask(plugin, () -> {
            String worldName = plugin.getConfigManager().getResourceWorldName();
            Bukkit.broadcastMessage(plugin.getConfigManager().getMsgWorldCreating());
            step2_recreate(worldName);
        });
    }

    private void evacuatePlayers(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        Location safe = getFallbackLocation();
        for (Player p : world.getPlayers()) {
            p.teleport(safe);
            p.sendMessage(plugin.getConfigManager().getMsgWorldResetting());
        }
    }

    private void step1_evacuateAndDelete() {
        String worldName = plugin.getConfigManager().getResourceWorldName();
        World  world     = Bukkit.getWorld(worldName);

        Bukkit.broadcastMessage(plugin.getConfigManager().getMsgWorldResetting());
        evacuatePlayers(worldName);

        WorldManager wm = getWorldManager();
        if (wm != null) {
            wm.getWorld(worldName).peek(mvWorld ->
                    wm.deleteWorld(DeleteWorldOptions.world(mvWorld))
                            .onSuccess(deleted -> {
                                plugin.getLogger().info("[WorldResetManager] MV5 hapus '" + worldName + "'.");
                                step2_recreate(worldName);
                            })
                            .onFailure(reason -> {
                                plugin.getLogger().warning("[WorldResetManager] MV5 delete gagal: " + reason + " — fallback.");
                                if (world != null) Bukkit.unloadWorld(world, false);
                                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> step2_fallbackDeleteFolder(worldName));
                            })
            ).onEmpty(() -> {
                if (world != null) Bukkit.unloadWorld(world, false);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> step2_fallbackDeleteFolder(worldName));
            });
        } else {
            if (world != null) Bukkit.unloadWorld(world, false);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> step2_fallbackDeleteFolder(worldName));
        }
    }

    private void step2_fallbackDeleteFolder(String worldName) {
        File folder = new File(Bukkit.getWorldContainer(), worldName);
        if (folder.exists()) {
            try (Stream<Path> walk = Files.walk(folder.toPath())) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(f -> { if (!f.delete()) plugin.getLogger().warning("[WorldResetManager] Gagal hapus: " + f); });
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[WorldResetManager] Error hapus folder", e);
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> step2_recreate(worldName));
    }

    private void step2_recreate(String worldName) {
        WorldManager wm = getWorldManager();
        if (wm == null) {
            plugin.getLogger().severe("[WorldResetManager] MV5 tidak tersedia.");
            Bukkit.broadcastMessage(plugin.getConfigManager().getMsgWorldCreateFailed());
            finishReset();
            return;
        }

        long seed = System.currentTimeMillis();
        plugin.getLogger().info("[WorldResetManager] Membuat '" + worldName + "' seed=" + seed);

        wm.createWorld(CreateWorldOptions.worldName(worldName)
                        .environment(World.Environment.NORMAL)
                        .seed(seed)
                        .generateStructures(true))
                .onSuccess(newWorld -> {
                    plugin.getLogger().info("[WorldResetManager] World dibuat.");
                    applyWorldBorder(worldName);
                    structureTracker.clearAll();
                    Bukkit.broadcastMessage(plugin.getConfigManager().getMsgWorldCreateDone());
                    finishReset();

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        StructurePlacementTask spt = plugin.getStructurePlacementTask();
                        if (spt != null) spt.seedInitialStructures(worldName);
                    }, 40L);
                })
                .onFailure(reason -> {
                    plugin.getLogger().severe("[WorldResetManager] Gagal buat world: " + reason);
                    Bukkit.broadcastMessage(plugin.getConfigManager().getMsgWorldCreateFailed());
                    finishReset();
                });
    }

    private void applyWorldBorder(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        // getWorldBorderSize() = ukuran sisi (misal 5000 = area 5000x5000 blok)
        // setSize() di Bukkit menerima DIAMETER, jadi nilainya sama dengan ukuran sisi
        double size = plugin.getConfigManager().getWorldBorderSize();
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(size);
        border.setDamageAmount(0.5);
        border.setDamageBuffer(5.0);
        border.setWarningDistance(10);
        border.setWarningTime(15);
        plugin.getLogger().info("[WorldResetManager] Border diameter=" + size
                + " (±" + (size / 2) + " blok dari 0,0) diterapkan pada '" + worldName + "'.");
    }

    private void finishReset() {
        resetting = false;
        plugin.getLogger().info("[WorldResetManager] Operasi selesai.");
    }

    private WorldManager getWorldManager() {
        try { return MultiverseCoreApi.get().getWorldManager(); }
        catch (IllegalStateException e) {
            plugin.getLogger().warning("[WorldResetManager] Singleton tidak tersedia, coba ServicesManager...");
        }
        var provider = Bukkit.getServicesManager().getRegistration(MultiverseCoreApi.class);
        if (provider != null) return provider.getProvider().getWorldManager();
        plugin.getLogger().severe("[WorldResetManager] Multiverse-Core 5 tidak ter-load!");
        return null;
    }
}