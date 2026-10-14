package carpet.helpers;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatCrafting;
import net.minecraft.stats.StatisticsManager;
import net.minecraft.stats.StatisticsManagerServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StatHelper {
    public static final IScoreCriteria DIG_ALL = new ScoreCriteriaReadOnly("stat.mineBlock.all");
    private static final Set<StatBase> MINING_STATS = new HashSet<>();
    private static final Logger LOGGER = LogManager.getLogger();
    private static final StatBase[] BLOCK_STATE_STATS = new StatBase[256 * 16];
    private static final Int2ObjectMap<StatBase> CRAFT_META_STATS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<StatBase> OBJECT_USE_META_STATS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<StatBase> OBJECTS_PICKED_UP_META_STATS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<StatBase> OBJECTS_DROPPED_META_STATS = new Int2ObjectOpenHashMap<>();

    public static File[] getStatFiles(MinecraftServer server) {
        File statsDir = new File(server.getWorld(0).getSaveHandler().getWorldDirectory(), "stats");
        File[] files = statsDir.listFiles((dir, name) -> name.endsWith(".json"));
        return files == null ? new File[0] : files;
    }

    public static void initialize(Scoreboard scoreboard, MinecraftServer server, ScoreObjective objective) {
        initializeWithDividerWithDifferentCriteria(scoreboard, server, objective, 1, objective.getCriteria());
    }

    public static void initializeWithDividerWithDifferentCriteria(Scoreboard scoreboard, MinecraftServer server,
            ScoreObjective objective, int divider, IScoreCriteria criteria) {
        if (!server.isCallingFromMinecraftThread()) {
            server.addScheduledTask(() -> initializeWithDividerWithDifferentCriteria(scoreboard, server, objective, divider, criteria));
            return;
        }
        if ((criteria != DIG_ALL && !(criteria instanceof ScoreCriteriaStat)) || scoreboard.getObjective(objective.getName()) != objective) return;
        if (divider <= 0) throw new IllegalArgumentException("Score divider must be positive");
        Map<UUID, File> files = new HashMap<>();
        Map<UUID, Integer> values = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        for (File file : getStatFiles(server)) {
            try {
                String filename = file.getName();
                UUID uuid = UUID.fromString(filename.substring(0, filename.length() - 5));
                files.put(uuid, file);
                EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(uuid);
                if (player != null) values.put(uuid, statisticValue(server.getPlayerList().getPlayerStatsFile(player), criteria));
                GameProfile profile = server.getPlayerProfileCache().getProfileByUUID(uuid);
                if (profile != null) names.put(uuid, profile.getName());
            } catch (IllegalArgumentException ignored) {}
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            values.put(player.getUniqueID(), statisticValue(player.getStatFile(), criteria));
            names.put(player.getUniqueID(), player.getName());
            if (criteria == DIG_ALL) updateDigAll(player);
        }
        MinecraftSessionService sessionService = server.getMinecraftSessionService();
        CompletableFuture.runAsync(() -> {
            try {
                for (Map.Entry<UUID, File> entry : files.entrySet()) {
                    UUID uuid = entry.getKey();
                    if (!values.containsKey(uuid)) {
                        StatisticsManagerServer statistics = new StatisticsManagerServer(server, entry.getValue());
                        statistics.readStatFile();
                        values.put(uuid, statisticValue(statistics, criteria));
                    }
                    if (values.get(uuid) / divider != 0 && !names.containsKey(uuid)) {
                        GameProfile profile = sessionService.fillProfileProperties(new GameProfile(uuid, null), false);
                        if (profile.isComplete()) names.put(uuid, profile.getName());
                        else LOGGER.warn("Could not find name of user " + uuid);
                    }
                }
                server.addScheduledTask(() -> applyInitialScores(scoreboard, server, objective, criteria, divider, values, names));
            } catch (Exception e) {
                LOGGER.warn("Unable to initialize scoreboard " + objective.getName(), e);
            }
        });
    }

    private static void applyInitialScores(Scoreboard scoreboard, MinecraftServer server, ScoreObjective objective,
            IScoreCriteria criteria, int divider, Map<UUID, Integer> values, Map<UUID, String> names) {
        if (!server.isServerRunning() || scoreboard.getObjective(objective.getName()) != objective) return;
        int total = 0;
        for (Map.Entry<UUID, Integer> entry : values.entrySet()) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(entry.getKey());
            int value = (player == null ? entry.getValue() : statisticValue(server.getPlayerList().getPlayerStatsFile(player), criteria)) / divider;
            String name = player == null ? names.get(entry.getKey()) : player.getName();
            if (value == 0 || name == null) continue;
            scoreboard.getOrCreateScore(name, objective).setScorePoints(value);
            total += value;
        }
        if (criteria == DIG_ALL) updateDigTotal(scoreboard, objective);
        else scoreboard.getOrCreateScore("Total", objective).setScorePoints(total);
    }

    private static int statisticValue(StatisticsManager statistics, IScoreCriteria criteria) {
        return criteria == DIG_ALL ? countMinedBlocks(statistics) : statistics.readStat(((ScoreCriteriaStat)criteria).stat);
    }

    public static int countMinedBlocks(StatisticsManager statistics) {
        long total = 0;
        for (StatBase stat : MINING_STATS) total += Math.max(0, statistics.readStat(stat));
        return (int)Math.min(Integer.MAX_VALUE, total);
    }

    public static boolean isMiningStat(StatBase stat) { return MINING_STATS.contains(stat); }

    public static void updateDigAll(EntityPlayerMP player) {
        Scoreboard scoreboard = player.getWorldScoreboard();
        for (ScoreObjective objective : scoreboard.getObjectivesFromCriteria(DIG_ALL)) {
            scoreboard.getOrCreateScore(player.getName(), objective).setScorePoints(countMinedBlocks(player.getStatFile()));
            updateDigTotal(scoreboard, objective);
        }
    }

    private static void updateDigTotal(Scoreboard scoreboard, ScoreObjective objective) {
        long total = 0;
        for (Score score : scoreboard.getSortedScores(objective)) {
            if (!"Total".equals(score.getPlayerName())) total += Math.max(0, score.getScorePoints());
        }
        scoreboard.getOrCreateScore("Total", objective).setScorePoints((int)Math.min(Integer.MAX_VALUE, total));
    }

    public static StatBase getBlockStateStats(IBlockState state) {
        Block block = state.getBlock();
        int id = Block.getIdFromBlock(block);
        int meta = block.getMetaFromState(state);
        return BLOCK_STATE_STATS[(id << 4) | meta];
    }

    public static StatBase getCraftStats(Item item, int meta) {
        int id = Item.getIdFromItem(item);
        return CRAFT_META_STATS.get((id << 4) | (meta & 0xf));
    }

    public static StatBase getObjectUseStats(Item item, int meta) {
        int id = Item.getIdFromItem(item);
        return OBJECT_USE_META_STATS.get((id << 4) | (meta & 0xf));
    }

    public static StatBase getObjectsPickedUpStats(Item item, int meta) {
        int id = Item.getIdFromItem(item);
        return OBJECTS_PICKED_UP_META_STATS.get((id << 4) | (meta & 0xf));
    }

    public static StatBase getDroppedObjectStats(Item item, int meta) {
        int id = Item.getIdFromItem(item);
        return OBJECTS_DROPPED_META_STATS.get((id << 4) | (meta & 0xf));
    }

    public static void addCraftStats(Item item, StatCrafting baseStat) {
        int id = Item.getIdFromItem(item);
        if (item.getHasSubtypes()) {
            for (int meta = 0; meta < 16; meta++) {
                int stateId = (id << 4) | meta;
                ItemStack stackWithMeta = new ItemStack(item, 1, meta);
                ITextComponent text = new TextComponentTranslation("stat.craftItem", stackWithMeta.getTextComponent());
                StatSubItem statWithMeta = (StatSubItem) new StatSubItem(baseStat, meta, text).registerStat();
                CRAFT_META_STATS.put(stateId, statWithMeta);
            }
        } else {
            for (int meta = 0; meta < 16; meta++) {
                int stateId = (id << 4) | meta;
                CRAFT_META_STATS.put(stateId, baseStat);
            }
        }
    }

    public static void addMineStats(Block block, StatCrafting baseStat) {
        MINING_STATS.add(baseStat);
        Item item = Item.getItemFromBlock(block);
        int id = Item.getIdFromItem(item);
        if (item.getHasSubtypes()) {
            for (int meta = 0; meta < 16; meta++) {
                int stateId = (id << 4) | meta;
                ItemStack stackWithMeta = new ItemStack(block, 1, meta);
                ITextComponent text = new TextComponentTranslation("stat.mineBlock", stackWithMeta.getTextComponent());
                StatSubItem statWithMeta = (StatSubItem) new StatSubItem(baseStat, meta, text).registerStat();
                BLOCK_STATE_STATS[stateId] = statWithMeta;
            }
        } else {
            for (int meta = 0; meta < 16; meta++) {
                int stateId = (id << 4) | meta;
                BLOCK_STATE_STATS[stateId] = baseStat;
            }
        }
    }

    public static void addUseStats(Item item, StatCrafting baseStat) {
        int id = Item.getIdFromItem(item);
        if (item.getHasSubtypes()) {
            for (int meta = 0; meta < 16; meta++) {
                int stateId = (id << 4) | meta;
                ItemStack stackWithMeta = new ItemStack(item, 1, meta);
                ITextComponent text = new TextComponentTranslation("stat.useItem", stackWithMeta.getTextComponent());
                StatSubItem statWithMeta = (StatSubItem) new StatSubItem(baseStat, meta, text).registerStat();
                OBJECT_USE_META_STATS.put(stateId, statWithMeta);
            }
        } else {
            for (int meta = 0; meta < 16; meta++) {
                int stateId = (id << 4) | meta;
                OBJECT_USE_META_STATS.put(stateId, baseStat);
            }
        }
    }

    public static void addPickedUpAndDroppedStats(Item item, StatCrafting basePickupStat, StatCrafting baseDropStat) {
        int id = Item.getIdFromItem(item);
        if (item.getHasSubtypes()) {
            for (int meta = 0; meta < 16; meta++) {
                int stateId = (id << 4) | meta;
                ItemStack stackWithMeta = new ItemStack(item, 1, meta);
                ITextComponent textPickup = new TextComponentTranslation("stat.pickup", stackWithMeta.getTextComponent());
                StatSubItem pickupWithMeta = (StatSubItem) new StatSubItem(basePickupStat, meta, textPickup).registerStat();
                OBJECTS_PICKED_UP_META_STATS.put(stateId, pickupWithMeta);
                ITextComponent textDrop = new TextComponentTranslation("stat.drop", stackWithMeta.getTextComponent());
                StatSubItem dropWithMeta = (StatSubItem) new StatSubItem(baseDropStat, meta, textDrop).registerStat();
                OBJECTS_DROPPED_META_STATS.put(stateId, dropWithMeta);
            }
        } else {
            for (int meta = 0; meta < 16; meta++) {
                int stateId = (id << 4) | meta;
                OBJECTS_PICKED_UP_META_STATS.put(stateId, basePickupStat);
                OBJECTS_DROPPED_META_STATS.put(stateId, baseDropStat);
            }
        }
    }
}
