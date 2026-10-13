package carpet;

import carpet.schematic.SharedSchematicStore;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.*;

public final class SharedSchematicStoreCheck {
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    private static void rejected(Runnable action) { try { action.run(); throw new AssertionError("Invalid action accepted"); } catch (IllegalArgumentException expected) {} }
    private static Path file(Path directory, boolean malformed) throws Exception {
        NBTTagCompound data = new NBTTagCompound(); data.setInteger("Version", 4); data.setTag("Metadata", new NBTTagCompound());
        NBTTagCompound regions = new NBTTagCompound(), region = new NBTTagCompound(), size = new NBTTagCompound(), pos = new NBTTagCompound();
        pos.setInteger("x", 0); pos.setInteger("y", 0); pos.setInteger("z", 0);
        size.setInteger("x", -2); size.setInteger("y", 1); size.setInteger("z", 1); region.setTag("Size", size); region.setTag("Position", pos);
        NBTTagList palette = new NBTTagList(); NBTTagCompound state = new NBTTagCompound(); state.setString("Name", "minecraft:stone"); palette.appendTag(state); region.setTag("BlockStatePalette", palette);
        region.setTag("BlockStates", new NBTTagLongArray(new long[] {malformed ? 15 : 0})); region.setTag("TileEntities", new NBTTagList());
        regions.setTag("fixture", region); data.setTag("Regions", regions); Path file = Files.createTempFile(directory, "fixture-", ".litematic");
        try (OutputStream output = Files.newOutputStream(file)) { CompressedStreamTools.writeCompressed(data, output); } return file;
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register(); Path root = Files.createTempDirectory("kisde-sharing-check-"); SharedSchematicStore store = new SharedSchematicStore(root);
        UUID author = UUID.randomUUID(), stranger = UUID.randomUUID(), id = UUID.randomUUID();
        String placement = "{\"origin\":[12,64,-30],\"rotation\":\"CLOCKWISE_90\",\"mirror\":\"LEFT_RIGHT\",\"schematic\":\"../../secret\"}";
        String clean = SharedSchematicStore.placement(placement); check(!clean.contains("secret"), "Remote file path retained");
        Path source = file(root, false); SharedSchematicStore.validate(source, placement); String hash = SharedSchematicStore.hash(source);
        Path unsupported = file(root, false); NBTTagCompound unsupportedData;
        try (InputStream input = Files.newInputStream(unsupported)) { unsupportedData = CompressedStreamTools.readCompressed(input); }
        unsupportedData.setInteger("Version", 6);
        try (OutputStream output = Files.newOutputStream(unsupported)) { CompressedStreamTools.writeCompressed(unsupportedData, output); }
        rejected(() -> { try { SharedSchematicStore.validate(unsupported, placement); } catch (IOException e) { throw new RuntimeException(e); } });
        store.commit(source, id, author, "Author", false, 0, "../../Display name", -1, clean, hash);
        check(store.list().size() == 1 && store.get(id).getLong("Revision") == 1, "Commit failed");
        check(store.get(id).getInteger("Dimension") == -1 && store.get(id).getString("Placement").contains("CLOCKWISE_90"), "Transform lost");
        final SharedSchematicStore initial = store;
        rejected(() -> initial.authorize(id, stranger, false, 1)); rejected(() -> initial.authorize(id, author, false, 0)); store.authorize(id, stranger, true, 1);
        store = new SharedSchematicStore(root); check(store.get(id).getString("Owner").equals(author.toString()), "Restart ownership lost");
        final SharedSchematicStore restored = store;
        rejected(() -> restored.checkQuota(author, null, SharedSchematicStore.MAX_FILE + 1));
        rejected(() -> SharedSchematicStore.placement("{\"origin\":[0,256,0]}")); rejected(() -> SharedSchematicStore.placement("{\"origin\":[0,64,0],\"rotation\":\"INVALID\"}"));
        rejected(() -> SharedSchematicStore.name("bad\nname"));
        rejected(() -> SharedSchematicStore.placement("{\"origin\":[18446744073709551616,64,0]}"));
        rejected(() -> SharedSchematicStore.boundedJson(String.join("", Collections.nCopies(1000, "["))));
        Path bad = file(root, true); try { SharedSchematicStore.validate(bad, clean); throw new AssertionError("Bad palette index accepted"); } catch (IllegalArgumentException expected) {}
        UUID blockedId = UUID.randomUUID(); Path failedCommit = file(root, true); String failedHash = SharedSchematicStore.hash(failedCommit); Files.createDirectory(root.resolve(blockedId + ".nbt"));
        try { restored.commit(failedCommit, blockedId, author, "Author", false, 0, "Blocked", 0, clean, failedHash); throw new AssertionError("Manifest replacement unexpectedly succeeded"); }
        catch (IOException expected) { check(!Files.exists(root.resolve(failedHash + ".litematic")), "Failed manifest commit left an orphan blob"); }
        Files.delete(root.resolve(blockedId + ".nbt"));
        Path replacement = file(root, false); restored.commit(replacement, id, author, "Author", false, 1, "Updated", 0, clean, hash);
        check(restored.get(id).getLong("Revision") == 2, "Revision not advanced");
        rejected(() -> restored.authorize(id, author, false, 1));
        NBTTagCompound beforeRename = restored.get(id);
        rejected(() -> { try { restored.rename(id, stranger, false, 2, "Forbidden"); } catch (IOException e) { throw new RuntimeException(e); } });
        rejected(() -> { try { restored.rename(id, author, false, 1, "Stale"); } catch (IOException e) { throw new RuntimeException(e); } });
        rejected(() -> { try { restored.rename(id, author, false, 2, "bad\nname"); } catch (IOException e) { throw new RuntimeException(e); } });
        NBTTagCompound renamed = restored.rename(id, author, false, 2, "Renamed");
        check(renamed.getLong("Revision") == 3 && renamed.getString("Name").equals("Renamed"), "Rename failed");
        for (String key : Arrays.asList("Hash", "Owner", "Author", "Placement", "Dimension", "Bytes")) check(beforeRename.getTag(key).equals(renamed.getTag(key)), "Rename changed " + key);
        check(new SharedSchematicStore(root).get(id).equals(renamed), "Rename did not survive restart");
        NBTTagCompound operatorRename = restored.rename(id, stranger, true, 3, "Operator rename");
        check(operatorRename.getLong("Revision") == 4 && operatorRename.getString("Owner").equals(author.toString()), "Operator rename changed ownership");
        restored.delete(id, stranger, true, 4); check(restored.list().isEmpty(), "Operator delete failed");
        check(!Files.exists(root.resolve(hash + ".litematic")), "Unused blob retained");
        System.out.println("Shared store persistence, ownership, revision, transforms, sanitization and bounds checks passed");
    }
}
