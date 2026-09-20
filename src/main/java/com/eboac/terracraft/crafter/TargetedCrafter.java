package com.eboac.terracraft.crafter;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Implemented by {@code CrafterBlockEntity} through a mixin.
 *
 * <p>A targeted crafter holds one extra slot: a template of the item it is meant to produce.
 * That template is never consumed. Its only job is to tell the crafter which recipe it is for,
 * which in turn decides what the crafter will accept as input.
 */
public interface TargetedCrafter {

    /** The single-slot container holding the template item. Never consumed by crafting. */
    Container terracraft$targetContainer();

    /** The item this crafter is set to produce, or empty if it has not been told. */
    ItemStack terracraft$target();
}
