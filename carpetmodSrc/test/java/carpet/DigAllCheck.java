package carpet;

import carpet.commands.CommandDigAll;
import carpet.helpers.StatHelper;
import carpet.helpers.StatSubItem;
import carpet.patches.EntityPlayerMPFake;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.*;
import net.minecraft.stats.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.WorldServer;
import sun.misc.Unsafe;

public final class DigAllCheck {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((Unsafe)field.get(null)).allocateInstance(type));
    }
    private static final class Player extends EntityPlayerMP {
        String name; Scoreboard board;
        Player() { super(null, null, new GameProfile(UUID.randomUUID(), "Fixture"), null); }
        public String getName() { return name; }
        public boolean canUseCommand(int level, String command) { return false; }
        public Scoreboard getWorldScoreboard() { return board; }
        public void sendMessage(ITextComponent text) { }
    }
    private static Player player(String name, Scoreboard board) throws Exception {
        Player player = allocate(Player.class); player.name = name; player.board = board; player.setUniqueId(UUID.randomUUID());
        Field stats = EntityPlayerMP.class.getDeclaredField("statsFile"); stats.setAccessible(true);
        stats.set(player, new StatisticsManagerServer(null, Files.createTempFile("kisde-dig-stats-", ".json").toFile())); return player;
    }
    private static final class World extends WorldServer {
        Scoreboard board;
        World() { super(null,null,null,0,null); }
        public Scoreboard getScoreboard() { return board; }
    }
    private static final class Players extends DedicatedPlayerList {
        EntityPlayerMP online;
        Players() { super(null); }
        public EntityPlayerMP getPlayerByUUID(UUID uuid) { return online != null && online.getUniqueID().equals(uuid) ? online : null; }
        public StatisticsManagerServer getPlayerStatsFile(EntityPlayer player) { return ((EntityPlayerMP)player).getStatFile(); }
    }
    private static final class Server extends DedicatedServer {
        World world; Players players; List<Runnable> tasks;
        Server() { super(null,null,null,null,null,null); }
        public WorldServer getWorld(int dimension) { return world; }
        public DedicatedPlayerList getPlayerList() { return players; }
        public boolean isServerRunning() { return true; }
        public boolean isCallingFromMinecraftThread() { return false; }
        public ListenableFuture<Object> addScheduledTask(Runnable task) { tasks.add(task); return Futures.immediateFuture(null); }
    }
    private static int score(Scoreboard board, ScoreObjective objective, String name) { return board.getOrCreateScore(name, objective).getScorePoints(); }
    public static void run() throws Exception {
        boolean rule = CarpetSettings.commandDigAll, fakeStats = CarpetSettings.fakePlayerStats;
        try {
            check(CarpetSettings.hasRule("commandDig-All"), "Exact hyphenated rule missing");
            CarpetSettings.commandDigAll = false;
            check("false".equals(CarpetSettings.get("commandDig-All")), "Rule field mapping incorrect");
            Scoreboard board = new Scoreboard(); Player alice = player("Alice",board), bob = player("Bob",board);
            CommandDigAll command = new CommandDigAll(); check(!command.checkPermission(null,alice), "Disabled command permitted");
            try { command.execute(null,alice,new String[0]); throw new AssertionError("Disabled command executed"); } catch (CommandException expected) { }
            CarpetSettings.commandDigAll = true; check(command.checkPermission(null,alice), "Enabled command denies ordinary player");
            Server server = allocate(Server.class); server.world = allocate(World.class); server.world.board = board;
            server.players = allocate(Players.class); server.tasks = new ArrayList<>();
            command.execute(server,alice,new String[0]); ScoreObjective objective = board.getObjective("st.dig-all");
            check(objective != null && objective.getCriteria() == StatHelper.DIG_ALL && objective.getCriteria().isReadOnly() && board.getObjectiveInDisplaySlot(1) == objective, "Sidebar/criterion incorrect");
            check(objective.getDisplayName().equals("\u00a76Dig-all"), "Display title incorrect");
            objective.setDisplayName("Blocks mined");
            command.execute(server,alice,new String[0]); check(server.tasks.size() == 1, "Repeated command rescanned history");
            check(objective.getDisplayName().equals("\u00a76Dig-all"), "Existing title not updated");
            try { command.execute(server,alice,new String[]{"extra"}); throw new AssertionError("Extra args accepted"); } catch (CommandException expected) { }

            StatBase stoneMeta = StatHelper.getBlockStateStats(Blocks.STONE.getStateFromMeta(1));
            check(stoneMeta instanceof StatSubItem, "Metadata fixture missing");
            StatBase stone = ((StatSubItem)stoneMeta).getBase(), dirt = StatHelper.getBlockStateStats(Blocks.DIRT.getDefaultState()), grass = StatHelper.getBlockStateStats(Blocks.GRASS.getDefaultState());
            if (dirt instanceof StatSubItem) dirt = ((StatSubItem)dirt).getBase();
            alice.addStat(stoneMeta,3); alice.addStat(dirt,4); alice.addStat(grass,2);
            alice.addStat(StatList.getObjectUseStats(Items.DIAMOND_PICKAXE),99);
            alice.addStat(StatList.getObjectUseStats(Items.DIAMOND_SHOVEL),88);
            check(StatHelper.countMinedBlocks(alice.getStatFile()) == 9 && score(board,objective,"Alice") == 9 && score(board,objective,"Total") == 9, "Metadata doubled, alias omitted or tool uses counted");
            bob.getStatFile().unlockAchievement(bob,dirt,5); StatHelper.updateDigAll(bob);
            check(score(board,objective,"Bob") == 5 && score(board,objective,"Total") == 14, "Historical/login values omitted");
            CarpetSettings.commandDigAll = false; bob.addStat(dirt,1);
            check(score(board,objective,"Bob") == 6 && score(board,objective,"Total") == 15, "Disabled command froze existing scores");
            alice.getStatFile().saveStatFile(); StatisticsManagerServer saved = new StatisticsManagerServer(null,Files.createTempFile("unused", ".json").toFile());
            Field file = StatisticsManagerServer.class.getDeclaredField("statsFile"); file.setAccessible(true); file.set(saved,file.get(alice.getStatFile())); saved.readStatFile();
            check(StatHelper.countMinedBlocks(saved) == 9, "Saved history changed");

            Method apply = StatHelper.class.getDeclaredMethod("applyInitialScores",Scoreboard.class,MinecraftServer.class,ScoreObjective.class,IScoreCriteria.class,int.class,Map.class,Map.class); apply.setAccessible(true);
            EntityPlayerMPFake bot = allocate(EntityPlayerMPFake.class); bot.world = server.world; bot.setUniqueId(UUID.randomUUID());
            Field profile = EntityPlayer.class.getDeclaredField("gameProfile"); profile.setAccessible(true); profile.set(bot,new GameProfile(bot.getUniqueID(),"Bot"));
            Field botStats = EntityPlayerMP.class.getDeclaredField("statsFile"); botStats.setAccessible(true);
            botStats.set(bot,new StatisticsManagerServer(null,Files.createTempFile("kisde-dig-bot-", ".json").toFile()));
            CarpetSettings.fakePlayerStats = false; StatHelper.updateDigAll(bot);
            check(!board.entityHasObjective("Bot",objective), "Disabled fake-player stats created a bot row on join");
            bot.getStatFile().unlockAchievement(bot,dirt,7); server.players.online = bot;
            Map<UUID,Integer> botValues = new HashMap<>(); botValues.put(bot.getUniqueID(),7);
            Map<UUID,String> botNames = new HashMap<>(); botNames.put(bot.getUniqueID(),"Bot");
            apply.invoke(null,board,server,objective,StatHelper.DIG_ALL,1,botValues,botNames);
            check(!board.entityHasObjective("Bot",objective) && score(board,objective,"Total") == 15, "History initialization included a disabled bot");
            CarpetSettings.fakePlayerStats = true; bot.addStat(dirt,2);
            check(score(board,objective,"Bot") == 9 && score(board,objective,"Total") == 24, "Enabled bot mining stats were excluded");
            CarpetSettings.fakePlayerStats = false; bot.addStat(dirt,2); StatHelper.updateDigAll(bot);
            check(score(board,objective,"Bot") == 9 && score(board,objective,"Total") == 24, "Disabled bot changed existing stats");
            board.removeObjectiveFromEntity("Bot",objective); server.players.online = null;
            StatHelper.updateDigAll(bob);
            UUID offline = UUID.randomUUID(); Map<UUID,Integer> values = new HashMap<>(); values.put(offline,7); Map<UUID,String> names = new HashMap<>(); names.put(offline,"Offline");
            apply.invoke(null,board,server,objective,StatHelper.DIG_ALL,1,values,names);
            check(score(board,objective,"Offline") == 7 && score(board,objective,"Total") == 22, "Offline initialization lost live rows/total");
            alice.getStatFile().unlockAchievement(alice,stone,Integer.MAX_VALUE); alice.getStatFile().unlockAchievement(alice,dirt,Integer.MAX_VALUE); StatHelper.updateDigAll(alice);
            check(score(board,objective,"Alice") == Integer.MAX_VALUE && score(board,objective,"Total") == Integer.MAX_VALUE, "Score overflowed");
            alice.getStatFile().unlockAchievement(alice,stone,0); alice.getStatFile().unlockAchievement(alice,dirt,0); StatHelper.updateDigAll(alice);
            check(score(board,objective,"Total") == 15, "Total stayed incorrectly saturated");

            ScoreboardSaveData data = new ScoreboardSaveData(); data.setScoreboard(board); NBTTagCompound nbt = data.writeToNBT(new NBTTagCompound());
            Scoreboard loaded = new Scoreboard(); data = new ScoreboardSaveData(); data.setScoreboard(loaded); data.readFromNBT(nbt);
            check(loaded.getObjective("st.dig-all").getCriteria() == StatHelper.DIG_ALL && score(loaded,loaded.getObjective("st.dig-all"),"Total") == 15, "Persistence lost aggregate criterion/scores");
            board.removeObjective(objective); ScoreObjective replacement = board.addScoreObjective("st.dig-all",IScoreCriteria.DUMMY);
            apply.invoke(null,board,server,objective,StatHelper.DIG_ALL,1,values,names); check(board.getSortedScores(replacement).isEmpty(), "Stale initialization changed replacement");
            CarpetSettings.commandDigAll = true;
            try { command.execute(server,alice,new String[0]); throw new AssertionError("Conflicting scoreboard overwritten"); } catch (CommandException expected) { }
            check(board.getObjective("st.dig-all") == replacement, "Conflict removed existing scoreboard");
            System.out.println("PASS dig-all rule, native metadata/base mining, history, live totals, saturation, persistence and stale/conflict guards");
        } finally { CarpetSettings.commandDigAll = rule; CarpetSettings.fakePlayerStats = fakeStats; }
    }
}
