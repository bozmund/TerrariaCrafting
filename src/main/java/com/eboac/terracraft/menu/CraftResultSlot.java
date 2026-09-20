package com.eboac.terracraft.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A display-only cell in the browser grid.
 *
 * <p>It holds a copy of a recipe result so the client will render it, but it is not real
 * storage: nothing may be placed into it and nothing may be picked out of it. Clicks on it are
 * intercepted by {@link CraftBrowserMenu#clicked} and turned into a craft instead.
 */
public class CraftResultSlot extends Slot {

    public CraftResultSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
