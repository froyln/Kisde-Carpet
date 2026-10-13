package carpet.commands;

import carpet.helpers.StatHelper;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.SyntaxErrorException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.item.Item;
import net.minecraft.scoreboard.IScoreCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.*;

public class CommandScoreboardStats extends CommandCarpetBase {


    /**
     * Gets the name of the command
     */
    @Override
    public String getName() {
        return "sb";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/sb <stat type(b|c|d|k|m|p|u|K|or empty for general stats)>.<stat target>" + " <where to display(list|sidebar|belowName|empty for sidebar) or /sc clear <where to clear(empty is sidebar)>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (command_enabled("scoreboardStats", sender)) {
            if (args.length > 0 && args.length < 3) {
                String[] arguments = args[0].split("\\.");
                Scoreboard scoreboard = server.getWorld(0).getScoreboard();
                String criteria;
                IScoreCriteria scoreCriteria = null;
                if (args[0].equalsIgnoreCase("clear")) {
                    if (args.length == 2) {
                        switch (args[1].toLowerCase(Locale.ROOT)) {
                            case "list":
                                this.checkAndDeleteScoreOnDisplay(0, scoreboard);
                                server.getPlayerList().sendMessage(new TextComponentString(TextFormatting.GRAY + "Tab cleared by " + sender.getName()));
                                break;
                            case "sidebar":
                                this.checkAndDeleteScoreOnDisplay(1, scoreboard);
                                server.getPlayerList().sendMessage(new TextComponentString(TextFormatting.GRAY + "Sidebar cleared by " + sender.getName()));
                                break;
                            case "belowname":
                                this.checkAndDeleteScoreOnDisplay(2, scoreboard);
                                server.getPlayerList().sendMessage(new TextComponentString(TextFormatting.GRAY + "belowName cleared by " + sender.getName()));
                                break;
                            case "all":
                                this.deleteAllScores(scoreboard);
                                server.getPlayerList().sendMessage(new TextComponentString(TextFormatting.GRAY + "Cleared all scores by " + sender.getName()));
                                break;
                            default:
                                throw new SyntaxErrorException("You can only clear list, sidebar or belowName", new Object[0]);
                        }
                    }
                    else {
                        this.checkAndDeleteScoreOnDisplay(1, scoreboard);
                        server.getPlayerList().sendMessage(new TextComponentString(TextFormatting.GRAY + "Sidebar cleared by " + sender.getName()));
                    }
                    return;
                }
                String statType = null;
                switch (arguments[0]) {
                    case "b":
                        criteria = "stat.breakItem.minecraft." + arguments[1];
                        if (arguments.length == 3) {
                            criteria += "." + arguments[2];
                        }
                        scoreCriteria = IScoreCriteria.INSTANCES.get(criteria);
                        statType = " broken";
                        break;
                    case "c":
                        criteria = "stat.craftItem.minecraft." + arguments[1];
                        if (arguments.length == 3) {
                            criteria += "." + arguments[2];
                        }
                        scoreCriteria = IScoreCriteria.INSTANCES.get(criteria);
                        statType = " crafted";
                        break;
                    case "d":
                        criteria = "stat.drop.minecraft." + arguments[1];
                        if (arguments.length == 3) {
                            criteria += "." + arguments[2];
                        }
                        scoreCriteria = IScoreCriteria.INSTANCES.get(criteria);
                        statType = " dropped";
                        break;
                    case "k":
                        criteria = "stat.killEntity." + arguments[1];
                        scoreCriteria = IScoreCriteria.INSTANCES.get(criteria);
                        statType = " killed";
                        break;
                    case "m":
                        criteria = "stat.mineBlock.minecraft." + arguments[1];
                        if (arguments.length == 3) {
                            criteria += "." + arguments[2];
                        }
                        scoreCriteria = IScoreCriteria.INSTANCES.get(criteria);
                        statType = " mined";
                        break;
                    case "p":
                        criteria = "stat.pickup.minecraft." + arguments[1];
                        if (arguments.length == 3) {
                            criteria += "." + arguments[2];
                        }
                        scoreCriteria = IScoreCriteria.INSTANCES.get(criteria);
                        statType = " picked up";
                        break;
                    case "u":
                        criteria = "stat.useItem.minecraft." + arguments[1];
                        if (arguments.length == 3) {
                            criteria += "." + arguments[2];
                        }
                        scoreCriteria = IScoreCriteria.INSTANCES.get(criteria);
                        statType = " used";
                        break;
                    case "killedBy":
                        criteria = "stat.entityKilledBy." + arguments[1];
                        scoreCriteria = IScoreCriteria.INSTANCES.get(criteria);
                        statType = "Killed by ";
                        break;
                    case "health":
                        criteria = "health";
                        scoreCriteria = IScoreCriteria.INSTANCES.get(criteria);
                        statType = "Health";
                        break;
                    case "playedOneMinute":
                        criteria = "minutesPlayed";
                        scoreCriteria = IScoreCriteria.INSTANCES.get(criteria);
                        statType = "Minutes played";
                        break;
                    default:
                        criteria = "stat." + arguments[0];
                        scoreCriteria = IScoreCriteria.INSTANCES.get(criteria);
                        statType = "other";
                }
                if (scoreCriteria != null) {
                    int i = 1;

                    String objectiveName = "st." + args[0];

                    if (arguments.length > 1) {
                        ResourceLocation resourceLocation = new ResourceLocation(arguments[1]);
                        Item item = Item.REGISTRY.getObject(resourceLocation);

                        int idForItem = Item.REGISTRY.getIDForObject(item);
                        StringBuilder sb = new StringBuilder("st.");
                        sb.append(arguments[0]);
                        sb.append(".");
                        sb.append(idForItem);
                        if (arguments.length > 2) {
                            sb.append(".");
                            sb.append(arguments[2]);
                        }
                        objectiveName = sb.toString();
                    }

                    ScoreObjective objective;
                    if (objectiveName.length() > 16) { //Shouldn't happen
                        objectiveName = objectiveName.substring(0, 16);
                    }
                    if (scoreboard.getObjective(objectiveName) == null) {
                        String displayName = "ERROR";
                        objective = scoreboard.addScoreObjective(objectiveName, scoreCriteria);
                        if (!statType.equals("Killed by ")) {
                            if (arguments.length == 3) {
                                displayName = TextFormatting.GOLD + arguments[1].replace('_', ' ').substring(0, 1).toUpperCase()
                                        + arguments[1].replace('_', ' ').substring(1) + " " + arguments[2] + statType;
                            }
                            else {
                                switch (statType) {
                                    case "Health":
                                    case "Minutes played":
                                        displayName = TextFormatting.GOLD + statType;
                                        break;
                                    case "other":
                                        displayName = TextFormatting.GOLD + arguments[0];
                                        break;
                                    default:
                                        displayName = TextFormatting.GOLD + arguments[1].replace('_', ' ').substring(0, 1).toUpperCase()
                                                + arguments[1].replace('_', ' ').substring(1) + statType;

                                        break;
                                }
                            }
                        }
                        else {
                            displayName = TextFormatting.GOLD + statType + arguments[1];
                        }
                        displayName = abreviateIfNecessary(displayName, "glazed", "glz");
                        displayName = abreviateIfNecessary(displayName, "terracotta", "terracota");
                        displayName = abreviateIfNecessary(displayName, "pressure", "prsure");
                        displayName = abreviateIfNecessary(displayName, "weighted", "wghtd");
                        displayName = abreviateIfNecessary(displayName, "crafted", "craft");
                        displayName = abreviateIfNecessary(displayName, "picked", "pick");
                        displayName = abreviateIfNecessary(displayName, "dropped", "drop");


                        if (displayName.length() > 32) {
                            displayName = displayName.substring(0, 32);
                        }

                        objective.setDisplayName(displayName);

                        if (statType.equals("Minutes played")) {
                            StatHelper.initializeWithDividerWithDifferentCriteria(scoreboard, server, objective, 1200, IScoreCriteria.INSTANCES.get("stat.playOneMinute"));
                        } else {
                            StatHelper.initialize(scoreboard, server, objective);
                        }
                    }
                    else {
                        objective = scoreboard.getObjective(objectiveName);
                    }
                    if (args.length == 2) {
                        i = Scoreboard.getObjectiveDisplaySlotNumber(args[1]);
                        if (i == -1) {
                            throw new SyntaxErrorException("You can only display it on list, sidebar or belowName,", new Object[0]);
                        }
                        else {
                            ScoreObjective displayedObjective = scoreboard.getObjectiveInDisplaySlot(i);
                            if (displayedObjective == null) {
                                scoreboard.setObjectiveInDisplaySlot(i, objective);
                            }
                            else {
                                scoreboard.setObjectiveInDisplaySlot(i, objective);
                                if (!(isObjectiveOnDisplay(displayedObjective, scoreboard)) && displayedObjective.getName().startsWith("st.")) {
                                    scoreboard.removeObjective(displayedObjective);
                                }
                            }
                        }
                    }
                    else {
                        ScoreObjective displayedObjective = scoreboard.getObjectiveInDisplaySlot(1);
                        if (displayedObjective == null) {
                            scoreboard.setObjectiveInDisplaySlot(i, objective);
                        }
                        else {
                            scoreboard.setObjectiveInDisplaySlot(i, objective);
                            if (!isObjectiveOnDisplay(displayedObjective, scoreboard) && displayedObjective.getName().startsWith("st.")) {
                                scoreboard.removeObjective(displayedObjective);
                            }
                        }
                    }
                    String displaySlotName = Scoreboard.getObjectiveDisplaySlot(i);
                    server.getPlayerList().sendMessage(new TextComponentString(TextFormatting.GRAY + displaySlotName.substring(0, 1).toUpperCase() + displaySlotName.substring(1) + " changed by " + sender.getName()));
                }
                else {
                    sender.sendMessage(new TextComponentString(TextFormatting.RED + "That is not a valid stat"));
                }
            }
            else {
                throw new WrongUsageException(getUsage(sender), new Object[0]);
            }
        }
    }

