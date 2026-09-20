package com.eboac.terracraft.craft;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out whether an item is reachable through a *chain* of crafts, not just one.
 *
 * <p>Terraria shows you a crafting table as available when you are holding a single log, because
 * the log becomes planks and the planks become the table. This does the same: given the items the
 * player can reach, it searches backwards through recipes for anything missing, and returns the
 * list of intermediate crafts to run first.
 *
 * <p>The search is count-aware -- it tracks how many of each item it has spent, so one plank does
 * not get reported as enough for a recipe needing four. That honesty costs a little speed, which
 * is why the depth is capped and only a handful of candidate recipes per item are considered.
 */
public final class CraftingPlanner {

    /** How many crafts deep to look. Three covers log -> planks -> sticks -> tools. */
    private static final int MAX_DEPTH = 3;

    /** Some items have many recipes producing them. Trying them all is rarely worth the time. */
    private static final int MAX_RECIPES_PER_ITEM = 3;

    /**
     * Hard ceiling on search nodes per recipe. Recipes the player genuinely cannot reach are the
     * expensive case -- they make the search explore every branch before failing -- and there are
     * far more of those than reachable ones. Without this ceiling a tag-heavy recipe could burn
     * tens of thousands of nodes, and we run this for ~1500 recipes every time the browser opens.
     */
    private static final int NODE_BUDGET = 600;

    /** Tag ingredients can accept dozens of items; the first few are representative enough. */
    private static final int MAX_CANDIDATES_TO_CRAFT = 6;

    private final Map<Item, Integer> initial = new HashMap<>();
    private final Map<Item, Integer> available = new HashMap<>();
    private final Map<Item, List<RecipeHolder<CraftingRecipe>>> byOutput;
    private final Map<RecipeHolder<CraftingRecipe>, ItemStack> results;
    private final Map<Ingredient, List<Item>> candidateCache = new IdentityHashMap<>();

    private final List<RecipeHolder<CraftingRecipe>> steps = new ArrayList<>();
    private final Set<ResourceKey<Recipe<?>>> active = new HashSet<>();
    private int budget;

    public CraftingPlanner(IngredientPool pool,
                           Map<Item, List<RecipeHolder<CraftingRecipe>>> byOutput,
                           Map<RecipeHolder<CraftingRecipe>, ItemStack> results) {
        this.byOutput = byOutput;
        this.results = results;
        for (int i = 0; i < pool.sourceCount(); i++) {
            ItemStack stack = pool.stackAt(i);
            initial.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
    }

    /**
     * @return the intermediate crafts needed before {@code target} can be made, in the order they
     *         must be run (empty if the player can already make it outright), or null if no chain
     *         within the depth limit gets there
     */
    public List<RecipeHolder<CraftingRecipe>> plan(CraftingRecipe target) {
        available.clear();
        available.putAll(initial);
        steps.clear();
        active.clear();
        budget = NODE_BUDGET;

        if (!consumeIngredients(target, MAX_DEPTH)) {
            return null;
        }
        return List.copyOf(steps);
    }

    /** Spends one of everything the recipe needs, crafting prerequisites if it has to. */
    private boolean consumeIngredients(CraftingRecipe recipe, int depth) {
        PlacementInfo placement = recipe.placementInfo();
        IntList slotToIngredient = placement.slotsToIngredientIndex();
        List<Ingredient> ingredients = placement.ingredients();

        for (int slot = 0; slot < slotToIngredient.size(); slot++) {
            int index = slotToIngredient.getInt(slot);
            if (index == PlacementInfo.EMPTY_SLOT) {
                continue;
            }
            if (!consumeOne(ingredients.get(index), depth)) {
                return false;
            }
        }
        return true;
    }

    /** Spends one item satisfying this ingredient, making one first if none is in stock. */
    private boolean consumeOne(Ingredient ingredient, int depth) {
        List<Item> candidates = candidates(ingredient);

        // Prefer something we already have -- no point crafting planks when planks are in the chest.
        for (Item item : candidates) {
            if (available.getOrDefault(item, 0) > 0) {
                available.merge(item, -1, Integer::sum);
                return true;
            }
        }

        if (depth <= 0 || budget <= 0) {
            return false;
        }

        int examined = 0;
        for (Item item : candidates) {
            if (examined++ >= MAX_CANDIDATES_TO_CRAFT || budget <= 0) {
                break;
            }
            List<RecipeHolder<CraftingRecipe>> producers = byOutput.get(item);
            if (producers == null) {
                continue;
            }
            int tried = 0;
            for (RecipeHolder<CraftingRecipe> producer : producers) {
                if (tried++ >= MAX_RECIPES_PER_ITEM) {
                    break;
                }
                // Cycle guard: planks -> ... -> planks would otherwise recurse forever.
                if (!active.add(producer.id())) {
                    continue;
                }
                budget--;

                Map<Item, Integer> snapshot = new HashMap<>(available);
                int mark = steps.size();

                boolean ok = consumeIngredients(producer.value(), depth - 1);
                active.remove(producer.id());

                if (ok) {
                    ItemStack produced = results.get(producer);
                    int yield = produced == null ? 1 : Math.max(1, produced.getCount());
                    steps.add(producer);
                    // The craft yields several; we spend one and keep the rest for later steps.
                    available.merge(item, yield - 1, Integer::sum);
                    return true;
                }

                available.clear();
                available.putAll(snapshot);
                while (steps.size() > mark) {
                    steps.removeLast();
                }
            }
        }

        return false;
    }

    private List<Item> candidates(Ingredient ingredient) {
        return candidateCache.computeIfAbsent(ingredient,
                ing -> ing.items().map(Holder::value).toList());
    }
}
