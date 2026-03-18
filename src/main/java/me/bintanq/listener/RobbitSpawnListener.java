package me.bintanq.listener;

import io.lumine.mythic.bukkit.MythicBukkit;
import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import me.bintanq.manager.EventWindowManager;
import me.bintanq.util.StructureTracker;
import me.bintanq.util.StructureTracker.StructureEntry;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RobbitSpawnListener implements Listener {

    private static final long CHECK_COOLDOWN_MS = 3_000L;

    private final EasterEventVisantara plugin;
    private final StructureTracker     structureTracker;
    private final EventWindowManager   eventWindow;

    private final ConcurrentHashMap<UUID, Long>    lastCheck     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, StructureEntry> robbitEntries = new ConcurrentHashMap<>();

    public RobbitSpawnListener(EasterEventVisantara plugin,
                               StructureTracker structureTracker,
                               EventWindowManager eventWindow) {
        this.plugin           = plugin;
        this.structureTracker = structureTracker;
        this.eventWindow      = eventWindow;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        Player player = event.getPlayer();
        ConfigManager cfg = plugin.getConfigManager();

        if (!player.getWorld().getName().equals(cfg.getResourceWorldName())) return;
        if (!eventWindow.isEventActive()) return;

        long now  = System.currentTimeMillis();
        Long last = lastCheck.get(player.getUniqueId());
        if (last != null && (now - last) < CHECK_COOLDOWN_MS) return;
        lastCheck.put(player.getUniqueId(), now);

        StructureEntry nearest = structureTracker.getNearestEntry(player.getLocation(), cfg.getRobbitTriggerRadius());
        if (nearest == null) return;

        if (nearest.getRobbitCount() >= cfg.getRobbitMaxPerStructure()) {
            if (plugin.isDebugMode()) {
                player.sendMessage("§7[DEBUG] Robbit cap tercapai di struktur "
                        + nearest.blockX + "," + nearest.blockY + "," + nearest.blockZ);
            }
            return;
        }

        spawnRobbit(nearest, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRobbitDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        StructureEntry entry = robbitEntries.remove(uuid);
        if (entry != null) {
            entry.decrementRobbitCount();
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("[RobbitSpawnListener] Robbit " + uuid + " mati, count struktur: " + entry.getRobbitCount());
            }
        }
    }

    private void spawnRobbit(StructureEntry entry, Location playerLocation) {
        String mobId = plugin.getConfigManager().getRobbitMobId();

        if (MythicBukkit.inst().getMobManager().getMythicMob(mobId).isEmpty()) {
            plugin.getLogger().warning("[RobbitSpawnListener] MythicMob '" + mobId + "' tidak ditemukan!");
            return;
        }

        Location spawnLoc = new Location(
                playerLocation.getWorld(),
                entry.blockX + 0.5,
                entry.blockY + 1.0,
                entry.blockZ + 0.5
        );

        try {
            Entity spawned = MythicBukkit.inst().getAPIHelper().spawnMythicMob(mobId, spawnLoc);
            if (spawned != null) {
                entry.incrementRobbitCount();
                robbitEntries.put(spawned.getUniqueId(), entry);
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[RobbitSpawnListener] Spawn " + mobId
                            + " di " + formatLoc(spawnLoc)
                            + " | count: " + entry.getRobbitCount());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[RobbitSpawnListener] Gagal spawn " + mobId + ": " + e.getMessage());
        }
    }

    public void clearCooldowns() {
        lastCheck.clear();
        robbitEntries.clear();
    }

    private String formatLoc(Location loc) {
        return String.format("%.0f,%.0f,%.0f[%s]",
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "?");
    }
}