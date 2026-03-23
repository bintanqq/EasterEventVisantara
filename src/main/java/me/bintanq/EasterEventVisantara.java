package me.bintanq;

import me.bintanq.command.EasterCommand;
import me.bintanq.listener.BalloonListener;
import me.bintanq.listener.RobbitSpawnListener;
import me.bintanq.listener.WorldLockListener;
import me.bintanq.manager.ConfigManager;
import me.bintanq.manager.EventWindowManager;
import me.bintanq.manager.StructureManager;
import me.bintanq.manager.WorldResetManager;
import me.bintanq.task.BalloonFloatTask;
import me.bintanq.task.BalloonSpawnTask;
import me.bintanq.task.StructurePlacementTask;
import me.bintanq.task.WorldResetTask;
import me.bintanq.util.BalloonTracker;
import me.bintanq.util.LootPopulator;
import me.bintanq.util.NbtStructureUtil;
import me.bintanq.util.NotifyManager;
import me.bintanq.util.StructureTracker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class EasterEventVisantara extends JavaPlugin {

    private static EasterEventVisantara instance;

    private ConfigManager          configManager;
    private EventWindowManager     eventWindowManager;
    private WorldResetManager      worldResetManager;
    private StructureManager       structureManager;

    private BalloonTracker         balloonTracker;
    private NotifyManager          notifyManager;
    private StructureTracker       structureTracker;
    private NbtStructureUtil       nbtStructureUtil;
    private LootPopulator          lootPopulator;

    private BalloonSpawnTask       balloonSpawnTask;
    private BukkitTask             balloonFloatTask;
    private StructurePlacementTask structurePlacementTask;
    private WorldResetTask         worldResetTask;

    private RobbitSpawnListener    robbitSpawnListener;

    private volatile boolean debugMode = false;

    @Override
    public void onEnable() {
        instance = this;
        sendStartupArt();

        configManager = new ConfigManager(this);
        configManager.loadAll();

        balloonTracker   = new BalloonTracker(this);
        notifyManager    = new NotifyManager(this);
        structureTracker = new StructureTracker();
        structureTracker.init(this);
        structureTracker.loadFromFile();
        nbtStructureUtil = new NbtStructureUtil(this);
        lootPopulator    = new LootPopulator(this);

        eventWindowManager = new EventWindowManager(this);
        worldResetManager  = new WorldResetManager(this, structureTracker);
        structureManager   = new StructureManager(this, structureTracker);

        getServer().getPluginManager().registerEvents(new BalloonListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldLockListener(this), this);

        robbitSpawnListener = new RobbitSpawnListener(this, structureTracker, eventWindowManager);
        getServer().getPluginManager().registerEvents(robbitSpawnListener, this);

        EasterCommand cmd = new EasterCommand(this);
        getCommand("easter").setExecutor(cmd);
        getCommand("easter").setTabCompleter(cmd);

        startTasks();

        getLogger().info("EasterEventVisantara enabled successfully.");
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (balloonTracker      != null) balloonTracker.despawnAll();
        if (notifyManager       != null) notifyManager.removeAll();
        if (robbitSpawnListener != null) robbitSpawnListener.clearCooldowns();
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&d[EasterEvent] &cPlugin disabled. Goodbye!"));
    }

    public void startTasks() {
        if (balloonSpawnTask != null) cancelTasks();

        balloonSpawnTask = new BalloonSpawnTask(this);
        long intervalTicks = configManager.getSpawnIntervalMinutes() * 60L * 20L;
        balloonSpawnTask.runTaskTimer(this, 40L, intervalTicks);

        balloonFloatTask = new BalloonFloatTask(this).runTaskTimer(this, 5L, 2L);
        balloonTracker.startCleanupTask();

        structurePlacementTask = new StructurePlacementTask(
                this, structureManager, structureTracker,
                nbtStructureUtil, eventWindowManager, lootPopulator);
        structurePlacementTask.start();

        worldResetTask = new WorldResetTask(this, worldResetManager);
        worldResetTask.start();
    }

    public void cancelTasks() {
        if (balloonSpawnTask       != null && !balloonSpawnTask.isCancelled()) balloonSpawnTask.cancel();
        if (balloonFloatTask       != null && !balloonFloatTask.isCancelled()) balloonFloatTask.cancel();
        if (balloonTracker         != null) balloonTracker.cancelCleanupTask();
        if (structurePlacementTask != null) structurePlacementTask.stop();
        if (worldResetTask         != null) worldResetTask.stop();
    }

    public void reload() {
        cancelTasks();
        notifyManager.removeAll();
        robbitSpawnListener.clearCooldowns();
        configManager.loadAll();
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

    public static EasterEventVisantara getInstance()              { return instance; }
    public ConfigManager          getConfigManager()              { return configManager; }
    public EventWindowManager     getEventWindowManager()         { return eventWindowManager; }
    public WorldResetManager      getWorldResetManager()          { return worldResetManager; }
    public StructureManager       getStructureManager()           { return structureManager; }
    public BalloonTracker         getBalloonTracker()             { return balloonTracker; }
    public NotifyManager          getNotifyManager()              { return notifyManager; }
    public StructureTracker       getStructureTracker()           { return structureTracker; }
    public NbtStructureUtil       getNbtStructureUtil()           { return nbtStructureUtil; }
    public LootPopulator          getLootPopulator()              { return lootPopulator; }
    public StructurePlacementTask getStructurePlacementTask()     { return structurePlacementTask; }
    public boolean                isDebugMode()                   { return debugMode; }
    public void                   setDebugMode(boolean v)         { this.debugMode = v; }
}