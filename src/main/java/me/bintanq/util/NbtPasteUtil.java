package me.bintanq.util;

import com.fastasyncworldedit.core.FaweAPI;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import me.bintanq.EasterEventVisantara;
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class NbtPasteUtil {

    private static final String SCHEMATICS_FOLDER = "schematics";

    private final EasterEventVisantara plugin;
    private final File schematicsDir;

    public NbtPasteUtil(EasterEventVisantara plugin) {
        this.plugin        = plugin;
        this.schematicsDir = new File(plugin.getDataFolder(), SCHEMATICS_FOLDER);
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs();
            plugin.getLogger().info("[NbtPasteUtil] Folder schematics dibuat: " + schematicsDir.getAbsolutePath());
            plugin.getLogger().info("[NbtPasteUtil] Taruh file .schem kamu di: " + schematicsDir.getAbsolutePath());
        }
    }

    public CompletableFuture<Boolean> pasteAsync(String schematicFileName, Location origin, boolean ignoreAir) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            File file = new File(schematicsDir, schematicFileName);

            if (!file.exists()) {
                plugin.getLogger().warning("[NbtPasteUtil] Schematic tidak ditemukan: " + file.getAbsolutePath());
                plugin.getLogger().warning("[NbtPasteUtil] Taruh file ke: plugins/EasterEventVisantara/schematics/" + schematicFileName);
                future.complete(false);
                return;
            }

            ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format == null) {
                plugin.getLogger().warning("[NbtPasteUtil] Format tidak dikenali: " + schematicFileName);
                future.complete(false);
                return;
            }

            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                Clipboard clipboard = reader.read();
                paste(clipboard, origin, ignoreAir);
                plugin.getLogger().info("[NbtPasteUtil] Berhasil paste '" + schematicFileName + "' di " + formatLoc(origin));
                future.complete(true);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[NbtPasteUtil] Gagal paste '" + schematicFileName + "'", e);
                future.complete(false);
            }
        });

        return future;
    }

    public Clipboard loadClipboard(String schematicFileName) {
        File file = new File(schematicsDir, schematicFileName);
        if (!file.exists()) {
            plugin.getLogger().warning("[NbtPasteUtil] Schematic tidak ditemukan: " + file.getAbsolutePath());
            return null;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            plugin.getLogger().warning("[NbtPasteUtil] Format tidak dikenali: " + schematicFileName);
            return null;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            return reader.read();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[NbtPasteUtil] Gagal load clipboard", e);
            return null;
        }
    }

    private void paste(Clipboard clipboard, Location origin, boolean ignoreAir) throws Exception {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(origin.getWorld());
        BlockVector3 to = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());

        try (EditSession editSession = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(weWorld)
                .fastMode(true)
                .build()) {

            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(ignoreAir)
                    .build();

            Operations.complete(operation);
            editSession.flushSession();
        }
    }

    public File getSchematicsDir() {
        return schematicsDir;
    }

    private String formatLoc(Location loc) {
        return String.format("%.0f, %.0f, %.0f in %s",
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "unknown");
    }
}