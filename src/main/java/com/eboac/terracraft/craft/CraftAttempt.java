package com.eboac.terracraft.craft;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import java.util.List;

/**
 * A filled-in crafting grid for one recipe.
 *
 * @param input   the grid, ready to hand to {@code Recipe#matches} / {@code Recipe#assemble}
 * @param claimed how many items this craft takes from each {@link IngredientPool} source,
 *                indexed the same way as the pool. Null for a preview attempt, which is
 *                built from canonical items rather than the player's actual stock.
 */
public record CraftAttempt(CraftingInput input, int[] claimed) {

    public static CraftAttempt of(int width, int height, List<ItemStack> grid, int[] claimed) {
        return new CraftAttempt(CraftingInput.of(width, height, grid), claimed);
    }
}
