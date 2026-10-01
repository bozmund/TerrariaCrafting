package com.eboac.terracraft.craft;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Turns "every crafting recipe in the game" into "the list this player sees right now".
 *
 * <p>Terraria ignores the *shape* of a recipe -- you never arrange anything in a grid. We keep
 * that feel by reconstructing a valid grid ourselves from the recipe's own placement data, so
 * shaped and shapeless recipes both behave like a flat shopping list of ingredients.
 *
 * <p>It also follows Terraria in showing an item as available when the player only has the raw
 * material for it: one log counts as a crafting table, because the chain log -> planks -> table
 * can be run for them. {@link CraftingPlanner} does that search.
 *
 * <p>All of this is server-only. {@code RecipeManager} lives on the server, and computing
 * craftability on the client would be trivially cheatable.
 */
public final class RecipeScanner {

    /**
     * A recipe's result never changes, but working it out means building a grid and assembling.
     * Doing that for ~1500 recipes on every refresh -- and we refresh on every craft -- is the
     * difference between an instant browser and a visible stutter.
     */
    private static final Map<ResourceKey<Recipe<?>>, ItemStack> PREVIEW_CACHE = new ConcurrentHashMap<>();

    private RecipeScanner() {
    }

    public static void invalidate() {
        PREVIEW_CACHE.clear();
    }

    /**
     * The outcome of a scan. The two maps are kept because bulk crafting needs to re-plan after
     * every single craft -- the pool changes underneath it -- and rebuilding them per craft would
     * mean walking the whole recipe list again.
     *
     * @param byOutput which recipes produce a given item
     * @param results  each recipe's result stack
     */
    public record ScanResult(List<CraftEntry> entries,
                             Map<Item, List<RecipeHolder<CraftingRecipe>>> byOutput,
                             Map<RecipeHolder<CraftingRecipe>, ItemStack> results) {
    }

