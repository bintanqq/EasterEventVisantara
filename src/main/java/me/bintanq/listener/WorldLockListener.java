package me.bintanq.listener;

import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import me.bintanq.manager.WorldResetManager;
import me.bintanq.task.StructurePlacementTask;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class WorldLockListener implements Listener {

    private final EasterEventVisantara plugin;

    public WorldLockListener(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    // Saat player berpindah world secara normal (portal, /mv tp, dll)
    @EventHandler(priority = EventPriority.HIGH)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!event.getPlayer().getWorld().getName()
                .equals(plugin.getConfigManager().getResourceWorldName())) return;
        if (!isLocked()) return;

        event.getPlayer().sendMessage(plugin.getConfigManager().getMsgWorldLocked());
        Bukkit.getScheduler().runTask(plugin, () ->
                event.getPlayer().teleport(plugin.getWorldResetManager().getFallbackLocation()));
    }

    // Saat player respawn di resource world (edge case)
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!event.getRespawnLocation().getWorld().getName()
                .equals(plugin.getConfigManager().getResourceWorldName())) return;
        if (!isLocked()) return;

        event.setRespawnLocation(plugin.getWorldResetManager().getFallbackLocation());
    }

    private boolean isLocked() {
        WorldResetManager wrm = plugin.getWorldResetManager();
        if (wrm != null && wrm.isResetting()) return true;
        StructurePlacementTask spt = plugin.getStructurePlacementTask();
        return spt != null && spt.isSeeding();
    }
}