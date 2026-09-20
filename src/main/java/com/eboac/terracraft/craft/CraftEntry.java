package com.eboac.terracraft.craft;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One row in the browser: a recipe, the item it makes, and how reachable it is.
 *
 * @param steps intermediate crafts to run first; empty means it can be made outright, null means
 *              the player cannot get there at all
 */
public record CraftEntry(RecipeHolder<CraftingRecipe> holder,
                         ItemStack result,
                         java.util.List<RecipeHolder<CraftingRecipe>> steps) {

    /** 0 = craft it now, 1 = craft it via intermediate steps, 2 = cannot. Drives sorting and dimming. */
    public int tier() {
        if (steps == null) {
            return 2;
        }
        return steps.isEmpty() ? 0 : 1;
    }

    public boolean obtainable() {
        return steps != null;
    }
}
