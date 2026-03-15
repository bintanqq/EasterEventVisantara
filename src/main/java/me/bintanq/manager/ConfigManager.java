package me.bintanq.manager;

import me.bintanq.EasterEventVisantara;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ConfigManager {

    private final EasterEventVisantara plugin;
    private FileConfiguration config;
    private FileConfiguration messages;

    public ConfigManager(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        config   = loadFile("config.yml");
        messages = loadFile("messages.yml");
    }

    private FileConfiguration loadFile(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        InputStream stream = plugin.getResource(name);
        if (stream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            cfg.setDefaults(defaults);
        }
        return cfg;
    }

    private String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private String msg(String path, String fallback) {
        return color(messages.getString(path, fallback));
    }

    private String prefixed(String path, String fallback) {
        return getPrefix() + msg(path, fallback);
    }

    public String getMythicMobId()            { return config.getString("balloon.mythicmob-id", "EasterBalloon"); }
    public int    getSpawnIntervalMinutes()   { return config.getInt("balloon.spawn-interval-minutes", 2); }
    public int    getMaxSpawnsPerBatch()      { return config.getInt("balloon.max-spawns-per-batch", 2); }
    public double getSpawnRadius()            { return config.getDouble("balloon.spawn-radius", 24.0); }
    public double getSpawnChance()            { return config.getDouble("balloon.spawn-chance", 0.50); }
    public int    getDespawnSeconds()         { return config.getInt("balloon.despawn-seconds", 60); }
    public int    getGlobalBalloonCap()       { return config.getInt("balloon.global-cap", 20); }
    public int    getPerPlayerBalloonCap()    { return config.getInt("balloon.per-player-cap", 3); }
    public boolean isCheckPerPlayer()         { return config.getBoolean("balloon.cap-check-per-player", true); }
    public boolean isRequireSpecificItem()    { return config.getBoolean("balloon.require-specific-item", false); }
    public String getRequiredItemString()     { return config.getString("balloon.required-item", "VANILLA:STICK"); }
    public List<String> getRewardCommands()   { return config.getStringList("balloon.reward-commands"); }

    public String getPrefix()               { return msg("prefix", "&6&l[EasterEvent] &r"); }
    public String getMsgReloadSuccess()     { return prefixed("reload.success", "&aKonfigurasi berhasil di-reload dan task di-restart."); }
    public String getMsgNoPermission()      { return prefixed("no-permission", "&cKamu tidak punya izin untuk melakukan ini."); }
    public String getMsgNotPlayer()         { return prefixed("not-player", "&cHanya player yang bisa menjalankan command ini."); }
    public String getMsgUnknownSubcommand() { return prefixed("unknown-subcommand", "&cSub-command tidak dikenal. Gunakan: &e/easter <reload|spawn|debug|status>"); }
    public String getMsgDebugEnabled()      { return prefixed("debug.enabled", "&aDebug mode &2AKTIF&a."); }
    public String getMsgDebugDisabled()     { return prefixed("debug.disabled", "&cDebug mode &4NONAKTIF&c."); }
    public String getMsgSpawnSuccess()      { return prefixed("spawn.success", "&aBalloon berhasil di-spawn di lokasimu!"); }
    public String getMsgSpawnFailed()       { return prefixed("spawn.failed", "&cGagal spawn balloon — cek console."); }
    public String getMsgItemMismatch()      { return prefixed("item-mismatch", "&cKamu butuh item yang benar untuk memencet balon ini!"); }
    public String getMsgCapReached()        { return prefixed("cap-reached", "&eSpawn dilewati — cap sudah tercapai di sekitar player ini."); }

    public String getMsgBalloonPopped(String playerName) {
        return prefixed("balloon.popped", "&6&lPOP! &e{player} &6memencet sebuah balon!")
                .replace("{player}", playerName);
    }

    public String getMsgDebugSpawn(double x, double y, double z, String world) {
        String raw = messages.getString("debug.spawn-info",
                "&7[DBG-SPAWN] &fBalloon queued at &e{x}&f, &e{y}&f, &e{z} &fin &e{world}");
        return color(raw
                .replace("{x}",     String.format("%.1f", x))
                .replace("{y}",     String.format("%.1f", y))
                .replace("{z}",     String.format("%.1f", z))
                .replace("{world}", world));
    }

    public String getMsgDebugHit(String entityId, String itemInfo, boolean passed) {
        String raw = messages.getString("debug.hit-info",
                "&7[DBG-HIT] &fEntity &e{entity} &f| item: &e{item} &f| lolos: &e{passed}");
        return color(raw
                .replace("{entity}", entityId)
                .replace("{item}",   itemInfo)
                .replace("{passed}", String.valueOf(passed)));
    }

    public String getMsgDebugDespawn(String entityId) {
        String raw = messages.getString("debug.despawn-info",
                "&7[DBG-DESPAWN] &fBalloon &e{entity} &fkadaluarsa dan dihapus.");
        return color(raw.replace("{entity}", entityId));
    }

    public String getMsgStatusHeader() {
        return prefixed("status.header", "&e===== Easter Balloon Status =====");
    }

    public String getMsgStatusGlobal(int active, int cap) {
        String raw = messages.getString("status.global",
                "&fGlobal aktif: &e{active}&f / &e{cap}");
        return color(raw
                .replace("{active}", String.valueOf(active))
                .replace("{cap}",    String.valueOf(cap)));
    }

    public String getMsgStatusPerPlayer(String playerName, int active, int cap) {
        String raw = messages.getString("status.per-player",
                "&f  &e{player}&f: &e{active}&f / &e{cap}");
        return color(raw
                .replace("{player}", playerName)
                .replace("{active}", String.valueOf(active))
                .replace("{cap}",    String.valueOf(cap)));
    }

    public String getMsgStatusCheckMode(boolean perPlayer) {
        String raw = messages.getString("status.check-mode",
                "&fMode cap: &e{mode}");
        return color(raw.replace("{mode}", perPlayer ? "Per Player" : "Global"));
    }
}