package me.bintanq.task;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class BalloonSpawnTask extends BukkitRunnable {

    private final EasterEventVisantara plugin;
    private final Random rng = new Random();

    public BalloonSpawnTask(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        ConfigManager cfg = plugin.getConfigManager();
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) return;

        List<String> disabledWorlds = cfg.getDisabledWorlds();

        for (int i = 0; i < players.size(); i++) {
            final Player player = players.get(i);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isCancelled()) return;
                if (!player.isOnline()) return;
                if (disabledWorlds.contains(player.getWorld().getName())) return;

                if (!cfg.isCheckPerPlayer()) {
                    int globalCap = cfg.getGlobalBalloonCap();
                    if (globalCap != -1 && plugin.getBalloonTracker().getActiveCount() >= globalCap) return;
                }

                if (rng.nextDouble() > cfg.getSpawnChance()) return;

                int currentCount = plugin.getBalloonTracker().getPlayerBalloonCount(player.getUniqueId());
                int maxAllowed   = cfg.getPerPlayerBalloonCap();
                int slotsLeft    = maxAllowed - currentCount;

                if (slotsLeft <= 0) {
                    if (plugin.isDebugMode()) player.sendMessage(cfg.getMsgCapReached());
                    return;
                }

                int clampedMin   = Math.min(cfg.getPerPlayerMin(), slotsLeft);
                int range        = slotsLeft - clampedMin;
                int attemptCount = clampedMin + (range > 0 ? rng.nextInt(range + 1) : 0);

                Location playerLoc  = player.getLocation().clone();
                double   radius     = cfg.getSpawnRadius();
                UUID     playerUUID = player.getUniqueId();
                AtomicBoolean notified = new AtomicBoolean(false);

                for (int j = 0; j < attemptCount; j++) {
                    final long attemptDelay = j * 2L;

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (isCancelled()) return;

                        Location candidate = calculateSpawnLocation(playerLoc, radius, cfg);
                        if (candidate == null) return;

                        if (plugin.isDebugMode()) {
                            String msg = cfg.getMsgDebugSpawn(
                                    candidate.getX(), candidate.getY(), candidate.getZ(),
                                    candidate.getWorld().getName());
                            Bukkit.getOnlinePlayers().stream()
                                    .filter(p -> p.hasPermission("easter.admin"))
                                    .forEach(p -> p.sendMessage(msg));
                        }

                        boolean spawned = spawnBalloon(candidate, playerUUID);
                        if (spawned && notified.compareAndSet(false, true) && player.isOnline()) {
                            plugin.getNotifyManager().notifyPlayer(player);
                        }
                    }, attemptDelay);
                }
            }, (long) i);
        }
    }

    private Location calculateSpawnLocation(Location origin, double radius, ConfigManager cfg) {
        int minY     = cfg.getSpawnMinY();
        int floatMin = cfg.getFloatHeightMin();
        int floatMax = cfg.getFloatHeightMax();

        for (int attempt = 0; attempt < 15; attempt++) {
            double angle   = rng.nextDouble() * 2 * Math.PI;
            double dist    = radius * 0.3 + rng.nextDouble() * radius * 0.7;
            double offsetX = Math.cos(angle) * dist;
            double offsetZ = Math.sin(angle) * dist;

            Location check = origin.clone().add(offsetX, 0, offsetZ);

            int highestY = origin.getWorld().getHighestBlockYAt(check);
            if (highestY < minY) continue;

            check.setY(highestY);

            Location ground = descendToGround(check);
            if (ground == null) continue;
            if (ground.getBlockY() < minY) continue;

            Location surface = ground.clone().add(0, 1, 0);
            if (surface.getBlock().getType() != Material.AIR) continue;

            int floatHeight = floatMin + (floatMax > floatMin ? rng.nextInt(floatMax - floatMin + 1) : 0);
            surface.add(0, floatHeight, 0);

            return surface;
        }
        return null;
    }

    private Location descendToGround(Location top) {
        Location current = top.clone();

        for (int i = 0; i < 40; i++) {
            Material type = current.getBlock().getType();

            boolean isNotGround = Tag.LEAVES.isTagged(type)
                    || Tag.LOGS.isTagged(type)
                    || Tag.SAPLINGS.isTagged(type)
                    || type == Material.VINE
                    || type == Material.BAMBOO
                    || type == Material.SUGAR_CANE
                    || type == Material.AIR
                    || type == Material.CAVE_AIR
                    || type == Material.VOID_AIR
                    || type.name().contains("LEAVES")
                    || type.name().contains("LOG");

            if (!isNotGround) return current;

            current = current.clone().subtract(0, 1, 0);
        }

        return null;
    }

    private boolean spawnBalloon(Location loc, UUID playerUUID) {
        ConfigManager cfg = plugin.getConfigManager();

        if (cfg.isCheckPerPlayer()) {
            if (plugin.getBalloonTracker().getPlayerBalloonCount(playerUUID) >= cfg.getPerPlayerBalloonCap()) return false;
        } else {
            int globalCap = cfg.getGlobalBalloonCap();
            if (globalCap != -1 && plugin.getBalloonTracker().getActiveCount() >= globalCap) return false;
        }

        if (MythicBukkit.inst().getMobManager().getMythicMob(cfg.getMythicMobId()).isEmpty()) {
            plugin.getLogger().warning("MythicMob '" + cfg.getMythicMobId() + "' not found!");
            return false;
        }

        Entity entity;
        try {
            BukkitAPIHelper api = MythicBukkit.inst().getAPIHelper();
            entity = api.spawnMythicMob(cfg.getMythicMobId(), loc);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn MythicMob: " + e.getMessage());
            return false;
        }

        if (entity == null) return false;

        plugin.getBalloonTracker().register(entity.getUniqueId(), playerUUID);
        return true;
    }
}