    public boolean isObjectiveOnDisplay(ScoreObjective scoreObjective, Scoreboard scoreboard) {
        boolean isObjectiveOnDisplay = false;
        for (int i = 0; i <= 2; i++) {
            if (scoreObjective.equals(scoreboard.getObjectiveInDisplaySlot(i))) {
                isObjectiveOnDisplay = true;
            }
        }
        return isObjectiveOnDisplay;
    }

    public void deleteAllScores(Scoreboard scoreboard) {
        Collection<ScoreObjective> allScoreObjectives = scoreboard.getScoreObjectives();
        Iterator<ScoreObjective> scoresIterator = allScoreObjectives.iterator();
        while (scoresIterator.hasNext()) {
            ScoreObjective scoreObjective = scoresIterator.next();
            if (scoreObjective.getName().startsWith("st.")) {
                scoresIterator.remove();
            }
        }
        for (int i = 0; i <= 2; i++) {
            scoreboard.setObjectiveInDisplaySlot(i, null);
        }
    }

    public void checkAndDeleteScoreOnDisplay(int displayIdentifier, Scoreboard scoreboard) throws CommandException {
        if (scoreboard.getObjectiveInDisplaySlot(displayIdentifier) != null) {
            if (scoreboard.getObjectiveInDisplaySlot(displayIdentifier).getName().startsWith("st.")) {
                scoreboard.removeObjective(scoreboard.getObjectiveInDisplaySlot(displayIdentifier));
            }
            else {
                throw new CommandException("You can't clear a handmade scoreboard", new Object[0]);
            }
        }
        else {
            throw new SyntaxErrorException("There is no scoreboard to clear", new Object[0]);
        }
    }


