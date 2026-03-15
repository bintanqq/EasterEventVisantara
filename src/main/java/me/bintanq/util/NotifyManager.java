package me.bintanq.util;

import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NotifyManager {

    private final EasterEventVisantara plugin;
    private final ConcurrentHashMap<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();

    public NotifyManager(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    public void notifyPlayer(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isNotifyOnSpawn()) return;

        List<String> types = cfg.getNotifyTypes();

        for (String type : types) {
            switch (type.toUpperCase()) {
                case "CHAT"      -> sendChat(player, cfg);
                case "ACTIONBAR" -> sendActionBar(player, cfg);
                case "BOSSBAR"   -> sendBossBar(player, cfg);
                default -> plugin.getLogger().warning("notify-type tidak valid: " + type
                        + ". Pilihan: CHAT, ACTIONBAR, BOSSBAR");
            }
        }

        // Sound diputar sekali saja terlepas dari berapa tipe notify yang aktif
        if (cfg.isNotifySoundEnabled()) {
            playSound(player, cfg);
        }
    }

    private void sendChat(Player player, ConfigManager cfg) {
        player.sendMessage(cfg.getMsgNotifyChat());
    }

    private void sendActionBar(Player player, ConfigManager cfg) {
        int durationSeconds = cfg.getNotifyDurationSeconds();
        String text = cfg.getMsgNotifyActionBar();
        int totalTicks = durationSeconds * 20;
        int[] ticksElapsed = {0};

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || ticksElapsed[0] >= totalTicks) {
                task.cancel();
                return;
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
            ticksElapsed[0] += 2;
        }, 0L, 2L);
    }

    private void sendBossBar(Player player, ConfigManager cfg) {
        UUID uuid = player.getUniqueId();

        removeBossBar(uuid);

        BarColor color;
        try {
            color = BarColor.valueOf(cfg.getBossBarColor().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("bossbar-color tidak valid: " + cfg.getBossBarColor() + ". Memakai PINK.");
            color = BarColor.PINK;
        }

        BarStyle style;
        try {
            style = BarStyle.valueOf(cfg.getBossBarStyle().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("bossbar-style tidak valid: " + cfg.getBossBarStyle() + ". Memakai SOLID.");
            style = BarStyle.SOLID;
        }

        BossBar bar = Bukkit.createBossBar(cfg.getMsgNotifyBossBar(), color, style);
        bar.setProgress(1.0);
        bar.addPlayer(player);
        activeBossBars.put(uuid, bar);

        int durationTicks = cfg.getNotifyDurationSeconds() * 20;
        int[] ticksElapsed = {0};

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || ticksElapsed[0] >= durationTicks) {
                task.cancel();
                removeBossBar(uuid);
                return;
            }
            double progress = 1.0 - ((double) ticksElapsed[0] / durationTicks);
            bar.setProgress(Math.max(0.0, progress));
            ticksElapsed[0] += 2;
        }, 0L, 2L);
    }

    private void playSound(Player player, ConfigManager cfg) {
        try {
            Sound sound = Sound.valueOf(cfg.getNotifySound().toUpperCase());
            player.playSound(player.getLocation(), sound, (float) cfg.getNotifySoundVolume(), (float) cfg.getNotifySoundPitch());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("notify-sound tidak valid: " + cfg.getNotifySound()
                    + ". Cek https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html");
        }
    }

    private void removeBossBar(UUID uuid) {
        BossBar old = activeBossBars.remove(uuid);
        if (old != null) old.removeAll();
    }

    public void removeAll() {
        activeBossBars.values().forEach(BossBar::removeAll);
        activeBossBars.clear();
    }
}