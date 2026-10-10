package carpet.helpers;

import carpet.CarpetSettings;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketEntityMetadata;
import net.minecraft.network.play.server.SPacketSetPassengers;
import net.minecraft.server.MinecraftServer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.Entity;

public final class PlayerSitHelper {
    public static final String SEAT_TAG = "kisde_carpet_seat";
    private static final Map<UUID, Sneaks> sneaks = new HashMap<>();
    private static final Map<EntityPlayerMP, EntityArmorStand> seats = new HashMap<>();

    private static final Set<UUID> heldSneak = new HashSet<>();

    public static boolean isSeat(Entity entity) { return seats.containsValue(entity); }

    public static boolean inputSneaking(EntityPlayerMP player, boolean value) {
        if (!heldSneak.contains(player.getUniqueID())) return value;
        if (!value) heldSneak.remove(player.getUniqueID());
        return false;
    }

    public static final class Sneaks {
        private long last;
        private int count;
        public boolean start(long now, boolean onGround) {
            if (!onGround) { count = 0; return false; }
            count = count > 0 && now - last < 300 && now >= last ? count + 1 : 1;
            last = now;
            if (count < 3) return false;
            count = 0;
            return true;
        }
    }

    public static void setSneaking(EntityPlayerMP player, boolean value) {
        if (heldSneak.contains(player.getUniqueID())) {
            player.setSneaking(inputSneaking(player, value));
            return;
        }
        if (!CarpetSettings.playerSit || !value || player.isSneaking()) {
            player.setSneaking(value);
            return;
        }
        player.setSneaking(true);
        if (player.isRiding() || !sneaks.computeIfAbsent(player.getUniqueID(), id -> new Sneaks())
                .start(System.nanoTime() / 1000000, player.onGround)) return;
        EntityArmorStand seat = new EntityArmorStand(player.world, player.posX, player.posY, player.posZ);
        NBTTagCompound nbt = seat.writeToNBT(new NBTTagCompound());
        nbt.setBoolean("Marker", true); nbt.setBoolean("Invisible", true);
        nbt.setBoolean("NoGravity", true); nbt.setBoolean("Invulnerable", true);
        seat.readFromNBT(nbt); seat.addTag(SEAT_TAG); seat.rotationYaw = player.rotationYaw;
        if (!player.world.spawnEntity(seat)) return;
        player.setSneaking(false);
        if (!player.startRiding(seat, true)) { seat.setDead(); return; }
        seats.put(player, seat);
        heldSneak.add(player.getUniqueID());
        player.connection.sendPacket(new SPacketEntityMetadata(player.getEntityId(), player.getDataManager(), true));
        player.getServerWorld().getEntityTracker().sendToTrackingAndSelf(seat, new SPacketSetPassengers(seat));
    }

    public static void remove(EntityPlayerMP player) {
        sneaks.remove(player.getUniqueID());
        heldSneak.remove(player.getUniqueID());
        EntityArmorStand seat = seats.remove(player);
        if (seat != null) { if (player.getRidingEntity() == seat) player.dismountRidingEntity(); seat.setDead(); }
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<EntityPlayerMP, EntityArmorStand>> it = seats.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<EntityPlayerMP, EntityArmorStand> entry = it.next();
            EntityPlayerMP player = entry.getKey(); EntityArmorStand seat = entry.getValue();
            if (!CarpetSettings.playerSit || seat.isDead || !player.isEntityAlive() ||
                    player.world != seat.world || player.getRidingEntity() != seat ||
                    server.getPlayerList().getPlayerByUUID(player.getUniqueID()) != player) {
                if (player.getRidingEntity() == seat) player.dismountRidingEntity();
                seat.setDead(); heldSneak.remove(player.getUniqueID()); it.remove();
            }
        }
        if (!CarpetSettings.playerSit) sneaks.clear();
        else sneaks.keySet().removeIf(id -> server.getPlayerList().getPlayerByUUID(id) == null);
    }
}
