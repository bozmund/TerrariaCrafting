package com.eboac.terracraft.crafter;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules that turn a crafter's target item into behaviour: which inputs it accepts, and how
 * its grid should be arranged so vanilla can craft the thing.
 */
public final class CrafterTargeting {

    public static final int GRID = 3;
    public static final int SLOTS = GRID * GRID;

    /** Item -> the recipe making it. Null values are cached too, so unmakeable targets stay cheap. */
    private static final java.util.Map<net.minecraft.world.item.Item, CraftingRecipe> RECIPE_FOR_ITEM =
            new java.util.concurrent.ConcurrentHashMap<>();

    private CrafterTargeting() {
    }

    /**
     * The recipe that produces the crafter's target item, or null if it has no target.
     *
     * <p>Cached per item: this is asked on every hopper insertion attempt, and walking the whole
     * recipe list each time would make a hopper line into a tick sink.
     */
    public static CraftingRecipe recipeFor(MinecraftServer server, Level level, ItemStack target) {
        if (target.isEmpty()) {
            return null;
        }
        return RECIPE_FOR_ITEM.computeIfAbsent(target.getItem(), item -> {
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                if (!(holder.value() instanceof CraftingRecipe recipe)
                        || recipe.isSpecial()
                        || recipe.placementInfo().isImpossibleToPlace()) {
                    continue;
                }
                ItemStack result = com.eboac.terracraft.craft.RecipeScanner.preview(recipe, level);
                if (!result.isEmpty() && result.is(item)) {
                    return recipe;
                }
            }
            return null;
        });
    }

    /**
     * Whether a crafter set to this recipe should accept {@code stack} into {@code slot}.
     *
     * <p>Accepting only what the recipe calls for is the whole point of the feature: a hopper can
     * be pointed at a crafter without needing a filter in front of it.
     */
    public static boolean accepts(CraftingRecipe recipe, CrafterBlockEntity crafter, int slot, ItemStack stack) {
        PlacementInfo placement = recipe.placementInfo();
        List<Ingredient> ingredients = placement.ingredients();

        boolean wanted = ingredients.stream().anyMatch(ingredient -> ingredient.test(stack));
        if (!wanted) {
            return false;
        }

        // One grid slot feeds one cell of the recipe, so there is no point stacking more copies of
        // an ingredient than the recipe has cells for it.
        int cellsNeeding = 0;
        IntList slotToIngredient = placement.slotsToIngredientIndex();
        for (int i = 0; i < slotToIngredient.size(); i++) {
            int index = slotToIngredient.getInt(i);
            if (index != PlacementInfo.EMPTY_SLOT && ingredients.get(index).test(stack)) {
                cellsNeeding++;
            }
        }

        int alreadyHeld = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (i != slot && !crafter.getItem(i).isEmpty() && ItemStack.isSameItem(crafter.getItem(i), stack)) {
                alreadyHeld++;
            }
        }
        return alreadyHeld < cellsNeeding;
    }

    /**
     * What the recipe wants, as one stack per distinct ingredient with its count set to the
     * number of grid cells calling for it.
     */
    public static List<ItemStack> needs(CraftingRecipe recipe) {
        PlacementInfo placement = recipe.placementInfo();
        IntList slotToIngredient = placement.slotsToIngredientIndex();
        List<Ingredient> ingredients = placement.ingredients();

        int width = GRID;
        if (recipe instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
        }

        List<ItemStack> cells = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            cells.add(ItemStack.EMPTY);
        }

        // Laid out exactly where arrange() will put things, so the ghosts the player sees are
        // the cells that will actually be filled.
        for (int cell = 0; cell < slotToIngredient.size(); cell++) {
            int index = slotToIngredient.getInt(cell);
            if (index == PlacementInfo.EMPTY_SLOT) {
                continue;
            }
            int row = cell / width;
            int column = cell % width;
            if (row >= GRID || column >= GRID) {
                continue;
            }
            int destination = row * GRID + column;
            // A tag ingredient accepts many items; the first stands in for the rest on screen.
            int finalDestination = destination;
            ingredients.get(index).items().findFirst()
                    .ifPresent(holder -> cells.set(finalDestination, new ItemStack(holder)));
        }
        return cells;
    }

    /**
     * Shuffles the crafter's grid so the items sit where {@code recipe} expects them.
     *
     * <p>Vanilla matches the grid against recipes by shape, so a hopper dropping planks into
     * whatever slot happens to be free would never line up. Rearranging just before the craft lets
     * vanilla do the actual crafting, ejecting and advancement work untouched.
     *
     * @return true if the grid now holds the recipe
     */
    public static boolean arrange(CraftingRecipe recipe, CrafterBlockEntity crafter) {
        PlacementInfo placement = recipe.placementInfo();
        IntList slotToIngredient = placement.slotsToIngredientIndex();
        List<Ingredient> ingredients = placement.ingredients();

        int width = GRID;
        int height = GRID;
        if (recipe instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
        }

        // Snapshot what we have, ignoring slots the player has switched off.
        List<ItemStack> pool = new ArrayList<>();
        List<Integer> poolSlots = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (crafter.isSlotDisabled(i)) {
                continue;
            }
            ItemStack stack = crafter.getItem(i);
            if (!stack.isEmpty()) {
                pool.add(stack);
                poolSlots.add(i);
            }
        }

        ItemStack[] arranged = new ItemStack[SLOTS];
        java.util.Arrays.fill(arranged, ItemStack.EMPTY);
        boolean[] used = new boolean[pool.size()];

        for (int cell = 0; cell < slotToIngredient.size(); cell++) {
            int index = slotToIngredient.getInt(cell);
            if (index == PlacementInfo.EMPTY_SLOT) {
                continue;
            }
            int row = cell / width;
            int column = cell % width;
            if (row >= height || row >= GRID || column >= GRID) {
                return false;
            }
            int destination = row * GRID + column;
            if (crafter.isSlotDisabled(destination)) {
                return false;
            }

            Ingredient ingredient = ingredients.get(index);
            int chosen = -1;
            for (int i = 0; i < pool.size(); i++) {
                if (!used[i] && ingredient.test(pool.get(i))) {
                    chosen = i;
                    break;
                }
            }
            if (chosen < 0) {
                return false;
            }
            used[chosen] = true;
            arranged[destination] = pool.get(chosen);
        }

        // Anything the recipe did not ask for stays in the crafter rather than vanishing.
        for (int i = 0; i < pool.size(); i++) {
            if (used[i]) {
                continue;
            }
            int free = -1;
            for (int j = 0; j < SLOTS; j++) {
                if (arranged[j].isEmpty() && !crafter.isSlotDisabled(j)) {
                    free = j;
                    break;
                }
            }
            if (free < 0) {
                return false;
            }
            arranged[free] = pool.get(i);
        }

        for (int i = 0; i < SLOTS; i++) {
            if (!crafter.isSlotDisabled(i)) {
                crafter.setItem(i, arranged[i]);
            }
        }
        return true;
    }
}
