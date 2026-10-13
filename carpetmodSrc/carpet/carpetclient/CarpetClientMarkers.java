package carpet.carpetclient;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.village.Village;
import net.minecraft.village.VillageCollection;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class CarpetClientMarkers {

    public static final int OUTER_BOUNDING_BOX = 0;
    public static final int END_CITY = 1;
    public static final int FORTRESS = 2;
    public static final int TEMPLE = 3;
    public static final int VILLAGE = 4;
    public static final int STRONGHOLD = 5;
    public static final int MINESHAFT = 6;
    public static final int MONUMENT = 7;
    public static final int MANSION = 8;

    public static List<Village> annihilatedVillages = new ArrayList<>();
    public static boolean changesOnly;

    private static final Set<EntityPlayerMP> playersVillageMarkers = new LinkedHashSet<>();

    public static void updateClientVillageMarkers(World world) {
        if (playersVillageMarkers.size() == 0) return;

        VillageCollection villageCollection = world.getVillageCollection();
        NBTTagList nbttaglist = new NBTTagList();
        NBTTagCompound tagCompound = new NBTTagCompound();
        tagCompound.setInteger("Tick", villageCollection.getTickCounter());

        for (Village village : annihilatedVillages) {
            NBTTagCompound compound = new NBTTagCompound();
            village.writeMarkerDataToNBT(compound);
            nbttaglist.appendTag(compound);
        }
        annihilatedVillages.clear();

        for (Village village : villageCollection.getVillageList()) {
            if (!CarpetClientMarkers.changesOnly || !village.oldVillage.equals(village.getVillageDoorInfoList())) {
                village.oldVillage = new ArrayList<>(village.getVillageDoorInfoList());
                NBTTagCompound compound = new NBTTagCompound();
                village.writeMarkerDataToNBT(compound);
                nbttaglist.appendTag(compound);
            }
        }

        tagCompound.setTag("Villages", nbttaglist);

        for (EntityPlayerMP sender : playersVillageMarkers) {
            CarpetClientMessageHandler.sendNBTVillageData(sender, tagCompound);
        }
        changesOnly = true;
    }

    public static void updateClientBoundingBoxMarkers(EntityPlayerMP sender, PacketBuffer data) {
        MinecraftServer ms = sender.world.getMinecraftServer();
        WorldServer ws = ms.getWorld(sender.dimension);
        NBTTagList list = ws.getChunkProvider().getBoundingBoxes(sender);
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        nbttagcompound.setTag("Boxes", list);
        nbttagcompound.setInteger("Dimention", sender.dimension);
        nbttagcompound.setLong("Seed", sender.world.getSeed());

        CarpetClientMessageHandler.sendNBTBoundingboxData(sender, nbttagcompound);
    }

    public static void registerVillagerMarkers(EntityPlayerMP sender, PacketBuffer data) {
        if (data.readableBytes() != 1) throw new IllegalArgumentException("Invalid village marker request");
        boolean addPlayer = data.readBoolean();
        if (addPlayer) {
            if (!playersVillageMarkers.add(sender)) return;
            changesOnly = false;
            updateClientVillageMarkers(sender.world);
        } else {
            playersVillageMarkers.remove(sender);
        }
    }

    public static void unregisterPlayerVillageMarkers(EntityPlayerMP player) {
        playersVillageMarkers.remove(player);
    }
}
