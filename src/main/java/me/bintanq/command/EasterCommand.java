package me.bintanq.command;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import me.bintanq.util.BalloonTracker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class EasterCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "easter.admin";
    private static final List<String> SUB_COMMANDS = Arrays.asList("reload", "spawn", "debug", "status");

    private final EasterEventVisantara plugin;

    public EasterCommand(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(cfg.getMsgNoPermission());
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(cfg.getMsgUnknownSubcommand());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.getConfigManager().getMsgReloadSuccess());
            }
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(cfg.getMsgNotPlayer());
                    return true;
                }
                handleSpawn(player, cfg);
            }
            case "debug" -> {
                boolean current = plugin.isDebugMode();
                plugin.setDebugMode(!current);
                sender.sendMessage(!current ? cfg.getMsgDebugEnabled() : cfg.getMsgDebugDisabled());
            }
            case "status" -> handleStatus(sender, cfg);
            default -> sender.sendMessage(cfg.getMsgUnknownSubcommand());
        }

        return true;
    }

    private void handleSpawn(Player player, ConfigManager cfg) {
        String mobId = cfg.getMythicMobId();

        if (MythicBukkit.inst().getMobManager().getMythicMob(mobId).isEmpty()) {
            player.sendMessage(cfg.getMsgSpawnFailed());
            plugin.getLogger().warning("/easter spawn gagal — MythicMob '" + mobId + "' tidak ditemukan.");
            return;
        }

        Entity entity;
        try {
            BukkitAPIHelper api = MythicBukkit.inst().getAPIHelper();
            entity = api.spawnMythicMob(mobId, player.getLocation());
        } catch (Exception e) {
            player.sendMessage(cfg.getMsgSpawnFailed());
            plugin.getLogger().warning("/easter spawn exception: " + e.getMessage());
            return;
        }

        if (entity == null) {
            player.sendMessage(cfg.getMsgSpawnFailed());
            return;
        }

        plugin.getBalloonTracker().register(entity.getUniqueId(), player.getUniqueId());
        player.sendMessage(cfg.getMsgSpawnSuccess());
    }

    private void handleStatus(CommandSender sender, ConfigManager cfg) {
        BalloonTracker tracker = plugin.getBalloonTracker();

        sender.sendMessage(cfg.getMsgStatusHeader());
        sender.sendMessage(cfg.getMsgStatusCheckMode(cfg.isCheckPerPlayer()));
        sender.sendMessage(cfg.getMsgStatusGlobal(tracker.getActiveCount(), cfg.getGlobalBalloonCap()));

        if (cfg.isCheckPerPlayer()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID uuid  = p.getUniqueId();
                int  count = tracker.getPlayerBalloonCount(uuid);
                if (count > 0) {
                    sender.sendMessage(cfg.getMsgStatusPerPlayer(p.getName(), count, cfg.getPerPlayerBalloonCap()));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return Collections.emptyList();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUB_COMMANDS.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}