package carpet.helpers;

import carpet.CarpetServer;
import carpet.CarpetSettings;
import carpet.network.PluginChannelHandler;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

public final class AnvilSync implements PluginChannelHandler {
    public static final String CHANNEL = "bc:anvil";
    private final Map<EntityPlayerMP, State> players = new HashMap<>();

    public String[] getChannels() { return new String[] {CHANNEL}; }
    public boolean register(String channel, EntityPlayerMP player) {
        players.put(player, null);
        if (player.openContainer instanceof ContainerRepair) ((ContainerRepair) player.openContainer).updateRepairOutput();
        return true;
    }
    public void unregister(String channel, EntityPlayerMP player) {
        players.remove(player);
        if (player.openContainer instanceof ContainerRepair) ((ContainerRepair) player.openContainer).updateRepairOutput();
    }
    public void onCustomPayload(CPacketCustomPayload packet, EntityPlayerMP player) { }
    public static boolean supports(EntityPlayer player) {
        return CarpetServer.anvilSync != null && CarpetServer.anvilSync.players.containsKey(player);
    }
    public void tick() {
        for (Map.Entry<EntityPlayerMP, State> entry : players.entrySet()) {
            EntityPlayerMP player = entry.getKey();
            if (!(player.openContainer instanceof ContainerRepair)) { entry.setValue(null); continue; }
            ContainerRepair repair = (ContainerRepair) player.openContainer;
            State previous = entry.getValue();
            if (previous == null || previous.container != repair || previous.uncapped != CarpetSettings.disableAnvilXpLimit || previous.enchantments != CarpetSettings.disableEnchantmentCap) repair.updateRepairOutput();
            State current = new State(repair);
            if (previous == null || previous.container != repair || previous.cost != current.cost || previous.uncapped != current.uncapped || previous.enchantments != current.enchantments) {
                PacketBuffer data = new PacketBuffer(Unpooled.buffer(9));
                data.writeInt(repair.windowId); data.writeInt(current.cost); data.writeBoolean(current.uncapped);
                player.connection.sendPacket(new SPacketCustomPayload(CHANNEL, data));
                entry.setValue(current);
            }
        }
    }
    private static final class State {
        final ContainerRepair container;
        final int cost;
        final boolean uncapped, enchantments;
        State(ContainerRepair container) {
            this.container = container;
            this.cost = Math.max(0, container.maximumCost);
            this.uncapped = CarpetSettings.disableAnvilXpLimit;
            this.enchantments = CarpetSettings.disableEnchantmentCap;
        }
    }
}
