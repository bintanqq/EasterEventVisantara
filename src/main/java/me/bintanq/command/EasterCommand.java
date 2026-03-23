package me.bintanq.command;

import io.lumine.mythic.bukkit.MythicBukkit;
import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import me.bintanq.manager.WorldResetManager;
import me.bintanq.task.StructurePlacementTask;
import me.bintanq.util.BalloonTracker;
import me.bintanq.util.StructureTracker;
import me.bintanq.util.StructureTracker.StructureEntry;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class EasterCommand implements CommandExecutor, TabCompleter {

    private static final String       PERM        = "easter.admin";
    private static final List<String> SUBS        = Arrays.asList("reload", "spawn", "debug", "status", "world");
    private static final List<String> STATUS_SUBS = Arrays.asList("structure");
    private static final List<String> WORLD_SUBS  = Arrays.asList("create", "reset", "seed");

    private final EasterEventVisantara plugin;

    public EasterCommand(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    private String c(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();

        if (!sender.hasPermission(PERM)) { sender.sendMessage(cfg.getMsgNoPermission()); return true; }
        if (args.length == 0)            { sender.sendMessage(cfg.getMsgUnknownSubcommand()); return true; }

        switch (args[0].toLowerCase()) {
            case "reload" -> { plugin.reload(); sender.sendMessage(cfg.getMsgReloadSuccess()); }
            case "spawn"  -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(cfg.getMsgNotPlayer()); return true; }
                handleSpawn(p, cfg);
            }
            case "debug"  -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("biome")) {
                    if (!(sender instanceof Player p)) { sender.sendMessage(cfg.getMsgNotPlayer()); return true; }
                    handleDebugBiome(p, cfg);
                    return true;
                }
                boolean cur = plugin.isDebugMode();
                plugin.setDebugMode(!cur);
                sender.sendMessage(!cur ? cfg.getMsgDebugEnabled() : cfg.getMsgDebugDisabled());
            }
            case "status" -> handleStatusDispatch(sender, args, cfg);
            case "world"  -> {
                if (args.length < 2) { sender.sendMessage(c(cfg.getPrefix() + "&7Usage: &d/easter world <create|reset>")); return true; }
                handleWorld(sender, args[1], cfg);
            }
            default -> sender.sendMessage(cfg.getMsgUnknownSubcommand());
        }
        return true;
    }

    private void handleStatusDispatch(CommandSender sender, String[] args, ConfigManager cfg) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("structure")) {
            String variantFilter = args.length >= 3 ? args[2] : null;
            handleStatusStructure(sender, variantFilter, cfg);
            return;
        }
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(cfg.getMsgPlayerNotFound(args[1])); return; }
            handleStatusPlayer(sender, target, cfg);
            return;
        }
        handleStatus(sender, cfg);
    }

    private void handleSpawn(Player player, ConfigManager cfg) {
        String mobId = cfg.getMythicMobId();
        if (MythicBukkit.inst().getMobManager().getMythicMob(mobId).isEmpty()) {
            player.sendMessage(cfg.getMsgSpawnFailed());
            return;
        }
        Entity entity;
        try {
            entity = MythicBukkit.inst().getAPIHelper().spawnMythicMob(mobId, player.getLocation());
        } catch (Exception e) {
            player.sendMessage(cfg.getMsgSpawnFailed());
            plugin.getLogger().warning("/easter spawn exception: " + e.getMessage());
            return;
        }
        if (entity == null) { player.sendMessage(cfg.getMsgSpawnFailed()); return; }
        plugin.getBalloonTracker().register(entity.getUniqueId(), player.getUniqueId());
        player.sendMessage(cfg.getMsgSpawnSuccess());
    }

    private void handleWorld(CommandSender sender, String sub, ConfigManager cfg) {
        WorldResetManager wrm = plugin.getWorldResetManager();
        switch (sub.toLowerCase()) {
            case "create" -> {
                if (wrm.isResetting()) { sender.sendMessage(c(cfg.getPrefix() + "&cSedang ada operasi world yang berjalan.")); return; }
                if (Bukkit.getWorld(cfg.getResourceWorldName()) != null) {
                    sender.sendMessage(c(cfg.getPrefix() + "&eWorld &d" + cfg.getResourceWorldName() + " &esudah ada. Gunakan &d/easter world reset&e."));
                    return;
                }
                sender.sendMessage(cfg.getMsgWorldCreating());
                wrm.createFresh();
            }
            case "reset" -> {
                if (wrm.isResetting()) { sender.sendMessage(c(cfg.getPrefix() + "&cSedang ada operasi world yang berjalan.")); return; }
                sender.sendMessage(c(cfg.getPrefix() + "&eMemulai reset Resource World..."));
                wrm.beginReset();
            }
            case "seed" -> {
                String worldName = cfg.getResourceWorldName();
                if (Bukkit.getWorld(worldName) == null) {
                    sender.sendMessage(c(cfg.getPrefix() + "&cWorld &d" + worldName + " &cbelum ada. Buat dulu dengan &d/easter world create&c."));
                    return;
                }
                StructurePlacementTask spt = plugin.getStructurePlacementTask();
                if (spt == null) { sender.sendMessage(c(cfg.getPrefix() + "&cStructurePlacementTask tidak aktif.")); return; }
                if (spt.isSeeding()) { sender.sendMessage(c(cfg.getPrefix() + "&eSeed sedang berjalan, harap tunggu.")); return; }
                sender.sendMessage(c(cfg.getPrefix() + "&eMemulai seed struktur di &d" + worldName + "&e..."));
                spt.seedInitialStructures(worldName);
            }
            default -> sender.sendMessage(c(cfg.getPrefix() + "&7Usage: &d/easter world <create|reset|seed>"));
        }
    }

    private void handleStatus(CommandSender sender, ConfigManager cfg) {
        BalloonTracker   bt  = plugin.getBalloonTracker();
        StructureTracker st  = plugin.getStructureTracker();
        String           rw  = cfg.getResourceWorldName();
        World            rwb = Bukkit.getWorld(rw);
        String div = c(cfg.getPrefix() + "&8&m-----------------------------");
        boolean seeding = plugin.getStructurePlacementTask() != null && plugin.getStructurePlacementTask().isSeeding();

        sender.sendMessage(cfg.getMsgStatusHeader());
        sender.sendMessage(div);
        sender.sendMessage(c(cfg.getPrefix() + "&fResource World: " + (rwb != null ? "&a" + rw + " &7(loaded)" : "&c" + rw + " &7(not loaded)")));
        String seedStatus;
        if (seeding) {
            StructurePlacementTask sptRef = plugin.getStructurePlacementTask();
            int prog  = sptRef != null ? sptRef.getSeedProgress() : 0;
            int total = sptRef != null ? sptRef.getSeedTotal()    : 0;
            int pct   = total > 0 ? (prog * 100 / total) : 0;
            seedStatus = c("&eSeeding... &f" + prog + "&7/&f" + total + " &7(" + pct + "%)");
        } else {
            seedStatus = rwb != null ? c("&aSiap") : c("&7-");
        }
        sender.sendMessage(c(cfg.getPrefix() + "&fStatus Seed: ") + seedStatus);
        sender.sendMessage(c(cfg.getPrefix() + "&fBorder: &d" + (int) cfg.getWorldBorderSize() + " &7x &d" + (int) cfg.getWorldBorderSize() + " &7blok"));
        sender.sendMessage(c(cfg.getPrefix() + "&fReset jam: &d" + cfg.getWorldResetTime() + " &7WIB"));
        sender.sendMessage(c(cfg.getPrefix() + "&fDebug Mode: " + (plugin.isDebugMode() ? "&aON" : "&cOFF")));
        sender.sendMessage(div);

        int structCount = st.getEntriesInWorld(rw).size();
        sender.sendMessage(c(cfg.getPrefix() + "&fStruktur aktif: &d" + structCount + " &7/ &d" + cfg.getStructureMaxTotal()));
        sender.sendMessage(c(cfg.getPrefix() + "&fSpawn chance: &d" + (int)(cfg.getStructureSpawnChance() * 100) + "%"));
        sender.sendMessage(c(cfg.getPrefix() + "&fMin jarak: &d" + cfg.getStructureMinDistance() + " &7blok"));
        List<String> biomes   = cfg.getStructureBiomeWhitelist();
        List<String> variants = cfg.getStructureVariantNames();
        sender.sendMessage(c(cfg.getPrefix() + "&fBiome: &d" + (biomes.isEmpty() ? "&7(semua)" : String.join("&7, &d", biomes))));
        sender.sendMessage(c(cfg.getPrefix() + "&fVariant: &d" + (variants.isEmpty() ? "&7(tidak ada)" : String.join("&7, &d", variants))));
        sender.sendMessage(c(cfg.getPrefix() + "&7Gunakan &d/easter status structure [variant] &7untuk list struktur"));
        sender.sendMessage(div);
        sender.sendMessage(cfg.getMsgStatusCheckMode(cfg.isCheckPerPlayer()));
        sender.sendMessage(cfg.getMsgStatusGlobal(bt.getActiveCount(), cfg.getGlobalBalloonCap()));

        if (cfg.isCheckPerPlayer()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                int cnt = bt.getPlayerBalloonCount(p.getUniqueId());
                if (cnt > 0) sender.sendMessage(cfg.getMsgStatusPerPlayer(p.getName(), cnt, cfg.getPerPlayerBalloonCap()));
            }
        }
    }

    private void handleStatusStructure(CommandSender sender, String variantFilter, ConfigManager cfg) {
        StructureTracker st      = plugin.getStructureTracker();
        String           rw      = cfg.getResourceWorldName();
        List<StructureEntry> all = st.getEntriesInWorld(rw);
        String div = c(cfg.getPrefix() + "&8&m-----------------------------");

        if (variantFilter != null) {
            all = all.stream()
                    .filter(e -> e.variant.equalsIgnoreCase(variantFilter))
                    .collect(Collectors.toList());
        }

        sender.sendMessage(c(cfg.getPrefix() + "&d--- Struktur Easter" +
                (variantFilter != null ? " [" + variantFilter + "]" : "") + " ---"));
        sender.sendMessage(div);

        if (all.isEmpty()) {
            sender.sendMessage(c(cfg.getPrefix() + "&7Tidak ada struktur" +
                    (variantFilter != null ? " dengan variant &d" + variantFilter : "") + "."));
            return;
        }

        sender.sendMessage(c(cfg.getPrefix() + "&fTotal: &d" + all.size() + " &fstruktur"));
        sender.sendMessage(div);

        int index = 1;
        for (StructureEntry e : all) {
            String variantColor = switch (e.variant.toLowerCase()) {
                case "giantegg"    -> "&6";
                case "eastershrine"-> "&5";
                default            -> "&b";
            };

            String entryText = c(cfg.getPrefix() + "&f#" + index + " " + variantColor + e.variant
                    + " &7@ &f" + e.blockX + "&7, &f" + e.blockY + "&7, &f" + e.blockZ
                    + " &7[&d" + e.worldName + "&7]"
                    + " &7Robbits: &d" + e.getRobbitCount()
                    + " &d&n[TP]");

            if (sender instanceof Player ps) {
                TextComponent msg = new TextComponent(entryText);
                msg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/tp " + e.blockX + " " + (e.blockY + 1) + " " + e.blockZ));
                msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(c("&fKlik untuk teleport ke\n&e" +
                                e.blockX + ", " + e.blockY + ", " + e.blockZ)).create()));
                ps.spigot().sendMessage(msg);
            } else {
                sender.sendMessage(entryText);
            }
            index++;
        }
        sender.sendMessage(div);
    }

    private void handleStatusPlayer(CommandSender sender, Player target, ConfigManager cfg) {
        BalloonTracker  tracker  = plugin.getBalloonTracker();
        Map<UUID, UUID> ownerMap = tracker.getBalloonOwnerMap();
        UUID            uid      = target.getUniqueId();
        int             count    = tracker.getPlayerBalloonCount(uid);

        sender.sendMessage(cfg.getMsgStatusPlayerHeader(target.getName(), count, cfg.getPerPlayerBalloonCap()));
        if (count == 0) { sender.sendMessage(cfg.getMsgStatusPlayerNone()); return; }

        int index = 1;
        for (Map.Entry<UUID, UUID> entry : ownerMap.entrySet()) {
            if (!entry.getValue().equals(uid)) continue;
            Location loc = tracker.getBalloonLocation(entry.getKey());
            if (loc == null) continue;

            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
            String world = loc.getWorld().getName();

            TextComponent line = new TextComponent(cfg.getMsgStatusPlayerEntry(index, x, y, z, world));
            line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + x + " " + y + " " + z));
            line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(cfg.getMsgStatusPlayerEntryHover(x, y, z)).create()));

            if (sender instanceof Player ps) ps.spigot().sendMessage(line);
            else sender.sendMessage(cfg.getMsgStatusPlayerEntry(index, x, y, z, world));
            index++;
        }
    }

    private void handleDebugBiome(Player player, ConfigManager cfg) {
        org.bukkit.Location loc = player.getLocation();
        org.bukkit.World w = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        int surfaceY = w.getHighestBlockYAt(bx, bz, org.bukkit.HeightMap.WORLD_SURFACE);
        String biome = w.getBiome(bx, surfaceY, bz).getKey().toString();
        String chunkX = String.valueOf(loc.getChunk().getX());
        String chunkZ = String.valueOf(loc.getChunk().getZ());
        player.sendMessage(c(cfg.getPrefix() + "&fBiome: &d" + biome));
        player.sendMessage(c(cfg.getPrefix() + "&fChunk: &d" + chunkX + "&7, &d" + chunkZ));
        player.sendMessage(c(cfg.getPrefix() + "&fSurface Y: &d" + surfaceY));
        player.sendMessage(c(cfg.getPrefix() + "&7Tambahkan &d" + biome + " &7ke biome-whitelist di config."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERM)) return Collections.emptyList();

        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return java.util.List.of("biome").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("status")) {
                List<String> opts = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                opts.addAll(STATUS_SUBS);
                return opts.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("world")) {
                return WORLD_SUBS.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("status") && args[1].equalsIgnoreCase("structure")) {
            return plugin.getConfigManager().getStructureVariantNames().stream()
                    .filter(v -> v.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}