package carpet.helpers;

import carpet.CarpetSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;

public final class CreativeOneHitKillHelper {
    public static boolean eligible(Entity target) {
        return !(target instanceof EntityPlayer) ||
                (!((EntityPlayer) target).isCreative() && !((EntityPlayer) target).isSpectator());
    }

    public static boolean attack(EntityPlayer player, Entity target) {
        if (!CarpetSettings.creativeOneHitKill || player.world.isRemote ||
                !player.isCreative() || !eligible(target)) return false;
        AxisAlignedBB area = target.getEntityBoundingBox().grow(2.0D, 0.5D, 2.0D);
        kill(target);
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.0F);
        if (player.isSneaking()) {
            for (Entity entity : player.world.getEntitiesWithinAABBExcludingEntity(player, area)) {
                if (entity != target && entity.canBeAttackedWithItem() && eligible(entity) &&
                        !(entity instanceof EntityHanging) && !(entity instanceof EntityArmorStand)) kill(entity);
            }
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        return true;
    }

    private static void kill(Entity entity) {
        if (entity instanceof MultiPartEntityPart && ((MultiPartEntityPart) entity).parent instanceof EntityDragon) {
            EntityDragon dragon = (EntityDragon) ((MultiPartEntityPart) entity).parent;
            for (MultiPartEntityPart part : dragon.dragonPartArray) part.setDead();
            dragon.onKillCommand();
        } else {
            entity.onKillCommand();
        }
    }
}
