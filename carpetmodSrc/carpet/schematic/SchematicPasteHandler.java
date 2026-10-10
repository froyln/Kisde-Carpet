package carpet.schematic;

import carpet.CarpetSettings;
import carpet.network.PacketSplitter;
import carpet.network.PluginChannelHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.BlockPistonMoving;
import net.minecraft.tileentity.TileEntityPiston;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.io.IOException;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import io.netty.buffer.Unpooled;

public final class SchematicPasteHandler implements PluginChannelHandler {
    private static final String CHANNEL = "bulletcarpet:paste";
    private static final String ACK_CHANNEL = "bc:paste_ack";
    private static final int MAX_PACKET_BYTES = 32767;
    private static final long OPERATION_IDLE_TICKS = 200;
    private final Map<UUID, Quota> quotas = new HashMap<>();
    private final Map<UUID, Long> lastOperations = new HashMap<>();

    @Override
    public String[] getChannels() {
        return new String[] {CHANNEL, ACK_CHANNEL};
    }

    @Override
    public void onCustomPayload(CPacketCustomPayload packet, EntityPlayerMP player) {
        if (ACK_CHANNEL.equals(packet.getChannelName())) return;
        PacketBuffer data = null;
        NBTTagCompound request = null;
        try {
            data = PacketSplitter.receive(player, packet, MAX_PACKET_BYTES);
            if (data == null) return;
            request = data.readCompoundTag();
            if (request == null || data.isReadable()) throw new IllegalArgumentException("Malformed paste request");
            if (!CarpetSettings.schematicPaste) throw new IllegalArgumentException("Server rule schematicPaste is disabled");
            if (!player.canUseCommand(2, "") || !player.isCreative()) throw new IllegalArgumentException("Paste requires OP permission and creative mode");
            if (request.hasKey("Action", 8)) {
                finish(request, player);
                return;
            }
            apply(request, player);
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            String message = cause.getMessage() == null ? "Paste failed" : cause.getMessage();
            if (request != null && request.hasKey("Operation", 4)) {
                long operation = request.getLong("Operation");
                Long lastOperation = lastOperations.get(player.getUniqueID());
                if (lastOperation != null && operation <= lastOperation) return;
                int applied = abort(player.getUniqueID(), operation);
                sendAck(player, operation, "Rejected", applied, message);
            } else player.sendMessage(new TextComponentString("[Bullet Carpet] " + message));
        } finally {
            if (data != null) data.release();
        }
    }

    @Override
    public void unregister(String channel, EntityPlayerMP player) {
        quotas.remove(player.getUniqueID());
        lastOperations.remove(player.getUniqueID());
    }

