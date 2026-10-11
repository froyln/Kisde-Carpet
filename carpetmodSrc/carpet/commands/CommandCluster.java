// Adapted from Void-Skeleton/Carpet-Vastech-Addition; LGPL-3.0.
package carpet.commands;

import carpet.CarpetSettings;
import carpet.helpers.ClusterHelper;
import carpet.utils.Messenger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class CommandCluster extends CommandCarpetBase {
    private final Map<ICommandSender, ClusterHelper> sessions = new WeakHashMap<>();
    private boolean computing;

    @Override
    public String getName() { return "cluster"; }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/cluster peek|read <NBT>|compute|loadCluster|loadGrid|constructLoader [block metadata [y]]";
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender.canUseCommand(2, getName());
    }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        return args.length == 1 && CarpetSettings.commandCluster
                ? getListOfStringsMatchingLastWord(args, "peek", "read", "compute", "loadCluster", "loadGrid", "constructLoader")
                : Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!sender.canUseCommand(2, getName())) throw new CommandException("commands.generic.permission");
        if (!command_enabled("commandCluster", sender)) return;
        if (args.length == 0) throw new WrongUsageException(getUsage(sender));
        ClusterHelper helper = sessions.computeIfAbsent(sender, key -> new ClusterHelper());
        switch (args[0]) {
            case "peek":
                Messenger.s(sender, helper.writeToNBT(new NBTTagCompound()).toString());
                return;
            case "read":
                if (args.length < 2) throw new WrongUsageException(getUsage(sender));
                if (computing) throw new CommandException("Wait for the current cluster calculation");
                try {
                    ClusterHelper replacement = new ClusterHelper();
                    replacement.readFromNBT(helper.writeToNBT(new NBTTagCompound()));
                    replacement.readFromNBT(JsonToNBT.getTagFromJson(buildString(args, 1)));
                    replacement.validate();
                    sessions.put(sender, replacement);
                    Messenger.s(sender, "Cluster parameters updated; compute before loading or construction");
                } catch (NBTException | IllegalArgumentException e) {
                    throw new CommandException("Invalid cluster parameters: %s", e.getMessage());
                }
                return;
            case "compute":
                if (computing) throw new CommandException("A cluster calculation is already running");
                try { helper.validate(); }
                catch (IllegalArgumentException e) { throw new CommandException("%s", e.getMessage()); }
                computing = true;
                long start = System.currentTimeMillis();
                HttpUtil.DOWNLOADER_EXECUTOR.submit(() -> {
                    int result;
                    try { result = helper.compute(); }
                    catch (RuntimeException e) { result = -1; }
                    final int size = result;
                    server.addScheduledTask(() -> {
                        computing = false;
                        if (!CarpetSettings.commandCluster || !sender.canUseCommand(2, getName())) return;
                        Messenger.s(sender, size < 0 ? "No cluster solution found" : "Cluster computed in "
                                + (System.currentTimeMillis() - start) + " ms; grid chunks: " + size
                                + "; target: " + helper.optimalTargetChunk + "; excluded: " + helper.optimalExcludedRelative);
                    });
                });
                Messenger.s(sender, "Cluster calculation started");
                return;
            case "loadCluster":
            case "loadGrid":
            case "constructLoader":
                if (computing) throw new CommandException("Wait for the current cluster calculation");
                if (helper.optimalTargetChunk == null) throw new CommandException("Compute a cluster first");
                if (args[0].equals("constructLoader") && args.length == 1) {
                    Messenger.s(sender, "Disconnected grid chunks: " + helper.checkClusterLoader());
                    return;
                }
                if (sender instanceof EntityPlayerMP && !((EntityPlayerMP) sender).interactionManager.isCreative())
                    throw new CommandException("Cluster world changes require creative mode");
                if (helper.optimalLoadingGrid.size() > 4096)
                    throw new CommandException("World operations are limited to 4096 grid chunks");
                World world = sender.getEntityWorld();
                for (ChunkPos chunk : helper.optimalLoadingGrid) {
                    if (!world.getWorldBorder().contains(chunk.getBlock(0, 64, 0))
                            || !world.getWorldBorder().contains(chunk.getBlock(15, 64, 15)))
                        throw new CommandException("Cluster grid exceeds this world's border");
                }
                if (args[0].equals("loadCluster")) helper.loadCluster(world);
                else if (args[0].equals("loadGrid")) helper.loadGrid(world);
                else {
                    if (args.length < 3 || args.length > 4) throw new WrongUsageException(getUsage(sender));
                    IBlockState block = getBlockByText(sender, args[1]).getStateFromMeta(parseInt(args[2], 0, 15));
                    if (!block.isTopSolid() || block.getBlock().hasTileEntity())
                        throw new CommandException("Loader bridges require a solid block without a block entity");
                    int y = args.length == 4 ? parseInt(args[3], 0, 254) : 64;
                    if (helper.checkClusterLoader() != 0) throw new CommandException("Loader grid is disconnected");
                    checkEmptyLoader(world, helper, y);
                    helper.buildClusterLoader(world, y, block);
                }
                Messenger.s(sender, "Cluster operation completed in this dimension");
                return;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private static void checkEmptyLoader(World world, ClusterHelper helper, int y) throws CommandException {
        for (ChunkPos chunk : helper.optimalLoadingGrid) {
            BlockPos center = chunk.getBlock(8, y, 8);
            for (int offset = -8; offset <= 7; offset++) {
                for (BlockPos pos : new BlockPos[] {center.add(offset, 0, 0), center.add(0, 0, offset), center.up()}) {
                    if (!world.getWorldBorder().contains(pos) || !world.isBlockLoaded(pos) || !world.isAirBlock(pos))
                        throw new CommandException("Loader area must be loaded, inside the world border and empty: %s", pos);
                }
            }
        }
    }
}
