package carpet;

import carpet.helpers.CreativeOneHitKillHelper;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.GameType;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class KisdeRuleLiveCheck {
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    private static void input(EntityPlayerMPFake player, boolean sneak) throws Exception {
        net.minecraft.network.play.client.CPacketInput packet = new net.minecraft.network.play.client.CPacketInput();
        net.minecraft.network.PacketBuffer buffer = new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());
        try { buffer.writeFloat(0); buffer.writeFloat(0); buffer.writeByte(sneak ? 2 : 0); packet.readPacketData(buffer); }
        finally { buffer.release(); }
        player.connection.processInput(packet);
    }

    public static void run(MinecraftServer server) throws Exception {
        check(Files.exists(Paths.get(".kisde-rule-check")), "Disposable fixture marker missing");
        WorldServer world = server.getWorld(0);
        EntityPlayerMPFake player = EntityPlayerMPFake.createFake("DropOff", server, 80, 6, 80, 0, 0, 0, 1);
        EntityCow cow = new EntityCow(world);
        cow.setPosition(80, 6, 80);
        CarpetSettings.creativeOneHitKill = false;
        check(!CreativeOneHitKillHelper.attack(player, cow) && cow.getHealth() == 10, "Disabled kill changed target");
        CarpetSettings.creativeOneHitKill = true;
        player.interactionManager.setGameType(GameType.SURVIVAL);
        check(!CreativeOneHitKillHelper.attack(player, cow), "Survival kill allowed");
        player.interactionManager.setGameType(GameType.CREATIVE);
        cow.setHealth(100);
        check(CreativeOneHitKillHelper.attack(player, cow) && cow.getHealth() == 0, "Creative target survived");
        check(!CreativeOneHitKillHelper.attack(player, player), "Creative player killed");
        EntityDragon dragon = new EntityDragon(world);
        check(CreativeOneHitKillHelper.attack(player, dragon.dragonPartHead) && dragon.isDead, "Dragon part survived");
        for (net.minecraft.entity.MultiPartEntityPart part : dragon.dragonPartArray) check(part.isDead, "Dragon part remains");
        EntityCow target = new EntityCow(world), nearby = new EntityCow(world), outside = new EntityCow(world);
        target.setPosition(80, 6, 80); nearby.setPosition(81, 6, 80); outside.setPosition(84, 6, 80);
        EntityArmorStand stand = new EntityArmorStand(world, 81, 6, 80);
        world.spawnEntity(target); world.spawnEntity(nearby); world.spawnEntity(outside); world.spawnEntity(stand);
        player.setSneaking(true);
        check(CreativeOneHitKillHelper.attack(player, target), "Sweep not activated");
        check(nearby.getHealth() == 0 && outside.getHealth() > 0 && !stand.isDead, "Sweep boundary/exclusion failed");
        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(85, 6, 80);
        world.setBlockState(pos.down(), net.minecraft.init.Blocks.STONE.getDefaultState());
        world.setBlockToAir(pos);
        EntityCow obstacle = new EntityCow(world); obstacle.setPosition(85.5, 6, 80.5); world.spawnEntity(obstacle);
        player.setPosition(80, 6, 80);
        net.minecraft.item.ItemBlock item = (net.minecraft.item.ItemBlock) net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE);
        player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new net.minecraft.item.ItemStack(item, 64));
        CarpetSettings.blockPlacementIgnoreEntity = false;
        check(item.onItemUse(player, world, pos, net.minecraft.util.EnumHand.MAIN_HAND, net.minecraft.util.EnumFacing.UP, .5F, .5F, .5F) == net.minecraft.util.EnumActionResult.FAIL, "Disabled collision skipped");
        CarpetSettings.blockPlacementIgnoreEntity = true;
        player.interactionManager.setGameType(GameType.SURVIVAL);
        check(item.onItemUse(player, world, pos, net.minecraft.util.EnumHand.MAIN_HAND, net.minecraft.util.EnumFacing.UP, .5F, .5F, .5F) == net.minecraft.util.EnumActionResult.FAIL, "Survival collision skipped");
        player.interactionManager.setGameType(GameType.CREATIVE);
        check(item.onItemUse(player, world, pos, net.minecraft.util.EnumHand.MAIN_HAND, net.minecraft.util.EnumFacing.UP, .5F, .5F, .5F) == net.minecraft.util.EnumActionResult.SUCCESS, "Creative collision not skipped");
        check(world.getBlockState(pos).getBlock() == net.minecraft.init.Blocks.STONE, "Creative placement missing");
        world.setBlockToAir(pos); obstacle.setDead(); CarpetSettings.blockPlacementIgnoreEntity = false;
        net.minecraft.block.Block[] containers = {net.minecraft.init.Blocks.CHEST, net.minecraft.init.Blocks.ENDER_CHEST, net.minecraft.init.Blocks.WHITE_SHULKER_BOX};
        for (int i = 0; i < containers.length; i++) {
            net.minecraft.util.math.BlockPos containerPos = new net.minecraft.util.math.BlockPos(88 + i * 2, 6, 80);
            net.minecraft.block.Block block = containers[i];
            world.setBlockState(containerPos, block.getDefaultState());
            world.setBlockState(containerPos.up(), net.minecraft.init.Blocks.STONE.getDefaultState());
            player.setPosition(containerPos.getX(), 6, 79);
            player.closeScreen(); CarpetSettings.creativeOpenContainerForcibly = false;
            block.onBlockActivated(world, containerPos, world.getBlockState(containerPos), player, net.minecraft.util.EnumHand.MAIN_HAND, net.minecraft.util.EnumFacing.UP, .5F, .5F, .5F);
            check(player.openContainer == player.inventoryContainer, "Disabled blocked container opened " + i);
            CarpetSettings.creativeOpenContainerForcibly = true;
            player.interactionManager.setGameType(GameType.SURVIVAL);
            block.onBlockActivated(world, containerPos, world.getBlockState(containerPos), player, net.minecraft.util.EnumHand.MAIN_HAND, net.minecraft.util.EnumFacing.UP, .5F, .5F, .5F);
            check(player.openContainer == player.inventoryContainer, "Survival blocked container opened " + i);
            player.interactionManager.setGameType(GameType.CREATIVE);
            block.onBlockActivated(world, containerPos, world.getBlockState(containerPos), player, net.minecraft.util.EnumHand.MAIN_HAND, net.minecraft.util.EnumFacing.UP, .5F, .5F, .5F);
            check(player.openContainer != player.inventoryContainer, "Creative blocked container failed " + i);
            player.closeScreen(); world.setBlockToAir(containerPos.up()); world.setBlockToAir(containerPos);
        }
        CarpetSettings.creativeOpenContainerForcibly = false;
        CarpetSettings.playerSit = true; player.onGround = true;
        for (int i = 0; i < 3; i++) { carpet.helpers.PlayerSitHelper.setSneaking(player, false); carpet.helpers.PlayerSitHelper.setSneaking(player, true); }
        check(player.isRiding() && !player.isSneaking(), "Triple sneak did not seat player");
        net.minecraft.entity.Entity seat = player.getRidingEntity();
        input(player, true);
        check(!player.isSneaking() && player.isRiding(), "Held third sneak dismounted seat");
        input(player, false);
        input(player, true);
        check(player.isSneaking(), "New sneak suppressed after release");
        player.setSneaking(false);
        EntityArmorStand unrelated = new EntityArmorStand(world); unrelated.addTag(carpet.helpers.PlayerSitHelper.SEAT_TAG);
        check(unrelated.writeToNBTOptional(new net.minecraft.nbt.NBTTagCompound()), "Public tag suppressed unrelated save");
        check(!seat.writeToNBTOptional(new net.minecraft.nbt.NBTTagCompound()), "Seat persisted");
        CarpetSettings.playerSit = false; carpet.helpers.PlayerSitHelper.tick(server);
        check(!player.isRiding() && seat.isDead, "Rule off did not clean seat");
        outside.setDead(); stand.setDead(); player.setSneaking(false); player.despawn();
        CarpetSettings.creativeOneHitKill = false;
        System.out.println("Kisde live checks passed");
    }
}
