package carpet;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.*;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

public final class EssentialRuleLiveCheck {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static EntityPlayerMPFake fake(MinecraftServer server, String name) {
        server.getPlayerProfileCache().addEntry(new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        BlockPos pos = server.getWorld(0).getSpawnPoint();
        return EntityPlayerMPFake.createFake(name, server, pos.getX() + .5, pos.getY() + 2, pos.getZ() + .5, 0, 0, 0, 0);
    }
    private static int count(EntityPlayerMPFake player, Item item) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) if (stack.getItem() == item) count += stack.getCount();
        return count;
    }
    private static int drops(WorldServer world, Item item) {
        int count = 0;
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityItem && !entity.isDead) {
                ItemStack stack = ((EntityItem)entity).getItem();
                if (stack.getItem() == item) count += stack.getCount();
            }
        }
        return count;
    }
    private static void removeDrops(WorldServer world) {
        for (Entity entity : new ArrayList<>(world.loadedEntityList)) if (entity instanceof EntityItem) world.removeEntity(entity);
    }
    private static void place(Entity entity, EntityPlayerMPFake player) { entity.setPosition(player.posX, player.posY, player.posZ); }
    private static final class FailingFrame extends EntityItemFrame {
        FailingFrame(World world) { super(world); }
        public EntityItem entityDropItem(ItemStack stack, float offset) { throw new IllegalStateException("fixture"); }
    }
    public static void run(MinecraftServer server) throws Exception {
        check(server.isCallingFromMinecraftThread(), "Not on server thread");
        CarpetSettings.removeFakePlayerSkins = true;
        WorldServer world = server.getWorld(0);
        world.getGameRules().setOrCreateGameRule("doMobLoot", "true");
        world.getGameRules().setOrCreateGameRule("doTileDrops", "true");
        world.getGameRules().setOrCreateGameRule("doEntityDrops", "true");
        world.getGameRules().setOrCreateGameRule("keepInventory", "false");
        EntityPlayerMPFake player = fake(server, "EssentialTester");
        player.inventory.clear(); player.setSneaking(true);
        check(server.getCommandManager().executeCommand(player, "suscribe carefulDrop") == 1, "Subscription command failed");
        check(carpet.logging.LoggerRegistry.getLogger("carefulDrop").subscribed(player), "Subscription missing");
        CarpetSettings.carefulDrop = false;
        EntityCow cow = new EntityCow(world); place(cow, player); world.spawnEntity(cow);
        cow.attackEntityFrom(DamageSource.causePlayerDamage(player), 100);
        check(count(player, Items.BEEF) == 0 && drops(world, Items.BEEF) > 0, "Disabled mob loot changed"); removeDrops(world);
        CarpetSettings.carefulDrop = true;
        cow = new EntityCow(world); place(cow, player); world.spawnEntity(cow);
        cow.attackEntityFrom(DamageSource.causePlayerDamage(player), 100);
        check(count(player, Items.BEEF) > 0 && drops(world, Items.BEEF) == 0, "Careful mob loot missing"); player.inventory.clear();
        world.getGameRules().setOrCreateGameRule("doMobLoot", "false");
        cow = new EntityCow(world); place(cow, player); world.spawnEntity(cow); cow.attackEntityFrom(DamageSource.causePlayerDamage(player), 100);
        check(count(player, Items.BEEF) == 0 && drops(world, Items.BEEF) == 0, "doMobLoot bypassed");
        world.getGameRules().setOrCreateGameRule("doMobLoot", "true");
        EntityZombie zombie = new EntityZombie(world); place(zombie, player); world.spawnEntity(zombie);
        zombie.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD)); zombie.setDropChance(EntityEquipmentSlot.MAINHAND, 2);
        zombie.attackEntityFrom(DamageSource.causePlayerDamage(player), 100);
        check(count(player, Items.DIAMOND_SWORD) == 1, "Equipment drop missing"); player.inventory.clear();
        EntityMinecartChest cart = new EntityMinecartChest(world, player.posX, player.posY, player.posZ);
        cart.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 3)); world.spawnEntity(cart);
        cart.attackEntityFrom(DamageSource.causePlayerDamage(player), 100);
        check(count(player, Items.DIAMOND) == 3 && count(player, Items.MINECART) == 1 && count(player, Item.getItemFromBlock(Blocks.CHEST)) == 1, "Minecart drops lost/duplicated");
        check(drops(world, Items.DIAMOND) == 0, "Minecart contents not collected"); player.inventory.clear();
        EntityBoat boat = new EntityBoat(world, player.posX, player.posY, player.posZ); world.spawnEntity(boat);
        boat.attackEntityFrom(DamageSource.causePlayerDamage(player), 100); check(count(player, Items.BOAT) == 1, "Boat drop missing"); player.inventory.clear();
        EntityItemFrame frame = new EntityItemFrame(world, player.getPosition(), EnumFacing.NORTH); frame.setDisplayedItem(new ItemStack(Items.DIAMOND));
        frame.dropItemOrSelf(player, true); check(count(player, Items.DIAMOND) == 1 && count(player, Items.ITEM_FRAME) == 1, "Frame drops missing"); player.inventory.clear();
        EntityPainting painting = new EntityPainting(world, player.getPosition(), EnumFacing.NORTH); painting.onBroken(player);
        check(count(player, Items.PAINTING) == 1, "Painting drop missing"); player.inventory.clear();
        world.getGameRules().setOrCreateGameRule("doEntityDrops", "false");
        frame = new EntityItemFrame(world, player.getPosition(), EnumFacing.NORTH); frame.setDisplayedItem(new ItemStack(Items.DIAMOND)); frame.dropItemOrSelf(player, true);
        check(count(player, Items.DIAMOND) == 0 && count(player, Items.ITEM_FRAME) == 0, "doEntityDrops bypassed");
        world.getGameRules().setOrCreateGameRule("doEntityDrops", "true");
        EntityArmorStand stand = new EntityArmorStand(world, player.posX, player.posY, player.posZ); world.spawnEntity(stand);
        stand.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        stand.attackEntityFrom(DamageSource.causePlayerDamage(player), 1); stand.attackEntityFrom(DamageSource.causePlayerDamage(player), 1);
        check(count(player, Items.ARMOR_STAND) == 1 && count(player, Items.DIAMOND_HELMET) == 1, "Armor stand drops missing"); player.inventory.clear();
        FailingFrame failing = new FailingFrame(world);
        try { failing.dropItemOrSelf(player, true); throw new AssertionError("Exception fixture missing"); } catch (IllegalStateException expected) { }
        net.minecraft.block.Block.spawnAsEntity(world, player.getPosition(), new ItemStack(Items.DIAMOND));
        check(count(player, Items.DIAMOND) == 0 && drops(world, Items.DIAMOND) == 1, "Exceptional dropper context leaked"); removeDrops(world);
        EntityPlayerMPFake victim = fake(server, "EssentialVictim"); victim.inventory.clear(); victim.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 4));
        victim.onDeath(DamageSource.causePlayerDamage(player)); check(count(player, Items.DIAMOND) == 4 && drops(world, Items.DIAMOND) == 0, "Player death inventory missing"); player.inventory.clear();
        check(server.getCommandManager().executeCommand(player, "subscribe carefulBreak") == 1, "Existing carefulBreak subscription broken");
        CarpetSettings.carefulBreak = true;
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos mined = player.getPosition().east(); world.setBlockState(mined, Blocks.STONE.getDefaultState());
        player.interactionManager.tryHarvestBlock(mined);
        check(count(player, Item.getItemFromBlock(Blocks.COBBLESTONE)) == 1, "Existing carefulBreak changed");
        player.inventory.clear(); CarpetSettings.carefulDrop = false;
        world.getGameRules().setOrCreateGameRule("keepInventory", "true");
        CarpetSettings.fakePlayerDropInventoryOnKill = false;
        victim = fake(server, "EssentialKillOff"); victim.inventory.clear(); victim.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 5)); check(server.getCommandManager().executeCommand(server, "player EssentialKillOff kill") == 1, "Native /player kill rejected");
        check(drops(world, Items.DIAMOND) == 0 && victim.inventory.getStackInSlot(0).getCount() == 5, "Disabled fake kill lost inventory");
        CarpetSettings.fakePlayerDropInventoryOnKill = true;
        victim = fake(server, "EssentialKillOn"); victim.inventory.clear(); victim.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 5));
        ItemStack cursed = new ItemStack(Items.DIAMOND_HELMET); cursed.addEnchantment(Enchantments.VANISHING_CURSE, 1); victim.inventory.setInventorySlotContents(39, cursed);
        check(server.getCommandManager().executeCommand(server, "kill EssentialKillOn") == 1, "Native /kill rejected"); check(drops(world, Items.DIAMOND) == 5 && drops(world, Items.DIAMOND_HELMET) == 0 && victim.inventory.isEmpty(), "Fake kill lost/duplicated inventory or curse"); removeDrops(world);
        victim = fake(server, "EssentialDespawn"); victim.inventory.clear(); victim.inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 5)); victim.despawn();
        check(drops(world, Items.DIAMOND) == 0 && victim.inventory.getStackInSlot(0).getCount() == 5, "Despawn gained drops");
        CarpetSettings.disableEnchantmentCap = true; CarpetSettings.disableAnvilXpLimit = true;
        ContainerRepair repair = new ContainerRepair(player.inventory, world, player.getPosition(), player);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD); sword.addEnchantment(Enchantments.SHARPNESS, 5); sword.setRepairCost(30);
        repair.getSlot(0).putStack(sword); repair.getSlot(1).putStack(sword.copy());
        int cost = repair.maximumCost; check(cost >= 40 && !repair.getSlot(2).getStack().isEmpty(), "Live anvil cap remains");
        player.experienceLevel = cost + 10; repair.getSlot(2).onTake(player, repair.getSlot(2).getStack().copy());
        check(player.experienceLevel == 10 && repair.getSlot(0).getStack().isEmpty() && repair.getSlot(1).getStack().isEmpty(), "Anvil XP/inputs not consumed");
        CarpetSettings.commandPublicOp = false;
        check(server.getCommandManager().executeCommand(player, "op EssentialTester") == 0 && !server.getPlayerList().canSendCommands(player.getGameProfile()), "Disabled public op changed access");
        CarpetSettings.commandPublicOp = true;
        check(server.getCommandManager().executeCommand(player, "op EssentialTester") == 1 && server.getPlayerList().canSendCommands(player.getGameProfile()), "Public op failed to grant access");
        CarpetSettings.commandPublicOp = false; server.getPlayerList().removeOp(player.getGameProfile());
        CarpetSettings.commandGM = "ops";
        check(server.getCommandManager().executeCommand(player, "gmc") == 0, "Native gamemode permission bypass");
        CarpetSettings.commandGM = "0";
        check(server.getCommandManager().executeCommand(player, "gmc") == 1 && player.interactionManager.getGameType() == GameType.CREATIVE, "Native gamemode command failed");
        check(server.getCommandManager().executeCommand(player, "gms") == 1 && player.interactionManager.getGameType() == GameType.SURVIVAL, "Native survival command failed");
        CarpetSettings.fakePlayerDropInventoryOnKill = false; player.onKillCommand();
        System.out.println("Essential live checks passed: native subscription, drops, gamerules, cleanup, carefulBreak, fake kills, anvil XP");
    }
}
