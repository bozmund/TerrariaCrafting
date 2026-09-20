package com.eboac.terracraft.craft;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns "every crafting recipe in the game" into "the list this player sees right now".
 *
 * <p>Terraria ignores the *shape* of a recipe -- you never arrange anything in a grid. We keep
 * that feel by reconstructing a valid grid ourselves from the recipe's own placement data, so
 * shaped and shapeless recipes both behave like a flat shopping list of ingredients.
 *
 * <p>All of this is server-only. {@code RecipeManager} lives on the server, and computing
 * craftability on the client would be trivially cheatable.
 */
public final class RecipeScanner {

    /**
     * A recipe's result never changes, but working it out means building a grid and assembling.
     * Doing that for ~1500 recipes on every refresh -- and we refresh on every craft -- is the
     * difference between an instant browser and a visible stutter. Recipes are reloaded on
     * /reload, so the cache is cleared from {@link #invalidate()}.
     */
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>, ItemStack>
            PREVIEW_CACHE = new ConcurrentHashMap<>();

    private RecipeScanner() {
    }

    public static void invalidate() {
        PREVIEW_CACHE.clear();
    }

    /**
     * Builds the full browser list.
     *
     * @param includeUncraftable when false, recipes the player cannot afford are dropped entirely
     * @param search             lower-cased substring filter on the result's display name, or blank
     */
    public static List<CraftEntry> scan(MinecraftServer server,
                                        Level level,
                                        IngredientPool pool,
                                        boolean includeUncraftable,
                                        String search) {

        String query = search.trim().toLowerCase(Locale.ROOT);
        List<CraftEntry> entries = new ArrayList<>();

        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe recipe)) {
                continue;
            }
            // Special recipes (map cloning, firework assembly, armour dyeing...) compute their
            // result from the exact inputs, so there is no single item to show in a browser.
            if (recipe.isSpecial() || recipe.placementInfo().isImpossibleToPlace()) {
                continue;
            }
            // Terraria-style station rule: without a crafting table nearby you are limited to
            // what fits in the 2x2 grid you always carry with you.
            if (!pool.hasCraftingTableNearby() && !fitsInHandGrid(recipe)) {
                continue;
            }

            ItemStack preview = PREVIEW_CACHE.computeIfAbsent(holder.id(), key -> preview(recipe, level));
            if (preview.isEmpty()) {
                continue;
            }
            if (!query.isEmpty()
                    && !preview.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }

            CraftAttempt attempt = attempt(recipe, pool);
            boolean craftable = attempt != null && recipe.matches(attempt.input(), level);

            if (!craftable && !includeUncraftable) {
                continue;
            }

            @SuppressWarnings("unchecked")
            RecipeHolder<CraftingRecipe> typed = (RecipeHolder<CraftingRecipe>) holder;
            entries.add(new CraftEntry(typed, preview, craftable));
        }

        // Craftable first, then alphabetical, so the useful half of the list is always on top.
        entries.sort(Comparator
                .comparing((CraftEntry e) -> !e.craftable())
                .thenComparing(e -> e.result().getHoverName().getString()));

        return entries;
    }

    /** True if the recipe would fit in the player's 2x2 inventory grid. */
    private static boolean fitsInHandGrid(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.getWidth() <= 2 && shaped.getHeight() <= 2;
        }
        return recipe.placementInfo().ingredients().size() <= 4;
    }

    /**
     * The result to show in the browser, built from one canonical item per ingredient rather
     * than from the player's stock -- so recipes you cannot currently afford still show what
     * they would produce.
     */
    public static ItemStack preview(CraftingRecipe recipe, Level level) {
        CraftAttempt attempt = build(recipe, ingredient -> {
            Optional<Holder<Item>> first = ingredient.items().findFirst();
            return first.map(holder -> new ItemStack(holder)).orElse(ItemStack.EMPTY);
        });
        if (attempt == null || !recipe.matches(attempt.input(), level)) {
            return ItemStack.EMPTY;
        }
        return recipe.assemble(attempt.input());
    }

    /**
     * Tries to fill the recipe's grid from the pool.
     *
     * @return an attempt carrying both the grid and what it would consume, or null if the
     *         player cannot cover every ingredient
     */
    public static CraftAttempt attempt(CraftingRecipe recipe, IngredientPool pool) {
        PlacementInfo placement = recipe.placementInfo();
        IntList slotToIngredient = placement.slotsToIngredientIndex();
        List<Ingredient> ingredients = placement.ingredients();

        int[] claimed = new int[pool.sourceCount()];
        int[] gridSource = new int[slotToIngredient.size()];

        // Allocate the most constrained ingredients first. Without this, a recipe needing both
        // "any log" and "oak log specifically" could hand the only oak log to the loose
        // ingredient and then wrongly report the recipe as uncraftable.
        Integer[] order = new Integer[slotToIngredient.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
            gridSource[i] = -1;
        }
        java.util.Arrays.sort(order, Comparator.comparingInt(slot -> {
            int index = slotToIngredient.getInt(slot);
            return index == PlacementInfo.EMPTY_SLOT
                    ? Integer.MAX_VALUE
                    : pool.candidateCount(ingredients.get(index));
        }));

        for (int slot : order) {
            int ingredientIndex = slotToIngredient.getInt(slot);
            if (ingredientIndex == PlacementInfo.EMPTY_SLOT) {
                continue;
            }
            int source = pool.findAvailable(ingredients.get(ingredientIndex), claimed);
            if (source < 0) {
                return null;
            }
            claimed[source]++;
            gridSource[slot] = source;
        }

        int[] dimensions = dimensions(recipe, slotToIngredient.size());
        int width = dimensions[0];
        int height = dimensions[1];

        List<ItemStack> grid = new ArrayList<>(width * height);
        for (int i = 0; i < width * height; i++) {
            int source = i < gridSource.length ? gridSource[i] : -1;
            grid.add(source < 0 ? ItemStack.EMPTY : pool.stackAt(source).copyWithCount(1));
        }

        return new CraftAttempt(net.minecraft.world.item.crafting.CraftingInput.of(width, height, grid), claimed);
    }

    /** Shared grid-filling used by both {@link #preview} and {@link #attempt}. */
    private static CraftAttempt build(CraftingRecipe recipe, java.util.function.Function<Ingredient, ItemStack> chooser) {
        PlacementInfo placement = recipe.placementInfo();
        IntList slotToIngredient = placement.slotsToIngredientIndex();
        List<Ingredient> ingredients = placement.ingredients();

        int[] dimensions = dimensions(recipe, slotToIngredient.size());
        int width = dimensions[0];
        int height = dimensions[1];

        List<ItemStack> grid = new ArrayList<>(width * height);
        for (int i = 0; i < width * height; i++) {
            grid.add(ItemStack.EMPTY);
        }

        for (int slot = 0; slot < slotToIngredient.size(); slot++) {
            int ingredientIndex = slotToIngredient.getInt(slot);
            if (ingredientIndex == PlacementInfo.EMPTY_SLOT) {
                continue;
            }
            ItemStack chosen = chooser.apply(ingredients.get(ingredientIndex));
            if (chosen.isEmpty()) {
                return null;
            }
            grid.set(slot, chosen.copyWithCount(1));
        }

        return CraftAttempt.of(width, height, grid, null);
    }

    /**
     * A shaped recipe already knows its grid size. A shapeless one does not care, so we lay its
     * ingredients out left-to-right in up to three columns.
     */
    private static int[] dimensions(CraftingRecipe recipe, int slotCount) {
        if (recipe instanceof ShapedRecipe shaped) {
            return new int[]{shaped.getWidth(), shaped.getHeight()};
        }
        int width = Math.max(1, Math.min(slotCount, 3));
        int height = Math.max(1, (slotCount + 2) / 3);
        return new int[]{width, height};
    }
}
