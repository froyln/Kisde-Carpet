package carpet;

import carpet.commands.CommandGMS;
import com.google.common.base.Predicate;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import sun.misc.Unsafe;

public final class BedCameraCheck {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((Unsafe)field.get(null)).allocateInstance(type));
    }
    private static final class World extends WorldServer {
        boolean day;
        IBlockState bed;
        World() { super(null, null, null, 0, null); }
        public boolean isDaytime() { return day; }
        public IBlockState getBlockState(BlockPos pos) { return bed; }
        public Biome getBiome(BlockPos pos) { return Biomes.PLAINS; }
        public <T extends Entity> List<T> getEntitiesWithinAABB(Class<? extends T> type, AxisAlignedBB box, Predicate<? super T> predicate) {
            return Collections.singletonList(null);
        }
    }
    private static final class Manager extends PlayerInteractionManager {
        GameType mode;
        Manager() { super(null); }
        public GameType getGameType() { return mode; }
    }
    private static final class Player extends EntityPlayerMP {
        boolean dead, moved, restored, removedNightvision, originalNightvision, asleep;
        int messages;
        Player() { super(null, null, new GameProfile(UUID.randomUUID(), "BedCamera"), null); }
        public boolean isEntityAlive() { return !dead; }
        public boolean isPlayerSleeping() { return asleep; }
        public void sendStatusMessage(ITextComponent text, boolean actionBar) { messages++; }
        public boolean moveToStoredCameraData() { restored = true; return moved; }
        public void setGameType(GameType type) { ((Manager)interactionManager).mode = type; }
        public boolean hadNightvision() { return originalNightvision; }
        public void removePotionEffect(Potion potion) { removedNightvision = true; }
    }
    private static final class Server extends DedicatedServer {
        GameType mode;
        Server() { super(null, null, null, null, null, null); }
        public GameType getGameType() { return mode; }
    }
    public static void run() throws Exception {
        boolean oldBed = CarpetSettings.bedSetSpawn;
        boolean oldSurvival = CarpetSettings.alwaysSetPlayerIntoSurvival;
        try {
            World world = allocate(World.class);
            Field provider = net.minecraft.world.World.class.getDeclaredField("provider"); provider.setAccessible(true);
            provider.set(world, new WorldProviderSurface());
            Field players = net.minecraft.world.World.class.getDeclaredField("playerEntities"); players.setAccessible(true);
            players.set(world, new ArrayList<EntityPlayer>());
            world.bed = Blocks.BED.getDefaultState().withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD);
            Player player = allocate(Player.class); player.world = world;
            BlockPos head = new BlockPos(0, 0, 0);
            world.day = true;
            CarpetSettings.bedSetSpawn = false;
            player.trySleep(head);
            check(player.getBedLocation() == null, "Disabled rule changed daytime spawn");
            CarpetSettings.bedSetSpawn = true;
            Blocks.BED.onBlockActivated(world, head, world.bed, player, EnumHand.MAIN_HAND, EnumFacing.UP, 0, 0, 0);
            check(head.equals(player.getBedLocation()) && !player.isSpawnForced(), "Daytime bed click did not set normal spawn");
            int messages = player.messages;
            player.trySleep(head);
            check(player.messages == messages, "Repeated spawn notification");
            player.setSpawnPoint(null, false); world.day = false;
            check(player.trySleep(head) == EntityPlayer.SleepResult.NOT_SAFE && head.equals(player.getBedLocation()), "Hostiles prevented spawn update");
            player.setSpawnPoint(null, false); player.posX = 10;
            check(player.trySleep(head) == EntityPlayer.SleepResult.TOO_FAR_AWAY && player.getBedLocation() == null, "Distant bed changed spawn");
            player.posX = 0; player.dead = true; player.trySleep(head);
            check(player.getBedLocation() == null, "Dead player changed spawn"); player.dead = false;
            provider.set(world, new WorldProviderHell()); player.trySleep(head);
            check(player.getBedLocation() == null, "Forbidden dimension changed spawn");
            provider.set(world, new WorldProviderSurface()); world.day = true;
            IBlockState foot = world.bed.withProperty(BlockBed.PART, BlockBed.EnumPartType.FOOT);
            Blocks.BED.onBlockActivated(world, head.offset(foot.getValue(BlockBed.FACING).getOpposite()), foot, player, EnumHand.MAIN_HAND, EnumFacing.UP, 0, 0, 0);
            check(head.equals(player.getBedLocation()), "Foot click stored wrong bed position");
            player.setSpawnPoint(null, false);
            Player sleeper = allocate(Player.class); sleeper.asleep = true; sleeper.bedLocation = head;
            world.playerEntities.add(sleeper);
            Blocks.BED.onBlockActivated(world, head, world.bed.withProperty(BlockBed.OCCUPIED, true), player, EnumHand.MAIN_HAND, EnumFacing.UP, 0, 0, 0);
            check(player.getBedLocation() == null, "Occupied bed changed spawn");
            world.playerEntities.clear();
            Server server = allocate(Server.class); Manager manager = allocate(Manager.class); Field interaction = EntityPlayerMP.class.getDeclaredField("interactionManager"); interaction.setAccessible(true); interaction.set(player, manager);
            CarpetSettings.alwaysSetPlayerIntoSurvival = false;
            for (boolean moved : new boolean[] {false, true}) {
                for (boolean logout : new boolean[] {false, true}) {
                    for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE, GameType.SPECTATOR}) {
                        manager.mode = GameType.SPECTATOR; server.mode = mode;
                        player.moved = moved; player.restored = false; player.removedNightvision = false; player.fallDistance = 20;
                        CommandGMS.setPlayerToSurvival(server, player, logout);
                        check(manager.mode == (mode == GameType.SPECTATOR ? GameType.SURVIVAL : mode), "Camera return left incorrect mode after teleport");
                        check(player.restored && player.fallDistance == 0 && player.removedNightvision, "Camera cleanup skipped");
                    }
                }
            }
            CarpetSettings.alwaysSetPlayerIntoSurvival = true; server.mode = GameType.CREATIVE;
            manager.mode = GameType.SPECTATOR; player.originalNightvision = true; player.removedNightvision = false;
            CommandGMS.setPlayerToSurvival(server, player, false);
            check(manager.mode == GameType.SURVIVAL && !player.removedNightvision, "Survival override or original night vision lost");
            System.out.println("PASS bed spawn activation and camera return cleanup");
        } finally {
            CarpetSettings.bedSetSpawn = oldBed;
            CarpetSettings.alwaysSetPlayerIntoSurvival = oldSurvival;
        }
    }
}
