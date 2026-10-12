package carpet.schematic;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

public final class SharedSchematicStore {
    public static final int MAX_FILE = 16 * 1024 * 1024, MAX_ENTRIES = 256, MAX_OWNER = 32;
    public static final long MAX_LIBRARY = 1024L * 1024 * 1024;
    public static final int MAX_REGIONS = 32, MAX_BLOCKS = 16 * 1024 * 1024;
    private final Path root;
    private final Map<UUID, NBTTagCompound> entries = new LinkedHashMap<>();

    public SharedSchematicStore(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root, "*.nbt")) {
            for (Path file : files) {
                if (entries.size() >= MAX_ENTRIES) throw new IOException("Shared catalog exceeds limit");
                NBTTagCompound entry = readNbt(file, 65536);
                UUID id = UUID.fromString(entry.getString("Id"));
                if (!file.getFileName().toString().equals(id + ".nbt")) throw new IOException("Invalid catalog filename");
                UUID.fromString(entry.getString("Owner"));
                name(entry.getString("Name"));
                placement(entry.getString("Placement"));
                Path blob = blob(entry.getString("Hash"));
                if (entry.getInteger("Bytes") <= 0 || entry.getInteger("Bytes") > MAX_FILE || entry.getLong("Revision") <= 0 || !Files.isRegularFile(blob) || Files.size(blob) != entry.getInteger("Bytes")) throw new IOException("Missing shared schematic");
                entries.put(id, entry);
            }
        } catch (IllegalArgumentException e) { throw new IOException("Invalid shared catalog", e); }
        if (bytes() > MAX_LIBRARY) throw new IOException("Shared library exceeds storage limit");
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if ((name.startsWith("incoming-") || name.startsWith("manifest-")) && name.endsWith(".tmp")) Files.deleteIfExists(file);
                else if (name.matches("[0-9a-f]{64}\\.litematic") && entries.values().stream().noneMatch(e -> name.equals(e.getString("Hash") + ".litematic"))) Files.deleteIfExists(file);
            }
        }
    }
    public List<NBTTagCompound> list() {
        List<NBTTagCompound> result = new ArrayList<>();
        for (NBTTagCompound entry : entries.values()) result.add(entry.copy());
        result.sort(Comparator.comparing(e -> e.getString("Name").toLowerCase(Locale.ROOT)));
        return result;
    }
    public NBTTagCompound get(UUID id) { NBTTagCompound e = entries.get(id); return e == null ? null : e.copy(); }
    public Path file(NBTTagCompound entry) { return blob(entry.getString("Hash")); }
    public Path temporary() throws IOException { return Files.createTempFile(root, "incoming-", ".tmp"); }
    public long bytes() { long sum = 0; for (NBTTagCompound e : entries.values()) sum += e.getInteger("Bytes"); return sum; }
    public void authorize(UUID id, UUID actor, boolean operator, long revision) {
        NBTTagCompound entry = entries.get(id);
        if (entry == null) throw new IllegalArgumentException("Shared schematic no longer exists");
        if (!operator && !entry.getString("Owner").equals(actor.toString())) throw new IllegalArgumentException("Only the author or an operator can manage this share");
        if (entry.getLong("Revision") != revision) throw new IllegalArgumentException("Share changed; refresh and try again");
    }
    public void checkQuota(UUID actor, UUID updating, int incoming) {
        if (incoming <= 0 || incoming > MAX_FILE) throw new IllegalArgumentException("Schematic must be between 1 byte and 16 MiB");
        long own = entries.values().stream().filter(e -> e.getString("Owner").equals(actor.toString())).count();
        if (updating == null && (entries.size() >= MAX_ENTRIES || own >= MAX_OWNER)) throw new IllegalArgumentException("Shared schematic count limit reached");
        long previous = updating == null ? 0 : entries.get(updating).getInteger("Bytes");
        if (bytes() - previous + incoming > MAX_LIBRARY) throw new IllegalArgumentException("Shared library storage limit reached");
    }
    public NBTTagCompound commit(Path temporary, UUID id, UUID actor, String author, boolean operator,
            long expectedRevision, String label, int dimension, String transform, String hash) throws IOException {
        NBTTagCompound old = entries.get(id);
        if (old != null) authorize(id, actor, operator, expectedRevision);
        else if (expectedRevision != 0) throw new IllegalArgumentException("Shared schematic was deleted");
        checkQuota(actor, old == null ? null : id, Math.toIntExact(Files.size(temporary)));
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Id", id.toString()); entry.setString("Name", name(label));
        entry.setString("Owner", old == null ? actor.toString() : old.getString("Owner"));
        entry.setString("Author", old == null ? author : old.getString("Author"));
        entry.setLong("Revision", old == null ? 1 : old.getLong("Revision") + 1);
        entry.setInteger("Dimension", dimension); entry.setString("Placement", placement(transform));
        entry.setString("Hash", hash); entry.setInteger("Bytes", Math.toIntExact(Files.size(temporary)));
        Path destination = blob(hash);
        boolean createdBlob = !Files.exists(destination);
        if (createdBlob) Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        else Files.delete(temporary);
        Path manifest = Files.createTempFile(root, "manifest-", ".tmp");
        boolean committed = false;
        try {
            try (OutputStream stream = Files.newOutputStream(manifest)) { CompressedStreamTools.writeCompressed(entry, stream); }
            Files.move(manifest, root.resolve(id + ".nbt"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            committed = true;
        } finally {
            Files.deleteIfExists(manifest);
            if (!committed && createdBlob && entries.values().stream().noneMatch(e -> hash.equals(e.getString("Hash")))) Files.deleteIfExists(destination);
        }
        entries.put(id, entry);
        if (old != null) cleanBlob(old.getString("Hash"));
        return entry.copy();
    }
    public void delete(UUID id, UUID actor, boolean operator, long revision) throws IOException {
        authorize(id, actor, operator, revision);
        NBTTagCompound old = entries.get(id);
        Files.delete(root.resolve(id + ".nbt")); entries.remove(id); cleanBlob(old.getString("Hash"));
    }
    private void cleanBlob(String hash) throws IOException {
        if (entries.values().stream().noneMatch(e -> hash.equals(e.getString("Hash")))) {
            try { Files.deleteIfExists(blob(hash)); }
            catch (IOException e) { org.apache.logging.log4j.LogManager.getLogger().warn("Unable to remove unused shared schematic blob", e); }
        }
    }
    private Path blob(String hash) {
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid file hash");
        return root.resolve(hash + ".litematic");
    }
    public static String name(String name) {
        if (name.isEmpty() || name.length() > 96 || name.chars().anyMatch(c -> Character.isISOControl(c) || c == 167)) throw new IllegalArgumentException("Invalid schematic name");
        return name;
    }
    public static String placement(String text) {
        if (text.length() > 16384) throw new IllegalArgumentException("Placement settings too large");
        boundedJson(text);
        JsonObject input = new JsonParser().parse(text).getAsJsonObject(), clean = new JsonObject();
        clean.add("origin", position(input.getAsJsonArray("origin"), true));
        clean.addProperty("rotation", rotation(input, "rotation")); clean.addProperty("mirror", mirror(input));
        if (input.has("placements")) {
            JsonArray regions = input.getAsJsonArray("placements"), output = new JsonArray(); Set<String> names = new HashSet<>();
            if (regions.size() > MAX_REGIONS) throw new IllegalArgumentException("Too many subregion placements");
            for (JsonElement item : regions) {
                JsonObject source = item.getAsJsonObject(), settings = source.getAsJsonObject("placement");
                String regionName = source.get("name").getAsString();
                if (regionName.isEmpty() || regionName.length() > 128 || !names.add(regionName)) throw new IllegalArgumentException("Invalid subregion name");
                JsonObject target = new JsonObject(); target.addProperty("name", regionName);
                target.add("pos", position(settings.getAsJsonArray("pos"), false));
                target.add("default_pos", settings.has("default_pos") ? position(settings.getAsJsonArray("default_pos"), false) : target.get("pos"));
                target.addProperty("rotation", rotation(settings, "rotation")); target.addProperty("mirror", mirror(settings));
                target.addProperty("enabled", !settings.has("enabled") || settings.get("enabled").getAsBoolean());
                target.addProperty("ignore_entities", settings.has("ignore_entities") && settings.get("ignore_entities").getAsBoolean());
                JsonObject pair = new JsonObject(); pair.addProperty("name", regionName); pair.add("placement", target); output.add(pair);
            }
            clean.add("placements", output);
        }
        return clean.toString();
    }
    public static void boundedJson(String text) {
        int depth = 0; boolean quoted = false, escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 16) throw new IllegalArgumentException("Placement JSON is too deeply nested"); }
            else if (c == '}' || c == ']') { if (--depth < 0) throw new IllegalArgumentException("Invalid placement JSON"); }
        }
        if (depth != 0 || quoted) throw new IllegalArgumentException("Invalid placement JSON");
    }
    private static JsonArray position(JsonArray pos, boolean absolute) {
        if (pos == null || pos.size() != 3) throw new IllegalArgumentException("Invalid placement position");
        JsonArray result = new JsonArray();
        for (int i = 0; i < 3; i++) {
            long value = Long.parseLong(pos.get(i).getAsString());
            if (value < -30000000 || value > 30000000 || (absolute && i == 1 && (value < 0 || value > 255))) throw new IllegalArgumentException("Placement outside world bounds");
            result.add(new JsonPrimitive((int) value));
        }
        return result;
    }
    private static String rotation(JsonObject object, String key) {
        String value = object.has(key) ? object.get(key).getAsString() : "NONE";
        if (!Arrays.asList("NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90").contains(value)) throw new IllegalArgumentException("Invalid rotation");
        return value;
    }
    private static String mirror(JsonObject object) {
        String value = object.has("mirror") ? object.get("mirror").getAsString() : "NONE";
        if (!Arrays.asList("NONE", "LEFT_RIGHT", "FRONT_BACK").contains(value)) throw new IllegalArgumentException("Invalid mirror");
        return value;
    }
    public static NBTTagCompound readNbt(Path file, long limit) throws IOException {
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            NBTTagCompound tag = CompressedStreamTools.read(input, new NBTSizeTracker(limit));
            if (input.read() != -1) throw new IOException("Trailing schematic data");
            return tag;
        }
    }
    public static void validate(Path file, String placement) throws IOException {
        if (Files.size(file) <= 0 || Files.size(file) > MAX_FILE) throw new IllegalArgumentException("Invalid schematic file size");
        NBTTagCompound tag = readNbt(file, 64L * 1024 * 1024);
        if (tag.getInteger("Version") < 1 || tag.getInteger("Version") > 4 || !tag.hasKey("Regions", 10)) throw new IllegalArgumentException("Expected a supported 1.12.2 Litematica file");
        NBTTagCompound metadata = tag.getCompoundTag("Metadata");
        if (metadata.getString("Name").length() > 256 || metadata.getString("Author").length() > 128 || metadata.getString("Description").length() > 8192) throw new IllegalArgumentException("Schematic metadata text too large");
        NBTTagCompound regions = tag.getCompoundTag("Regions");
        if (regions.getSize() == 0 || regions.getSize() > MAX_REGIONS) throw new IllegalArgumentException("Invalid schematic region count");
        long total = 0, chunks = 0;
        for (String key : regions.getKeySet()) {
            if (key.isEmpty() || key.length() > 128 || !regions.hasKey(key, 10)) throw new IllegalArgumentException("Invalid region name");
            NBTTagCompound region = regions.getCompoundTag(key), size = region.getCompoundTag("Size"), position = region.getCompoundTag("Position");
            for (String axis : new String[] {"x", "y", "z"}) {
                if (!size.hasKey(axis, 3) || !position.hasKey(axis, 3) || Math.abs((long) position.getInteger(axis)) > 30000000) throw new IllegalArgumentException("Invalid region position or size");
            }
            long x = Math.abs((long) size.getInteger("x")), y = Math.abs((long) size.getInteger("y")), z = Math.abs((long) size.getInteger("z"));
            if (x == 0 || y == 0 || z == 0 || x > 4096 || y > 256 || z > 4096) throw new IllegalArgumentException("Invalid region dimensions");
            long volume = x * y * z; total += volume; chunks += ((x + 30) / 16) * ((z + 30) / 16);
            if (total > MAX_BLOCKS || chunks > 16384) throw new IllegalArgumentException("Schematic exceeds render size limits");
            NBTTagList palette = region.getTagList("BlockStatePalette", 10);
            if (palette.tagCount() == 0 || palette.tagCount() > Math.min(volume, 65536)) throw new IllegalArgumentException("Invalid block palette");
            for (int i = 0; i < palette.tagCount(); i++) {
                NBTTagCompound state = palette.getCompoundTagAt(i);
                if (!Block.REGISTRY.containsKey(new ResourceLocation(state.getString("Name")))) throw new IllegalArgumentException("Unknown block state");
                SchematicPasteHandler.readState(state);
            }
            if (!region.hasKey("BlockStates", 12)) throw new IllegalArgumentException("Missing block data");
            long[] words;
            try { Field data = Arrays.stream(NBTTagLongArray.class.getDeclaredFields()).filter(f -> f.getType() == long[].class).findFirst().orElseThrow(() -> new IllegalStateException("Missing packed block array")); data.setAccessible(true); words = (long[]) data.get(region.getTag("BlockStates")); }
            catch (ReflectiveOperationException e) { throw new IOException("Unable to read block data", e); }
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.tagCount() - 1));
            if (words.length != (volume * bits + 63) / 64) throw new IllegalArgumentException("Incorrect packed block length");
            long mask = (1L << bits) - 1;
            for (long i = 0; i < volume; i++) {
                long bit = i * bits; int word = (int) (bit >>> 6), offset = (int) (bit & 63); long id = words[word] >>> offset;
                if (offset + bits > 64) id |= words[word + 1] << (64 - offset);
                if ((id & mask) >= palette.tagCount()) throw new IllegalArgumentException("Invalid palette index");
            }
            NBTTagList tiles = region.getTagList("TileEntities", 10); Set<Long> positions = new HashSet<>();
            if (tiles.tagCount() > volume) throw new IllegalArgumentException("Too many block entities");
            for (int i = 0; i < tiles.tagCount(); i++) {
                NBTTagCompound tile = tiles.getCompoundTagAt(i); int tx = tile.getInteger("x"), ty = tile.getInteger("y"), tz = tile.getInteger("z");
                if (!tile.hasKey("id", 8) || !tile.hasKey("x", 3) || !tile.hasKey("y", 3) || !tile.hasKey("z", 3) || tx < 0 || ty < 0 || tz < 0 || tx >= x || ty >= y || tz >= z || !positions.add((ty * z + tz) * x + tx)) throw new IllegalArgumentException("Invalid block entity position");
            }
        }
        JsonObject transform = new JsonParser().parse(placement(placement)).getAsJsonObject();
        if (transform.has("placements")) for (JsonElement item : transform.getAsJsonArray("placements")) {
            if (!regions.hasKey(item.getAsJsonObject().get("name").getAsString(), 10)) throw new IllegalArgumentException("Placement refers to unknown subregion");
        }
    }
    public static String hash(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] block = new byte[32768];
            try (InputStream stream = Files.newInputStream(file)) { int n; while ((n = stream.read(block)) != -1) digest.update(block, 0, n); }
            StringBuilder value = new StringBuilder(); for (byte b : digest.digest()) value.append(String.format("%02x", b & 255)); return value.toString();
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
