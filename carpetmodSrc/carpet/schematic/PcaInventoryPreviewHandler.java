package carpet.schematic;

import carpet.CarpetSettings;
import carpet.network.PluginChannelHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.block.BlockChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.entity.item.EntityMinecartHopper;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityDropper;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.LogManager;

public final class PcaInventoryPreviewHandler implements PluginChannelHandler {
    private static final String REQUEST = "pca:sync_be";
    private static final String RESPONSE = "pca:update_be";
    private static final String ENTITY_REQUEST = "bc:inv_req";
    private static final String ENTITY_RESPONSE = "bc:inv_ack";
    private static final int MAX_RESPONSE_BYTES = 262144;
    private final Map<UUID, Long> lastRequestTick = new HashMap<>();

    @Override
    public String[] getChannels() {
        return new String[] {REQUEST, RESPONSE, ENTITY_REQUEST, ENTITY_RESPONSE};
    }

    @Override
    public void onCustomPayload(CPacketCustomPayload packet, EntityPlayerMP player) {
        String channel = packet.getChannelName();
        boolean entityRequest = ENTITY_REQUEST.equals(channel);
        if (!REQUEST.equals(channel) && !entityRequest) return;
        PacketBuffer request = packet.getBufferData();
        try {
            int requestId = request.readInt();
            UUID entityId = entityRequest ? new UUID(request.readLong(), request.readLong()) : null;
            BlockPos pos = entityRequest ? null : request.readBlockPos();
            if (request.isReadable()) return;
            long tick = player.world.getTotalWorldTime();
            Long last = lastRequestTick.get(player.getUniqueID());
            if (last != null && tick - last < 5) return;
            lastRequestTick.put(player.getUniqueID(), tick);
            sendSnapshot(player, requestId, pos, entityId, entityRequest);
        } catch (RuntimeException e) {
            LogManager.getLogger().error("Failed to handle PCA inventory preview request", e);
        }
    }

    @Override
    public void unregister(String channel, EntityPlayerMP player) {
        lastRequestTick.remove(player.getUniqueID());
    }

    private static void sendSnapshot(EntityPlayerMP player, int requestId, BlockPos pos, UUID entityId, boolean entityResponse) {
        WorldServer world = player.getServerWorld();
        NBTTagCompound snapshot = new NBTTagCompound();
        IInventory inventory;
        if (entityId != null) {
            Entity entity = world.getEntityFromUuid(entityId);
            boolean allowed = entity != null && supportedMinecart(entity) && canPreview(player, entity.getPosition());
            inventory = allowed ? (IInventory) entity : null;
        } else {
            boolean allowed = canPreview(player, pos);
            inventory = allowed ? findInventory(world, pos) : null;
        }
        if (inventory != null && inventory.getSizeInventory() <= 54 && (pos == null || !hasUnresolvedLoot(world, pos))) {
            NBTTagList items = new NBTTagList();
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    NBTTagCompound item = stack.writeToNBT(new NBTTagCompound());
                    item.setByte("Slot", (byte) slot);
                    items.appendTag(item);
                }
            }
            snapshot.setBoolean("Available", true);
            snapshot.setInteger("Size", inventory.getSizeInventory());
            snapshot.setTag("Items", items);
        } else {
            snapshot.setBoolean("Available", false);
        }

        PacketBuffer response = new PacketBuffer(Unpooled.buffer());
        boolean transferred = false;
        try {
            response.writeString(dimensionId(player.dimension));
            if (entityResponse) {
                response.writeLong(entityId.getMostSignificantBits());
                response.writeLong(entityId.getLeastSignificantBits());
            } else response.writeBlockPos(pos);
            response.writeInt(requestId);
            response.writeCompoundTag(snapshot);
            if (response.writerIndex() > MAX_RESPONSE_BYTES) return;
            player.connection.sendPacket(new SPacketCustomPayload(entityResponse ? ENTITY_RESPONSE : RESPONSE, response));
            transferred = true;
        } finally {
            if (!transferred && response.refCnt() > 0) response.release();
        }
    }

    private static boolean canPreview(EntityPlayerMP player, BlockPos pos) {
        WorldServer world = player.getServerWorld();
        return CarpetSettings.pcaProtocol && player.canUseCommand(2, "") &&
                pos.getY() >= 0 && pos.getY() < world.getHeight() && world.isBlockLoaded(pos) &&
                world.getWorldBorder().contains(pos) &&
                world.getPlayerChunkMap().isPlayerWatchingChunk(player, pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static IInventory findInventory(WorldServer world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof IInventory) || !supported(tile)) return null;
        if (!(world.getBlockState(pos).getBlock() instanceof BlockChest)) return (IInventory) tile;

        TileEntityChest first = (TileEntityChest) tile;
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            BlockPos adjacentPos = pos.offset(side);
            if (!world.isBlockLoaded(adjacentPos)) continue;
            IBlockState adjacentState = world.getBlockState(adjacentPos);
            if (adjacentState.getBlock() == world.getBlockState(pos).getBlock()) {
                TileEntity adjacent = world.getTileEntity(adjacentPos);
                if (adjacent instanceof TileEntityChest) {
                    return side == EnumFacing.WEST || side == EnumFacing.NORTH
                            ? new InventoryLargeChest("container.chestDouble", (TileEntityChest) adjacent, first)
                            : new InventoryLargeChest("container.chestDouble", first, (TileEntityChest) adjacent);
                }
            }
        }
        return first;
    }

    private static boolean supported(TileEntity tile) {
        Class<?> type = tile.getClass();
        return type == TileEntityChest.class || type == TileEntityHopper.class || type == TileEntityFurnace.class ||
                type == TileEntityDispenser.class || type == TileEntityDropper.class ||
                type == TileEntityShulkerBox.class || type == TileEntityBrewingStand.class;
    }

    private static boolean supportedMinecart(Entity entity) {
        return (entity.getClass() == EntityMinecartChest.class || entity.getClass() == EntityMinecartHopper.class) &&
                entity instanceof EntityMinecartContainer;
    }

    private static boolean hasUnresolvedLoot(WorldServer world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityLockableLoot && ((TileEntityLockableLoot) tile).getLootTable() != null) return true;
        if (tile instanceof TileEntityChest) {
            for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
                BlockPos adjacentPos = pos.offset(side);
                if (!world.isBlockLoaded(adjacentPos) || world.getBlockState(adjacentPos).getBlock() != world.getBlockState(pos).getBlock()) continue;
                TileEntity adjacent = world.getTileEntity(adjacentPos);
                return adjacent instanceof TileEntityLockableLoot && ((TileEntityLockableLoot) adjacent).getLootTable() != null;
            }
        }
        return false;
    }

    private static String dimensionId(int dimension) {
        if (dimension == -1) return "minecraft:the_nether";
        if (dimension == 1) return "minecraft:the_end";
        return "minecraft:overworld";
    }
}
