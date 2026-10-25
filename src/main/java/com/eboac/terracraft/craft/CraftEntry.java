package com.eboac.terracraft.craft;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One row in the browser: a recipe, the item it makes, and how reachable it is.
 *
 * @param steps   intermediate crafts to run first; empty means it can be made outright, null means
 *                the player cannot get there at all. Meaningless (and left empty) for a special entry.
 * @param special true for a recipe whose result depends on the exact item placed -- map cloning,
 *                armor dyeing, a modded "codex + paper" style recipe -- which the browser cannot
 *                represent as a single click. Clicking a special entry opens a real crafting grid
 *                instead of crafting anything itself.
 * @param known   whether a {@link com.eboac.terracraft.api.KnowledgeGate} allows this player to make
 *                it. This is a deliberately separate axis from {@link #tier()}: tier answers "can it
 *                be afforded", known answers "is it allowed". A recipe can be tier 0 and unknown,
 *                which is the whole point of showing locked recipes dimmed rather than hiding them.
 *                With no gate installed every entry is known.
 */
public record CraftEntry(RecipeHolder<CraftingRecipe> holder,
                         ItemStack result,
                         java.util.List<RecipeHolder<CraftingRecipe>> steps,
                         boolean special,
                         boolean known) {

    public static CraftEntry normal(RecipeHolder<CraftingRecipe> holder, ItemStack result,
                                    java.util.List<RecipeHolder<CraftingRecipe>> steps, boolean known) {
        return new CraftEntry(holder, result, steps, false, known);
    }

    public static CraftEntry special(RecipeHolder<CraftingRecipe> holder, ItemStack icon, boolean known) {
        return new CraftEntry(holder, icon, java.util.List.of(), true, known);
    }

    /**
     * 0 = craft it now, 1 = craft it via intermediate steps, 2 = cannot, 3 = special (always last).
     * Drives sorting and dimming.
     */
    public int tier() {
        if (special) {
            return 3;
        }
        if (steps == null) {
            return 2;
        }
        return steps.isEmpty() ? 0 : 1;
    }

    public boolean obtainable() {
        return special || steps != null;
    }
}
