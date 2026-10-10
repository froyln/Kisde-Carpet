package carpet;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameType;

public final class KisdeOpPlayerNoCheatCheck
{
    public static void run(MinecraftServer server, EntityPlayerMPFake player)
    {
        server.getPlayerList().addOp(player.getGameProfile());
        try
        {
            CarpetSettings.opPlayerNoCheat = false;
            if (server.getCommandManager().executeCommand(player, "gamemode 0") != 1 || player.interactionManager.getGameType() != GameType.SURVIVAL)
                throw new AssertionError("Cheat control command failed");
            player.interactionManager.setGameType(GameType.CREATIVE);
            CarpetSettings.opPlayerNoCheat = true;
            if (server.getCommandManager().executeCommand(player, "gamemode 0") != 0 || player.interactionManager.getGameType() != GameType.CREATIVE)
                throw new AssertionError("OP gamemode was not blocked");
            if (server.getCommandManager().executeCommand(player, "execute DropOff ~ ~ ~ summon pig ~ ~ ~") != 0)
                throw new AssertionError("OP execute summon was not blocked");
        }
        finally
        {
            CarpetSettings.opPlayerNoCheat = false;
            player.interactionManager.setGameType(GameType.CREATIVE);
            server.getPlayerList().removeOp(player.getGameProfile());
        }
    }
}
