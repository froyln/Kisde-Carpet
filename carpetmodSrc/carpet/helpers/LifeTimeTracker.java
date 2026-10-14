package carpet.helpers;

import carpet.CarpetSettings;
import com.google.common.base.Predicate;
import java.util.function.Supplier;
import net.minecraft.command.CommandException;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.*;

public final class LifeTimeTracker
{
    private static boolean tracking;
    private static final ThreadLocal<String> spawnReason = new ThreadLocal<>();
    private static String session = UUID.randomUUID().toString();
    private static final Map<Entity, Birth> pending = new WeakHashMap<>();
    private static long clock, startTick, stopTick;
    private static long startMillis = System.currentTimeMillis(), stopMillis = startMillis;
    private static final Map<Entity, Birth> active = new IdentityHashMap<>();
    private static final Map<String, Stats> stats = new TreeMap<>();
    private static final Map<String, Filter> filters = new TreeMap<>();

    public static boolean permitted(ICommandSender sender)
    {
        String level = CarpetSettings.commandLifeTime;
        if ("true".equals(level) || "0".equals(level)) return true;
        if ("false".equals(level)) return false;
        if ("ops".equals(level)) return sender.canUseCommand(2, "lifetime");
        return level.matches("[0-4]") && sender.canUseCommand(Integer.parseInt(level), "lifetime");
    }

    public static void tick(MinecraftServer server)
    {
        clock++;
        if (tracking && "false".equals(CarpetSettings.commandLifeTime)) stop();
    }

    public static boolean isTracking() { return tracking; }

    public static void start(boolean restart)
    {
        if (tracking && !restart) return;
        active.clear(); pending.clear(); stats.clear();
        session = UUID.randomUUID().toString();
        startTick = stopTick = clock;
        startMillis = stopMillis = System.currentTimeMillis();
        tracking = true;
    }

    public static void stop()
    {
        if (!tracking) return;
        stopTick = clock; stopMillis = System.currentTimeMillis();
        tracking = false; active.clear(); pending.clear();
    }

    public static void stopServer()
    {
        stop(); stats.clear(); filters.clear(); clock = 0;
    }

    public static void unloaded(Entity entity) { active.remove(entity); pending.remove(entity); }

    public static void write(Entity entity, NBTTagCompound tag)
    {
        Birth birth = active.get(entity);
        tag.removeTag("KisdeLifetime");
        if (!tracking || birth == null) return;
        NBTTagCompound record = new NBTTagCompound();
        record.setString("Session", session); record.setLong("Tick", birth.tick);
        record.setString("Bucket", birth.bucket); record.setString("Position", birth.position);
        record.setLong("Quantity", birth.quantity);
        tag.setTag("KisdeLifetime", record);
    }

    public static void read(Entity entity, NBTTagCompound tag)
    {
        pending.remove(entity);
        if (!tracking || !tag.hasKey("KisdeLifetime", 10)) return;
        NBTTagCompound record = tag.getCompoundTag("KisdeLifetime");
        if (!session.equals(record.getString("Session")) || !stats.containsKey(record.getString("Bucket"))) return;
        pending.put(entity, new Birth(record.getLong("Tick"), record.getString("Bucket"), record.getString("Position"), record.getLong("Quantity")));
    }

    public static void loaded(Entity entity)
    {
        Birth birth = pending.remove(entity);
        if (tracking && birth != null && !active.containsKey(entity)) active.put(entity, birth);
    }

    public static boolean spawn(World world, Entity entity, String reason)
    {
        return spawnWithReason(reason, () -> world.spawnEntity(entity));
    }

    public static boolean spawnWithReason(String reason, Supplier<Boolean> action)
    {
        if (!tracking) return action.get();
        String previous = spawnReason.get();
        spawnReason.set(reason);
        try { return action.get(); }
        finally { if (previous == null) spawnReason.remove(); else spawnReason.set(previous); }
    }

    public static void spawned(Entity entity, String reason)
    {
        if (!tracking || entity.world.isRemote || active.containsKey(entity) || !tracked(entity)) return;
        pending.remove(entity);
        String scoped = spawnReason.get();
        if (scoped != null) reason = scoped;
        ResourceLocation key = EntityList.getKey(entity);
        if (key == null) return;
        String type = key.toString();
        if (!accepts("global", entity) || !accepts(type, entity)) return;
        String bucket = entity.world.provider.getDimensionType().getId() + " " + type;
        Stats data = stats.computeIfAbsent(bucket, k -> new Stats());
        long quantity = quantity(entity);
        data.spawned++; data.spawnQuantity += quantity;
        data.spawns.merge(reason, 1L, Long::sum);
        active.put(entity, new Birth(entity.world.getTotalWorldTime(), bucket, position(entity), quantity));
    }

    public static void removed(Entity entity, String reason)
    {
        Birth birth = active.get(entity);
        removed(entity, reason, birth == null ? 0 : birth.quantity);
    }

    public static void removed(Entity entity, String reason, long quantity)
    {
        if (!tracking) return;
        Birth birth = active.remove(entity);
        if (birth == null) return;
        Stats data = stats.get(birth.bucket);
        if (data == null) return;
        long age = Math.max(0, entity.world.getTotalWorldTime() - birth.tick);
        data.removed++; data.removeQuantity += Math.max(0, quantity); data.sum += age;
        data.removals.merge(reason, 1L, Long::sum);
        String endpoints = birth.position + " -> " + position(entity);
        if (age < data.min) { data.min = age; data.minPositions = endpoints; }
        if (age > data.max) { data.max = age; data.maxPositions = endpoints; }
    }

    private static boolean tracked(Entity e)
    {
        return e instanceof EntityLiving || e instanceof EntityItem || e instanceof EntityXPOrb || e instanceof EntityMinecart || e instanceof EntityBoat;
    }

