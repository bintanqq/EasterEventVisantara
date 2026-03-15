package me.bintanq;

import me.bintanq.command.EasterCommand;
import me.bintanq.listener.BalloonListener;
import me.bintanq.manager.ConfigManager;
import me.bintanq.task.BalloonSpawnTask;
import me.bintanq.util.BalloonTracker;
import org.bukkit.plugin.java.JavaPlugin;

public class EasterEventVisantara extends JavaPlugin {

    private static EasterEventVisantara instance;
    private ConfigManager configManager;
    private BalloonTracker balloonTracker;
    private BalloonSpawnTask balloonSpawnTask;
    private volatile boolean debugMode = false;

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        configManager.loadAll();

        balloonTracker = new BalloonTracker(this);

        getServer().getPluginManager().registerEvents(new BalloonListener(this), this);

        EasterCommand cmd = new EasterCommand(this);
        getCommand("easter").setExecutor(cmd);
        getCommand("easter").setTabCompleter(cmd);

        startTasks();

        getLogger().info("EasterEventVisantara v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        cancelTasks();
        balloonTracker.despawnAll();
        getLogger().info("EasterEventVisantara disabled.");
    }

    public void startTasks() {
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
        configManager.loadAll();
        startTasks();
    }

    public static EasterEventVisantara getInstance() { return instance; }
    public ConfigManager getConfigManager()          { return configManager; }
    public BalloonTracker getBalloonTracker()         { return balloonTracker; }
    public boolean isDebugMode()                     { return debugMode; }
    public void setDebugMode(boolean v)              { this.debugMode = v; }
}