package me.bintanq.task;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BalloonSpawnTask extends BukkitRunnable {

    private final EasterEventVisantara plugin;
    private final Random rng = new Random();
    private final AtomicInteger cursor = new AtomicInteger(0);

    public BalloonSpawnTask(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        ConfigManager cfg = plugin.getConfigManager();

        if (!cfg.isCheckPerPlayer()) {
            if (plugin.getBalloonTracker().getActiveCount() >= cfg.getGlobalBalloonCap()) return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) return;

        int batchSize = cfg.getMaxSpawnsPerBatch();
        int total     = players.size();
        int start     = cursor.getAndUpdate(c -> (c + batchSize) % total);

        for (int i = 0; i < batchSize; i++) {
            Player player = players.get((start + i) % total);

            if (cfg.isCheckPerPlayer()) {
                int currentCount = plugin.getBalloonTracker().getPlayerBalloonCount(player.getUniqueId());
                if (currentCount >= cfg.getPerPlayerBalloonCap()) {
                    if (plugin.isDebugMode()) {
                        player.sendMessage(plugin.getConfigManager().getMsgCapReached());
                    }
                    continue;
                }
            }

            if (rng.nextDouble() > cfg.getSpawnChance()) continue;

            Location playerLoc = player.getLocation().clone();
            double   radius    = cfg.getSpawnRadius();
            UUID     playerUUID = player.getUniqueId();

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Location candidate = calculateSpawnLocation(playerLoc, radius);
                if (candidate == null) return;

                if (plugin.isDebugMode()) {
                    String msg = plugin.getConfigManager().getMsgDebugSpawn(
                            candidate.getX(), candidate.getY(), candidate.getZ(),
                            candidate.getWorld().getName());
                    Bukkit.getScheduler().runTask(plugin, () ->
                            Bukkit.getOnlinePlayers().stream()
                                    .filter(p -> p.hasPermission("easter.admin"))
                                    .forEach(p -> p.sendMessage(msg)));
                }

                Bukkit.getScheduler().runTask(plugin, () -> spawnBalloon(candidate, playerUUID));
            });
        }
    }

    private Location calculateSpawnLocation(Location origin, double radius) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle   = rng.nextDouble() * 2 * Math.PI;
            double dist    = radius * 0.3 + rng.nextDouble() * radius * 0.7;
            double offsetX = Math.cos(angle) * dist;
            double offsetZ = Math.sin(angle) * dist;

            Location check = origin.clone().add(offsetX, 5, offsetZ);

            for (int dy = 0; dy <= 10; dy++) {
                Location surface = check.clone().subtract(0, dy, 0);
                if (!surface.getBlock().getType().isAir()) {
                    return surface.add(0, 1, 0);
                }
            }
        }
        return null;
    }

    private void spawnBalloon(Location loc, java.util.UUID playerUUID) {
        ConfigManager cfg = plugin.getConfigManager();

        if (cfg.isCheckPerPlayer()) {
            int currentCount = plugin.getBalloonTracker().getPlayerBalloonCount(playerUUID);
            if (currentCount >= cfg.getPerPlayerBalloonCap()) return;
        } else {
            if (plugin.getBalloonTracker().getActiveCount() >= cfg.getGlobalBalloonCap()) return;
        }

        if (MythicBukkit.inst().getMobManager().getMythicMob(cfg.getMythicMobId()).isEmpty()) {
            plugin.getLogger().warning("MythicMob '" + cfg.getMythicMobId() + "' tidak ditemukan!");
            return;
        }

        Entity entity;
        try {
            BukkitAPIHelper api = MythicBukkit.inst().getAPIHelper();
            entity = api.spawnMythicMob(cfg.getMythicMobId(), loc);
        } catch (Exception e) {
            plugin.getLogger().warning("Gagal spawn MythicMob: " + e.getMessage());
            return;
        }

        if (entity == null) return;

        plugin.getBalloonTracker().register(entity.getUniqueId(), playerUUID);
    }
}