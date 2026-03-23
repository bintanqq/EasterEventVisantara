package me.bintanq.manager;

import me.bintanq.EasterEventVisantara;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public double getSpawnRadius()          { return config.getDouble("balloon.spawn-radius", 24.0); }
    public double getSpawnChance()          { return config.getDouble("balloon.spawn-chance", 0.50); }
    public int    getDespawnSeconds()       { return config.getInt("balloon.despawn-seconds", 60); }
    public int    getGlobalBalloonCap()     { return config.getInt("balloon.global-cap", 20); }
    public int    getPerPlayerBalloonCap()  { return config.getInt("balloon.per-player-cap", 5); }
    public int    getPerPlayerMin()         { return config.getInt("balloon.per-player-min", 1); }
    public boolean isCheckPerPlayer()       { return config.getBoolean("balloon.cap-check-per-player", true); }
    public boolean isRequireSpecificItem()  { return config.getBoolean("balloon.require-specific-item", false); }
    public String getRequiredItemString()   { return config.getString("balloon.required-item", "VANILLA:STICK"); }
    public List<String> getRewardCommands() { return config.getStringList("balloon.reward-commands"); }
    public boolean isAnnounceGlobal()       { return config.getBoolean("balloon.announce-global", true); }
    public List<String> getDisabledWorlds() { return config.getStringList("balloon.disabled-worlds"); }
    public int getFloatHeightMin()          { return config.getInt("balloon.float-height-min", 3); }
    public int getFloatHeightMax()          { return config.getInt("balloon.float-height-max", 5); }
    public int getSpawnMinY()               { return config.getInt("balloon.spawn-min-y", 60); }
    public boolean isNotifyOnSpawn()        { return config.getBoolean("balloon.notify-on-spawn", true); }
    public List<String> getNotifyTypes()    { return config.getStringList("balloon.notify-type"); }
    public String getBossBarColor()         { return config.getString("balloon.bossbar-color", "PINK"); }
    public String getBossBarStyle()         { return config.getString("balloon.bossbar-style", "SOLID"); }
    public int getNotifyDurationSeconds()   { return config.getInt("balloon.notify-duration-seconds", 10); }
    public boolean isNotifySoundEnabled()   { return config.getBoolean("balloon.notify-sound-enabled", true); }
    public String getNotifySound()          { return config.getString("balloon.notify-sound", "ENTITY_EXPERIENCE_ORB_PICKUP"); }
    public double getNotifySoundVolume()    { return config.getDouble("balloon.notify-sound-volume", 1.0); }
    public double getNotifySoundPitch()     { return config.getDouble("balloon.notify-sound-pitch", 1.2); }
    public double getFloatRiseSpeed()       { return config.getDouble("balloon.float-rise-speed", 0.03); }
    public boolean isFloatEnabled()         { return config.getBoolean("balloon.float-enabled", true); }
    public int getFloatMaxYOffset()         { return config.getInt("balloon.float-max-y-offset", 20); }

    public String getResourceWorldName()    { return config.getString("world-settings.resource-world-name", "easter_resource"); }
    public String getWorldResetTime()       { return config.getString("world-settings.reset-time", "00:00"); }
    public double getWorldBorderSize()      { return config.getDouble("world-settings.border-size", 5000.0); }
    public int getWorldResetHour()   { try { return Integer.parseInt(getWorldResetTime().split(":")[0]); } catch (Exception e) { return 0; } }
    public int getWorldResetMinute() { try { return Integer.parseInt(getWorldResetTime().split(":")[1]); } catch (Exception e) { return 0; } }

    public String getFallbackSpawnWorld()   { return config.getString("world-settings.fallback-spawn.world", "SpawnLobby"); }
    public double getFallbackSpawnX()       { return config.getDouble("world-settings.fallback-spawn.x", 522); }
    public double getFallbackSpawnY()       { return config.getDouble("world-settings.fallback-spawn.y", -10); }
    public double getFallbackSpawnZ()       { return config.getDouble("world-settings.fallback-spawn.z", -213); }
    public float  getFallbackSpawnYaw()     { return (float) config.getDouble("world-settings.fallback-spawn.yaw", 0); }
    public float  getFallbackSpawnPitch()   { return (float) config.getDouble("world-settings.fallback-spawn.pitch", 0); }

    public List<String> getStructureBiomeWhitelist()  { return config.getStringList("structure-settings.biome-whitelist"); }
    public double getStructureSpawnChance()            { return config.getDouble("structure-settings.spawn-chance", 0.05); }
    public int getStructureMaxPerChunk()               { return config.getInt("structure-settings.max-per-chunk", 1); }
    public int getStructureMinDistance()               { return config.getInt("structure-settings.min-distance-between", 256); }
    public int getStructureMaxTotal()                  { return config.getInt("structure-settings.max-total", 30); }
    public int getStructureFlatCheckRadius()           { return config.getInt("structure-settings.flat-check-radius", 4); }
    public int getStructureFlatMaxHeightDiff()         { return config.getInt("structure-settings.flat-max-height-diff", 3); }
    public int getStructureClearHeightAbove()          { return config.getInt("structure-settings.clear-height-above", 12); }
    public int getAttemptsPerSlot()                    { return config.getInt("structure-settings.attempts-per-slot", 10); }

    public List<String> getStructureVariantNames() {
        ConfigurationSection sec = config.getConfigurationSection("structure-settings.variants");
        if (sec == null) return new ArrayList<>();
        return new ArrayList<>(sec.getKeys(false));
    }

    public String getStructureVariantNbtFile(String v) {
        return config.getString("structure-settings.variants." + v + ".nbt-file", v + ".nbt");
    }

    public int getStructureVariantWeight(String v) {
        return config.getInt("structure-settings.variants." + v + ".weight", 1);
    }

    public int getStructureVariantPasteYOffset(String v) {
        return config.getInt("structure-settings.variants." + v + ".paste-y-offset", 0);
    }


    public String getRobbitMobId()           { return config.getString("robbit-settings.mob-id", "RobbitEaster"); }
    public double getRobbitTriggerRadius()   { return config.getDouble("robbit-settings.trigger-radius", 16.0); }
    public int getRobbitMaxPerStructure()    { return config.getInt("robbit-settings.max-per-structure", 3); }

    public List<String> getLootTableVariants() {
        ConfigurationSection sec = config.getConfigurationSection("loot-tables");
        if (sec == null) return new ArrayList<>();
        return new ArrayList<>(sec.getKeys(false));
    }

    public List<Map<String, Object>> getLootTableEntries(String variant) {
        List<Map<String, Object>> result = new ArrayList<>();
        ConfigurationSection sec = config.getConfigurationSection("loot-tables." + variant + ".entries");
        if (sec == null) return result;
        for (String key : sec.getKeys(false)) {
            String base = "loot-tables." + variant + ".entries." + key;
            Map<String, Object> entry = new HashMap<>();
            entry.put("type",       config.getString(base + ".type", "VANILLA"));
            entry.put("id",         config.getString(base + ".id", "STONE"));
            entry.put("amount-min", config.getInt(base + ".amount-min", 1));
            entry.put("amount-max", config.getInt(base + ".amount-max", 1));
            entry.put("weight",     config.getInt(base + ".weight", 1));
            result.add(entry);
        }
        return result;
    }

    public String getLootTableForStructure(String v) {
        return config.getString("structure-settings.variants." + v + ".loot-table", "default");
    }

    public String getPrefix()               { return msg("prefix", "&d[EasterEvent] &r"); }
    public String getMsgReloadSuccess()     { return prefixed("reload.success", "&aKonfigurasi berhasil diperbarui."); }
    public String getMsgNoPermission()      { return prefixed("no-permission", "&cKamu tidak punya izin."); }
    public String getMsgNotPlayer()         { return prefixed("not-player", "&cHanya untuk pemain."); }
    public String getMsgUnknownSubcommand() { return prefixed("unknown-subcommand", "&7Gunakan: &d/easter <reload|spawn|debug|status|world>"); }
    public String getMsgDebugEnabled()      { return prefixed("debug.enabled", "&dDebug: &aON"); }
    public String getMsgDebugDisabled()     { return prefixed("debug.disabled", "&dDebug: &cOFF"); }
    public String getMsgSpawnSuccess()      { return prefixed("spawn.success", "&bEaster Balloon &fberhasil dimunculkan."); }
    public String getMsgSpawnFailed()       { return prefixed("spawn.failed", "&cGagal memunculkan balon, cek console."); }
    public String getMsgItemMismatch()      { return prefixed("item-mismatch", "&dGunakan item khusus untuk memecahkan balon ini!"); }
    public String getMsgCapReached()        { return prefixed("cap-reached", "&eLimit tercapai untuk pemain ini."); }
    public String getMsgStatusHeader()      { return prefixed("status.header", "&d--- Easter Status ---"); }
    public String getMsgStatusPlayerNone()  { return prefixed("status.player-none", "&7Tidak ada balon aktif."); }
    public String getMsgNotifyChat()        { return color(messages.getString("notify-spawn.chat", "&d✦ Easter Balloon muncul di dekatmu!")); }
    public String getMsgNotifyActionBar()   { return color(messages.getString("notify-spawn.actionbar", "&d✦ Easter Balloon muncul di dekatmu!")); }
    public String getMsgNotifyBossBar()     { return color(messages.getString("notify-spawn.bossbar", "&d✦ Easter Balloon muncul di dekatmu!")); }
    public String getMsgWorldResetting()    { return prefixed("world-reset.resetting", "&eResource World sedang direset!"); }
    public String getMsgWorldResetDone()    { return prefixed("world-reset.done", "&aResource World selesai direset!"); }
    public String getMsgWorldCreating()     { return prefixed("world-create.creating", "&eMemuat Resource World..."); }
    public String getMsgWorldCreateDone()   { return prefixed("world-create.done", "&aResource World berhasil dibuat!"); }
    public String getMsgWorldCreateFailed() { return prefixed("world-create.failed", "&cGagal membuat Resource World. Cek console."); }
    public String getMsgWorldLocked()       { return prefixed("world-locked", "&cResource World sedang generate struktur. Harap tunggu!"); }
    public String getMsgWorldSeedDone()     { return prefixed("world-seed-done", "&aStruktur selesai! Resource World siap dimasuki."); }
    public String getMsgStructurePasted()   { return prefixed("structure.pasted", "&aStruktur berhasil dipaste."); }
    public String getMsgStructureFailed()   { return prefixed("structure.failed", "&cGagal paste struktur. Cek console."); }

    public String getMsgPlayerNotFound(String name) {
        return color(messages.getString("player-not-found", "&cPlayer &e{name} &ctidak ditemukan.")
                .replace("{name}", name));
    }

    public List<String> getMsgBalloonPoppedLines(String playerName) {
        return messages.getStringList("balloon.popped").stream()
                .map(line -> color(line.replace("{player}", playerName)))
                .collect(Collectors.toList());
    }

    public String getMsgStatusCheckMode(boolean perPlayer) {
        return color(messages.getString("status.check-mode", "&fCap Mode: &e{mode}")
                .replace("{mode}", perPlayer ? "Per Player" : "Global"));
    }

    public String getMsgStatusGlobal(int active, int cap) {
        return color(messages.getString("status.global", "&fGlobal: &d{active}&f/&d{cap}")
                .replace("{active}", String.valueOf(active))
                .replace("{cap}", cap == -1 ? "∞" : String.valueOf(cap)));
    }

    public String getMsgStatusPerPlayer(String name, int active, int cap) {
        return color(messages.getString("status.per-player", "&f  &d{player}&f: &e{active}&f/&e{cap}")
                .replace("{player}", name)
                .replace("{active}", String.valueOf(active))
                .replace("{cap}", String.valueOf(cap)));
    }

    public String getMsgStatusPlayerHeader(String name, int active, int cap) {
        return color(messages.getString("status.player-header", "&d--- Balon Milik {player} ({active}/{cap}) ---")
                .replace("{player}", name)
                .replace("{active}", String.valueOf(active))
                .replace("{cap}", String.valueOf(cap)));
    }

    public String getMsgStatusPlayerEntry(int index, int x, int y, int z, String world) {
        return color(messages.getString("status.player-entry",
                        "&f  &e#{index} &7@ &f{x},{y},{z} &7[{world}] &d&n[Teleport]")
                .replace("{index}", String.valueOf(index))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z))
                .replace("{world}", world));
    }

    public String getMsgStatusPlayerEntryHover(int x, int y, int z) {
        return color(messages.getString("status.player-entry-hover", "&fKlik untuk teleport ke &e{x},{y},{z}")
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y))
                .replace("{z}", String.valueOf(z)));
    }

    public String getMsgDebugSpawn(double x, double y, double z, String world) {
        return color(messages.getString("debug.spawn-info", "&7[DEBUG] &fSpawn &e{x},{y},{z} &f| &e{world}")
                .replace("{x}", String.format("%.1f", x))
                .replace("{y}", String.format("%.1f", y))
                .replace("{z}", String.format("%.1f", z))
                .replace("{world}", world));
    }

    public String getMsgDebugHit(String entityId, String itemInfo, boolean passed) {
        return color(messages.getString("debug.hit-info",
                        "&7[DEBUG] &fEntity: &e{entity} &f| Item: &e{item} &f| Valid: &e{passed}")
                .replace("{entity}", entityId)
                .replace("{item}", itemInfo)
                .replace("{passed}", String.valueOf(passed)));
    }

    public String getMsgDebugDespawn(String entityId) {
        return color(messages.getString("debug.despawn-info", "&7[DEBUG] &fEntity &e{entity} &fdespawned.")
                .replace("{entity}", entityId));
    }
}