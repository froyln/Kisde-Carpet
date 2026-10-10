package carpet.schematic;

import carpet.CarpetSettings;
import carpet.network.PacketSplitter;
import carpet.network.PluginChannelHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.util.text.TextComponentString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SchematicSaveHandler implements PluginChannelHandler {
    private static final String CHANNEL = "litematica:save";
    private static final int MAX_REQUEST_BYTES = 32767;
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_REGIONS = 32;
    private static final long MAX_BLOCKS = 32768;
    private static final long MAX_NBT_READ = 2097152L;

    @Override
    public String[] getChannels() {
        return new String[] {CHANNEL};
    }

    @Override
    public void onCustomPayload(CPacketCustomPayload packet, EntityPlayerMP player) {
        PacketBuffer requestData = null;
        NBTTagCompound request = null;
        UUID id = null;
        try {
            requestData = PacketSplitter.receive(player, packet, MAX_REQUEST_BYTES);
            if (requestData == null) return;
            request = requestData.readCompoundTag();
            if (request == null || requestData.isReadable()) throw new IllegalArgumentException("Malformed save request");
            id = readUuid(request);
            if (id == null) throw new IllegalArgumentException("Missing request id");
            if (!CarpetSettings.schematicSave) throw new IllegalArgumentException("Server rule schematicSave is disabled");
            if (!player.canUseCommand(2, "") || !player.isCreative()) throw new IllegalArgumentException("Capture requires OP permission and creative mode");
            NBTTagCompound result = capture(request, player);
            send(player, result);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Capture failed" : e.getMessage();
            if (id != null) sendError(player, id, message);
            else player.sendMessage(new TextComponentString("[Bullet Carpet] " + message));
        } finally {
            if (requestData != null) requestData.release();
        }
    }

    private static NBTTagCompound capture(NBTTagCompound request, EntityPlayerMP player) {
        if (!request.hasKey("SaveMethod", 8) || !"AllAtOnce".equals(request.getString("SaveMethod"))) {
            throw new IllegalArgumentException("Only Litematica AllAtOnce saves are supported");
        }
        if (!request.hasKey("RequestedData", 10)) throw new IllegalArgumentException("Missing requested data");
        NBTTagCompound flags = request.getCompoundTag("RequestedData");
        if (!flags.hasKey("Blocks", 1) || !flags.getBoolean("Blocks") ||
                !flags.hasKey("BlockEntities", 1) || !flags.getBoolean("BlockEntities")) {
            throw new IllegalArgumentException("Enable Blocks and Block Entities in Litematica save settings");
        }
        if (flags.getBoolean("BlockTicks") || flags.getBoolean("Entities") || flags.getBoolean("ExposedBlocksOnly")) {
            throw new IllegalArgumentException("Disable block ticks, entities, and exposed-block-only mode");
        }
        if (!request.hasKey("Regions", 10)) throw new IllegalArgumentException("Missing selected regions");

        NBTTagCompound inputRegions = request.getCompoundTag("Regions");
        if (inputRegions.getSize() == 0 || inputRegions.getSize() > MAX_REGIONS) throw new IllegalArgumentException("Invalid region count");
        WorldServer world = player.getServerWorld();
        Map<String, Bounds> bounds = new HashMap<>();
        long totalBlocks = 0;
        for (String name : inputRegions.getKeySet()) {
            if (name.isEmpty() || name.length() > 128 || !inputRegions.hasKey(name, 11)) throw new IllegalArgumentException("Invalid region name or bounds");
            int[] corners = inputRegions.getIntArray(name);
            if (corners.length != 6) throw new IllegalArgumentException("Invalid bounds for region " + name);
            Bounds region = new Bounds(corners);
            if (region.minY < 0 || region.maxY >= world.getActualHeight()) throw new IllegalArgumentException("Region outside world height: " + name);
            totalBlocks = Math.addExact(totalBlocks, region.volume);
            if (totalBlocks > MAX_BLOCKS) throw new IllegalArgumentException("Selection exceeds " + MAX_BLOCKS + " blocks");
            bounds.put(name, region);
        }

        NBTTagCompound response = new NBTTagCompound();
        response.setLong("UUIDM", request.getLong("UUIDM"));
        response.setLong("UUIDL", request.getLong("UUIDL"));
        response.setString("SaveMethod", "AllAtOnce");
        NBTTagCompound outputRegions = new NBTTagCompound();
        long[] tileNbtBytes = {0};
        for (Map.Entry<String, Bounds> entry : bounds.entrySet()) {
            outputRegions.setTag(entry.getKey(), captureRegion(world, entry.getValue(), tileNbtBytes));
        }
        response.setTag("Regions", outputRegions);
        return response;
    }

    private static NBTTagCompound captureRegion(WorldServer world, Bounds bounds, long[] tileNbtBytes) {
        int volume = (int) bounds.volume;
        IBlockState[] states = new IBlockState[volume];
        List<IBlockState> palette = new ArrayList<>();
        Map<IBlockState, Integer> paletteIds = new HashMap<>();
        NBTTagList blockEntities = new NBTTagList();
        int index = 0;
        for (int y = bounds.minY; y <= bounds.maxY; y++) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                for (int x = bounds.minX; x <= bounds.maxX; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.isBlockLoaded(pos)) throw new IllegalArgumentException("Selection includes an unloaded chunk");
                    IBlockState state = world.getBlockState(pos);
                    states[index++] = state;
                    if (!paletteIds.containsKey(state)) {
                        paletteIds.put(state, palette.size());
                        palette.add(state);
                    }
                    TileEntity tile = world.getTileEntity(pos);
                    if (tile != null) {
                        NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
                        tag.setInteger("x", x - bounds.minX);
                        tag.setInteger("y", y - bounds.minY);
                        tag.setInteger("z", z - bounds.minZ);
                        tileNbtBytes[0] = Math.addExact(tileNbtBytes[0], encodedSize(tag));
                        if (tileNbtBytes[0] > MAX_NBT_READ) throw new IllegalArgumentException("Capture exceeds the NBT size limit");
                        blockEntities.appendTag(tag);
                    }
                }
            }
        }

        NBTTagList paletteTags = new NBTTagList();
        for (IBlockState state : palette) paletteTags.appendTag(writeState(state));
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        int[] stateIds = new int[states.length];
        for (int i = 0; i < states.length; i++) stateIds[i] = paletteIds.get(states[i]);
        long[] packed = packStates(stateIds, bits);

        NBTTagCompound blocks = new NBTTagCompound();
        blocks.setTag("BlockStatePalette", paletteTags);
        blocks.setTag("BlockStates", new NBTTagLongArray(packed));
        blocks.setInteger("sizeX", bounds.sizeX);
        blocks.setInteger("sizeY", bounds.sizeY);
        blocks.setInteger("sizeZ", bounds.sizeZ);
        blocks.setLong("TotalBlockCount", volume);
        NBTTagCompound region = new NBTTagCompound();
        region.setTag("Blocks", blocks);
        region.setTag("BlockEntities", blockEntities);
        return region;
    }

    static long[] packStates(int[] stateIds, int bits) {
        long bitCount = Math.multiplyExact((long) stateIds.length, bits);
        long[] packed = new long[(int) ((bitCount + 63) / 64)];
        for (int i = 0; i < stateIds.length; i++) {
            long bitIndex = (long) i * bits;
            int word = (int) (bitIndex >>> 6);
            int offset = (int) (bitIndex & 63);
            packed[word] |= (long) stateIds[i] << offset;
            if (offset + bits > 64) packed[word + 1] |= (long) stateIds[i] >>> (64 - offset);
        }
        return packed;
    }

    static int unpackState(long[] packed, int index, int bits) {
        long bitIndex = (long) index * bits;
        int word = (int) (bitIndex >>> 6);
        int offset = (int) (bitIndex & 63);
        long state = packed[word] >>> offset;
        if (offset + bits > 64) state |= packed[word + 1] << (64 - offset);
        return (int) (state & ((1L << bits) - 1));
    }

    private static int encodedSize(NBTTagCompound tag) {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writeCompoundTag(tag);
            return packet.writerIndex();
        } finally {
            packet.release();
        }
    }

    private static NBTTagCompound writeState(IBlockState state) {
        ResourceLocation name = Block.REGISTRY.getNameForObject(state.getBlock());
        if (name == null) throw new IllegalArgumentException("Block has no registry name");
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Name", name.toString());
        if (!state.getProperties().isEmpty()) {
            NBTTagCompound properties = new NBTTagCompound();
            for (IProperty<?> property : state.getProperties().keySet()) {
                properties.setString(property.getName(), propertyValue(state, property));
            }
            tag.setTag("Properties", properties);
        }
        return tag;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValue(IBlockState state, IProperty property) {
        return property.getName(state.getValue(property));
    }

    private static void send(EntityPlayerMP player, NBTTagCompound response) throws IOException {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writeCompoundTag(response);
            if (packet.writerIndex() > MAX_RESPONSE_BYTES) throw new IllegalArgumentException("Capture exceeds the response byte limit");
            PacketBuffer checked = new PacketBuffer(packet.copy());
            try {
                checked.readCompoundTag();
                if (checked.isReadable()) throw new IllegalArgumentException("Invalid encoded capture");
            } finally {
                checked.release();
            }
            PacketSplitter.send(player, CHANNEL, packet);
        } catch (RuntimeException | IOException e) {
            throw e;
        }
    }

    private static void sendError(EntityPlayerMP player, UUID id, String message) {
        NBTTagCompound error = new NBTTagCompound();
        error.setLong("UUIDM", id.getMostSignificantBits());
        error.setLong("UUIDL", id.getLeastSignificantBits());
        error.setString("SaveMethod", "AllAtOnce");
        error.setString("Error", message.length() > 256 ? message.substring(0, 256) : message);
        try {
            PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
            packet.writeCompoundTag(error);
            PacketSplitter.send(player, CHANNEL, packet);
        } catch (RuntimeException e) {
            player.sendMessage(new TextComponentString("[Bullet Carpet] " + message));
        }
    }

    private static UUID readUuid(NBTTagCompound tag) {
        if (!tag.hasKey("UUIDM", 4) || !tag.hasKey("UUIDL", 4)) return null;
        return new UUID(tag.getLong("UUIDM"), tag.getLong("UUIDL"));
    }

    static final class Bounds {
        final int minX, minY, minZ, maxX, maxY, maxZ, sizeX, sizeY, sizeZ;
        final long volume;

        Bounds(int[] corners) {
            minX = Math.min(corners[0], corners[3]);
            minY = Math.min(corners[1], corners[4]);
            minZ = Math.min(corners[2], corners[5]);
            maxX = Math.max(corners[0], corners[3]);
            maxY = Math.max(corners[1], corners[4]);
            maxZ = Math.max(corners[2], corners[5]);
            sizeX = Math.addExact(Math.subtractExact(maxX, minX), 1);
            sizeY = Math.addExact(Math.subtractExact(maxY, minY), 1);
            sizeZ = Math.addExact(Math.subtractExact(maxZ, minZ), 1);
            volume = Math.multiplyExact(Math.multiplyExact((long) sizeX, sizeY), sizeZ);
        }
    }
}
