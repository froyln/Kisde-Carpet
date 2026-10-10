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
        System.out.println("Kisde rule checks passed");
    }
}
