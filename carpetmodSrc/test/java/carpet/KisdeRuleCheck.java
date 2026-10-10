package carpet;

import net.minecraft.tileentity.TileEntityEndGateway;
import java.lang.reflect.Field;

public final class KisdeRuleCheck {
    public static void main(String[] args) throws Exception {
        TileEntityEndGateway gateway = new TileEntityEndGateway();
        Field cooldown = TileEntityEndGateway.class.getDeclaredField("teleportCooldown");
        cooldown.setAccessible(true);
        cooldown.setInt(gateway, 40);
        CarpetSettings.endGatewayCooldown = true;
        if (!gateway.isCoolingDown()) throw new AssertionError("Vanilla cooldown missing");
        CarpetSettings.endGatewayCooldown = false;
        if (gateway.isCoolingDown()) throw new AssertionError("Disabled cooldown active");
        CarpetSettings.endGatewayCooldown = true;
        if (!gateway.isCoolingDown()) throw new AssertionError("Cooldown did not restore");
        cooldown.setInt(gateway, 0);
        if (gateway.isCoolingDown()) throw new AssertionError("Zero cooldown active");
        carpet.helpers.PlayerSitHelper.Sneaks sneaks = new carpet.helpers.PlayerSitHelper.Sneaks();
        if (sneaks.start(1000, true) || sneaks.start(1100, true) || !sneaks.start(1200, true)) throw new AssertionError("Three sneaks missing");
        if (sneaks.start(1400, true) || sneaks.start(1700, true) || sneaks.start(1800, true) || !sneaks.start(1900, true)) throw new AssertionError("300 ms threshold failed");
        if (sneaks.start(2000, false) || sneaks.start(2100, true) || sneaks.start(2200, true) || !sneaks.start(2300, true)) throw new AssertionError("Airborne sneak counted");
        System.out.println("Kisde rule checks passed");
    }
}
