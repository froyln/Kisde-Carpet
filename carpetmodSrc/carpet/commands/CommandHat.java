package carpet.commands;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

public class CommandHat extends CommandCarpetBase {
    public String getName() { return "hat"; }
    public String getUsage(ICommandSender sender) { return "/hat"; }
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return commandPermitted("commandHat", sender);
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!checkPermission(server, sender)) throw new CommandException("commands.generic.permission");
        if (args.length != 0) throw new WrongUsageException(getUsage(sender));
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ItemStack held = player.getHeldItemMainhand();
        ItemStack oldHat = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        ItemStack hat = held.copy();
        hat.setCount(1);
        if (!player.capabilities.isCreativeMode) held.shrink(1);
        player.setItemStackToSlot(EntityEquipmentSlot.HEAD, hat);
        if (!player.capabilities.isCreativeMode) player.inventory.placeItemBackInInventory(player.world, oldHat);
        player.inventoryContainer.detectAndSendChanges();
    }
}
