package carpet;

import carpet.commands.*;
import carpet.helpers.CarefulDrop;
import carpet.logging.Logger;
import carpet.logging.LoggerRegistry;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.command.*;
import net.minecraft.command.server.CommandOp;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.*;
import sun.misc.Unsafe;

public final class EssentialRuleCheck {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((Unsafe)field.get(null)).allocateInstance(type));
    }
    private static final class Player extends EntityPlayerMP {
        int permission;
        boolean sneak;
        ItemStack dropped;
        GameType mode;
        Player() { super(null, null, new GameProfile(UUID.randomUUID(), "Fixture"), null); }
        public String getName() { return "Fixture"; }
        public boolean canUseCommand(int level, String name) { return permission > 0 && permission >= level; }
        public boolean isSneaking() { return sneak; }
        public void setGameType(GameType type) { mode = type; }
        public void sendMessage(ITextComponent text) { }
        public void playSound(net.minecraft.util.SoundEvent sound, float volume, float pitch) { }
        public void setCommandStat(net.minecraft.command.CommandResultStats.Type type, int value) { }
        public void addStat(net.minecraft.stats.StatBase stat, int value) { }
        public void dupeItem(int slot) { }
        public EntityItem dropItem(ItemStack stack, boolean around, boolean trace) { dropped = stack.copy(); stack.setCount(0); return null; }
        public void addExperienceLevel(int levels) { experienceLevel += levels; }
    }
    private static final class Connection extends net.minecraft.network.NetHandlerPlayServer {
        Connection() { super(null, null, null); }
        public void sendPacket(net.minecraft.network.Packet<?> packet) { }
    }
    private static final class Players extends DedicatedPlayerList {
        Player player;
        Players() { super(null); }
        public EntityPlayerMP getPlayerByUsername(String name) { return player; }
    }
    private static final class Server extends DedicatedServer {
        Players players;
        Server() { super(null, null, null, null, null, null); }
        public DedicatedPlayerList getPlayerList() { return players; }
    }
    private static Player player() throws Exception {
        Player player = allocate(Player.class);
        player.world = allocate(WorldServer.class);
        player.connection = allocate(Connection.class);
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities();
        player.inventoryContainer = new ContainerPlayer(player.inventory, false, player);
        player.openContainer = player.inventoryContainer;
        player.sneak = true;
        return player;
    }
    private static void denied(ICommand command, Player player) throws Exception {
        try { command.execute(null, player, new String[0]); throw new AssertionError("Unauthorized command executed"); }
        catch (CommandException ex) { check(ex.getMessage().equals("commands.generic.permission"), "Wrong permission failure"); }
    }
    private static void commands() throws Exception {
        Player player = player();
        CommandHat hat = new CommandHat(); CommandMore more = new CommandMore();
        ICommand[] commands = {hat, more, new CommandGM("gmc", GameType.CREATIVE)};
        String[] rules = {"commandHat", "commandMore", "commandGM"};
        Method validator = CarpetSettings.class.getDeclaredMethod("validateCommandLifeTime", String.class); validator.setAccessible(true);
        for (int i = 0; i < commands.length; i++) {
            Field rule = CarpetSettings.class.getField(rules[i]);
            check(CarpetSettings.getDefault(rules[i]).equals("false"), "Enabled default");
            rule.set(null, "false"); denied(commands[i], player);
            for (String level : new String[] {"true", "ops", "0", "1", "2", "3", "4"}) {
                check((boolean)validator.invoke(null, level), "Valid permission rejected");
                rule.set(null, level);
                for (int permission = 0; permission <= 4; permission++) {
                    player.permission = permission;
                    int required = "true".equals(level) ? 0 : "ops".equals(level) ? 2 : Integer.parseInt(level);
                    check(commands[i].checkPermission(null, player) == (permission >= required), "Wrong command permission");
                }
            }
            check(!(boolean)validator.invoke(null, "5"), "Invalid permission accepted");
            rule.set(null, "true");
        }
        player.permission = 2; CarpetSettings.opPlayerNoCheat = true;
        denied(commands[2], player); CarpetSettings.opPlayerNoCheat = false;
        String[] names = {"gmc", "gms", "gma", "gmsp"};
        GameType[] types = {GameType.CREATIVE, GameType.SURVIVAL, GameType.ADVENTURE, GameType.SPECTATOR};
        for (int i = 0; i < names.length; i++) {
            new CommandGM(names[i], types[i]).execute(null, player, new String[0]);
            check(player.mode == types[i], "Wrong gamemode");
        }
        NBTTagCompound data = new NBTTagCompound(); data.setString("Fixture", "kept");
        ItemStack held = new ItemStack(Items.DIAMOND, 3); held.setTagCompound(data.copy());
        player.inventory.setInventorySlotContents(0, held);
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.setTagCompound(data.copy());
        player.inventory.setInventorySlotContents(39, helmet);
        hat.execute(null, player, new String[0]);
        check(held.getCount() == 2 && player.inventory.getStackInSlot(39).getCount() == 1, "Hat consumed wrong amount");
        check(player.inventory.getStackInSlot(1).getItem() == Items.DIAMOND_HELMET && data.equals(player.inventory.getStackInSlot(1).getTagCompound()), "Old helmet lost");
        check(data.equals(player.inventory.getStackInSlot(39).getTagCompound()), "Hat NBT lost");
        more.execute(null, player, new String[0]); check(held.getCount() == 64 && data.equals(held.getTagCompound()), "More changed NBT/count");
        player.inventory.setInventorySlotContents(0, ItemStack.EMPTY);
        player.inventory.setInventorySlotContents(40, new ItemStack(Items.ENDER_PEARL, 2));
        more.execute(null, player, new String[0]); check(player.inventory.getStackInSlot(40).getCount() == 16, "Offhand fallback missing");
        helmet = new ItemStack(Items.DIAMOND_HELMET); helmet.setTagCompound(data.copy());
        for (int i = 0; i < 36; i++) player.inventory.setInventorySlotContents(i, new ItemStack(net.minecraft.item.Item.getItemFromBlock(Blocks.STONE), 64));
        player.inventory.setInventorySlotContents(39, helmet.copy()); hat.execute(null, player, new String[0]);
        check(player.dropped != null && player.dropped.getItem() == Items.DIAMOND_HELMET, "Full inventory lost helmet");
        player.inventory.setInventorySlotContents(0, ItemStack.EMPTY); hat.execute(null, player, new String[0]);
        check(player.inventory.getStackInSlot(39).isEmpty(), "Empty hand did not remove hat");
        player.capabilities.isCreativeMode = true;
        player.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 4)); hat.execute(null, player, new String[0]);
        check(player.inventory.getStackInSlot(0).getCount() == 4, "Creative hat consumed item");
        CarpetSettings.commandPublicOp = false; check(!new CommandOp().checkPermission(null, player), "Public op enabled by default");
        CarpetSettings.commandPublicOp = true; player.permission = 0; check(new CommandOp().checkPermission(null, player), "Public op did not open");
        CarpetSettings.commandPublicOp = false; check(!new CommandOp().checkPermission(null, player), "Public op did not revoke");
        check(new CommandSubscribe().getAliases().contains("suscribe"), "Subscription alias missing");
        System.out.println("PASS permissions, gamemodes, hat conservation and more");
    }
    @SuppressWarnings("unchecked")
    private static void pickup() throws Exception {
        Player player = player();
        Field registry = LoggerRegistry.class.getDeclaredField("loggerRegistry"); registry.setAccessible(true);
        final boolean[] subscribed = {true};
        ((Map<String, Logger>)registry.get(null)).put("carefulDrop", new Logger(null, "carefulDrop", null, null, carpet.logging.LogHandler.CHAT) {
            public boolean subscribed(EntityPlayerMP p) { return subscribed[0] && p == player; }
        });
        CarpetSettings.carefulDrop = true;
        NBTTagCompound data = new NBTTagCompound(); data.setString("Fixture", "kept");
        ItemStack stack = new ItemStack(Items.DIAMOND, 5); stack.setTagCompound(data.copy());
        Entity previous = CarefulDrop.begin(player);
        try {
            CarpetSettings.carefulDrop = false; CarefulDrop.collect(player.world, stack); check(stack.getCount() == 5, "Disabled pickup");
            CarpetSettings.carefulDrop = true; subscribed[0] = false; CarefulDrop.collect(player.world, stack); check(stack.getCount() == 5, "Unsubscribed pickup");
            subscribed[0] = true; player.sneak = false; CarefulDrop.collect(player.world, stack); check(stack.getCount() == 5, "Standing pickup");
            player.sneak = true; CarefulDrop.collect(player.world, stack); check(stack.isEmpty(), "Eligible pickup missing");
            check(data.equals(player.inventory.getStackInSlot(0).getTagCompound()), "Pickup NBT lost");
            player.inventory.clear(); for (int i = 0; i < 36; i++) player.inventory.setInventorySlotContents(i, new ItemStack(Items.DIAMOND, 64));
            player.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 63));
            stack = new ItemStack(Items.DIAMOND, 5); CarefulDrop.collect(player.world, stack);
            check(stack.getCount() == 4 && player.inventory.getStackInSlot(0).getCount() == 64, "Partial pickup lost items");
            player.capabilities.isCreativeMode = true; CarefulDrop.collect(player.world, stack); check(stack.getCount() == 4, "Creative overflow destroyed");
            Entity nested = CarefulDrop.begin(null); CarefulDrop.collect(player.world, stack); check(stack.getCount() == 4, "Nested context inherited"); CarefulDrop.end(nested);
            player.inventory.clear(); CarefulDrop.collect(player.world, stack); check(stack.isEmpty(), "Context not restored");
            player.inventory.clear(); CarpetSettings.stackableShulkersPlayerInventory = true;
            net.minecraft.item.Item shulker = net.minecraft.item.Item.getItemFromBlock(Blocks.WHITE_SHULKER_BOX);
            player.inventory.setInventorySlotContents(0, new ItemStack(shulker, 63));
            stack = new ItemStack(shulker); CarefulDrop.collect(player.world, stack);
            check(stack.isEmpty() && player.inventory.getStackInSlot(0).getCount() == 64 && !CarpetServer.playerInventoryStacking, "Shulker stacking/context failed");
            CarpetSettings.stackableShulkersPlayerInventory = false;

        } finally { CarefulDrop.end(previous); }
        stack = new ItemStack(Items.DIAMOND, 1); CarefulDrop.collect(player.world, stack); check(stack.getCount() == 1, "Context leaked");
        System.out.println("PASS opt-in, sneak, NBT, partial/full/creative overflow and cleanup");
    }
    private static ContainerRepair anvil(Player player, int level, int priorCost) {
        ContainerRepair repair = new ContainerRepair(player.inventory, player.world, net.minecraft.util.math.BlockPos.ORIGIN, player);
        ItemStack left = new ItemStack(Items.DIAMOND_SWORD); left.addEnchantment(Enchantments.SHARPNESS, level); left.setRepairCost(priorCost);
        ItemStack right = left.copy();
        repair.getSlot(0).putStack(left); repair.getSlot(1).putStack(right); repair.updateRepairOutput();
        return repair;
    }
    private static void enchants() throws Exception {
        Player player = player(); Server server = allocate(Server.class); server.players = allocate(Players.class); server.players.player = player;
        CommandEnchant enchant = new CommandEnchant();
        CarpetSettings.disableEnchantmentCap = false;
        player.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND_SWORD));
        try { enchant.execute(server, player, new String[] {"Fixture", "sharpness", "6"}); throw new AssertionError("Vanilla cap absent"); } catch (NumberInvalidException expected) { }
        CarpetSettings.disableEnchantmentCap = true;
        enchant.execute(server, player, new String[] {"Fixture", "sharpness", "32767"});
        check(EnchantmentHelper.getEnchantmentLevel(Enchantments.SHARPNESS, player.getHeldItemMainhand()) == 32767, "Enchantment NBT overflow");
        player.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND_SWORD));
        for (String invalid : new String[] {"32768", "0", "-1"}) {
            try { enchant.execute(server, player, new String[] {"Fixture", "sharpness", invalid}); throw new AssertionError("Invalid enchantment accepted"); } catch (NumberInvalidException expected) { }
        }
        CarpetSettings.disableEnchantmentCap = false; CarpetSettings.disableAnvilXpLimit = false;
        check(EnchantmentHelper.getEnchantmentLevel(Enchantments.SHARPNESS, anvil(player, 5, 0).getSlot(2).getStack()) == 5, "Vanilla anvil cap changed");
        CarpetSettings.disableEnchantmentCap = true;
        check(EnchantmentHelper.getEnchantmentLevel(Enchantments.SHARPNESS, anvil(player, 5, 0).getSlot(2).getStack()) == 6, "Uncapped anvil missing");
        check(anvil(player, 5, 30).getSlot(2).getStack().isEmpty(), "Disabled XP limit changed");
        CarpetSettings.disableAnvilXpLimit = true;
        ContainerRepair repair = anvil(player, 5, 30);
        check(!repair.getSlot(2).getStack().isEmpty() && repair.maximumCost > 40, "Uncapped cost missing");
        player.experienceLevel = repair.maximumCost - 1; check(!repair.getSlot(2).canTakeStack(player), "Insufficient XP accepted");
        player.experienceLevel = repair.maximumCost; check(repair.getSlot(2).canTakeStack(player), "Enough XP rejected");
        repair = anvil(player, 32767, Integer.MAX_VALUE);
        check(repair.maximumCost == Integer.MAX_VALUE && repair.getSlot(2).getStack().getRepairCost() == Integer.MAX_VALUE, "Anvil cost overflow");
        check(EnchantmentHelper.getEnchantmentLevel(Enchantments.SHARPNESS, repair.getSlot(2).getStack()) == 32767, "Anvil level overflow");
        System.out.println("PASS enchant/anvil disabled behavior, levels, XP access and overflow");
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        commands(); pickup(); enchants();
        for (String rule : new String[] {"carefulDrop", "disableEnchantmentCap", "disableAnvilXpLimit", "commandGM", "commandHat", "commandMore", "commandPublicOp", "fakePlayerDropInventoryOnKill"}) {
            check(CarpetSettings.hasRule(rule) && CarpetSettings.getDefault(rule).equals("false"), "Missing/changed rule default: " + rule);
        }
        System.out.println("Essential rule checks passed");
    }
}
