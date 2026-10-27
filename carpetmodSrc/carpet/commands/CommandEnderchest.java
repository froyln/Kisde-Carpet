package carpet.commands;

import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.SyntaxErrorException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class CommandEnderchest extends CommandCarpetBase {
    /**
     * Gets the name of the command
     */
    @Override
    public String getName() {
        return "enderchest";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/enderchest <Player>";
    }

    /**
     * Return the required permiss on level for this command.
     */
    public int getRequiredPermissionLevel() {
        return 2;
    }

    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender.canUseCommand(this.getRequiredPermissionLevel(), this.getName());
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (command_enabled("commandEnderchest", sender)) {
            if (checkPermission(server, sender)) {
                if (args.length == 1) {
                    EntityPlayerMP sendingPlayer = (EntityPlayerMP) sender;
                    if (sendingPlayer.getName().equalsIgnoreCase(args[0])) {
                        throw new SyntaxErrorException("Cannot take your own ender chest", new Object[0]);
                    }
                    GameProfile targetPlayerGameProfile = server.getPlayerProfileCache().getGameProfileForUsername(args[0]);
                    server.getPlayerList().saveAllPlayerData();
                    if (this.isPlayerOnline(server, args[0])) {
                        EntityPlayerMP targetPlayer = server.getPlayerList().getPlayerByUsername(args[0]);
                        NBTTagList objectiveEnderChest = server.getPlayerList().readPlayerDataFromFile(targetPlayer).getTagList("EnderItems", 10);
                        this.swapEnderChests(server, sendingPlayer, targetPlayer, objectiveEnderChest);
                    }
                    else {
                        if (this.getOfflinePlayerData(server, targetPlayerGameProfile) != null) {
                            EntityPlayerMP targetPlayer = server.getPlayerList().createPlayerForUser(targetPlayerGameProfile);
                            NBTTagList objectiveEnderChest = this.getOfflinePlayerData(server, targetPlayerGameProfile).getTagList("EnderItems", 10);
                            this.swapEnderChests(server, sendingPlayer, targetPlayer, objectiveEnderChest);
                        }
                        else {
                            throw new SyntaxErrorException("Player does not exist", new Object[0]);
                        }
                    }
                }
                else {
                    throw new WrongUsageException(getUsage(sender), new Object[0]);
                }
            }
        }
    }


    public NBTTagCompound getOfflinePlayerData(MinecraftServer server, GameProfile offlinePlayerGameProfile) {

        return (offlinePlayerGameProfile != null) ? server.getPlayerList().readPlayerDataFromFile(new EntityPlayerMP(server, server.getWorld(0), offlinePlayerGameProfile, new PlayerInteractionManager(server.getWorld(0)))) : null;
    }

    public boolean isPlayerOnline(MinecraftServer server, String playerName) {
        return Arrays.stream(server.getOnlinePlayerNames()).anyMatch((name) -> name.equalsIgnoreCase(playerName));
    }

    public void swapEnderChests(MinecraftServer server, EntityPlayerMP sendingPlayer, EntityPlayerMP targetPlayer, NBTTagList objectiveEnderChest) {
        sendingPlayer.getInventoryEnderChest().loadInventoryFromNBT(objectiveEnderChest);
        targetPlayer.readFromNBT(getOfflinePlayerData(server, targetPlayer.getGameProfile()));
        targetPlayer.getInventoryEnderChest().clear();
        targetPlayer.getInventoryEnderChest().saveInventoryToNBT();
        server.getWorld(0).getSaveHandler().getPlayerNBTManager().writePlayerData(targetPlayer);
        sendingPlayer.sendMessage(new TextComponentString(TextFormatting.GREEN + "Ender chest was successfully taken"));
    }


    /**
     * Get a list of options for when the user presses the TAB key
     */
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> userNames = getListOfStringsMatchingLastWord(args, server.getPlayerProfileCache().getUsernames());
            Iterator<String> validUsernameIterator = userNames.iterator();
            Scoreboard scoreboard = server.getWorld(0).getScoreboard();

            while (validUsernameIterator.hasNext()) {
                String currentName = validUsernameIterator.next();
                GameProfile targetPlayerGameProfile = server.getPlayerProfileCache().getGameProfileForUsername(currentName);
                if (getOfflinePlayerData(server, targetPlayerGameProfile) == null) {
                    validUsernameIterator.remove();
                }
               if (scoreboard.getPlayersTeam(currentName)!=null && scoreboard.getPlayersTeam(currentName).getName().equals("Bots")) {
                    validUsernameIterator.remove();
                }
            }
            return userNames;
        }
        else {
            return Collections.emptyList();
        }
        //return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getPlayerList().getOnlinePlayerNames()) : Collections.emptyList();
        //return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getPlayerProfileCache().getUsernames()) : Collections.emptyList();
        //return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getPlayerList().getWhitelistedPlayerNames()) : Collections.emptyList();
    }
}





