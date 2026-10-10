package carpet.commands;

import carpet.helpers.LifeTimeTracker;
import carpet.utils.Messenger;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import java.util.*;

public class CommandLifeTime extends CommandCarpetBase
{
    public String getName() { return "lifetime"; }
    public String getUsage(ICommandSender sender) { return "/lifetime tracking [start|stop|restart|realtime], filter [global|type] [set <selectors>|clear], <type> [lifetime|spawning|removal] [realtime] [dimension <id>]"; }
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return LifeTimeTracker.permitted(sender); }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
    {
        if (!checkPermission(server, sender)) throw new CommandException("commands.generic.permission");
        if (args.length == 0 || "help".equals(args[0])) {
            tell(sender, Collections.singletonList(getUsage(sender)));
            tell(sender, Collections.singletonList("Tracks newly spawned mobs, items, XP orbs, boats and minecarts. Filters use 1.12 selectors, OR within a filter; global AND type. Existing entities are excluded; tracked entities retain age through chunk unload/reload within the same tracking session. No file recorder."));
            return;
        }
        if ("tracking".equals(args[0])) {
            if (args.length > 2) throw new WrongUsageException(getUsage(sender));
            String action = args.length == 1 ? "status" : args[1];
            switch (action) {
                case "start": LifeTimeTracker.start(false); break;
                case "restart": LifeTimeTracker.start(true); break;
                case "stop": LifeTimeTracker.stop(); break;
                case "status": case "realtime": break;
                default: throw new WrongUsageException(getUsage(sender));
            }
            tell(sender, LifeTimeTracker.report(null, null, "realtime".equals(action), null));
            return;
        }
        if ("filter".equals(args[0])) {
            if (args.length == 1) { tell(sender, LifeTimeTracker.showFilters(null)); return; }
            String key = "global".equals(args[1]) ? "global" : type(args[1]);
            if (args.length == 2) { tell(sender, LifeTimeTracker.showFilters(key)); return; }
            if ("clear".equals(args[2]) && args.length == 3) LifeTimeTracker.clearFilter(key);
            else if ("set".equals(args[2]) && args.length >= 4) LifeTimeTracker.setFilter(key, sender, Arrays.copyOfRange(args, 3, args.length));
            else throw new WrongUsageException(getUsage(sender));
            tell(sender, LifeTimeTracker.showFilters(key)); return;
        }
        String entityType = "all".equals(args[0]) ? null : type(args[0]);
        String detail = null; boolean realtime = false; Integer dimension = null;
        for (int i = 1; i < args.length; i++) {
            if ("realtime".equals(args[i]) && !realtime) realtime = true;
            else if (Arrays.asList("lifetime", "spawning", "removal").contains(args[i]) && detail == null) detail = args[i];
            else if ("dimension".equals(args[i]) && dimension == null && i + 1 < args.length) dimension = parseInt(args[++i]);
            else throw new WrongUsageException(getUsage(sender));
        }
        tell(sender, LifeTimeTracker.report(entityType, detail, realtime, dimension));
    }

    private static String type(String value) throws CommandException
    {
        ResourceLocation type;
        try { type = new ResourceLocation(value); }
        catch (RuntimeException ex) { throw new CommandException("Invalid entity type: %s", value); }
        if (!EntityList.getEntityNameList().contains(type)) throw new CommandException("Unknown entity type: %s", value);
        return type.toString();
    }

    private void tell(ICommandSender sender, List<String> lines)
    {
        for (String line : lines) msg(sender, Messenger.m(null, "w " + line));
    }

    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos)
    {
        if (!checkPermission(server, sender)) return Collections.emptyList();
        if (args.length == 1) {
            List<String> choices = new ArrayList<>(Arrays.asList("tracking", "filter", "help", "all")); choices.addAll(LifeTimeTracker.types());
            return getListOfStringsMatchingLastWord(args, choices);
        }
        if ("tracking".equals(args[0]) && args.length == 2) return getListOfStringsMatchingLastWord(args, "start", "stop", "restart", "realtime");
        if ("filter".equals(args[0])) {
            if (args.length == 2) {
                List<String> choices = new ArrayList<>(); choices.add("global");
                for (ResourceLocation type : EntityList.getEntityNameList()) choices.add(type.toString());
                return getListOfStringsMatchingLastWord(args, choices);
            }
            if (args.length == 3) return getListOfStringsMatchingLastWord(args, "set", "clear");
            return getListOfStringsMatchingLastWord(args, "@e", "@e[type=", "@e[tag=");
        }
        return getListOfStringsMatchingLastWord(args, "lifetime", "spawning", "removal", "realtime", "dimension");
    }
}
