package carpet;

import carpet.helpers.ClusterHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;

final class VastechWorldCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class ProbeWorld extends World {
        final Map<BlockPos, IBlockState> blocks = new HashMap<>();
        final Map<BlockPos, TileEntity> tiles = new HashMap<>();
        int spawned;
        ProbeWorld() {
            super(null, new net.minecraft.world.storage.WorldInfo(new net.minecraft.world.WorldSettings(0,
                    net.minecraft.world.GameType.CREATIVE, false, false, net.minecraft.world.WorldType.FLAT), "probe"),
                    new net.minecraft.world.WorldProviderSurface(), new net.minecraft.profiler.Profiler(), false);
        }
        protected net.minecraft.world.chunk.IChunkProvider createChunkProvider() { return null; }
        public boolean isChunkLoaded(int x, int z, boolean empty) { return true; }
        public IBlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.getDefaultState()); }
        public TileEntity getTileEntity(BlockPos pos) { return tiles.get(pos); }
        public boolean spawnEntity(net.minecraft.entity.Entity entity) { spawned++; return true; }
        public int getRedstonePower(BlockPos pos, net.minecraft.util.EnumFacing facing) { return 11; }
        public boolean setBlockState(BlockPos pos, IBlockState state, int flags) {
            BlockPos key = pos.toImmutable();
            blocks.put(key, state);
            tiles.remove(key);
            TileEntity tile = state.getBlock() == Blocks.HOPPER ? new TileEntityHopper()
                    : state.getBlock() == Blocks.CHEST ? new TileEntityChest()
                    : state.getBlock() == Blocks.STANDING_SIGN ? new TileEntitySign() : null;
            if (tile != null) { tile.setWorld(this); tile.setPos(key); tiles.put(key, tile); }
            return true;
        }
        public void markChunkDirty(BlockPos pos, TileEntity tile) { }
        public void notifyBlockUpdate(BlockPos pos, IBlockState old, IBlockState state, int flags) { }
    }

    private static final class CounterBlock extends Block {
        int updates;
        boolean fail;
        CounterBlock() { super(Material.ROCK); }
        public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
            if (fail) throw new ClassCastException("fixture");
            updates++;
        }
    }

    static void run() {
        CarpetSettings.redstoneMultimeter = false;
        CarpetSettings.redstoneMultimeterLegacy = false;
        ProbeWorld world = new ProbeWorld();
        BlockPos pos = new BlockPos(8, 64, 8);
        world.setBlockState(pos, Blocks.TNT.getDefaultState());
        CarpetSettings.TNTdoesNotPrime = true;
        Blocks.TNT.neighborChanged(world.getBlockState(pos), world, pos, Blocks.REDSTONE_BLOCK, pos.down());
        check(world.spawned == 0 && world.getBlockState(pos).getBlock() == Blocks.TNT, "TNT primed while disabled");
        CarpetSettings.TNTdoesNotPrime = false;
        Blocks.TNT.neighborChanged(world.getBlockState(pos), world, pos, Blocks.REDSTONE_BLOCK, pos.down());
        check(world.spawned == 1 && world.isAirBlock(pos), "Normal TNT priming missing");
        CounterBlock counter = new CounterBlock();
        world.setBlockState(pos.east(), counter.getDefaultState());
        CarpetSettings.totallyNoBlockUpdates = true;
        world.notifyNeighborsOfStateChange(pos, Blocks.STONE, true);
        check(counter.updates == 0, "Suppressed neighbor update delivered");
        CarpetSettings.totallyNoBlockUpdates = false;
        world.notifyNeighborsOfStateChange(pos, Blocks.STONE, true);
        check(counter.updates == 1, "Normal neighbor update missing");
        counter.fail = true;
        CarpetSettings.castSuppressorCrashFix = true;
        try { world.neighborChangedUpdateSuppressionFixed(pos.east(), Blocks.STONE, pos); throw new AssertionError("Cast suppression missing"); }
        catch (carpet.helpers.ThrowableSuppression expected) { }
        CarpetSettings.castSuppressorCrashFix = false;
        try { world.neighborChangedUpdateSuppressionFixed(pos.east(), Blocks.STONE, pos); throw new AssertionError("Baseline cast error swallowed"); }
        catch (net.minecraft.util.ReportedException expected) { check(expected.getCause() instanceof ClassCastException, "Baseline cast cause lost"); }
        world.setBlockState(pos.down(), Blocks.DIAMOND_BLOCK.getDefaultState());
        world.setBlockState(pos, Blocks.STANDING_SIGN.getDefaultState());
        TileEntitySign sign = (TileEntitySign) world.getTileEntity(pos);
        sign.signText[0] = new net.minecraft.util.text.TextComponentString("0xB");
        CarpetSettings.signComparatorOutput = true;
        check(Blocks.STANDING_SIGN.getComparatorInputOverride(world.getBlockState(pos), world, pos) == 11, "Sign output missing");
        CarpetSettings.signComparatorOutput = false;
        check(Blocks.STANDING_SIGN.getComparatorInputOverride(world.getBlockState(pos), world, pos) == 0, "Disabled sign output active");
        world.setBlockState(pos.down(), Blocks.EMERALD_BLOCK.getDefaultState());
        CarpetSettings.signComparatorDisplay = true;
        Blocks.STANDING_SIGN.neighborChanged(world.getBlockState(pos), world, pos, Blocks.STONE, pos.down());
        check(sign.signText[0].getUnformattedText().equals("11") && sign.signText[1].getUnformattedText().equals("0xb"), "Sign display missing");
        CarpetSettings.signComparatorDisplay = false;
        CarpetSettings.disableLightUpdates = true;
        check(!world.checkLight(pos) && !world.checkLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos), "Light suppression missing");
        CarpetSettings.disableLightUpdates = false;
        ClusterHelper loader = new ClusterHelper();
        loader.optimalClusterChunks.add(new net.minecraft.util.math.ChunkPos(0, 0));
        loader.optimalClusterChunks.add(new net.minecraft.util.math.ChunkPos(1, 0));
        loader.optimalLoadingGrid.addAll(loader.optimalClusterChunks);
        check(loader.checkClusterLoader() == 0, "Loader connectivity failed");
        world.blocks.clear(); world.tiles.clear();
        loader.buildClusterLoader(world, 64, Blocks.STONE.getDefaultState());
        check(world.getTileEntity(pos) instanceof TileEntityHopper && world.getTileEntity(pos.add(16, 0, 0)) instanceof TileEntityHopper,
                "Cluster hoppers missing");
        check(((TileEntityHopper) world.getTileEntity(pos)).getStackInSlot(0).getItem() == net.minecraft.init.Items.SHEARS, "Hopper payload missing");
        check(world.getTileEntity(pos.add(7, 0, 0)) instanceof TileEntityChest, "Bridge chest missing");
    }
}
