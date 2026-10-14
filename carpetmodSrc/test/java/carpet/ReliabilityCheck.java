package carpet;

import carpet.commands.CommandInventory;
import carpet.helpers.InventoryCustom;
import carpet.helpers.StatHelper;
import carpet.prometheus.MetricUpdater;
import carpet.prometheus.PrometheusExtension;
import carpet.prometheus.metrics.AbstractMetric;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.scoreboard.*;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.stats.StatBase;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.*;
import sun.misc.Unsafe;

public final class ReliabilityCheck {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method method = type.getDeclaredMethod(name, args); method.setAccessible(true); return method;
    }
    private static Map<UUID, InventoryCustom> sessions() throws Exception {
        Field field = CommandInventory.class.getDeclaredField("offlineEdits"); field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID, InventoryCustom> result = (Map<UUID, InventoryCustom>) field.get(null); return result;
    }

    private static final class Player extends EntityPlayerMP {
        InventoryEnderChest ender;
        NBTTagCompound metadata;
        Runnable closing;
        ItemStack dropped;
        String message;
        Player() { super(null, null, new GameProfile(UUID.randomUUID(), "Fixture"), null); }
        @Override public InventoryEnderChest getInventoryEnderChest() { return ender; }
        @Override public void closeScreen() { if (closing != null) closing.run(); }
        @Override public void sendMessage(net.minecraft.util.text.ITextComponent text) { message = text.getUnformattedText(); }
        @Override public EntityItem dropItem(ItemStack stack, boolean around, boolean trace) { dropped = stack.copy(); return null; }
        @Override public void readFromNBT(NBTTagCompound data) {
            metadata = data.copy(); inventory.readFromNBT(data.getTagList("Inventory", 10)); ender.loadInventoryFromNBT(data.getTagList("EnderItems", 10));
        }
        @Override public NBTTagCompound writeToNBT(NBTTagCompound data) {
            NBTTagCompound result = metadata.copy(); result.setTag("Inventory", inventory.writeToNBT(new NBTTagList())); result.setTag("EnderItems", ender.saveInventoryToNBT()); return result;
        }
    }
    private static Player player() throws Exception {
        Player player = allocate(Player.class); player.setUniqueId(UUID.randomUUID());
        player.inventory = new InventoryPlayer(player); player.capabilities = new PlayerCapabilities();
        player.ender = new InventoryEnderChest(); player.metadata = new NBTTagCompound();
        player.inventoryContainer = new ContainerPlayer(player.inventory, false, player); player.openContainer = player.inventoryContainer;
        return player;
    }
    private static final class Players extends DedicatedPlayerList {
        Map<UUID, EntityPlayerMP> online;
        NBTTagCompound saved;
        Players() { super(null); }
        @Override public EntityPlayerMP getPlayerByUUID(UUID uuid) { return online.get(uuid); }
        @Override public NBTTagCompound readPlayerDataFromFile(EntityPlayerMP player) { if (saved != null) player.readFromNBT(saved); return saved; }
    }
    private static final class Save extends SaveHandler {
        NBTTagCompound saved;
        Save() { super(null, "fixture", false, null); }
        @Override public IPlayerFileData getPlayerNBTManager() { return this; }
        @Override public void writePlayerData(EntityPlayer player) { saved = player.writeToNBT(new NBTTagCompound()); }
    }
    private static final class World extends WorldServer {
        Save save;
        World() { super(null, null, null, 0, null); }
        @Override public ISaveHandler getSaveHandler() { return save; }
    }
    private static final class Server extends DedicatedServer {
        Players players;
        World world;
        List<Runnable> tasks;
        boolean running;
        Server() { super(null, null, null, null, null, null); }
        @Override public DedicatedPlayerList getPlayerList() { return players; }
        @Override public WorldServer getWorld(int dimension) { return world; }
        @Override public boolean isServerRunning() { return running; }
        @Override public ListenableFuture<Object> addScheduledTask(Runnable task) { tasks.add(task); return Futures.immediateFuture(null); }
    }
    private static Server server() throws Exception {
        Server server = allocate(Server.class); server.players = allocate(Players.class); server.players.online = new HashMap<>();
        server.world = allocate(World.class); server.world.save = allocate(Save.class); server.tasks = new ArrayList<>(); server.running = true; return server;
    }

    private static InventoryCustom inventory(Player owner, Player editor, boolean ender, InventoryCustom.InventoryClosedListener close) {
        InventoryCustom result = new InventoryCustom("Fixture", true, 27,
                (view, data) -> { for (int i = 0; i < data.tagCount(); i++) { NBTTagCompound item = data.getCompoundTagAt(i); view.setInventorySlotContents(item.getByte("Slot") & 255, new ItemStack(item)); } },
                (view, slot, stack) -> { if (ender) owner.ender.setInventorySlotContents(slot, stack); else owner.inventory.setInventorySlotContents(slot, stack); },
                close, owner, editor);
        result.loadInventory(ender ? owner.ender.saveInventoryToNBT() : owner.inventory.writeToNBT(new NBTTagList())); return result;
    }
    private static void inventoryMutations() throws Exception {
        Player owner = player(), editor = player(); owner.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 10));
        InventoryCustom view = inventory(owner, editor, false, null);
        check(view.decrStackSize(0, 3).getCount() == 3 && owner.inventory.getStackInSlot(0).getCount() == 7, "Split removal did not synchronize");
        view.getStackInSlot(0).shrink(2); view.markDirty();
        check(owner.inventory.getStackInSlot(0).getCount() == 5, "In-place change did not synchronize");
        check(view.removeStackFromSlot(0).getCount() == 5 && owner.inventory.getStackInSlot(0).isEmpty(), "Whole-stack removal duplicated items");
        view.setInventorySlotContents(1, new ItemStack(Items.GOLD_INGOT, 5)); view.clear();
        check(owner.inventory.getStackInSlot(1).isEmpty(), "Clear did not synchronize");
        owner.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 10)); view = inventory(owner, editor, false, null);
        ContainerChest chest = new ContainerChest(editor.inventory, view, editor);
        chest.slotClick(0, 0, ClickType.PICKUP, editor);
        check(owner.inventory.getStackInSlot(0).isEmpty() && editor.inventory.getItemStack().getCount() == 10, "Pickup duplicated source");
        chest.slotClick(0, 1, ClickType.PICKUP, editor);
        check(owner.inventory.getStackInSlot(0).getCount() == 1 && editor.inventory.getItemStack().getCount() == 9, "Right-click placement lost items");
        editor.inventory.setItemStack(ItemStack.EMPTY); view.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 8));
        chest.slotClick(0, 0, ClickType.QUICK_MOVE, editor);
        check(owner.inventory.getStackInSlot(0).isEmpty(), "Shift-click retained source");
        int count = 0; for (ItemStack stack : editor.inventory.mainInventory) count += stack.getCount();
        check(count == 8, "Shift-click changed quantity");
        view.setInventorySlotContents(0, new ItemStack(Items.GOLD_INGOT, 4)); chest.slotClick(0, 1, ClickType.THROW, editor);
        check(owner.inventory.getStackInSlot(0).isEmpty() && editor.dropped.getCount() == 4, "Drop duplicated source");
        Player secondEditor = player(); owner.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 10));
        InventoryCustom firstView = inventory(owner, editor, false, null), secondView = inventory(owner, secondEditor, false, null);
        firstView.setRefreshSource(() -> owner.inventory.writeToNBT(new NBTTagList()));
        secondView.setRefreshSource(() -> owner.inventory.writeToNBT(new NBTTagList()));
        ContainerChest firstChest = new ContainerChest(editor.inventory, firstView, editor), secondChest = new ContainerChest(secondEditor.inventory, secondView, secondEditor);
        firstChest.slotClick(0, 0, ClickType.PICKUP, editor);
        secondChest.slotClick(0, 0, ClickType.PICKUP, secondEditor);
        check(editor.inventory.getItemStack().getCount() == 10 && secondEditor.inventory.getItemStack().isEmpty(), "Concurrent views duplicated a stale stack");
        editor.inventory.setItemStack(ItemStack.EMPTY); owner.inventory.setInventorySlotContents(0, new ItemStack(Items.GOLD_INGOT, 3));
        firstChest.slotClick(0, 0, ClickType.SWAP, editor);
        check(editor.inventory.getStackInSlot(0).getCount() == 3 && owner.inventory.getStackInSlot(0).isEmpty(), "Hotbar swap failed to synchronize");
    }
    private static void offlineSessions() throws Exception {
        Server server = server(); Player owner = player(), editor = player();
        owner.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 10));
        NBTTagCompound current = owner.writeToNBT(new NBTTagCompound()); current.setString("Unrelated", "new-value"); server.players.saved = current;
        Method finish = method(CommandInventory.class, "finishOfflineEdit", net.minecraft.server.MinecraftServer.class, InventoryCustom.class, boolean.class);
        InventoryCustom view = inventory(owner, editor, false, null); view.decrStackSize(0, 4); sessions().put(owner.getUniqueID(), view);
        finish.invoke(null, server, view, false);
        check(server.world.save.saved.getString("Unrelated").equals("new-value"), "Offline save overwrote unrelated data");
        check(owner.inventory.getStackInSlot(0).getCount() == 6 && sessions().isEmpty(), "Offline commit lost edits or session");
        NBTTagCompound beforeFailure = server.world.save.saved; server.players.saved = null;
        view = inventory(owner, editor, false, null); sessions().put(owner.getUniqueID(), view); finish.invoke(null, server, view, false);
        check(server.world.save.saved == beforeFailure && editor.message.contains("not saved"), "Failed reload overwrote player data without reporting failure");
        Player joined = player(); joined.setUniqueId(owner.getUniqueID()); joined.metadata.setString("Unrelated", "joined-value"); server.players.online.put(joined.getUniqueID(), joined);
        owner.ender.setInventorySlotContents(0, new ItemStack(Items.GOLD_INGOT, 7));
        view = inventory(owner, editor, true, null); sessions().put(owner.getUniqueID(), view); InventoryCustom active = view;
        editor.closing = () -> { try { finish.invoke(null, server, active, true); } catch (Exception e) { throw new AssertionError(e); } };
        CommandInventory.playerConnected(joined);
        check(joined.ender.getStackInSlot(0).getCount() == 7 && joined.metadata.getString("Unrelated").equals("joined-value"), "Join merge overwrote current player");
        check(sessions().isEmpty(), "Login retained offline edit");
        sessions().put(owner.getUniqueID(), view);
        try {
            method(CommandInventory.class, "openOfflineEdit", EntityPlayerMP.class, InventoryCustom.class).invoke(null, editor, view);
            throw new AssertionError("Concurrent offline edit accepted");
        } catch (InvocationTargetException expected) { check(expected.getCause() instanceof net.minecraft.command.CommandException, "Wrong concurrent edit failure"); }
        finally { sessions().clear(); }
        Field viewsField = CommandInventory.class.getDeclaredField("onlineEdits"); viewsField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID, Set<InventoryCustom>> views = (Map<UUID, Set<InventoryCustom>>) viewsField.get(null);
        InventoryCustom onlineView = inventory(joined, editor, true, inventory -> {
            try { method(CommandInventory.class, "removeOnlineEdit", InventoryCustom.class).invoke(null, inventory); }
            catch (Exception e) { throw new AssertionError(e); }
        });
        views.put(joined.getUniqueID(), new LinkedHashSet<>(Collections.singletonList(onlineView)));
        editor.closing = () -> onlineView.closeInventory(editor);
        CommandInventory.playerDisconnected(joined);
        check(views.isEmpty(), "Owner disconnect retained online editor");
    }
    private static void metrics() throws Exception {
        Server server = server(); PrometheusExtension extension = allocate(PrometheusExtension.class); PrometheusExtension.server = server;
        int[] updates = {0}; MetricUpdater updater = new MetricUpdater(extension);
        updater.addMetric(new AbstractMetric("fixture", "fixture") { @Override public void update(PrometheusExtension ignored) { updates[0]++; } });
        updater.run(); updater.run(); check(server.tasks.size() == 1 && updates[0] == 0, "Metrics ran off-thread or queued duplicates");
        server.tasks.remove(0).run(); check(updates[0] == 1, "Scheduled metrics did not run");
        updater.run(); updater.cancel(); server.tasks.remove(0).run(); check(updates[0] == 1, "Cancelled metrics still ran");
        PrometheusExtension.server = null;
    }
    private static void scoreboard() throws Exception {
        Server server = server(); Scoreboard board = new Scoreboard(); StatBase stat = new StatBase("fixture", new TextComponentString("Fixture"));
        ScoreObjective objective = board.addScoreObjective("st.fixture", new ScoreCriteriaStat(stat));
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(); Map<UUID, Integer> values = new HashMap<>(); Map<UUID, String> names = new HashMap<>();
        values.put(first, 2500); values.put(second, 1199); names.put(first, "Alice"); names.put(second, "Bob");
        Method apply = method(StatHelper.class, "applyInitialScores", Scoreboard.class, net.minecraft.server.MinecraftServer.class, ScoreObjective.class, net.minecraft.scoreboard.IScoreCriteria.class, int.class, Map.class, Map.class);
        apply.invoke(null, board, server, objective, objective.getCriteria(), 1200, values, names);
        check(board.getOrCreateScore("Alice", objective).getScorePoints() == 2 && board.getOrCreateScore("Total", objective).getScorePoints() == 2, "Divider or total changed");
        board.removeObjective(objective); ScoreObjective replacement = board.addScoreObjective("st.fixture", new ScoreCriteriaStat(stat));
        apply.invoke(null, board, server, objective, objective.getCriteria(), 1200, values, names);
        check(board.getSortedScores(replacement).isEmpty(), "Stale worker changed replacement objective");
        server.running = false; apply.invoke(null, board, server, replacement, replacement.getCriteria(), 1, values, names);
        check(board.getSortedScores(replacement).isEmpty(), "Stopped server accepted scores");
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register(); inventoryMutations(); offlineSessions(); metrics(); scoreboard();
        System.out.println("Inventory mutations/clicks, offline save/login isolation, metric scheduling/cancellation and scoreboard guards passed");
    }
}