    /**
     * Builds the full browser list.
     *
     * @param includeUnobtainable when false, recipes the player cannot reach are dropped entirely
     * @param search              substring filter on the result's display name, or blank
     */
    public static ScanResult scan(MinecraftServer server,
                                  Level level,
                                  IngredientPool pool,
                                  boolean includeUnobtainable,
                                  String search,
                                  ItemStack ingredientFilter) {

        String query = search.trim().toLowerCase(Locale.ROOT);

        // Pass one: every recipe we are willing to show at all, with its result.
        List<RecipeHolder<CraftingRecipe>> usable = new ArrayList<>();
        Map<RecipeHolder<CraftingRecipe>, ItemStack> results = new HashMap<>();
        Map<Item, List<RecipeHolder<CraftingRecipe>>> byOutput = new HashMap<>();

        // Special recipes -- map cloning, firework assembly, armour dyeing, a modded "codex +
        // paper" recipe -- compute their result from the exact item placed (which Cave Codex,
        // which written book), so there is no single item to show as a one-click result. They go
        // in a list of their own; see the loop below.
        List<CraftEntry> specialEntries = new ArrayList<>();

        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe recipe)) {
                continue;
            }
            if (recipe.placementInfo().isImpossibleToPlace()) {
                continue;
            }

            if (recipe.isSpecial()) {
                // These always need the full grid, so require a station the same as any other
                // recipe bigger than the hand grid, and are not affected by the craftable-only
                // toggle -- there is no "craftable" to test without real items in real slots.
                if (!pool.hasCraftingTableNearby()) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                RecipeHolder<CraftingRecipe> typedSpecial = (RecipeHolder<CraftingRecipe>) holder;
                ItemStack icon = specialIcon(typedSpecial);
                if (!query.isEmpty()
                        && !icon.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    continue;
                }
                specialEntries.add(CraftEntry.special(typedSpecial, icon));
                continue;
            }

            // Terraria-style station rule: without a crafting table nearby you are limited to
            // what fits in the 2x2 grid you always carry with you.
            if (!pool.hasCraftingTableNearby() && !fitsInHandGrid(recipe)) {
                continue;
            }
            if (!ingredientFilter.isEmpty() && !usesIngredient(recipe, ingredientFilter)) {
                continue;
            }

            ItemStack preview = PREVIEW_CACHE.computeIfAbsent(holder.id(), key -> preview(recipe, level));
            if (preview.isEmpty()) {
                continue;
            }

            @SuppressWarnings("unchecked")
            RecipeHolder<CraftingRecipe> typed = (RecipeHolder<CraftingRecipe>) holder;
            usable.add(typed);
            results.put(typed, preview);
            byOutput.computeIfAbsent(preview.getItem(), item -> new ArrayList<>()).add(typed);
        }

        // Pass two: how reachable is each one? The planner needs byOutput built first, which is
        // why this cannot be folded into the loop above.
        CraftingPlanner planner = new CraftingPlanner(pool, byOutput, results);
        List<CraftEntry> entries = new ArrayList<>();

        for (RecipeHolder<CraftingRecipe> holder : usable) {
            ItemStack result = results.get(holder);

            if (!query.isEmpty()
                    && !result.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }

            List<RecipeHolder<CraftingRecipe>> steps = planner.plan(holder.value());
            if (steps == null && !includeUnobtainable) {
                continue;
            }
            entries.add(CraftEntry.normal(holder, result, steps));
        }

        entries.addAll(specialEntries);

        // Craftable now, then craftable via a chain, then the rest, then special entries always
        // last -- alphabetical within each tier.
        entries.sort(Comparator
                .comparingInt(CraftEntry::tier)
                .thenComparing(entry -> entry.result().getHoverName().getString()));

        return new ScanResult(entries, byOutput, results);
    }

    /**
     * A representative icon and name for a special recipe, since its real result cannot be
     * computed without the exact item the player intends to use.
     *
     * <p>Most recipes -- vanilla and modded alike -- share their registry id with the item they
     * are "about" (the recipe {@code alexscaves:cave_biome_map} produces the item of the same
     * id), so that is tried first. Falling back to a plain crafting table icon with the id's path
     * turned into a readable name covers whatever does not follow that convention.
     */
    private static ItemStack specialIcon(RecipeHolder<CraftingRecipe> holder) {
        Identifier id = holder.id().identifier();
        Optional<Item> directMatch = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id);

        ItemStack icon = directMatch.map(item -> new ItemStack(item, 1))
                .orElseGet(() -> new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE));

        if (directMatch.isEmpty()) {
            String[] words = id.getPath().split("_");
            StringBuilder name = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
                }
            }
            icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(name.toString().strip()));
        }
        return icon;
    }

    /**
     * What one craft of this recipe costs, as one stack per distinct ingredient with its count
     * set to how many cells call for it.
     *
     * <p>{@code ingredients()} is one entry per grid cell rather than per distinct item, so a
     * recipe wanting five iron ingots arrives as five separate entries and has to be grouped.
     */
    public static List<ItemStack> ingredientCounts(CraftingRecipe recipe) {
        PlacementInfo placement = recipe.placementInfo();
        IntList slotToIngredient = placement.slotsToIngredientIndex();
        List<Ingredient> ingredients = placement.ingredients();

        Map<Item, Integer> counts = new java.util.LinkedHashMap<>();
        for (int cell = 0; cell < slotToIngredient.size(); cell++) {
            int index = slotToIngredient.getInt(cell);
            if (index == PlacementInfo.EMPTY_SLOT) {
                continue;
            }
            // A tag ingredient accepts many items; the first stands in for the rest on screen.
            ingredients.get(index).items().findFirst()
                    .ifPresent(holder -> counts.merge(holder.value(), 1, Integer::sum));
        }

        List<ItemStack> costs = new ArrayList<>(counts.size());
        counts.forEach((item, count) -> costs.add(new ItemStack(item, count)));
        return costs;
    }

    /** True if this recipe consumes the given item, so "what can I make from this?" can be asked. */
    private static boolean usesIngredient(CraftingRecipe recipe, ItemStack stack) {
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.test(stack)) {
                return true;
            }
        }
        return false;
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
            return first.map(ItemStack::new).orElse(ItemStack.EMPTY);
        });
        if (attempt == null || !recipe.matches(attempt.input(), level)) {
            return ItemStack.EMPTY;
        }
        return recipe.assemble(attempt.input());
    }

    /**
     * Tries to fill the recipe's grid from the pool as it stands right now -- no chains.
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
        Arrays.sort(order, Comparator.comparingInt(slot -> {
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

        return new CraftAttempt(CraftingInput.of(width, height, grid), claimed);
    }

    /** Shared grid-filling used by {@link #preview}. */
    private static CraftAttempt build(CraftingRecipe recipe, Function<Ingredient, ItemStack> chooser) {
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
