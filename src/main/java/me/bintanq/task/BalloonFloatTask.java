package me.bintanq.task;

import me.bintanq.EasterEventVisantara;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BalloonFloatTask extends BukkitRunnable {

    private final EasterEventVisantara plugin;

    public BalloonFloatTask(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfigManager().isFloatEnabled()) return;

        double riseSpeed  = plugin.getConfigManager().getFloatRiseSpeed();
        int maxYOffset    = plugin.getConfigManager().getFloatMaxYOffset();
        ConcurrentHashMap<UUID, Long> active = plugin.getBalloonTracker().getActiveBalloons();

        if (active.isEmpty()) return;

        for (UUID uuid : active.keySet()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null || entity.isDead()) continue;

            if (maxYOffset != -1) {
                Double spawnY = plugin.getBalloonTracker().getBalloonSpawnY(uuid);
                if (spawnY != null && entity.getLocation().getY() >= spawnY + maxYOffset) {
                    // Already at max height, zero out vertical velocity to hover
                    entity.setVelocity(new Vector(0, 0, 0));
                    continue;
                }
            }

            entity.setVelocity(new Vector(0, riseSpeed, 0));
        }
    }
}