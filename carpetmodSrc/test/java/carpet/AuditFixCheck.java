package carpet;

import carpet.commands.CommandStructure;
import carpet.commands.CommandTicktimes;
import carpet.helpers.AnvilSync;
import carpet.helpers.LifeTimeTracker;
import carpet.schematic.SchematicSharingHandler;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.mojang.authlib.GameProfile;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.border.WorldBorder;
import sun.misc.Unsafe;

public final class AuditFixCheck {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static <T> T allocate(Class<T> type) throws Exception {
        Field singleton = Unsafe.class.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        return type.cast(((Unsafe)singleton.get(null)).allocateInstance(type));
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
    }
    private static void rejected(Method method, Object instance, Object... args) throws Exception {
        try { method.invoke(instance, args); throw new AssertionError("Invalid operation accepted"); }
        catch (InvocationTargetException expected) { check(expected.getCause() instanceof CommandException || expected.getCause() instanceof IllegalArgumentException, "Wrong failure: " + expected.getCause()); }
    }
    private static final class World extends WorldServer {
        boolean loaded;
        WorldBorder border;
        IBlockState state;
        net.minecraft.world.gen.structure.template.TemplateManager templates;
        World() { super(null, null, null, 0, null); }
        public int getActualHeight() { return 256; }
        public net.minecraft.world.gen.structure.template.TemplateManager getStructureTemplateManager() { return templates; }
        public WorldBorder getWorldBorder() { return border; }
        public boolean isBlockLoaded(BlockPos pos) { return loaded; }
        public IBlockState getBlockState(BlockPos pos) { return state; }
        public TileEntity getTileEntity(BlockPos pos) { return null; }
        public boolean setBlockToAir(BlockPos pos) { return true; }
        public boolean isRemovableBlock(BlockPos pos) { return true; }
        public void playEvent(EntityPlayer player, int id, BlockPos pos, int data) { }
        public void playEvent(int id, BlockPos pos, int data) { }
    }
    private static final class Connection extends NetHandlerPlayServer {
        List<Packet<?>> packets;
        Connection() { super(null, null, null); }
        public void sendPacket(Packet<?> packet) { packets.add(packet); }
    }
    private static final class Player extends EntityPlayerMP {
        int messages;
        Player() { super(null, null, new GameProfile(UUID.randomUUID(), "AuditFixture"), null); }
        public String getName() { return "AuditFixture"; }
        public boolean canUseCommand(int level, String command) { return false; }
        public boolean canHarvestBlock(IBlockState state) { return true; }
        public void sendMessage(net.minecraft.util.text.ITextComponent text) { messages++; }
        public void addExperienceLevel(int amount) { experienceLevel += amount; }
        public void addStat(net.minecraft.stats.StatBase stat, int value) { }
    }
    private static Player player() throws Exception {
        Player player = allocate(Player.class); player.setUniqueId(UUID.randomUUID());
        World world = allocate(World.class); world.loaded = true; world.border = new WorldBorder(); world.state = Blocks.AIR.getDefaultState();
        player.world = world; player.capabilities = new PlayerCapabilities(); player.inventory = new InventoryPlayer(player);
        player.inventoryContainer = new ContainerPlayer(player.inventory, false, player); player.openContainer = player.inventoryContainer;
        Connection connection = allocate(Connection.class); connection.packets = new ArrayList<>(); player.connection = connection;
        return player;
    }
    private static void structure() throws Exception {
        Player ordinary = player(); CommandStructure command = new CommandStructure();
        for (String action : new String[]{"load", "save"}) {
            try { command.execute(null, ordinary, new String[]{action}); throw new AssertionError("Structure mutation accepted"); }
            catch (CommandException expected) { check("commands.generic.permission".equals(expected.getMessage()), "Permission checked after world/argument access"); }
        }
        check(command.checkPermission(null, ordinary), "Public structure list removed");
        Method size = method(CommandStructure.class, "validateSize", BlockPos.class);
        size.invoke(null, new BlockPos(32, 32, 32));
        for (BlockPos value : new BlockPos[]{new BlockPos(0, 1, 1), new BlockPos(-1, 1, 1), new BlockPos(32769, 1, 1), new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)}) rejected(size, null, value);
        Method bounds = method(CommandStructure.class, "validateRegion", net.minecraft.world.World.class, BlockPos.class, BlockPos.class);
        World world = (World)ordinary.world;
        bounds.invoke(null, world, new BlockPos(15, 64, -1), new BlockPos(-1, 65, 15));
        rejected(bounds, null, world, new BlockPos(0, -1, 0), BlockPos.ORIGIN);
        rejected(bounds, null, world, BlockPos.ORIGIN, new BlockPos(0, 256, 0));
        rejected(bounds, null, world, BlockPos.ORIGIN, new BlockPos(30000000, 64, 0));
        world.border.setTransition(16); rejected(bounds, null, world, BlockPos.ORIGIN, new BlockPos(20, 64, 0));
        world.border.setTransition(60000000); world.loaded = false; rejected(bounds, null, world, BlockPos.ORIGIN, new BlockPos(0, 64, 0));
        System.out.println("PASS structure authorization, size/overflow, border/height and loaded chunks");
    }
    private static final class ThrowingBlock extends Block {
        int fail;
        Player expected;
        ThrowingBlock() { super(Material.ROCK); }
        public void onBlockHarvested(net.minecraft.world.World world, BlockPos pos, IBlockState state, EntityPlayer player) {
            check(PlayerInteractionManager.playerMinedBlock == expected, "Missing mining context");
            if (fail == 1) throw new IllegalStateException("fixture");
        }
        public void harvestBlock(net.minecraft.world.World world, EntityPlayer player, BlockPos pos, IBlockState state, TileEntity tile, ItemStack held) {
            check(PlayerInteractionManager.playerMinedBlock == expected, "Missing harvest context");
            if (fail == 2) throw new IllegalStateException("fixture");
        }
    }
    private static void mining() throws Exception {
        Player player = player(), previous = player(); World world = (World)player.world;
        PlayerInteractionManager manager = new PlayerInteractionManager(world); manager.player = player; field(EntityPlayerMP.class, "interactionManager").set(player, manager);
        ThrowingBlock block = new ThrowingBlock(); block.expected = player; world.state = block.getDefaultState();
        for (int phase = 0; phase < 3; phase++) {
            CarpetSettings.duplicationFixUpdateSuppression = phase == 2;
            field(PlayerInteractionManager.class, "gameType").set(manager, phase == 1 ? GameType.SURVIVAL : GameType.CREATIVE);
            block.fail = phase == 1 ? 2 : 1; PlayerInteractionManager.playerMinedBlock = previous;
            try { manager.tryHarvestBlock(BlockPos.ORIGIN); throw new AssertionError("Mining failure missing"); }
            catch (IllegalStateException expected) { check("fixture".equals(expected.getMessage()), "Wrong mining failure"); }
            check(PlayerInteractionManager.playerMinedBlock == previous, "Mining exception leaked context");
            block.fail = 0; check(manager.tryHarvestBlock(BlockPos.ORIGIN), "Normal harvest changed");
            check(PlayerInteractionManager.playerMinedBlock == previous, "Successful harvest lost previous context");
        }
        PlayerInteractionManager.playerMinedBlock = null; CarpetSettings.duplicationFixUpdateSuppression = false;
        System.out.println("PASS native mining exceptions and success at all three context scopes");
    }
    private static final class Server extends DedicatedServer {
        List<Runnable> tasks;
        boolean protectedBlocks;
        Server() { super(null, null, null, null, null, null); }
        public boolean isServerRunning() { return true; }
        public int getBuildLimit() { return 256; }
        public boolean isBlockProtected(net.minecraft.world.World world, BlockPos pos, EntityPlayer player) { return protectedBlocks; }
        public void sendMessage(net.minecraft.util.text.ITextComponent text) { }
        public ListenableFuture<Object> addScheduledTask(Runnable task) { tasks.add(task); return Futures.immediateFuture(null); }
    }
    @SuppressWarnings("unchecked")
    private static void sharing() throws Exception {
        Server server = allocate(Server.class); server.tasks = new ArrayList<>(); List<Runnable> workers = new ArrayList<>();
        Constructor<SchematicSharingHandler> constructor = SchematicSharingHandler.class.getDeclaredConstructor(MinecraftServer.class, Executor.class); constructor.setAccessible(true);
        SchematicSharingHandler handler = constructor.newInstance(server, (Executor)workers::add);
        Semaphore permits = (Semaphore)field(SchematicSharingHandler.class, "validationSlots").get(handler);
        Map<UUID,Object> uploads = (Map<UUID,Object>)field(SchematicSharingHandler.class, "uploads").get(handler);
        Class<?> uploadClass = Class.forName("carpet.schematic.SchematicSharingHandler$Upload"); Constructor<?> upload = uploadClass.getDeclaredConstructors()[0]; upload.setAccessible(true);
        Method finish = method(SchematicSharingHandler.class, "finish", EntityPlayerMP.class, NBTTagCompound.class);
        Method cancel = method(SchematicSharingHandler.class, "cancel", UUID.class);
        Player player = player(); Path directory = Files.createTempDirectory("kisde-audit-uploads-");
        for (int i = 0; i < 5; i++) {
            UUID token = UUID.randomUUID(); Path path = Files.createTempFile(directory, "upload-", ".tmp");
            Object operation = upload.newInstance(token, UUID.randomUUID(), 0L, "fixture", "{}", 0, 1, String.join("", Collections.nCopies(64, "0")), path, 0L);
            field(uploadClass, "offset").setInt(operation, 1); uploads.put(player.getUniqueID(), operation);
            NBTTagCompound request = new NBTTagCompound(); request.setString("Transfer", token.toString());
            if (i < 4) finish.invoke(handler, player, request); else rejected(finish, handler, player, request);
            cancel.invoke(handler, player.getUniqueID());
        }
        check(workers.size() == 4 && permits.availablePermits() == 0, "Cancellation bypassed validation permits");
        for (Runnable worker : workers) worker.run();
        check(server.tasks.size() == 4 && permits.availablePermits() == 0, "Queued completions freed permits early");
        for (Runnable task : server.tasks) task.run();
        check(permits.availablePermits() == 4 && uploads.isEmpty(), "Cancelled completion leaked permits/state");
        handler.close();
        System.out.println("PASS cancellation keeps four worker/completion permits until actual completion");
    }
    private static void ticktimes() throws Exception {
        Method start = method(CommandTicktimes.class, "startLogging", MinecraftServer.class, File.class);
        Path directory = Files.createTempDirectory("kisde-audit-ticks-"); Path first = directory.resolve("first.txt"), second = directory.resolve("second.txt");
        for (int cycle = 0; cycle < 10; cycle++) {
            start.invoke(null, null, first.toFile()); CommandTicktimes.record(100L + cycle); check(CommandTicktimes.stopLogging(), "First writer did not drain/stop");
            start.invoke(null, null, second.toFile()); CommandTicktimes.record(200L + cycle); check(CommandTicktimes.stopLogging(), "Second writer did not drain/stop");
        }
        List<String> a = Files.readAllLines(first), b = Files.readAllLines(second);
        for (int i = 0; i < 10; i++) { check(a.get(i).equals(Long.toString(100L+i)), "First log mixed/lost samples"); check(b.get(i).equals(Long.toString(200L+i)), "Second log mixed/lost samples"); }
        check(a.size() == 10 && b.size() == 10, "Extra logging samples");
        if (Files.exists(Paths.get("/dev/full"))) {
            start.invoke(null, null, new File("/dev/full")); CommandTicktimes.record(1); check(CommandTicktimes.stopLogging(), "Failed writer did not stop");
            check(field(CommandTicktimes.class, "writeThread").get(null) == null, "Failed writer retained state");
        }
        System.out.println("PASS ticktime restart ownership, drain, close and disk-write failure");
    }
    private static void config() throws Exception {
        Path directory = Files.createTempDirectory("kisde-audit-metrics-"); Path config = directory.resolve("config");
        Constructor<carpet.prometheus.PrometheusExtension> constructor = carpet.prometheus.PrometheusExtension.class.getDeclaredConstructor(Path.class); constructor.setAccessible(true);
        Field port = field(carpet.prometheus.PrometheusExtension.class, "port");
        check(port.getInt(constructor.newInstance(config)) == 1234 && Files.readAllLines(config).get(0).equals("port:1234"), "Default config changed");
        for (String invalid : new String[]{"", "{}", "port:no", "port:-1", "port:1", "port:65536", "port:2147483648"}) {
            Files.write(config, invalid.getBytes("UTF-8")); check(port.getInt(constructor.newInstance(config)) == 1234, "Malformed config did not fall back");
        }
        Files.write(config, "port:65535".getBytes("UTF-8")); check(port.getInt(constructor.newInstance(config)) == 65535, "Valid legacy config rejected");
        check(port.getInt(constructor.newInstance(directory)) == 1234, "Unreadable config crashed constructor");
        System.out.println("PASS default/valid/empty/malformed/out-of-range/unreadable metrics config");
    }
    private static ContainerRepair anvil(Player player, int priorCost) {
        ContainerRepair repair = new ContainerRepair(player.inventory, player.world, BlockPos.ORIGIN, player); repair.windowId = 7;
        ItemStack left = new ItemStack(Items.DIAMOND_SWORD); left.addEnchantment(Enchantments.SHARPNESS, 5); left.setRepairCost(priorCost);
        repair.getSlot(0).putStack(left); repair.getSlot(1).putStack(left.copy()); return repair;
    }
    private static void anvil() throws Exception {
        Player player = player(); CarpetSettings.disableEnchantmentCap = true; CarpetSettings.disableAnvilXpLimit = true;
        CarpetServer.anvilSync = new AnvilSync();
        ContainerRepair repair = anvil(player, 20000);
        check(repair.maximumCost == 40006 && repair.getSlot(2).getStack().isEmpty() && player.messages == 1, "Vanilla-client high cost not safely rejected");
        repair.updateRepairOutput(); check(player.messages == 1, "Unsupported cost warning repeated");
        player.openContainer = repair; CarpetServer.anvilSync.register(AnvilSync.CHANNEL, player); CarpetServer.anvilSync.tick();
        check(!repair.getSlot(2).getStack().isEmpty(), "Capable client high cost rejected");
        List<Packet<?>> packets = ((Connection)player.connection).packets;
        SPacketCustomPayload packet = (SPacketCustomPayload)packets.get(packets.size()-1); PacketBuffer data = (PacketBuffer)field(SPacketCustomPayload.class, "data").get(packet);
        check(data.readableBytes() == 9 && data.readInt() == 7 && data.readInt() == 40006 && data.readBoolean(), "Full cost/window/rule packet truncated"); data.release();
        int count = packets.size(); CarpetServer.anvilSync.tick(); check(packets.size() == count, "Unchanged anvil resent state");
        player.experienceLevel = 40005; check(!repair.getSlot(2).canTakeStack(player), "Capability bypassed XP check");
        player.experienceLevel = 40016; check(repair.getSlot(2).canTakeStack(player), "Full XP rejected");
        repair.getSlot(2).onTake(player, repair.getSlot(2).getStack().copy());
        check(player.experienceLevel == 10 && repair.getSlot(0).getStack().isEmpty() && repair.getSlot(1).getStack().isEmpty(), "Full XP/inputs not charged");
        repair = anvil(player, 20000); player.openContainer = repair;
        CarpetSettings.disableAnvilXpLimit = false; CarpetServer.anvilSync.tick(); check(repair.getSlot(2).getStack().isEmpty(), "Open anvil did not reflect rule revocation");
        CarpetSettings.disableAnvilXpLimit = true; CarpetServer.anvilSync.tick(); check(!repair.getSlot(2).getStack().isEmpty(), "Open anvil did not reflect re-enable");
        CarpetServer.anvilSync.unregister(AnvilSync.CHANNEL, player); check(repair.getSlot(2).getStack().isEmpty() && !AnvilSync.supports(player), "Removed capability still permits unsupported output");
        player.openContainer = player.inventoryContainer; CarpetServer.anvilSync.tick();
        CarpetSettings.disableEnchantmentCap = false; CarpetSettings.disableAnvilXpLimit = false;
        check(!anvil(player, 0).getSlot(2).getStack().isEmpty(), "Default normal-cost behavior changed");
        System.out.println("PASS full-width anvil cost, capability fallback, XP, rule updates and cleanup");
    }
    private static void followupBugs() throws Exception {
        Player player = player();
        CarpetSettings.disableEnchantmentCap = true; CarpetSettings.disableAnvilXpLimit = false;
        ContainerRepair repair = anvil(player, Integer.MAX_VALUE); player.experienceLevel = 4;
        check(repair.maximumCost == Integer.MAX_VALUE && repair.getSlot(2).getStack().isEmpty() && !repair.getSlot(2).canTakeStack(player), "Rule revocation allowed overflowing cost");
        CarpetSettings.disableAnvilXpLimit = true; CarpetServer.anvilSync = new AnvilSync();
        player.openContainer = repair; CarpetServer.anvilSync.register(AnvilSync.CHANNEL, player);
        check(repair.maximumCost == Integer.MAX_VALUE && !repair.getSlot(2).getStack().isEmpty(), "Enabled saturated cost changed");
        player.experienceLevel = Integer.MAX_VALUE; check(repair.getSlot(2).canTakeStack(player), "Saturated affordable result rejected");
        check(repair.getSlot(2).getStack().getRepairCost() == Integer.MAX_VALUE, "Next prior cost overflowed");
        CarpetSettings.disableAnvilXpLimit = false; repair.updateRepairOutput();
        check(repair.getSlot(2).getStack().isEmpty(), "Cap toggle did not reject saturated item");
        CarpetSettings.disableEnchantmentCap = false;
        check(!anvil(player, 0).getSlot(2).getStack().isEmpty(), "Ordinary anvil changed");

        Server server = allocate(Server.class); server.tasks = new ArrayList<>();
        World world = (World)player.world; server.worlds = new WorldServer[]{world}; world.state = Blocks.STONE.getDefaultState();
        Path directory = Files.createTempDirectory("kisde-structure-save-");
        world.templates = new net.minecraft.world.gen.structure.template.TemplateManager(directory.toString(), null);
        CommandStructure command = new CommandStructure();
        command.execute(server, server, new String[]{"save", "audit/nested", "0", "1", "0", "0", "1", "0"});
        Path saved = directory.resolve("audit/nested.nbt"); check(Files.isRegularFile(saved), "Nested save missing");
        try (InputStream stream = Files.newInputStream(saved)) {
            NBTTagCompound data = CompressedStreamTools.readCompressed(stream);
            check(data.getTagList("size", 3).getIntAt(0) == 1 && data.getTagList("blocks", 10).tagCount() == 1, "Saved block layout changed");
        }
        Path blocked = directory.resolve("blocked"); Files.write(blocked, new byte[]{1});
        world.templates = new net.minecraft.world.gen.structure.template.TemplateManager(blocked.toString(), null);
        try {
            command.execute(server, server, new String[]{"save", "fixture", "0", "1", "0", "0", "1", "0"});
            throw new AssertionError("Failed save reported success");
        } catch (CommandException expected) { check(expected.getMessage().startsWith("Unable to save structure"), "Wrong save failure"); }

        carpet.network.PluginChannelManager manager = new carpet.network.PluginChannelManager(server); final int[] registrations = {0};
        manager.register(new carpet.network.PluginChannelHandler() {
            public String[] getChannels() { return new String[]{"bc:one", "bc:two"}; }
            public boolean register(String channel, EntityPlayerMP actor) { registrations[0]++; return true; }
            public void unregister(String channel, EntityPlayerMP actor) { }
            public void onCustomPayload(net.minecraft.network.play.client.CPacketCustomPayload packet, EntityPlayerMP actor) { }
        });
        Method register = method(carpet.network.PluginChannelManager.class, "processRegister", EntityPlayerMP.class, PacketBuffer.class);
        Method unregister = method(carpet.network.PluginChannelManager.class, "processUnregister", EntityPlayerMP.class, PacketBuffer.class);
        String[] names = new String[1000]; Arrays.fill(names, "bc:one");
        for (String channels : new String[]{String.join("\0", names), "bc:one", "bc:two"}) {
            PacketBuffer data = new PacketBuffer(io.netty.buffer.Unpooled.wrappedBuffer(channels.getBytes("UTF-8")));
            try { register.invoke(manager, player, data); } finally { data.release(); }
        }
        check(registrations[0] == 2 && manager.tracker.getChannels(player).size() == 2, "Duplicate register invoked handler");
        PacketBuffer data = new PacketBuffer(io.netty.buffer.Unpooled.wrappedBuffer("bc:one".getBytes("UTF-8")));
        try { unregister.invoke(manager, player, data); data.readerIndex(0); register.invoke(manager, player, data); } finally { data.release(); }
        check(registrations[0] == 3 && manager.tracker.isRegistered(player, "bc:one"), "Re-registration stopped working");

        Method heading = method(carpet.helpers.RedstoneWireTurbo.class, "computeHeading", int.class, int.class);
        int[][] diagonals = {{-1,-1,0,3}, {1,-1,0,1}, {-1,1,2,3}, {1,1,2,1}};
        for (int[] direction : diagonals) {
            Set<Integer> observed = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                int result = (Integer)heading.invoke(null, direction[0], direction[1]);
                check(result == direction[2] || result == direction[3], "Diagonal chose invalid direction"); observed.add(result);
            }
            check(observed.size() == 2, "Diagonal choice has fixed bias");
        }
        for (int[] direction : new int[][]{{0,-1,0},{1,0,1},{0,1,2},{-1,0,3}}) {
            check((Integer)heading.invoke(null,direction[0],direction[1]) == direction[2], "Cardinal heading changed");
        }
        System.out.println("PASS revoked anvil overflow, nested/failed structure saves, duplicate/re-registration and diagonal redstone choices");
    }
    private static final class MiningManager extends PlayerInteractionManager {
        int starts, stops;
        BlockPos position;
        EnumFacing facing;
        boolean instant, fail;
        MiningManager(net.minecraft.world.World world) { super(world); }
        public void onBlockClicked(BlockPos pos, EnumFacing side) {
            starts++; position = pos; facing = side; instant = activateInstantMine;
            if (fail) throw new IllegalStateException("fixture");
        }
        public void blockRemoving(BlockPos pos) { stops++; position = pos; }
    }
    private static PacketBuffer miningPacket(int bodyLength, boolean start, BlockPos pos, int facing, boolean instant) {
        PacketBuffer data = new PacketBuffer(io.netty.buffer.Unpooled.buffer());
        data.writeVarInt(bodyLength); data.writeBoolean(start); data.writeBlockPos(pos); data.writeByte(facing); data.writeBoolean(instant);
        if (bodyLength == 12) data.writeBoolean(true);
        return data;
    }
    private static void legacyMining() throws Exception {
        Player player = player(); Server server = allocate(Server.class);
        field(EntityPlayerMP.class, "server").set(player, server);
        MiningManager manager = new MiningManager(player.world);
        field(EntityPlayerMP.class, "interactionManager").set(player, manager);
        player.posX = .5; player.posY = 1; player.posZ = .5;
        World world = (World)player.world; BlockPos target = new BlockPos(0, 0, 1);
        boolean previous = PlayerInteractionManager.activateInstantMine;
        boolean careful = CarpetSettings.carefulBreak; CarpetSettings.carefulBreak = false;
        try {
            PlayerInteractionManager.activateInstantMine = false;
            for (int body : new int[]{12, 11}) {
                PacketBuffer data = miningPacket(body, true, target, EnumFacing.UP.getIndex(), true);
                try { carpet.carpetclient.CarpetClientMining.handle(player, data); } finally { data.release(); }
                check(manager.starts == (body == 12 ? 1 : 2) && manager.position.equals(target) && manager.facing == EnumFacing.UP && manager.instant, "Valid framed Carpet mining start rejected/misdecoded");
                check(!PlayerInteractionManager.activateInstantMine && !CarpetSettings.carefulBreak, "Client preferences leaked server state");
                data = miningPacket(body, false, target, EnumFacing.UP.getIndex(), false);
                try { carpet.carpetclient.CarpetClientMining.handle(player, data); } finally { data.release(); }
            }
            check(manager.stops == 2, "Valid framed Carpet mining stop rejected");
            for (int mode = 0; mode < 6; mode++) {
                PacketBuffer data = miningPacket(12, true, mode == 1 ? new BlockPos(100, 0, 0) : mode == 2 ? new BlockPos(0, -1, 0) : target, mode == 3 ? 6 : 1, true);
                server.protectedBlocks = mode == 4; world.loaded = mode != 5;
                if (mode == 0) data.setByte(0, 11);
                try { carpet.carpetclient.CarpetClientMining.handle(player, data); } finally { data.release(); }
                check(manager.starts == 2, "Invalid/protected mining accepted");
            }
            check(!((Connection)player.connection).packets.isEmpty(), "Protected block did not resync");
            server.protectedBlocks = false; world.loaded = true; manager.fail = true;
            PacketBuffer data = miningPacket(12, true, target, 1, true);
            try {
                try { carpet.carpetclient.CarpetClientMining.handle(player, data); throw new AssertionError("Expected mining callback failure"); }
                catch (IllegalStateException expected) { check(!PlayerInteractionManager.activateInstantMine, "Callback failure leaked instant mining"); }
            } finally { data.release(); }
        } finally { PlayerInteractionManager.activateInstantMine = previous; CarpetSettings.carefulBreak = careful; }
        System.out.println("PASS actual framed Carpet mining start/stop, malformed/reach/protection and callback cleanup");
    }
    private static void lifecycleAndCi() throws Exception {
        Player player = player(); String previous = CarpetSettings.commandLifeTime;
        CarpetSettings.commandLifeTime = "0"; check(LifeTimeTracker.permitted(player), "Lifetime zero denies ordinary player");
        CarpetSettings.commandLifeTime = "false"; check(!LifeTimeTracker.permitted(player), "Lifetime disabled allowed");
        CarpetSettings.commandLifeTime = previous;
        check(!CarpetSettings.hasRule("updateCarpetAll"), "Removed updater rule retained");
        check(!Files.exists(Paths.get("carpetmodSrc/carpet/helpers/CarpetUpdater.java")) && !Files.exists(Paths.get("carpetmodSrc/carpet/commands/CommandUpdateCarpet.java")), "Removed updater files retained");
        for (String file : new String[]{".github/workflows/deploy.yaml", ".github/workflows/pull-requests.yaml"}) {
            String workflow = new String(Files.readAllBytes(Paths.get(file)), "UTF-8");
            check(workflow.contains("build/distributions/Kisde-Carpet_dev.zip") && !workflow.contains("build/distributions/Carpetmod_dev.zip"), "CI wrong artifact");
            check(workflow.contains("auditFixCheck essentialRuleCheck securityCheck reliabilityCheck schematicPackingCheck schematicInventoryRuleCheck sharedSchematicStoreCheck kisdeRuleCheck vastechRuleCheck createRelease"), "CI missing checks");
        }
        System.out.println("PASS lifetime zero/default, updater removal and CI check/artifact gates");
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register(); structure(); mining(); sharing(); ticktimes(); config(); anvil(); followupBugs(); legacyMining(); lifecycleAndCi();
        System.out.println("Audit fix checks passed");
    }
}
