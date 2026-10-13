package carpet.helpers;

import carpet.CarpetSettings;
import carpet.CarpetServer;
import carpet.logging.Logger;
import carpet.logging.LoggerRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public final class CarefulDrop {
    private static Entity dropper;

    public static Entity begin(Entity attacker) {
        Entity previous = dropper;
        dropper = attacker;
        return previous;
    }

    public static void end(Entity previous) { dropper = previous; }

    public static void collect(World world, ItemStack stack) {
        if (world.isRemote || !CarpetSettings.carefulDrop || !(dropper instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) dropper;
        Logger subscription = LoggerRegistry.getLogger("carefulDrop");
        if (player.world != world || !player.isSneaking() || subscription == null || !subscription.subscribed(player)) return;
        InventoryPlayer inventory = player.inventory;
        boolean previousStacking = CarpetServer.playerInventoryStacking;
        CarpetServer.playerInventoryStacking = true;
        try {
            while (!stack.isEmpty()) {
                int slot = stack.isItemDamaged() ? -1 : inventory.storeItemStack(stack);
                if (slot < 0) slot = inventory.getFirstEmptyStack();
                if (slot < 0) break;
                ItemStack current = inventory.getStackInSlot(slot);
                int capacity = Math.min(stack.getMaxStackSize(), inventory.getInventoryStackLimit()) - current.getCount();
                if (capacity <= 0) break;
                // Explicit slots preserve overflow even for creative players.
                inventory.add(slot, stack.splitStack(Math.min(capacity, stack.getCount())));
            }
        } finally {
            CarpetServer.playerInventoryStacking = previousStacking;
        }
        inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }
}
