package carpet.commands;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

public class CommandMore extends CommandCarpetBase {
    public String getName() { return "more"; }
    public String getUsage(ICommandSender sender) { return "/more"; }
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return commandPermitted("commandMore", sender);
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!checkPermission(server, sender)) throw new CommandException("commands.generic.permission");
        if (args.length != 0) throw new WrongUsageException(getUsage(sender));
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) held = player.getHeldItemOffhand();
        if (held.isEmpty()) throw new CommandException("You are not holding an item");
        held.setCount(held.getMaxStackSize());
        player.inventoryContainer.detectAndSendChanges();
    }
}
