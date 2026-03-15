package me.bintanq;

import me.bintanq.command.EasterCommand;
import me.bintanq.listener.BalloonListener;
import me.bintanq.manager.ConfigManager;
import me.bintanq.task.BalloonSpawnTask;
import me.bintanq.util.BalloonTracker;
import me.bintanq.util.NotifyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class EasterEventVisantara extends JavaPlugin {

    private static EasterEventVisantara instance;
    private ConfigManager configManager;
    private BalloonTracker balloonTracker;
    private NotifyManager notifyManager;
    private BalloonSpawnTask balloonSpawnTask;
    private volatile boolean debugMode = false;

    @Override
    public void onEnable() {
        instance = this;

        // Visual Logger
        sendStartupArt();

        configManager = new ConfigManager(this);
        configManager.loadAll();

        balloonTracker = new BalloonTracker(this);
        notifyManager  = new NotifyManager(this);

        getServer().getPluginManager().registerEvents(new BalloonListener(this), this);

        EasterCommand cmd = new EasterCommand(this);
        getCommand("easter").setExecutor(cmd);
        getCommand("easter").setTabCompleter(cmd);

        startTasks();
    }

    private void sendStartupArt() {
        String version = getDescription().getVersion();
        String[] art = {
                "&d      .---.      ",
                "&d     / &e^ ^ &d\\     &fEasterEvent &dVisantara",
                "&d    | &e>   < &d|    &7Version: &f" + version,
                "&d    |  &e\\ /  &d|    &7Status:  &aOnline",
                "&d     \\  &e'  &d/     &7Author:  &fbintanq",
                "&d      '---'      "
        };

        Bukkit.getConsoleSender().sendMessage("");
        for (String line : art) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
        Bukkit.getConsoleSender().sendMessage("");
    }

    @Override
    public void onDisable() {
        cancelTasks();
        balloonTracker.despawnAll();
        notifyManager.removeAll();

        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&d[EasterEvent] &cPlugin has been disabled. Goodbye!"));
    }

    public void startTasks() {
        if (balloonSpawnTask != null) cancelTasks();
        balloonSpawnTask = new BalloonSpawnTask(this);
        long intervalTicks = configManager.getSpawnIntervalMinutes() * 60L * 20L;
        balloonSpawnTask.runTaskTimer(this, 40L, intervalTicks);
        balloonTracker.startCleanupTask();
    }

    public void cancelTasks() {
        if (balloonSpawnTask != null && !balloonSpawnTask.isCancelled()) {
            balloonSpawnTask.cancel();
        }
        balloonTracker.cancelCleanupTask();
    }

    public void reload() {
        cancelTasks();
        notifyManager.removeAll();
        configManager.loadAll();
        startTasks();
    }

    public static EasterEventVisantara getInstance() { return instance; }
    public ConfigManager getConfigManager()          { return configManager; }
    public BalloonTracker getBalloonTracker()         { return balloonTracker; }
    public NotifyManager getNotifyManager()           { return notifyManager; }
    public boolean isDebugMode()                     { return debugMode; }
    public void setDebugMode(boolean v)              { this.debugMode = v; }
}