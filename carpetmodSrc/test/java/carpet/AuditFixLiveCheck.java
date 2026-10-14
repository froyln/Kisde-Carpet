package carpet;

import carpet.helpers.AnvilSync;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import net.minecraft.command.CommandBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetExperience;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

public final class AuditFixLiveCheck {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static ContainerRepair repair(EntityPlayerMP player) {
        check(player.openContainer instanceof ContainerRepair, "Anvil not open"); return (ContainerRepair)player.openContainer;
    }
    private static void xp(EntityPlayerMP player, int level) {
        player.experienceLevel = level; player.connection.sendPacket(new SPacketSetExperience(player.experience, player.experienceTotal, level));
    }
    private static void inputs(EntityPlayerMP player, int prior) {
        ContainerRepair repair = repair(player);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD); sword.addEnchantment(Enchantments.SHARPNESS, 5); sword.setRepairCost(prior);
        repair.getSlot(0).putStack(sword); repair.getSlot(1).putStack(sword.copy()); repair.updateRepairOutput();
    }
    public static void run(MinecraftServer server, String action) throws Exception {
        check(server.isCallingFromMinecraftThread(), "Not on server thread");
        EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(action.startsWith("legacy") ? "AuditLegacy" : "AuditAnvil");
        check(player != null, "Test player not connected"); WorldServer world = player.getServerWorld();
        if (action.equals("open") || action.equals("legacy")) {
            CarpetSettings.set("disableAnvilXpLimit", "true"); CarpetSettings.set("disableEnchantmentCap", "true");
            player.setGameType(GameType.SURVIVAL); player.inventory.clear(); xp(player, 40016);
            BlockPos pos = player.getPosition().east(2); world.setBlockState(pos, Blocks.ANVIL.getDefaultState(), 2);
            Blocks.ANVIL.onBlockActivated(world, pos, world.getBlockState(pos), player, EnumHand.MAIN_HAND, EnumFacing.UP, .5F, .5F, .5F);
            inputs(player, 20000);
            if (action.equals("open")) {
                check(AnvilSync.supports(player), "Client capability not registered");
                check(repair(player).maximumCost == 40006 && !repair(player).getSlot(2).getStack().isEmpty(), "Paired anvil high cost missing");
            } else {
                check(!AnvilSync.supports(player) && repair(player).getSlot(2).getStack().isEmpty(), "Legacy high cost not rejected");
                check(player.experienceLevel == 40016 && repair(player).getSlot(0).getStack().getCount() == 1 && repair(player).getSlot(1).getStack().getCount() == 1, "Legacy rejection consumed XP/inputs");
            }
        } else if (action.equals("insufficient")) {
            xp(player, 40005);
        } else if (action.equals("denycheck")) {
            check(player.experienceLevel == 40005 && !repair(player).getSlot(2).getStack().isEmpty() && !repair(player).getSlot(0).getStack().isEmpty() && !repair(player).getSlot(1).getStack().isEmpty() && player.inventory.getItemStack().isEmpty(), "Insufficient-XP native click mutated inputs/cursor/XP");
            xp(player, 40016);
        } else if (action.equals("verify")) {
            check(player.experienceLevel == 10 && repair(player).getSlot(0).getStack().isEmpty() && repair(player).getSlot(1).getStack().isEmpty(), "Native anvil did not charge full XP/consume inputs");
            ItemStack result = player.inventory.getItemStack();
            check(result.getItem() == Items.DIAMOND_SWORD && result.getCount() == 1 && net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(Enchantments.SHARPNESS, result) == 6, "Native result changed");
        } else if (action.equals("off")) {
            player.inventory.setItemStack(ItemStack.EMPTY); xp(player, 100);
            CarpetSettings.set("disableAnvilXpLimit", "false"); inputs(player, 30);
            check(repair(player).maximumCost == 66 && repair(player).getSlot(2).getStack().isEmpty(), "Disabled anvil cap changed");
        } else if (action.equals("on")) {
            CarpetSettings.set("disableAnvilXpLimit", "true");
        } else if (action.equals("regressions")) {
            player.closeScreen(); player.inventory.setItemStack(ItemStack.EMPTY);
            BlockPos source = player.getPosition().east(8), destination = source.south(8);
            world.setBlockState(source, Blocks.STONE.getDefaultState(), 2);
            world.setBlockState(source.east(), Blocks.CHEST.getDefaultState(), 2);
            ((TileEntityChest)world.getTileEntity(source.east())).setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 3));
            String from = source.getX()+" "+source.getY()+" "+source.getZ(), to = source.east().getX()+" "+source.getY()+" "+source.getZ();
            String target = destination.getX()+" "+destination.getY()+" "+destination.getZ();
            check(server.getCommandManager().executeCommand(player, "structure load endcity/base_floor") == 0, "Ordinary player structure load allowed");
            check(server.getCommandManager().executeCommand(server, "structure save audit_fixture "+from+" "+to) == 1, "Authorized structure save failed");
            check(server.getCommandManager().executeCommand(server, "structure load audit_fixture "+target+" no_mirror rotate_90") == 1, "Authorized rotated structure load failed");
            check(world.getBlockState(destination).getBlock() == Blocks.STONE && world.getBlockState(destination.south()).getBlock() == Blocks.CHEST, "Rotated structure blocks changed");
            check(((TileEntityChest)world.getTileEntity(destination.south())).getStackInSlot(0).getCount() == 3, "Structure inventory data changed");
            int chunks = world.getChunkProvider().getLoadedChunkCount();
            check(server.getCommandManager().executeCommand(server, "structure load audit_fixture 1000000 64 1000000") == 0 && world.getChunkProvider().getLoadedChunkCount() == chunks, "Rejected structure generated chunks");
            check(server.getCommandManager().executeCommand(player, "structure list") == 1, "Public structure listing removed");
            CarpetSettings.set("commandLifeTime", "0");
            check(server.getCommandManager().executeCommand(player, "lifetime tracking") == 1, "Lifetime level zero not public");
            CarpetSettings.set("commandLifeTime", "true");
            check(!server.getCommandManager().getCommands().containsKey("updateCarpet") && !CarpetSettings.hasRule("updateCarpetAll"), "Updater still registered");
            check(server.getCommandManager().executeCommand(server, "ticktimes start audit-native-ticks.txt") == 1, "Tick logging start failed");
        } else if (action.equals("ticks")) {
            check(server.getCommandManager().executeCommand(server, "ticktimes stop") == 1, "Tick logging stop failed");
            java.util.List<String> values = Files.readAllLines(Paths.get("audit-native-ticks.txt"));
            check(!values.isEmpty() && values.stream().allMatch(v -> Long.parseLong(v) >= 0), "Native tick samples missing/invalid");
        } else if (action.equals("legacyclaim")) {
            check(AnvilSync.supports(player) && !repair(player).getSlot(2).getStack().isEmpty(), "Negotiated legacy capability not reflected");
            xp(player, 0); check(!repair(player).getSlot(2).canTakeStack(player), "Claimed capability bypassed actual XP");
        } else throw new IllegalArgumentException("Unknown test action");
        System.out.println("AUDIT_NATIVE PASS " + action);
    }
}
