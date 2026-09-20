package com.eboac.terracraft.menu;

import com.eboac.terracraft.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class CraftBrowserMenu extends AbstractContainerMenu {

    public CraftBrowserMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.CRAFT_BROWSER, containerId);
        addStandardInventorySlots(playerInventory, 8, 32);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
