package carpet.commands;

import net.minecraft.command.ICommandSender;

public class CommandSubscribe extends CommandLog {

    private final String USAGE = "/subscribe <subscribeName> [?option]";

    @Override
    public String getName() {
        return "subscribe";
    }

    @Override
    public java.util.List<String> getAliases() {
        return java.util.Collections.singletonList("suscribe");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return USAGE;
    }
}
