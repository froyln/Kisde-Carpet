package carpet;

import carpet.schematic.PcaInventoryPreviewHandler;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;
import sun.misc.Unsafe;

public final class InventoryPreviewCheck {
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((Unsafe)field.get(null)).allocateInstance(type));
    }
    private static final class Chunks extends PlayerChunkMap {
        boolean sent;
        Chunks() { super(null); }
        public boolean isPlayerWatchingChunk(EntityPlayerMP player, int x, int z) {
            return sent && x == -2 && z == 3;
        }
    }
    private static final class World extends WorldServer {
        Chunks chunks; boolean loaded;
        World() { super(null,null,null,0,null); }
        public boolean isBlockLoaded(BlockPos pos) { return loaded; }
        public int getActualHeight() { return 256; }
        public WorldBorder getWorldBorder() { return new WorldBorder(); }
        public PlayerChunkMap getPlayerChunkMap() { return chunks; }
    }
    private static final class Player extends EntityPlayerMP {
        boolean operator;
        Player() { super(null,null,new GameProfile(UUID.randomUUID(),"Fixture"),null); }
        public boolean canUseCommand(int level, String name) { return operator; }
    }
    public static void run() throws Exception {
        Method allowed = PcaInventoryPreviewHandler.class.getDeclaredMethod("canPreview",EntityPlayerMP.class,BlockPos.class);
        allowed.setAccessible(true);
        World world = allocate(World.class); world.chunks = allocate(Chunks.class); world.loaded = true; world.chunks.sent = true;
        Player player = allocate(Player.class); player.world = world; player.operator = true;
        BlockPos remote = new BlockPos(-17,64,48);
        boolean oldRule = CarpetSettings.pcaProtocol;
        try {
            CarpetSettings.pcaProtocol = true;
            check((Boolean)allowed.invoke(null,player,remote),"Free-camera target in sent chunk rejected");
            world.chunks.sent = false;
            check(!(Boolean)allowed.invoke(null,player,remote),"Unsent chunk exposed"); world.chunks.sent = true;
            world.loaded = false;
            check(!(Boolean)allowed.invoke(null,player,remote),"Unloaded chunk exposed"); world.loaded = true;
            player.operator = false;
            check(!(Boolean)allowed.invoke(null,player,remote),"Non-operator preview allowed"); player.operator = true;
            check(!(Boolean)allowed.invoke(null,player,new BlockPos(-17,256,48)),"Invalid height allowed");
            check(!(Boolean)allowed.invoke(null,player,new BlockPos(0,64,0)),"Wrong chunk allowed");
            CarpetSettings.pcaProtocol = false;
            check(!(Boolean)allowed.invoke(null,player,remote),"Disabled preview allowed");
        } finally { CarpetSettings.pcaProtocol = oldRule; }
        System.out.println("PASS inventory preview free-camera sent chunks, negative coordinates, loaded state, height, permission and rule");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
