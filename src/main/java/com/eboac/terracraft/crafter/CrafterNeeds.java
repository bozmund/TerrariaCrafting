package com.eboac.terracraft.crafter;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Implemented by {@code CrafterMenu} through a mixin so the screen can read what it needs. */
public interface CrafterNeeds {

    void terracraft$setNeeds(List<ItemStack> needs);

    List<ItemStack> terracraft$needs();

    /**
     * Server side: send the current requirements to the viewing player if the target has changed
     * since last time. Called once per tick from the menu's change broadcast.
     */
    void terracraft$pushNeeds();
}
