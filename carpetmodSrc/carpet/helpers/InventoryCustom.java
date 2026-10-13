package carpet.helpers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class InventoryCustom extends InventoryBasic {

    private InventoryLoadedListener onLoadInventory;
    private InventoryChangedListener onChangeInventory;
    private InventoryClosedListener onCloseInventory;
    public EntityPlayerMP entityPlayerOwner;
    public EntityPlayerMP entityPlayer;
    private boolean loaded;
    private ItemStack[] previous;
    private Supplier<NBTTagList> refreshSource;
    private NBTTagList lastSource;

    public InventoryCustom(String title, boolean customName, int slotCount, InventoryLoadedListener onLoadInventory, InventoryChangedListener onChangeInventory, @Nullable InventoryClosedListener onCloseInventory, EntityPlayerMP entityPlayerOwner, EntityPlayerMP entityPlayer) {
        super(title, customName, slotCount);

        this.onLoadInventory = onLoadInventory;
        this.onChangeInventory = onChangeInventory;
        this.onCloseInventory = onCloseInventory;
        this.entityPlayerOwner = entityPlayerOwner;
        this.entityPlayer = entityPlayer;
    }

    public void loadInventory(NBTTagList nbttaglist) {
        this.loaded = false;
        super.clear();
        this.onLoadInventory.load(this, nbttaglist);
        this.lastSource = nbttaglist.copy();
        this.previous = new ItemStack[this.getSizeInventory()];
        for (int slot = 0; slot < this.previous.length; slot++) this.previous[slot] = this.getStackInSlot(slot).copy();
        this.loaded = true;
    }

    public void setRefreshSource(Supplier<NBTTagList> source) { this.refreshSource = source; }

    public void refresh() {
        if (this.refreshSource != null) {
            NBTTagList current = this.refreshSource.get();
            if (!current.equals(this.lastSource)) this.loadInventory(current);
        }
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        this.refresh();
        return super.isUsableByPlayer(player);
    }

    public void closeInventory(EntityPlayer player) {
        this.markDirty();
        super.closeInventory(player);
        if(this.onCloseInventory != null) this.onCloseInventory.close(this);
    }

    @Override
    public void markDirty() {
        if (this.loaded) {
            for (int slot = 0; slot < this.previous.length; slot++) {
                ItemStack stack = this.getStackInSlot(slot);
                if (!ItemStack.areItemStacksEqual(stack, this.previous[slot])) {
                    this.previous[slot] = stack.copy();
                    this.onChangeInventory.change(this, slot, stack.copy());
                }
            }
        }
        super.markDirty();
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        ItemStack removed = super.removeStackFromSlot(index);
        this.markDirty();
        return removed;
    }

    @Override
    public void clear() {
        super.clear();
        this.markDirty();
    }

    public interface InventoryLoadedListener {
        void load(IInventory inventory, NBTTagList nbttaglist);
    }

    public interface InventoryChangedListener {
        void change(IInventory inventory, int index, ItemStack itemStack);
    }

    public interface InventoryClosedListener {
        void close(IInventory inventory);
    }

}
