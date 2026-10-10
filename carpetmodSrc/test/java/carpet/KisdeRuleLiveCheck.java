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

    public static void run(MinecraftServer server) {
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
        outside.setDead(); stand.setDead(); player.setSneaking(false); player.despawn();
        CarpetSettings.creativeOneHitKill = false;
        System.out.println("Kisde live checks passed");
    }
}
