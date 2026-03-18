package me.bintanq.util;

import me.bintanq.EasterEventVisantara;
import me.bintanq.manager.ConfigManager;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

public class LootPopulator {

    private static final int SLOTS_TO_FILL = 9;
    private static final int XZ_RADIUS     = 24;
    private static final int Y_DOWN        = 5;
    private static final int Y_UP          = 30;

    private final EasterEventVisantara plugin;
    private final Random rng = new Random();

    public LootPopulator(EasterEventVisantara plugin) {
        this.plugin = plugin;
    }

    public void populateNearbyChests(Location origin, String structureVariant) {
        ConfigManager cfg          = plugin.getConfigManager();
        String        lootTableKey = cfg.getLootTableForStructure(structureVariant);

        List<Map<String, Object>> entries = cfg.getLootTableEntries(lootTableKey);
        if (entries.isEmpty()) {
            plugin.getLogger().warning("[LootPopulator] Loot table '" + lootTableKey
                    + "' kosong untuk variant '" + structureVariant + "'.");
            return;
        }

        int totalWeight = entries.stream()
                .mapToInt(e -> (int) e.getOrDefault("weight", 1))
                .sum();

        List<Block> chests = findNearbyChests(origin);

        plugin.getLogger().info("[LootPopulator] Ditemukan " + chests.size()
                + " chest di sekitar " + formatLoc(origin)
                + " (scan XZ=" + XZ_RADIUS + " Y=-" + Y_DOWN + "/+" + Y_UP + ")"
                + " | table='" + lootTableKey + "'");

        for (Block block : chests) {
            fillChest(block, entries, totalWeight);
        }

        if (!chests.isEmpty()) {
            plugin.getLogger().info("[LootPopulator] " + chests.size() + " chest diisi -> '" + lootTableKey + "'");
        }
    }

    private List<Block> findNearbyChests(Location origin) {
        List<Block> result = new ArrayList<>();
        for (int dx = -XZ_RADIUS; dx <= XZ_RADIUS; dx++) {
            for (int dz = -XZ_RADIUS; dz <= XZ_RADIUS; dz++) {
                for (int dy = -Y_DOWN; dy <= Y_UP; dy++) {
                    Block block = origin.getBlock().getRelative(dx, dy, dz);
                    if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                        result.add(block);
                    }
                }
            }
        }
        return result;
    }

    private void fillChest(Block block, List<Map<String, Object>> entries, int totalWeight) {
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) return;

        // Ambil BlockState langsung dari block world, bukan cache
        if (!(block.getState() instanceof Chest chest)) {
            plugin.getLogger().warning("[LootPopulator] BlockState bukan Chest di " + formatLoc(block.getLocation()));
            return;
        }

        // getBlockInventory() = inventory langsung di block dunia, bukan snapshot
        Inventory inv = chest.getBlockInventory();
        inv.clear();

        for (int slot = 0; slot < SLOTS_TO_FILL; slot++) {
            Map<String, Object> entry = weightedPick(entries, totalWeight);
            if (entry == null) continue;

            String type   = (String) entry.getOrDefault("type",       "VANILLA");
            String id     = (String) entry.getOrDefault("id",         "STONE");
            int    minAmt = (int)    entry.getOrDefault("amount-min", 1);
            int    maxAmt = (int)    entry.getOrDefault("amount-max", 1);
            int    amount = minAmt + (maxAmt > minAmt ? rng.nextInt(maxAmt - minAmt + 1) : 0);

            ItemStack item = buildItem(type, id, amount);
            if (item != null) inv.setItem(slot, item);
        }

        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[LootPopulator] Filled chest @ " + formatLoc(block.getLocation()));
        }
    }

    private Map<String, Object> weightedPick(List<Map<String, Object>> entries, int totalWeight) {
        if (totalWeight <= 0 || entries.isEmpty()) return null;
        int roll = rng.nextInt(totalWeight);
        int cumulative = 0;
        for (Map<String, Object> entry : entries) {
            cumulative += (int) entry.getOrDefault("weight", 1);
            if (roll < cumulative) return entry;
        }
        return entries.get(entries.size() - 1);
    }

    private ItemStack buildItem(String type, String id, int amount) {
        if ("MMOITEMS".equalsIgnoreCase(type)) {
            ItemStack mmo = buildMmoItem(id, amount);
            if (mmo != null) return mmo;
            plugin.getLogger().warning("[LootPopulator] MMOItems '" + id + "' tidak ada, fallback ke EMERALD.");
            return new ItemStack(Material.EMERALD, 1);
        }
        try {
            return new ItemStack(Material.valueOf(id.toUpperCase()), amount);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[LootPopulator] Material tidak dikenal: '" + id + "'");
            return null;
        }
    }

    private ItemStack buildMmoItem(String typeAndId, int amount) {
        String[] parts = typeAndId.split(":", 2);
        if (parts.length < 2) {
            plugin.getLogger().warning("[LootPopulator] Format MMOITEMS salah (TYPE:ID): '" + typeAndId + "'");
            return null;
        }
        String typeStr = parts[0].toUpperCase();
        String itemId  = parts[1].toUpperCase();
        try {
            Type mmoType = MMOItems.plugin.getTypes().get(typeStr);
            if (mmoType == null) {
                plugin.getLogger().warning("[LootPopulator] MMOItems type '" + typeStr + "' tidak ada.");
                return null;
            }
            ItemStack stack = MMOItems.plugin.getItem(mmoType, itemId);
            if (stack == null) {
                plugin.getLogger().warning("[LootPopulator] MMOItems item '" + itemId + "' tidak ada.");
                return null;
            }
            stack.setAmount(amount);
            return stack;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[LootPopulator] Error build MMOItems '" + typeAndId + "'", e);
            return null;
        }
    }

    private String formatLoc(Location loc) {
        return String.format("%.0f,%.0f,%.0f[%s]",
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "?");
    }
}