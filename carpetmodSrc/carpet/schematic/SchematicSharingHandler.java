package carpet.schematic;

import carpet.CarpetSettings;
import carpet.network.PluginChannelHandler;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;

public final class SchematicSharingHandler implements PluginChannelHandler {
    public static final String CHANNEL = "bc:schematics";
    public static final int CHUNK = 24576;
    private final MinecraftServer server;
    private SharedSchematicStore store;
    private final Set<EntityPlayerMP> players = new HashSet<>();
    private final Map<UUID, Upload> uploads = new HashMap<>();
    private final Map<UUID, Download> downloads = new HashMap<>();
    private final Map<UUID, Long> requestWindows = new HashMap<>();
    private final Map<UUID, Integer> requestCounts = new HashMap<>();
    private volatile boolean closed;

    public SchematicSharingHandler(MinecraftServer server) { this.server = server; }
    public String[] getChannels() { return new String[] {CHANNEL}; }
    public boolean register(String channel, EntityPlayerMP player) { players.add(player); status(player); return true; }
    public void unregister(String channel, EntityPlayerMP player) {
        players.remove(player); cancel(player.getUniqueID()); requestWindows.remove(player.getUniqueID()); requestCounts.remove(player.getUniqueID());
    }
    private SharedSchematicStore store() throws IOException {
        if (store == null) store = new SharedSchematicStore(server.getActiveAnvilConverter().getFile(server.getFolderName(), "kisde-shared-schematics").toPath());
        return store;
    }
    public void sync() {
        if (!CarpetSettings.schematicShare) { for (UUID actor : new HashSet<>(uploads.keySet())) cancel(actor); for (UUID actor : new HashSet<>(downloads.keySet())) cancel(actor); }
        for (EntityPlayerMP player : players) status(player);
    }
    private void status(EntityPlayerMP player) { NBTTagCompound data = message("Status"); data.setBoolean("Enabled", CarpetSettings.schematicShare); send(player, data); }
    public void onCustomPayload(CPacketCustomPayload packet, EntityPlayerMP player) {
        NBTTagCompound data = null;
        try {
            if (closed || !players.contains(player) || !CarpetSettings.schematicShare) throw new IllegalArgumentException("Server schematic sharing is disabled");
            PacketBuffer bytes = packet.getBufferData();
            if (bytes.readableBytes() > 32767) throw new IllegalArgumentException("Sharing packet too large");
            data = bytes.readCompoundTag();
            if (data == null || bytes.isReadable() || !data.hasKey("Action", 8)) throw new IllegalArgumentException("Invalid sharing packet");
            String action = data.getString("Action");
            if (!"Chunk".equals(action)) throttle(player);
            switch (action) {
                case "List": list(player, data); break;
                case "BeginUpload": begin(player, data); break;
                case "Chunk": chunk(player, data); break;
                case "EndUpload": finish(player, data); break;
                case "Download": download(player, data); break;
                case "Delete": delete(player, data); break;
                case "Cancel": cancel(player.getUniqueID()); break;
                default: throw new IllegalArgumentException("Unknown sharing action");
            }
        } catch (Exception e) {
            cancel(player.getUniqueID());
            String reason = e instanceof IllegalArgumentException ? e.getMessage() : "Unable to process shared schematic";
            if (!(e instanceof IllegalArgumentException)) LogManager.getLogger().warn("Shared schematic request failed", e);
            error(player, reason);
        }
    }
    private void throttle(EntityPlayerMP player) {
        UUID actor = player.getUniqueID(); long now = server.getTickCounter();
        if (now - requestWindows.getOrDefault(actor, -100L) >= 20) { requestWindows.put(actor, now); requestCounts.put(actor, 0); }
        int count = requestCounts.getOrDefault(actor, 0) + 1; requestCounts.put(actor, count);
        if (count > 20) throw new IllegalArgumentException("Too many sharing requests; wait a moment");
    }
    private NBTTagCompound metadata(NBTTagCompound entry, EntityPlayerMP player) {
        NBTTagCompound copy = entry.copy(); copy.setBoolean("CanManage", entry.getString("Owner").equals(player.getUniqueID().toString()) || player.canUseCommand(2, "")); return copy;
    }
    private void list(EntityPlayerMP player, NBTTagCompound request) throws IOException {
        List<NBTTagCompound> entries = store().list(); int size = Math.max(1, Math.min(12, request.getInteger("PageSize")));
        int page = Math.max(0, Math.min(Math.max(0, (entries.size() - 1) / size), request.getInteger("Page")));
        NBTTagList result = new NBTTagList(); for (int i = page * size; i < Math.min(entries.size(), (page + 1) * size); i++) result.appendTag(metadata(entries.get(i), player));
        NBTTagCompound response = message("List"); response.setInteger("Page", page); response.setInteger("Total", entries.size()); response.setTag("Entries", result); send(player, response);
    }
    private void available(EntityPlayerMP player) {
        if (uploads.containsKey(player.getUniqueID()) || downloads.containsKey(player.getUniqueID())) throw new IllegalArgumentException("A file transfer is already active");
        if (uploads.size() + downloads.size() >= 4) throw new IllegalArgumentException("Server transfers are busy; try again shortly");
    }
    private void begin(EntityPlayerMP player, NBTTagCompound data) throws IOException {
        available(player);
        UUID existing = data.getString("Id").isEmpty() ? null : UUID.fromString(data.getString("Id"));
        long revision = data.getLong("Revision");
        if (existing != null) store().authorize(existing, player.getUniqueID(), player.canUseCommand(2, ""), revision);
        else if (revision != 0) throw new IllegalArgumentException("Invalid new share revision");
        int length = data.getInteger("Bytes"); store().checkQuota(player.getUniqueID(), existing, length);
        long reserved = uploads.values().stream().mapToLong(u -> u.length).sum();
        if (store().bytes() + reserved + length > SharedSchematicStore.MAX_LIBRARY) throw new IllegalArgumentException("Shared library storage limit reached");
        if (existing == null && store().list().size() + uploads.size() >= SharedSchematicStore.MAX_ENTRIES) throw new IllegalArgumentException("Shared catalog is full");
        String name = SharedSchematicStore.name(data.getString("Name")), placement = SharedSchematicStore.placement(data.getString("Placement"));
        String hash = data.getString("Hash"); if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid file hash");
        Upload upload = new Upload(UUID.fromString(data.getString("Transfer")), existing == null ? UUID.randomUUID() : existing,
                revision, name, placement, player.dimension, length, hash, store().temporary(), server.getTickCounter());
        uploads.put(player.getUniqueID(), upload);
        NBTTagCompound ready = message("Ready"); ready.setString("Transfer", upload.token.toString()); send(player, ready);
    }
    private void chunk(EntityPlayerMP player, NBTTagCompound data) throws IOException {
        Upload upload = upload(player, data);
        if (upload.finalizing) throw new IllegalArgumentException("Upload is already complete");
        byte[] chunk = data.getByteArray("Data");
        long tick = server.getTickCounter(); if (upload.rateTick != tick) { upload.rateTick = tick; upload.chunks = 0; }
        if (++upload.chunks > 8) throw new IllegalArgumentException("Upload rate is too high");
        if (!data.hasKey("Offset", 3) || data.getInteger("Offset") != upload.offset || chunk.length == 0 || chunk.length > CHUNK || chunk.length > upload.length - upload.offset) throw new IllegalArgumentException("Invalid upload chunk");
        upload.stream.write(chunk); upload.offset += chunk.length; upload.lastTick = tick;
        NBTTagCompound ready = message("Ready"); ready.setString("Transfer", upload.token.toString()); ready.setInteger("Offset", upload.offset); send(player, ready);
    }
    private Upload upload(EntityPlayerMP player, NBTTagCompound data) {
        Upload upload = uploads.get(player.getUniqueID());
        if (upload == null || !upload.token.toString().equals(data.getString("Transfer"))) throw new IllegalArgumentException("Unknown upload transfer");
        return upload;
    }
    private void finish(EntityPlayerMP player, NBTTagCompound data) throws IOException {
        Upload upload = upload(player, data);
        if (upload.finalizing || upload.offset != upload.length) throw new IllegalArgumentException("Incomplete upload");
        upload.stream.close(); upload.finalizing = true;
        CompletableFuture.runAsync(() -> {
            String failure = null;
            try {
                if (!SharedSchematicStore.hash(upload.path).equals(upload.hash)) throw new IllegalArgumentException("File checksum mismatch");
                SharedSchematicStore.validate(upload.path, upload.placement);
            } catch (Exception e) { failure = e instanceof IllegalArgumentException ? e.getMessage() : "Invalid schematic file"; }
            final String error = failure;
            if (closed || !server.isServerRunning()) { remove(upload.path); return; }
            server.addScheduledTask(() -> {
                if (closed || uploads.get(player.getUniqueID()) != upload) { remove(upload.path); return; }
                try {
                    if (error != null) throw new IllegalArgumentException(error);
                    if (!CarpetSettings.schematicShare || !players.contains(player) || player.dimension != upload.dimension) throw new IllegalArgumentException("Sharing access or dimension changed during upload");
                    NBTTagCompound entry = store().commit(upload.path, upload.id, player.getUniqueID(), player.getName(), player.canUseCommand(2, ""), upload.revision, upload.name, upload.dimension, upload.placement, upload.hash);
                    uploads.remove(player.getUniqueID());
                    for (EntityPlayerMP viewer : players) { NBTTagCompound update = message("Changed"); update.setTag("Entry", metadata(entry, viewer)); send(viewer, update); }
                    NBTTagCompound complete = message("Uploaded"); complete.setString("Transfer", upload.token.toString()); complete.setString("Id", upload.id.toString()); send(player, complete);
                } catch (Exception e) { cancel(player.getUniqueID()); error(player, e instanceof IllegalArgumentException ? e.getMessage() : "Unable to store shared schematic"); }
            });
        });
    }
    private void download(EntityPlayerMP player, NBTTagCompound data) throws IOException {
        available(player); NBTTagCompound entry = store().get(UUID.fromString(data.getString("Id")));
        if (entry == null) throw new IllegalArgumentException("Shared schematic no longer exists");
        if (player.dimension != entry.getInteger("Dimension")) throw new IllegalArgumentException("Join the schematic's dimension to load its placement");
        Download download = new Download(UUID.randomUUID(), entry, Files.newInputStream(store().file(entry)), server.getTickCounter()); downloads.put(player.getUniqueID(), download);
        NBTTagCompound begin = message("Download"); begin.setString("Transfer", download.token.toString()); begin.setTag("Entry", metadata(entry, player)); send(player, begin);
    }
    private void delete(EntityPlayerMP player, NBTTagCompound data) throws IOException {
        UUID id = UUID.fromString(data.getString("Id")); store().delete(id, player.getUniqueID(), player.canUseCommand(2, ""), data.getLong("Revision"));
        for (EntityPlayerMP viewer : players) { NBTTagCompound removed = message("Deleted"); removed.setString("Id", id.toString()); send(viewer, removed); }
    }
    public void tick() {
        if (closed) return;
        long now = server.getTickCounter();
        for (UUID actor : new HashSet<>(uploads.keySet())) if (now - uploads.get(actor).lastTick > 600) { EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(actor); cancel(actor); if (player != null) error(player, "Shared upload timed out"); }
        for (UUID actor : new HashSet<>(downloads.keySet())) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(actor); Download download = downloads.get(actor);
            try {
                if (!CarpetSettings.schematicShare || player == null || !players.contains(player) || player.dimension != download.entry.getInteger("Dimension")) { cancel(actor); continue; }
                byte[] bytes = new byte[Math.min(CHUNK, download.entry.getInteger("Bytes") - download.offset)]; int count = download.stream.read(bytes);
                if (count < 0 && download.offset != download.entry.getInteger("Bytes")) throw new IOException("Truncated shared schematic");
                if (count > 0) { NBTTagCompound chunk = message("Chunk"); chunk.setString("Transfer", download.token.toString()); chunk.setInteger("Offset", download.offset); chunk.setByteArray("Data", count == bytes.length ? bytes : Arrays.copyOf(bytes, count)); send(player, chunk); download.offset += count; }
                if (download.offset == download.entry.getInteger("Bytes")) { NBTTagCompound done = message("Downloaded"); done.setString("Transfer", download.token.toString()); send(player, done); cancel(actor); }
            } catch (Exception e) { cancel(actor); if (player != null) error(player, "Shared download failed"); }
        }
    }
    private void cancel(UUID actor) {
        Upload upload = uploads.remove(actor); if (upload != null) { try { upload.stream.close(); } catch (IOException ignored) {} remove(upload.path); }
        Download download = downloads.remove(actor); if (download != null) try { download.stream.close(); } catch (IOException ignored) {}
    }
    public void close() { closed = true; for (UUID actor : new HashSet<>(uploads.keySet())) cancel(actor); for (UUID actor : new HashSet<>(downloads.keySet())) cancel(actor); players.clear(); }
    private static void remove(Path path) { try { Files.deleteIfExists(path); } catch (IOException e) { LogManager.getLogger().warn("Unable to remove shared upload temporary file", e); } }
    private static NBTTagCompound message(String action) { NBTTagCompound tag = new NBTTagCompound(); tag.setString("Action", action); return tag; }
    private static void error(EntityPlayerMP player, String text) { NBTTagCompound tag = message("Error"); tag.setString("Message", text == null ? "Sharing request failed" : text.substring(0, Math.min(256, text.length()))); send(player, tag); }
    private static void send(EntityPlayerMP player, NBTTagCompound tag) {
        PacketBuffer bytes = new PacketBuffer(Unpooled.buffer());
        try { bytes.writeCompoundTag(tag); player.connection.sendPacket(new SPacketCustomPayload(CHANNEL, bytes)); }
        catch (RuntimeException e) { if (bytes.refCnt() > 0) bytes.release(); LogManager.getLogger().warn("Unable to send schematic sharing packet", e); }
    }
    private static final class Upload {
        final UUID token, id; final long revision; final String name, placement, hash; final int dimension, length; final Path path; final OutputStream stream;
        int offset, chunks; long lastTick, rateTick = -1; boolean finalizing;
        Upload(UUID token, UUID id, long revision, String name, String placement, int dimension, int length, String hash, Path path, long tick) throws IOException {
            this.token = token; this.id = id; this.revision = revision; this.name = name; this.placement = placement; this.dimension = dimension; this.length = length; this.hash = hash; this.path = path; stream = Files.newOutputStream(path); lastTick = tick;
        }
    }
    private static final class Download {
        final UUID token; final NBTTagCompound entry; final InputStream stream; int offset;
        Download(UUID token, NBTTagCompound entry, InputStream stream, long tick) { this.token = token; this.entry = entry; this.stream = stream; }
    }
}