    public String abreviateIfNecessary(String string, String textToReplace, String abreviation) {
        String result = string;

        if (string.length() > 32 && string.contains(textToReplace)) {
            result = string.replace(textToReplace, abreviation);
        }

        return result;
    }

    /**
     * Get a list of options for when the user presses the TAB key
     */
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            Set<String> instancesKeys = IScoreCriteria.INSTANCES.keySet();
            options.add("clear");
            options.add("health");
            options.add("playedOneMinute");
            for (String key : instancesKeys) {
                String[] splittedKey = key.split("\\.");
                StringBuilder finalOption = new StringBuilder();
                if (splittedKey[0].equals("stat")) {
                    switch (splittedKey[1]) {
                        case "breakItem":
                            finalOption.append("b.");
                            break;
                        case "craftItem":
                            finalOption.append("c.");
                            break;
                        case "drop":
                            finalOption.append("d.");
                            break;
                        case "killEntity":
                            finalOption.append("k.");
                            break;
                        case "mineBlock":
                            finalOption.append("m.");
                            break;
                        case "pickup":
                            finalOption.append("p.");
                            break;
                        case "useItem":
                            finalOption.append("u.");
                            break;
                        case "entityKilledBy":
                            finalOption.append("killedBy.");
                            break;
                        default:
                            finalOption.append(splittedKey[1]);
                    }
                    if (splittedKey.length > 2) {
                        if (splittedKey.length > 3) {
                            finalOption.append(splittedKey[3]);
                            if (splittedKey.length == 5) {
                                finalOption.append("." + splittedKey[4]);
                            }
                        }
                        else {
                            finalOption.append(splittedKey[2]);
                        }
                    }
                    options.add(finalOption.toString());
                }
            }

            for (String option : options) {
                if (option.length() > 32 - 7) {
                    System.out.println(option);
                }
            }
            return getListOfStringsMatchingLastWord(args, options);
        }
        if (args.length == 2) {
            if (args[0].equals("clear")) {
                return getListOfStringsMatchingLastWord(args, "sidebar", "list", "belowName", "all");
            }
            return getListOfStringsMatchingLastWord(args, "sidebar", "list", "belowName");
        }
        else {
            return Collections.emptyList();
        }

    }

}

