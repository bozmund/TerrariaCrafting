package com.eboac.terracraft.craft;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One row in the browser: a recipe, the item it makes, and whether the player can
 * currently afford it.
 */
public record CraftEntry(RecipeHolder<CraftingRecipe> holder, ItemStack result, boolean craftable) {
}
