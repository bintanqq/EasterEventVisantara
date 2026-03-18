package me.bintanq.task;

import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import me.bintanq.manager.WorldResetManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class WorldResetTask extends BukkitRunnable {

    private static final long INTERVAL_TICKS = 20L * 60L;
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Jakarta");

    private final EasterEventVisantara plugin;
    private final WorldResetManager    resetManager;

    private int lastTriggeredMinuteOfDay = -1;
    private BukkitTask scheduledTask;

    public WorldResetTask(EasterEventVisantara plugin, WorldResetManager resetManager) {
        this.plugin       = plugin;
        this.resetManager = resetManager;
    }

    public void start() {
        scheduledTask = runTaskTimerAsynchronously(plugin, INTERVAL_TICKS, INTERVAL_TICKS);
        plugin.getLogger().info("[WorldResetTask] Reset harian dijadwalkan jam "
                + plugin.getConfigManager().getWorldResetTime() + " WIB.");
    }

    public void stop() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) scheduledTask.cancel();
    }

    @Override
    public void run() {
        ConfigManager cfg = plugin.getConfigManager();

        ZonedDateTime now = ZonedDateTime.now(TIMEZONE);
        int currentMod = now.getHour() * 60 + now.getMinute();
        int targetMod  = cfg.getWorldResetHour() * 60 + cfg.getWorldResetMinute();

        if (currentMod != targetMod) return;
        if (lastTriggeredMinuteOfDay == currentMod) return;

        lastTriggeredMinuteOfDay = currentMod;

        plugin.getLogger().info("[WorldResetTask] Waktu reset tercapai ("
                + String.format("%02d:%02d", cfg.getWorldResetHour(), cfg.getWorldResetMinute())
                + " WIB). Memulai reset...");

        Bukkit.getScheduler().runTask(plugin, resetManager::beginReset);
    }
}