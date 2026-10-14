package carpet;

import carpet.carpetclient.CarpetClientMarkers;
import carpet.carpetclient.CarpetClientMining;
import carpet.commands.CommandLog;
import carpet.commands.CommandTicktimes;
import carpet.logging.LogHandler;
import carpet.pubsub.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import sun.misc.Unsafe;

public final class SecurityCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object field(Object object, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static final class Player extends EntityPlayerMP {
        String name;
        boolean operator;
        Player() { super(null, null, new GameProfile(UUID.randomUUID(), "Fixture"), null); }
        @Override public String getName() { return name; }
        @Override public boolean canUseCommand(int level, String command) { return operator; }
    }

    private static Player player(String name, boolean operator) throws Exception {
        Field singleton = Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Player player = (Player) ((Unsafe) singleton.get(null)).allocateInstance(Player.class);
        player.name = name;
        player.operator = operator;
        return player;
    }

    private static void denied(CommandLog command, ICommandSender sender, String... args) throws Exception {
        try {
            command.execute(null, sender, args);
            throw new AssertionError("Unauthorized logger change accepted");
        } catch (CommandException expected) {
            check("commands.generic.permission".equals(expected.getMessage()), "Wrong permission failure");
        }
    }

    private static void permissions() throws Exception {
        boolean old = CarpetSettings.commandLog;
        CarpetSettings.commandLog = true;
        try {
            Player ordinary = player("Alice", false), operator = player("Admin", true);
            CommandLog command = new CommandLog();
            denied(command, ordinary, "tnt", "brief", "Admin", "command", "say", "fixture");
            denied(command, ordinary, "reset", "Admin");
            denied(command, ordinary, "setDefault", "tnt", "brief", "command", "say", "fixture");
            denied(command, ordinary, "removeDefault", "tnt");
            Method manage = method(CommandLog.class, "requireManagePermission", ICommandSender.class, String.class);
            manage.invoke(command, ordinary, "alice");
            manage.invoke(command, operator, "Alice");
            manage.invoke(command, operator, null);
            CommandTicktimes ticktimes = new CommandTicktimes();
            check(!ticktimes.checkPermission(null, ordinary) && ticktimes.checkPermission(null, operator), "Tick logging permission missing");
            try {
                ticktimes.execute(null, ordinary, new String[] {"start", "../../security-fixture"});
                throw new AssertionError("Unauthorized filesystem logging accepted");
            } catch (CommandException expected) {
                check("commands.generic.permission".equals(expected.getMessage()), "Tick logging reached filesystem");
            }
            check(LogHandler.createHandler("unknown") == null, "Unknown handler did not fail safely");
            check(LogHandler.createHandler("command", "say", "fixture") != null, "Valid command handler removed");
        } finally {
            CarpetSettings.commandLog = old;
        }
    }

    private static void nbtLengths() throws Exception {
        for (Class<?> type : new Class<?>[] {NBTTagByteArray.class, NBTTagIntArray.class, NBTTagLongArray.class}) {
            Method read = method(type, "read", DataInput.class, int.class, NBTSizeTracker.class);
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            for (int length : new int[] {-1, Integer.MIN_VALUE, 1 << 29, Integer.MAX_VALUE}) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                new DataOutputStream(bytes).writeInt(length);
                try {
                    read.invoke(constructor.newInstance(), new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 0, new NBTSizeTracker(2097152L));
                    throw new AssertionError("Invalid NBT array accepted");
                } catch (InvocationTargetException expected) {
                    Throwable cause = expected.getCause();
                    if (length < 0) check(cause instanceof IOException && !(cause instanceof EOFException), "Negative NBT length reached allocation");
                    else check(cause instanceof RuntimeException && cause.getMessage().contains("too big"), "Oversized NBT length reached allocation or input");
                }
            }
        }
        NBTTagCompound original = new NBTTagCompound();
        original.setByteArray("bytes", new byte[] {0, 1, -1});
        original.setIntArray("ints", new int[] {0, Integer.MAX_VALUE, Integer.MIN_VALUE});
        original.setTag("longs", new NBTTagLongArray(new long[] {0, Long.MAX_VALUE, Long.MIN_VALUE}));
        original.setByteArray("empty", new byte[0]);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        CompressedStreamTools.write(original, new DataOutputStream(encoded));
        NBTTagCompound copy = CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(encoded.toByteArray())), new NBTSizeTracker(2097152L));
        check(original.equals(copy), "Valid NBT array roundtrip changed");
    }

    private static void subscriptions() throws Exception {
        PubSubManager manager = new PubSubManager();
        PubSubNode parent = manager.getOrCreateNode("fixture"), child = manager.getOrCreateNode("fixture.value");
        PubSubMessenger messenger = new PubSubMessenger(manager);
        Player player = player("Alice", false);
        for (int i = 0; i < 100; i++) messenger.subscribe(player, Collections.singletonList(child.fullName));
        check(((Set<?>) field(child, PubSubNode.class, "subscribers")).size() == 1, "Duplicate subscriber retained");
        check((Integer) field(child, PubSubNode.class, "totalSubscriberCount") == 1, "Duplicate changed counters");
        Method unsubscribe = method(PubSubMessenger.class, "unsubscribe", EntityPlayerMP.class, Collection.class);
        for (int i = 0; i < 100; i++) unsubscribe.invoke(messenger, player, Collections.singletonList(child.fullName));
        check((Integer) field(child, PubSubNode.class, "totalSubscriberCount") == 0, "Repeated unsubscribe changed counters");
        messenger.subscribe(player, Collections.singletonList(child.fullName));
        check(((Set<?>) field(child, PubSubNode.class, "subscribers")).size() == 1, "Resubscribe blocked by stale entry");
        messenger.unregister(PubSubMessenger.CHANNEL_NAME, player);
        check(((Set<?>) field(child, PubSubNode.class, "subscribers")).isEmpty(), "Disconnect retained subscriber");
        check(((Map<?, ?>) field(messenger, PubSubMessenger.class, "subscriptions")).isEmpty(), "Disconnect retained player");
        messenger.subscribe(player, Arrays.asList(parent.fullName, child.fullName));
        check(((Set<?>) field(parent, PubSubNode.class, "subscribers")).size() == 1 && ((Set<?>) field(child, PubSubNode.class, "subscribers")).isEmpty(), "Parent-child batch deduplication changed");
        messenger.unregister(PubSubMessenger.CHANNEL_NAME, player);
        PubSubSubscriber callback = (node, value) -> {};
        Method add = method(PubSubNode.class, "subscribe", PubSubSubscriber.class), remove = method(PubSubNode.class, "unsubscribe", PubSubSubscriber.class);
        add.invoke(child, callback); add.invoke(child, callback);
        check((Integer) field(child, PubSubNode.class, "totalSubscriberCount") == 1, "Node add is not idempotent");
        remove.invoke(child, callback); remove.invoke(child, callback);
        check((Integer) field(manager.ROOT, PubSubNode.class, "totalSubscriberCount") == 0, "Node remove is not idempotent");
        Method names = method(PubSubMessenger.class, "readNames", PacketBuffer.class);
        for (int count : new int[] {-1, Integer.MAX_VALUE, 100}) {
            PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
            try {
                buffer.writeVarInt(count);
                try { names.invoke(null, buffer); throw new AssertionError("Invalid name count accepted"); }
                catch (InvocationTargetException expected) { check(expected.getCause() instanceof IllegalArgumentException, "Name count reached allocation"); }
            } finally { buffer.release(); }
        }
        PacketBuffer valid = new PacketBuffer(Unpooled.buffer());
        try {
            valid.writeVarInt(2); valid.writeString("fixture"); valid.writeString("fixture.value");
            check(names.invoke(null, valid).equals(Arrays.asList("fixture", "fixture.value")), "Valid name parsing changed");
        } finally { valid.release(); }
    }

    private static void villageMembership() throws Exception {
        @SuppressWarnings("unchecked") Collection<EntityPlayerMP> members = (Collection<EntityPlayerMP>) field(null, CarpetClientMarkers.class, "playersVillageMarkers");
        Player player = player("Alice", false);
        members.add(player);
        try {
            for (int i = 0; i < 100; i++) {
                PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
                try { buffer.writeBoolean(true); CarpetClientMarkers.registerVillagerMarkers(player, buffer); }
                finally { buffer.release(); }
            }
            check(members.size() == 1, "Duplicate village member retained");
            CarpetClientMarkers.unregisterPlayerVillageMarkers(player);
            check(members.isEmpty(), "Village member survived disconnect");
        } finally { members.remove(player); }
    }

    private static void mining() throws Exception {
        Method reach = method(CarpetClientMining.class, "withinReach", double.class, double.class, double.class, BlockPos.class, int.class);
        check((Boolean) reach.invoke(null, 0.5, 63.0, 0.5, new BlockPos(6, 64, 0), 256), "Exact vanilla reach rejected");
        check(!(Boolean) reach.invoke(null, 0.5, 63.0, 0.5, new BlockPos(7, 64, 0), 256), "Distant mining accepted");
        check(!(Boolean) reach.invoke(null, 0.5, -2.0, 0.5, new BlockPos(0, -1, 0), 256), "Negative height accepted");
        check(!(Boolean) reach.invoke(null, 0.5, 255.0, 0.5, new BlockPos(0, 256, 0), 256), "Build limit accepted");
        check((Boolean) reach.invoke(null, -0.5, 63.0, -0.5, new BlockPos(-1, 64, -1), 256), "Negative-coordinate mining rejected");
        for (int length : new int[] {0, 11, 13, 14}) {
            PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
            try { payload.writeZero(length); CarpetClientMining.handle(null, payload); }
            finally { payload.release(); }
        }
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        permissions();
        nbtLengths();
        subscriptions();
        villageMembership();
        mining();
        System.out.println("Security permissions, NBT allocation bounds, subscriptions, village cleanup and mining boundary checks passed");
    }
}
