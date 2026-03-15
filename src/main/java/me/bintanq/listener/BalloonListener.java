package me.bintanq.listener;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BalloonListener implements Listener {

    private final EasterEventVisantara plugin;

    // Simpan last attacker manual karena EntityDeathEvent kadang tidak set killer dengan benar
    private final ConcurrentHashMap<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();

    public BalloonListener(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBalloonHit(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();
        if (!(event.getDamager() instanceof Player player)) return;

        UUID entityUUID = damaged.getUniqueId();
        if (!plugin.getBalloonTracker().isTracked(entityUUID)) return;

        ConfigManager cfg = plugin.getConfigManager();

        if (cfg.isRequireSpecificItem()) {
            ItemStack held   = player.getInventory().getItemInMainHand();
            String   itemStr = cfg.getRequiredItemString();
            boolean  passes  = validateItem(held, itemStr);

            if (plugin.isDebugMode()) {
                player.sendMessage(cfg.getMsgDebugHit(entityUUID.toString(), describeItem(held, itemStr), passes));
            }

            if (!passes) {
                event.setCancelled(true);
                player.sendMessage(cfg.getMsgItemMismatch());
                return;
            }
        } else if (plugin.isDebugMode()) {
            player.sendMessage(cfg.getMsgDebugHit(entityUUID.toString(), "any (no requirement)", true));
        }

        // Catat attacker terakhir sebagai fallback untuk death event
        lastAttacker.put(entityUUID, player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBalloonDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        UUID uuid = entity.getUniqueId();

        if (!plugin.getBalloonTracker().isTracked(uuid)) return;

        event.getDrops().clear();
        event.setDroppedExp(0);

        plugin.getBalloonTracker().unregister(uuid);

        // Prioritaskan killer dari event, fallback ke lastAttacker map
        Player killer = entity.getKiller();
        if (killer == null) {
            UUID lastUUID = lastAttacker.remove(uuid);
            if (lastUUID != null) {
                killer = Bukkit.getPlayer(lastUUID);
            }
        } else {
            lastAttacker.remove(uuid);
        }

        String killerName = (killer != null) ? killer.getName() : "Unknown";

        ConfigManager cfg = plugin.getConfigManager();

        if (cfg.isAnnounceGlobal()) {
            List<String> lines = cfg.getMsgBalloonPoppedLines(killerName);
            Bukkit.getOnlinePlayers().forEach(p -> lines.forEach(p::sendMessage));
        } else {
            if (killer != null) {
                List<String> lines = cfg.getMsgBalloonPoppedLines(killerName);
                lines.forEach(killer::sendMessage);
            }
        }

        List<String> commands = cfg.getRewardCommands();
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", killerName));
        }
    }

    private boolean validateItem(ItemStack held, String itemStr) {
        if (held == null || held.getType() == Material.AIR) return false;

        String upper = itemStr.toUpperCase();

        if (upper.startsWith("VANILLA:")) {
            String matName = upper.substring(8);
            try {
                return held.getType() == Material.valueOf(matName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Material tidak valid di config: " + matName);
                return false;
            }
        }

        if (upper.startsWith("MMOITEMS:")) {
            String[] parts = itemStr.split(":", 3);
            if (parts.length < 3) {
                plugin.getLogger().warning("Format MMOItems tidak valid (butuh MMOITEMS:TYPE:ID): " + itemStr);
                return false;
            }

            String typeStr = parts[1].toUpperCase();
            String itemId  = parts[2].toUpperCase();

            try {
                Type type = MMOItems.plugin.getTypes().get(typeStr);
                if (type == null) {
                    plugin.getLogger().warning("MMOItems type tidak ditemukan: " + typeStr);
                    return false;
                }

                NBTItem nbtItem = NBTItem.get(held);
                if (!nbtItem.hasType()) return false;

                String foundType = nbtItem.getType();
                String foundId   = nbtItem.getString("MMOITEMS_ITEM_ID");

                if (foundId == null) return false;

                return foundType.equalsIgnoreCase(typeStr) && foundId.equalsIgnoreCase(itemId);

            } catch (Exception e) {
                plugin.getLogger().warning("MMOItems validation error: " + e.getMessage());
                return false;
            }
        }

        plugin.getLogger().warning("Format item tidak dikenal di config: " + itemStr);
        return false;
    }

    private String describeItem(ItemStack held, String requiredStr) {
        if (held == null || held.getType() == Material.AIR) return "AIR";
        return held.getType().name() + " (required: " + requiredStr + ")";
    }
}