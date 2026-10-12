package carpet.schematic;

import carpet.CarpetSettings;
import carpet.network.PluginChannelHandler;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

public final class SchematicInventoryRules implements PluginChannelHandler {
    public static final String CHANNEL = "bc:schem_rules";
    private final Set<EntityPlayerMP> players = new HashSet<>();
    private static final String[] INVENTORY_TAGS = {"Items", "LootTable", "LootTableSeed"};

    public String[] getChannels() { return new String[] {CHANNEL}; }
    public boolean register(String channel, EntityPlayerMP player) {
        players.add(player);
        send(player);
        return true;
    }
    public void unregister(String channel, EntityPlayerMP player) { players.remove(player); }
    public void onCustomPayload(CPacketCustomPayload packet, EntityPlayerMP player) {}
    public void sync() { for (EntityPlayerMP player : players) send(player); }
    private void send(EntityPlayerMP player) {
        PacketBuffer data = new PacketBuffer(Unpooled.buffer(2));
        data.writeBoolean(CarpetSettings.schematicSave);
        data.writeBoolean(CarpetSettings.schematicPaste);
        player.connection.sendPacket(new SPacketCustomPayload(CHANNEL, data));
    }
    public static void removeInventory(NBTTagCompound tag) {
        for (String key : INVENTORY_TAGS) tag.removeTag(key);
    }
    public static void retainInventory(NBTTagCompound existing, NBTTagCompound replacement) {
        removeInventory(replacement);
        if (existing != null) for (String key : INVENTORY_TAGS) {
            if (existing.hasKey(key)) replacement.setTag(key, existing.getTag(key).copy());
        }
    }
}
