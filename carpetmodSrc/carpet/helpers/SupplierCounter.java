package carpet.helpers;

import carpet.utils.Messenger;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SupplierCounter
{
    public static final Map<String, HopperCounter> COUNTERS;

    static
    {
        Map<String, HopperCounter> counters = new LinkedHashMap<>();
        for (EnumDyeColor color : EnumDyeColor.values())
        {
            counters.put(color.getName(), new HopperCounter(color, color.getName(), "scounter"));
        }
        COUNTERS = Collections.unmodifiableMap(counters);
    }

    private SupplierCounter() {}

    public static void add(MinecraftServer server, EnumDyeColor color, ItemStack stack)
    {
        if (!stack.isEmpty()) COUNTERS.get(color.getName()).add(server, stack);
    }

    public static HopperCounter getCounter(String name)
    {
        return COUNTERS.get(name);
    }

    public static void resetAll(MinecraftServer server)
    {
        for (HopperCounter counter : COUNTERS.values()) counter.reset(server);
    }

    public static List<ITextComponent> formatAll(MinecraftServer server, boolean realtime, boolean brief)
    {
        List<ITextComponent> text = new ArrayList<>();
        for (HopperCounter counter : COUNTERS.values())
        {
            if (counter.getTotalItems() > 0) text.addAll(counter.format(server, realtime, brief));
        }
        if (text.isEmpty()) text.add(Messenger.s(null, "No items have been supplied yet."));
        return text;
    }
}
