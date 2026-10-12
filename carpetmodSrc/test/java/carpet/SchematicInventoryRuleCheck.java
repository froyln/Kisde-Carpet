package carpet;

import carpet.schematic.SchematicInventoryRules;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public final class SchematicInventoryRuleCheck {
    public static void main(String[] args) {
        NBTTagCompound source = new NBTTagCompound();
        NBTTagList items = new NBTTagList();
        NBTTagCompound item = new NBTTagCompound(); item.setString("id", "minecraft:diamond"); items.appendTag(item);
        source.setTag("Items", items); source.setString("LootTable", "minecraft:chests/simple_dungeon"); source.setLong("LootTableSeed", 7);
        source.setString("CustomName", "Storage"); source.setString("Text1", "{\"text\":\"Hello\"}");
        NBTTagCompound filtered = source.copy(); SchematicInventoryRules.removeInventory(filtered);
        if (filtered.hasKey("Items") || filtered.hasKey("LootTable") || filtered.hasKey("LootTableSeed")) throw new AssertionError("Inventory leaked");
        if (!filtered.getString("CustomName").equals("Storage") || !filtered.getString("Text1").equals(source.getString("Text1"))) throw new AssertionError("Other data lost");
        if (!source.hasKey("Items")) throw new AssertionError("Source changed");
        NBTTagCompound destination = new NBTTagCompound(); destination.setTag("Items", new NBTTagList());
        NBTTagCompound replacement = source.copy(); SchematicInventoryRules.retainInventory(destination, replacement);
        if (replacement.getTagList("Items", 10).tagCount() != 0 || replacement.hasKey("LootTable")) throw new AssertionError("Source inventory pasted");
        if (!replacement.getString("CustomName").equals("Storage")) throw new AssertionError("Metadata lost");
        replacement = source.copy(); SchematicInventoryRules.retainInventory(null, replacement);
        if (replacement.hasKey("Items") || replacement.hasKey("LootTable")) throw new AssertionError("New container received inventory");
        SchematicInventoryRules.retainInventory(source, replacement);
        if (!replacement.getTagList("Items", 10).equals(source.getTagList("Items", 10))) throw new AssertionError("Existing inventory lost");
        System.out.println("Inventory filtering, loot denial, metadata and destination preservation checks passed");
    }
}
