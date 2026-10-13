package carpet.carpetclient;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

public final class CarpetClientMining {
    private CarpetClientMining() {}

    public static void handle(EntityPlayerMP player, PacketBuffer payload) {
        if (payload.readableBytes() != 12 || player.hasDisconnected()) return;
        payload.readBoolean();
        boolean start = payload.readBoolean();
        BlockPos pos = payload.readBlockPos();
        int facing = payload.readUnsignedByte();
        boolean instantMine = payload.readBoolean();
        if (facing >= EnumFacing.values().length || !withinReach(player.posX, player.posY, player.posZ,
                pos, player.server.getBuildLimit())) return;
        WorldServer world = player.getServerWorld();
        if (!world.isBlockLoaded(pos) || !world.getWorldBorder().contains(pos)) return;
        if (player.server.isBlockProtected(world, pos, player)) {
            player.connection.sendPacket(new SPacketBlockChange(world, pos));
            return;
        }
        boolean previous = PlayerInteractionManager.activateInstantMine;
        try {
            PlayerInteractionManager.activateInstantMine = instantMine;
            if (start) player.interactionManager.onBlockClicked(pos, EnumFacing.byIndex(facing));
            else player.interactionManager.blockRemoving(pos);
        } finally {
            PlayerInteractionManager.activateInstantMine = previous;
        }
    }

    private static boolean withinReach(double x, double y, double z, BlockPos pos, int buildLimit) {
        double dx = x - (pos.getX() + 0.5D);
        double dy = y - (pos.getY() + 0.5D) + 1.5D;
        double dz = z - (pos.getZ() + 0.5D);
        return pos.getY() >= 0 && pos.getY() < buildLimit && dx * dx + dy * dy + dz * dz <= 36.0D;
    }
}
