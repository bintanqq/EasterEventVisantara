package me.bintanq.util;

import io.lumine.mythic.bukkit.MythicBukkit;
import me.bintanq.EasterEventVisantara;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BalloonTracker {

    private final EasterEventVisantara plugin;

    private final ConcurrentHashMap<UUID, Long>   activeBalloons  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> playerBalloonCount = new ConcurrentHashMap<>();

    private BukkitTask cleanupTask;

    public BalloonTracker(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    public void register(UUID entityUUID, UUID ownerPlayerUUID) {
        long despawnAt = System.currentTimeMillis()
                + (plugin.getConfigManager().getDespawnSeconds() * 1000L);
        activeBalloons.put(entityUUID, despawnAt);

        if (ownerPlayerUUID != null) {
            playerBalloonCount.merge(ownerPlayerUUID, 1, Integer::sum);
        }
    }

    public void unregister(UUID entityUUID, UUID ownerPlayerUUID) {
        activeBalloons.remove(entityUUID);

        if (ownerPlayerUUID != null) {
            playerBalloonCount.computeIfPresent(ownerPlayerUUID, (k, v) -> {
                int newVal = v - 1;
                return newVal <= 0 ? null : newVal;
            });
        }
    }

    public boolean isTracked(UUID entityUUID) {
        return activeBalloons.containsKey(entityUUID);
    }

    public int getActiveCount() {
        return activeBalloons.size();
    }

    public int getPlayerBalloonCount(UUID playerUUID) {
        return playerBalloonCount.getOrDefault(playerUUID, 0);
    }

    public ConcurrentHashMap<UUID, Integer> getPlayerBalloonCountMap() {
        return playerBalloonCount;
    }

    public void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : activeBalloons.entrySet()) {
                if (now >= entry.getValue()) {
                    UUID uuid = entry.getKey();
                    Bukkit.getScheduler().runTask(plugin, () -> removeBalloon(uuid, null));
                }
            }
        }, 40L, 40L);
    }

    public void cancelCleanupTask() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
    }

    private void removeBalloon(UUID uuid, UUID ownerUUID) {
        if (!activeBalloons.containsKey(uuid)) return;

        unregister(uuid, ownerUUID);

        Entity entity = Bukkit.getEntity(uuid);
        if (entity == null || entity.isDead()) return;

        try {
            MythicBukkit.inst().getMobManager()
                    .getActiveMob(uuid)
                    .ifPresent(mob -> mob.getEntity().remove());
        } catch (Exception ignored) {
            entity.remove();
        }

        if (plugin.isDebugMode()) {
            String msg = plugin.getConfigManager().getMsgDebugDespawn(uuid.toString());
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("easter.admin"))
                    .forEach(p -> p.sendMessage(msg));
        }
    }

    public void despawnAll() {
        new HashSet<>(activeBalloons.keySet()).forEach(uuid -> removeBalloon(uuid, null));
        activeBalloons.clear();
        playerBalloonCount.clear();
    }
}