    private void apply(NBTTagCompound request, EntityPlayerMP player) {
        if (!request.hasKey("x", 3) || !request.hasKey("y", 3) || !request.hasKey("z", 3) || !request.hasKey("BlockState", 10)) {
            throw new IllegalArgumentException("Paste request is missing block data");
        }
        if (!request.hasKey("Operation", 4) || !request.hasKey("Sequence", 3) || !request.hasKey("Dimension", 3)) throw new IllegalArgumentException("Paste request has no operation sequence or dimension");
        long operation = request.getLong("Operation");
        int sequence = request.getInteger("Sequence");
        if (operation <= 0 || sequence < 0) throw new IllegalArgumentException("Invalid paste operation sequence");
        long tick = player.world.getTotalWorldTime();
        UUID playerId = player.getUniqueID();
        Quota quota = quotas.get(playerId);
        if (quota == null || tick - quota.lastRequestTick > OPERATION_IDLE_TICKS) {
            if (quota != null) rememberCompleted(playerId, quota.operation);
            Long lastOperation = lastOperations.get(playerId);
            if (lastOperation != null && operation <= lastOperation) return;
            if (sequence != 0) throw new IllegalArgumentException("Paste operation must start at sequence zero");
            if (request.getInteger("Dimension") != player.dimension) throw new IllegalArgumentException("Paste dimension does not match the connected world");
            quota = new Quota(operation, player.dimension);
            quotas.put(playerId, quota);
        } else if (quota.operation != operation) {
            if (operation < quota.operation) return;
            throw new IllegalArgumentException("Another paste operation is still active");
        }
        if (request.getInteger("Dimension") != quota.dimension || player.dimension != quota.dimension) throw new IllegalArgumentException("Paste dimension changed during the operation");
        if (!quota.acceptSequence(sequence)) return;
        WorldServer world = player.getServerWorld();
        BlockPos pos = new BlockPos(request.getInteger("x"), request.getInteger("y"), request.getInteger("z"));
        if (pos.getY() < 0 || pos.getY() >= world.getActualHeight() || !world.isBlockLoaded(pos) || !world.getWorldBorder().contains(pos)) {
            throw new IllegalArgumentException("Paste target is outside loaded world bounds");
        }

        IBlockState state = readState(request.getCompoundTag("BlockState"));
        NBTTagCompound tileData = request.hasKey("BlockEntity", 10) ? request.getCompoundTag("BlockEntity") : null;
        if (tileData != null && !state.getBlock().hasTileEntity()) throw new IllegalArgumentException("Block entity does not match block state");
        if (tileData == null && state.getBlock().hasTileEntity()) throw new IllegalArgumentException("Missing block entity data");

        TileEntity replacement = null;
        if (tileData != null) {
            if (!tileData.hasKey("id", 8)) throw new IllegalArgumentException("Block entity has no registry id");
            NBTTagCompound atTarget = tileData.copy();
            atTarget.setInteger("x", pos.getX());
            atTarget.setInteger("y", pos.getY());
            atTarget.setInteger("z", pos.getZ());
            if (new ResourceLocation("minecraft:sign").equals(new ResourceLocation(atTarget.getString("id")))) {
                for (int line = 1; line <= 4; line++) {
                    if (!atTarget.hasKey("Text" + line, 8)) throw new IllegalArgumentException("Missing sign text");
                    validateSignText(atTarget.getString("Text" + line));
                }
            }
            replacement = TileEntity.create(world, atTarget);
            if (!(state.getBlock() instanceof ITileEntityProvider)) throw new IllegalArgumentException("Unsupported block entity block");
            TileEntity expected = ((ITileEntityProvider) state.getBlock()).createNewTileEntity(world, state.getBlock().getMetaFromState(state));
            if (replacement == null || (expected == null
                    ? !(state.getBlock() instanceof BlockPistonMoving && replacement.getClass() == TileEntityPiston.class)
                    : replacement.getClass() != expected.getClass())) {
                throw new IllegalArgumentException("Block entity " + atTarget.getString("id") + " does not match block " + Block.REGISTRY.getNameForObject(state.getBlock()));
            }

        }

        IBlockState oldState = world.getBlockState(pos);
        int flags = 2;
        if (oldState != state) world.setBlockState(pos, state, flags);
        if (tileData != null) {
            NBTTagCompound atTarget = tileData.copy();
            atTarget.setInteger("x", pos.getX());
            atTarget.setInteger("y", pos.getY());
            atTarget.setInteger("z", pos.getZ());
            TileEntity placed = world.getTileEntity(pos);
            if (placed == null || placed.getClass() != replacement.getClass()) {
                placed = replacement;
                world.setTileEntity(pos, placed);
            }
            placed.readFromNBT(atTarget);
            placed.markDirty();
            world.notifyBlockUpdate(pos, state, state, flags);
        }
        quota.recordAccepted(tick);
    }

    static void validateSignText(String json) {
        if (json.length() > 8192) throw new IllegalArgumentException("Sign text exceeds the supported size");
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            int depth = 0;
            int tokens = 0;
            String name = null;
            JsonToken first = reader.peek();
            if (first != JsonToken.BEGIN_OBJECT && first != JsonToken.BEGIN_ARRAY && first != JsonToken.STRING) {
                throw new IllegalArgumentException("Invalid sign text");
            }
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                if (++tokens > 2048 || depth > 32) throw new IllegalArgumentException("Sign text is too complex");
                switch (reader.peek()) {
                    case BEGIN_OBJECT: reader.beginObject(); depth++; name = null; break;
                    case BEGIN_ARRAY: reader.beginArray(); depth++; name = null; break;
                    case END_OBJECT: reader.endObject(); depth--; name = null; break;
                    case END_ARRAY: reader.endArray(); depth--; name = null; break;
                    case NAME: name = reader.nextName(); break;
                    case STRING:
                        String value = reader.nextString();
                        if ("action".equals(name) && "run_command".equals(value)) {
                            throw new IllegalArgumentException("Sign command actions cannot be pasted");
                        }
                        name = null;
                        break;
                    case NUMBER: reader.nextString(); name = null; break;
                    case BOOLEAN: reader.nextBoolean(); name = null; break;
                    case NULL: reader.nextNull(); name = null; break;
                    default: throw new IllegalArgumentException("Invalid sign text");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid sign text", e);
        }
    }

