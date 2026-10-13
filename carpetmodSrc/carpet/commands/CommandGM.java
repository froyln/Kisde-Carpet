package carpet.commands;

import carpet.CarpetSettings;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameType;

public class CommandGM extends CommandCarpetBase {
    private final String name;
    private final GameType type;

    public CommandGM(String name, GameType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() { return name; }
    public String getUsage(ICommandSender sender) { return "/" + name; }

    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return commandPermitted("commandGM", sender) && !(CarpetSettings.opPlayerNoCheat
                && sender.getCommandSenderEntity() instanceof EntityPlayerMP && sender.canUseCommand(2, name));
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!checkPermission(server, sender)) throw new CommandException("commands.generic.permission");
        if (args.length != 0) throw new WrongUsageException(getUsage(sender));
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        player.setGameType(type);
        player.fallDistance = 0;
    }
}
