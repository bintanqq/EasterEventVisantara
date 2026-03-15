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
import java.util.stream.Collectors;

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

    public String getMythicMobId()          { return config.getString("balloon.mythicmob-id", "EasterBalloon"); }
    public int    getSpawnIntervalMinutes() { return config.getInt("balloon.spawn-interval-minutes", 2); }
    public int    getMaxSpawnsPerBatch()    { return config.getInt("balloon.max-spawns-per-batch", 2); }
    public double getSpawnRadius()          { return config.getDouble("balloon.spawn-radius", 24.0); }
    public double getSpawnChance()          { return config.getDouble("balloon.spawn-chance", 0.50); }
    public int    getDespawnSeconds()       { return config.getInt("balloon.despawn-seconds", 60); }
    public int    getGlobalBalloonCap()     { return config.getInt("balloon.global-cap", 20); }
    public int    getPerPlayerBalloonCap()  { return config.getInt("balloon.per-player-cap", 3); }
    public boolean isCheckPerPlayer()       { return config.getBoolean("balloon.cap-check-per-player", true); }
    public boolean isRequireSpecificItem()  { return config.getBoolean("balloon.require-specific-item", false); }
    public String getRequiredItemString()   { return config.getString("balloon.required-item", "VANILLA:STICK"); }
    public List<String> getRewardCommands() { return config.getStringList("balloon.reward-commands"); }
    public boolean isAnnounceGlobal()       { return config.getBoolean("balloon.announce-global", true); }

    public String getPrefix()               { return msg("prefix", "&d[EasterEvent] &r"); }
    public String getMsgReloadSuccess()     { return prefixed("reload.success", "&aKonfigurasi berhasil diperbarui."); }
    public String getMsgNoPermission()      { return prefixed("no-permission", "&cKamu tidak punya izin."); }
    public String getMsgNotPlayer()         { return prefixed("not-player", "&cHanya player yang bisa menjalankan command ini."); }
    public String getMsgUnknownSubcommand() { return prefixed("unknown-subcommand", "&7Gunakan: &d/easter <reload|spawn|debug|status>"); }
    public String getMsgDebugEnabled()      { return prefixed("debug.enabled", "&dDebug mode: &aON"); }
    public String getMsgDebugDisabled()     { return prefixed("debug.disabled", "&dDebug mode: &cOFF"); }
    public String getMsgSpawnSuccess()      { return prefixed("spawn.success", "&bEaster Balloon &fberhasil dimunculkan."); }
    public String getMsgSpawnFailed()       { return prefixed("spawn.failed", "&cGagal memunculkan balon, cek console."); }
    public String getMsgItemMismatch()      { return prefixed("item-mismatch", "&dGunakan item khusus untuk memecahkan balon ini!"); }
    public String getMsgCapReached()        { return prefixed("cap-reached", "&eSpawn skipped: &7Limit tercapai untuk pemain ini."); }
    public String getMsgStatusHeader()      { return prefixed("status.header", "&d--- Easter Status ---"); }
    public String getMsgStatusPlayerNone()  { return prefixed("status.player-none", "&7Tidak ada balon aktif milikmu saat ini."); }

    public List<String> getMsgBalloonPoppedLines(String playerName) {
        List<String> raw = messages.getStringList("balloon.popped");
        return raw.stream()
                .map(line -> color(line.replace("{player}", playerName)))
                .collect(Collectors.toList());
    }

    public String getMsgStatusCheckMode(boolean perPlayer) {
        String raw = messages.getString("status.check-mode", "&fCap Mode: &e{mode}");
        return color(raw.replace("{mode}", perPlayer ? "Per Player" : "Global"));
    }

    public String getMsgStatusGlobal(int active, int cap) {
        String capStr = (cap == -1) ? "∞" : String.valueOf(cap);
        String raw = messages.getString("status.global", "&fGlobal Active: &d{active}&f / &d{cap}");
        return color(raw.replace("{active}", String.valueOf(active)).replace("{cap}", capStr));
    }

    public String getMsgStatusPerPlayer(String playerName, int active, int cap) {
        String raw = messages.getString("status.per-player", "&f  &d{player}&f: &e{active}&f / &e{cap}");
        return color(raw
                .replace("{player}", playerName)
                .replace("{active}", String.valueOf(active))
                .replace("{cap}",    String.valueOf(cap)));
    }

    public String getMsgStatusPlayerHeader(String playerName, int active, int cap) {
        String raw = messages.getString("status.player-header",
                "&d--- Balon Milik {player} ({active}/{cap}) ---");
        return color(raw
                .replace("{player}", playerName)
                .replace("{active}", String.valueOf(active))
                .replace("{cap}",    String.valueOf(cap)));
    }

    public String getMsgStatusPlayerEntry(int index, int x, int y, int z, String world) {
        String raw = messages.getString("status.player-entry",
                "&f  &e#{index} &7@ &f{x}, {y}, {z} &7[{world}] &d&n[Teleport]");
        return color(raw
                .replace("{index}", String.valueOf(index))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z))
                .replace("{world}", world));
    }

    public String getMsgStatusPlayerEntryHover(int x, int y, int z) {
        String raw = messages.getString("status.player-entry-hover", "&fKlik untuk teleport ke &e{x}, {y}, {z}");
        return color(raw
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z)));
    }

    public String getMsgDebugSpawn(double x, double y, double z, String world) {
        String raw = messages.getString("debug.spawn-info",
                "&7[DEBUG] &fSpawn at &e{x}, {y}, {z} &f| World: &e{world}");
        return color(raw
                .replace("{x}",     String.format("%.1f", x))
                .replace("{y}",     String.format("%.1f", y))
                .replace("{z}",     String.format("%.1f", z))
                .replace("{world}", world));
    }

    public String getMsgDebugHit(String entityId, String itemInfo, boolean passed) {
        String raw = messages.getString("debug.hit-info",
                "&7[DEBUG] &fEntity: &e{entity} &f| Item: &e{item} &f| Valid: &e{passed}");
        return color(raw
                .replace("{entity}", entityId)
                .replace("{item}",   itemInfo)
                .replace("{passed}", String.valueOf(passed)));
    }

    public String getMsgDebugDespawn(String entityId) {
        String raw = messages.getString("debug.despawn-info",
                "&7[DEBUG] &fEntity: &e{entity} &fdespawned (timeout).");
        return color(raw.replace("{entity}", entityId));
    }
}