package com.eboac.terracraft.crafter;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Implemented by {@code CrafterMenu} through a mixin so the screen can read what it needs. */
public interface CrafterNeeds {

    void terracraft$setNeeds(List<ItemStack> needs);

    List<ItemStack> terracraft$needs();
}
