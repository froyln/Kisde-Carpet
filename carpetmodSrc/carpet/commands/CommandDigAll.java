package carpet.commands;

import carpet.CarpetSettings;
import carpet.helpers.StatHelper;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextFormatting;

public class CommandDigAll extends CommandCarpetBase {
    public String getName() { return "dig-all"; }
    public String getUsage(ICommandSender sender) { return "/dig-all"; }
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return CarpetSettings.commandDigAll; }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!checkPermission(server, sender)) throw new CommandException("commands.generic.permission");
        if (args.length != 0) throw new WrongUsageException(getUsage(sender));
        Scoreboard scoreboard = server.getWorld(0).getScoreboard();
        ScoreObjective objective = scoreboard.getObjective("st.dig-all");
        if (objective != null && objective.getCriteria() != StatHelper.DIG_ALL) {
            throw new CommandException("Scoreboard st.dig-all already exists with a different criterion");
        }
        if (objective == null) {
            objective = scoreboard.addScoreObjective("st.dig-all", StatHelper.DIG_ALL);
            StatHelper.initialize(scoreboard, server, objective);
        }
        objective.setDisplayName(TextFormatting.GOLD + "Dig-all");
        scoreboard.setObjectiveInDisplaySlot(1, objective);
    }
}