    private void finish(NBTTagCompound request, EntityPlayerMP player) {
        if (!request.hasKey("Operation", 4) || !request.hasKey("Action", 8)) throw new IllegalArgumentException("Invalid paste operation control");
        UUID playerId = player.getUniqueID();
        long operation = request.getLong("Operation");
        if (operation <= 0) throw new IllegalArgumentException("Invalid paste operation id");
        Quota quota = quotas.get(playerId);
        if (quota == null || quota.operation != operation) {
            Long lastOperation = lastOperations.get(playerId);
            if (lastOperation != null && operation <= lastOperation) return;
            throw new IllegalArgumentException("Paste operation is no longer active");
        }
        String action = request.getString("Action");
        if ("Cancel".equals(action)) {
            quotas.remove(playerId);
            rememberCompleted(playerId, operation);
            sendAck(player, operation, "Cancelled", quota.nextSequence, "Paste cancelled");
            return;
        }
        if (!"Complete".equals(action) || !request.hasKey("Sequence", 3) || request.getInteger("Sequence") != quota.nextSequence) {
            throw new IllegalArgumentException("Paste completion count does not match accepted blocks");
        }
        if (quota.dimension != player.dimension) throw new IllegalArgumentException("Paste dimension changed during the operation");
        quotas.remove(playerId);
        rememberCompleted(playerId, operation);
        sendAck(player, operation, "Complete", quota.nextSequence, "Paste applied");
    }

    private int abort(UUID playerId, long operation) {
        Quota quota = quotas.get(playerId);
        if (quota != null && quota.operation == operation) {
            quotas.remove(playerId);
            rememberCompleted(playerId, operation);
            return quota.nextSequence;
        }
        return 0;
    }

    private static void sendAck(EntityPlayerMP player, long operation, String status, int applied, String message) {
        PacketBuffer response = new PacketBuffer(Unpooled.buffer());
        boolean transferred = false;
        try {
            NBTTagCompound ack = new NBTTagCompound();
            ack.setLong("Operation", operation);
            ack.setString("Status", status);
            ack.setInteger("Applied", applied);
            ack.setString("Message", message.length() > 256 ? message.substring(0, 256) : message);
            response.writeCompoundTag(ack);
            player.connection.sendPacket(new SPacketCustomPayload(ACK_CHANNEL, response));
            transferred = true;
        } finally {
            if (!transferred && response.refCnt() > 0) response.release();
        }
    }

    private void rememberCompleted(UUID playerId, long operation) {
        Long previous = lastOperations.get(playerId);
        if (previous == null || operation > previous) lastOperations.put(playerId, operation);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState readState(NBTTagCompound tag) {
        if (!tag.hasKey("Name", 8)) throw new IllegalArgumentException("Block state has no registry name");
        ResourceLocation name = new ResourceLocation(tag.getString("Name"));
        Block block = Block.REGISTRY.getObject(name);
        if (block == null) throw new IllegalArgumentException("Unknown block " + name);
        IBlockState state = block.getDefaultState();
        if (tag.hasKey("Properties", 10)) {
            NBTTagCompound properties = tag.getCompoundTag("Properties");
            for (String key : properties.getKeySet()) {
                if (!properties.hasKey(key, 8)) throw new IllegalArgumentException("Invalid block property");
                IProperty property = block.getBlockState().getProperty(key);
                if (property == null) throw new IllegalArgumentException("Unknown block property " + key);
                com.google.common.base.Optional value = property.parseValue(properties.getString(key));
                if (!value.isPresent()) throw new IllegalArgumentException("Invalid block property value");
                state = state.withProperty(property, (Comparable) value.get());
            }
        }
        return state;
    }

    static final class Quota {
        final long operation;
        final int dimension;
        long lastRequestTick;
        int nextSequence;

        Quota(long operation, int dimension) {
            this.operation = operation;
            this.dimension = dimension;
        }

        boolean acceptSequence(int sequence) {
            if (sequence < nextSequence) return false;
            if (sequence != nextSequence) throw new IllegalArgumentException("Paste operation sequence is out of order");
            return true;
        }

        void recordAccepted(long tick) {
            nextSequence++;
            lastRequestTick = tick;
        }
    }
}
