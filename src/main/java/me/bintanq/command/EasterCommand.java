package me.bintanq.command;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import me.bintanq.util.BalloonTracker;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
                if (!(sender instanceof Player player)) { sender.sendMessage(cfg.getMsgNotPlayer()); return true; }
                handleSpawn(player, cfg);
            }
            case "debug" -> {
                boolean current = plugin.isDebugMode();
                plugin.setDebugMode(!current);
                sender.sendMessage(!current ? cfg.getMsgDebugEnabled() : cfg.getMsgDebugDisabled());
            }
            case "status" -> {
                if (args.length >= 2) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) { sender.sendMessage(cfg.getMsgPlayerNotFound(args[1])); return true; }
                    handleStatusPlayer(sender, target, cfg);
                } else {
                    handleStatus(sender, cfg);
                }
            }
            default -> sender.sendMessage(cfg.getMsgUnknownSubcommand());
        }

        return true;
    }

    private void handleSpawn(Player player, ConfigManager cfg) {
        String mobId = cfg.getMythicMobId();

        if (MythicBukkit.inst().getMobManager().getMythicMob(mobId).isEmpty()) {
            player.sendMessage(cfg.getMsgSpawnFailed());
            plugin.getLogger().warning("/easter spawn failed — MythicMob '" + mobId + "' not found.");
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

    private void handleStatus(CommandSender sender, ConfigManager cfg) {
        BalloonTracker tracker = plugin.getBalloonTracker();

        sender.sendMessage(cfg.getMsgStatusHeader());
        sender.sendMessage(cfg.getMsgStatusCheckMode(cfg.isCheckPerPlayer()));
        sender.sendMessage(cfg.getMsgStatusGlobal(tracker.getActiveCount(), cfg.getGlobalBalloonCap()));

        if (cfg.isCheckPerPlayer()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                int count = tracker.getPlayerBalloonCount(p.getUniqueId());
                if (count > 0) sender.sendMessage(cfg.getMsgStatusPerPlayer(p.getName(), count, cfg.getPerPlayerBalloonCap()));
            }
        }
    }

    private void handleStatusPlayer(CommandSender sender, Player target, ConfigManager cfg) {
        BalloonTracker tracker = plugin.getBalloonTracker();
        Map<UUID, UUID> ownerMap = tracker.getBalloonOwnerMap();

        UUID targetUUID = target.getUniqueId();
        int count = tracker.getPlayerBalloonCount(targetUUID);

        sender.sendMessage(cfg.getMsgStatusPlayerHeader(target.getName(), count, cfg.getPerPlayerBalloonCap()));

        if (count == 0) { sender.sendMessage(cfg.getMsgStatusPlayerNone()); return; }

        int index = 1;
        for (Map.Entry<UUID, UUID> entry : ownerMap.entrySet()) {
            if (!entry.getValue().equals(targetUUID)) continue;

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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return Collections.emptyList();

        if (args.length == 1) {
            return SUB_COMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}