    private static long quantity(Entity e)
    {
        if (e instanceof EntityItem) return ((EntityItem)e).getItem().getCount();
        if (e instanceof EntityXPOrb) return ((EntityXPOrb)e).getXpValue();
        return 1;
    }

    private static String position(Entity e)
    {
        return String.format(Locale.ROOT, "dim %d (%.2f, %.2f, %.2f)", e.world.provider.getDimensionType().getId(), e.posX, e.posY, e.posZ);
    }

    private static boolean accepts(String key, Entity entity)
    {
        Filter filter = filters.get(key);
        if (filter == null) return true;
        for (Predicate<Entity> predicate : filter.predicates) if (predicate.apply(entity)) return true;
        return false;
    }

    public static void setFilter(String key, ICommandSender sender, String[] selectors) throws CommandException
    {
        if (selectors.length == 0 || selectors.length > 16) throw new CommandException("Use 1 to 16 entity selectors");
        Filter filter = new Filter(sender, selectors);
        for (String selector : selectors)
        {
            if (!EntitySelector.isSelector(selector)) throw new CommandException("Invalid entity selector: %s", selector);
            filter.predicates.add(com.google.common.base.Predicates.and(EntitySelector.lifetimePredicates(filter, selector)));
        }
        filters.put(key, filter);
    }

    public static void clearFilter(String key) { filters.remove(key); }

    public static List<String> showFilters(String key)
    {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Filter> entry : filters.entrySet())
            if (key == null || key.equals(entry.getKey())) lines.add(entry.getKey() + ": " + String.join(" OR ", entry.getValue().selectors));
        if (lines.isEmpty()) lines.add("No matching filters (all entities accepted)");
        return lines;
    }

    public static List<String> report(String type, String detail, boolean realtime, Integer dimension)
    {
        List<String> lines = new ArrayList<>();
        long ticks = Math.max(0, (tracking ? clock : stopTick) - startTick);
        long millis = Math.max(0, (tracking ? System.currentTimeMillis() : stopMillis) - startMillis);
        double hours = Math.max(1, realtime ? millis / 50.0 : ticks) / 72000.0;
        lines.add(String.format(Locale.ROOT, "Lifetime %s: %d ticks, %.2f real seconds; %d active entities", tracking ? "tracking" : "stopped", ticks, millis / 1000.0, active.size()));
        for (Map.Entry<String, Stats> entry : stats.entrySet())
        {
            String[] key = entry.getKey().split(" ", 2);
            if (type != null && !type.equals(key[1])) continue;
            if (dimension != null && dimension.intValue() != Integer.parseInt(key[0])) continue;
            Stats data = entry.getValue();
            lines.add(String.format(Locale.ROOT, "dim %s %s: spawned %d (quantity %d, %.2f/h), removed %d (quantity %d, %.2f/h)", key[0], key[1], data.spawned, data.spawnQuantity, data.spawned / hours, data.removed, data.removeQuantity, data.removed / hours));
            if (detail == null || "lifetime".equals(detail))
            {
                if (data.removed > 0) {
                    lines.add(String.format(Locale.ROOT, "  lifetime: count %d, sum %d, min %d, max %d, average %.4f ticks", data.removed, data.sum, data.min, data.max, (double)data.sum / data.removed));
                    lines.add("  min: " + data.minPositions); lines.add("  max: " + data.maxPositions);
                } else lines.add("  lifetime: no completed lifetimes");
            }
            if (detail == null || "spawning".equals(detail)) reasons(lines, "spawn", data.spawns, data.spawned, hours);
            if (detail == null || "removal".equals(detail)) reasons(lines, "remove", data.removals, data.removed, hours);
        }
        if (lines.size() == 1) lines.add("No matching entity statistics");
        return lines;
    }

    private static void reasons(List<String> lines, String label, Map<String, Long> reasons, long total, double hours)
    {
        for (Map.Entry<String, Long> entry : reasons.entrySet())
            lines.add(String.format(Locale.ROOT, "  %s %s: %d, %.2f%%, %.2f/h", label, entry.getKey(), entry.getValue(), 100.0 * entry.getValue() / Math.max(1, total), entry.getValue() / hours));
    }

    public static List<String> types()
    {
        Set<String> types = new TreeSet<>();
        for (String key : stats.keySet()) types.add(key.split(" ", 2)[1]);
        return new ArrayList<>(types);
    }

    private static final class Birth
    {
        final long tick, quantity;
        final String bucket, position;
        Birth(long tick, String bucket, String position, long quantity) { this.tick = tick; this.bucket = bucket; this.position = position; this.quantity = quantity; }
    }

    private static final class Stats
    {
        long spawned, removed, spawnQuantity, removeQuantity, sum, min = Long.MAX_VALUE, max = -1;
        String minPositions, maxPositions;
        final Map<String, Long> spawns = new TreeMap<>(), removals = new TreeMap<>();
    }

    private static final class Filter implements ICommandSender
    {
        final MinecraftServer server;
        final int dimension;
        final Vec3d origin;
        final String[] selectors;
        final List<Predicate<Entity>> predicates = new ArrayList<>();
        Filter(ICommandSender sender, String[] selectors)
        {
            server = sender.getServer(); dimension = sender.getEntityWorld().provider.getDimensionType().getId(); origin = sender.getPositionVector();
            this.selectors = selectors.clone();
        }
        public String getName() { return "Lifetime filter"; }
        public boolean canUseCommand(int level, String command) { return level <= 1; }
        public BlockPos getPosition() { return new BlockPos(origin); }
        public Vec3d getPositionVector() { return origin; }
        public World getEntityWorld() { return server.getWorld(dimension); }
        public MinecraftServer getServer() { return server; }
    }
}
