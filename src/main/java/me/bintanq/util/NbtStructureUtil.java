package me.bintanq.util;

import me.bintanq.EasterEventVisantara;
import org.bukkit.Location;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class NbtStructureUtil {

    private static final String STRUCTURES_FOLDER = "structures";

    private final EasterEventVisantara plugin;
    private final File                 structuresDir;
    private final Random               rng = new Random();

    public NbtStructureUtil(EasterEventVisantara plugin) {
        this.plugin        = plugin;
        this.structuresDir = new File(plugin.getDataFolder(), STRUCTURES_FOLDER);
        if (!structuresDir.exists()) {
            structuresDir.mkdirs();
            plugin.getLogger().info("[NbtStructureUtil] Folder structures dibuat: " + structuresDir.getAbsolutePath());
            plugin.getLogger().info("[NbtStructureUtil] Taruh file .nbt kamu di: " + structuresDir.getAbsolutePath());
        }
    }

    public File getStructuresDir() {
        return structuresDir;
    }

    public CompletableFuture<Boolean> pasteAsync(String fileName, Location origin, boolean includeEntities) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        File file = new File(structuresDir, fileName);
        if (!file.exists()) {
            plugin.getLogger().warning("[NbtStructureUtil] File tidak ditemukan: " + file.getAbsolutePath());
            plugin.getLogger().warning("[NbtStructureUtil] Taruh file ke: " + file.getAbsolutePath());
            future.complete(false);
            return future;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            byte[] data;
            try (FileInputStream fis = new FileInputStream(file)) {
                data = fis.readAllBytes();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[NbtStructureUtil] Gagal baca file '" + fileName + "'", e);
                future.complete(false);
                return;
            }

            final byte[] finalData = data;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    StructureManager sm = plugin.getServer().getStructureManager();
                    Structure structure  = sm.loadStructure(new ByteArrayInputStream(finalData));

                    structure.place(origin, includeEntities,
                            org.bukkit.block.structure.StructureRotation.NONE,
                            org.bukkit.block.structure.Mirror.NONE,
                            0, 1.0f, rng);

                    plugin.getLogger().info("[NbtStructureUtil] Berhasil paste '" + fileName
                            + "' di " + formatLoc(origin));
                    future.complete(true);

                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "[NbtStructureUtil] Error paste '" + fileName + "'", e);
                    future.complete(false);
                }
            });
        });

        return future;
    }

    private String formatLoc(Location loc) {
        return String.format("%.0f,%.0f,%.0f[%s]",
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "?");
    }
}