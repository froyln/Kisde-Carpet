package carpet.commands;

import carpet.CarpetSettings;
import carpet.helpers.HopperCounter;
import carpet.helpers.SupplierCounter;
import carpet.utils.Messenger;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CommandSupplierCounter extends CommandCarpetBase
{
    @Override
    public String getName() { return "scounter"; }

    @Override
    public String getUsage(ICommandSender sender) { return "/scounter [<color|all> [reset|realtime]|reset|realtime]"; }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender)
    {
        return CarpetSettings.hopperNoItemCost;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        if (!CarpetSettings.hopperNoItemCost)
        {
            msg(sender, Messenger.s(null, "Enable hopperNoItemCost to use this command."));
            return;
        }
        if (args.length > 2) throw new WrongUsageException(getUsage(sender));
        String color = args.length == 0 ? "all" : args[0].toLowerCase(Locale.ROOT);
        String action = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        if (color.equals("reset") || color.equals("realtime"))
        {
            if (args.length != 1) throw new WrongUsageException(getUsage(sender));
            action = color;
            color = "all";
        }
        HopperCounter counter = SupplierCounter.getCounter(color);
        if (!color.equals("all") && counter == null) throw new WrongUsageException("Invalid color");
        if (!action.isEmpty() && !action.equals("reset") && !action.equals("realtime"))
            throw new WrongUsageException(getUsage(sender));
        if (action.equals("reset"))
        {
            if (counter == null) SupplierCounter.resetAll(server);
            else counter.reset(server);
            notifyCommandListener(sender, this, color + " supplier counters restarted.");
        }
        else if (counter == null) msg(sender, SupplierCounter.formatAll(server, action.equals("realtime"), false));
        else msg(sender, counter.format(server, action.equals("realtime"), false));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos)
    {
        if (!CarpetSettings.hopperNoItemCost) return Collections.emptyList();
        if (args.length == 1)
        {
            List<String> names = new ArrayList<>(SupplierCounter.COUNTERS.keySet());
            Collections.addAll(names, "all", "reset", "realtime");
            return getListOfStringsMatchingLastWord(args, names);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("all") ||
                SupplierCounter.getCounter(args[0].toLowerCase(Locale.ROOT)) != null))
            return getListOfStringsMatchingLastWord(args, "reset", "realtime");
        return Collections.emptyList();
    }
}
