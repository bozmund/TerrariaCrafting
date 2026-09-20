package com.eboac.terracraft.crafter;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The rules that turn a crafter's target item into behaviour.
 *
 * <p>A targeted crafter stops being a 3x3 grid and becomes a machine with ingredient hoppers:
 * slot <em>i</em> is dedicated to the target recipe's <em>i</em>th distinct ingredient and holds a
 * whole stack of it. One craft spends however many that recipe calls for -- five iron out of one
 * stack of iron, rather than one item out of each of five cells.
 */
public final class CrafterTargeting {

    public static final int GRID = 3;
    public static final int SLOTS = GRID * GRID;

    /** One dedicated ingredient slot: what it accepts, what to draw for it, how many per craft. */
    public record Requirement(Ingredient ingredient, ItemStack display, int count) {
    }

    /** Item -> the recipe making it. Null values are cached too, so unmakeable targets stay cheap. */
    private static final Map<Item, RecipeHolder<CraftingRecipe>> RECIPE_FOR_ITEM = new ConcurrentHashMap<>();
    private static final RecipeHolder<CraftingRecipe> NONE = null;

    private CrafterTargeting() {
    }

    /**
     * The recipe that produces the crafter's target item, or null if it has no target.
     *
     * <p>Cached per item: this is asked on every hopper insertion attempt, and walking the whole
     * recipe list each time would make a hopper line into a tick sink.
     */
    public static RecipeHolder<CraftingRecipe> recipeFor(MinecraftServer server, Level level, ItemStack target) {
        if (target.isEmpty()) {
            return NONE;
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
                    @SuppressWarnings("unchecked")
                    RecipeHolder<CraftingRecipe> typed = (RecipeHolder<CraftingRecipe>) holder;
                    return typed;
                }
            }
            return NONE;
        });
    }

    /**
     * The recipe's distinct ingredients with how many of each one craft consumes.
     *
     * <p>{@code ingredients()} is one entry per grid cell, not one per distinct item, so a recipe
     * wanting five iron ingots arrives as five separate entries. Grouping them is what turns a
     * grid into a short list of hoppers.
     */
    public static List<Requirement> requirements(CraftingRecipe recipe) {
        PlacementInfo placement = recipe.placementInfo();
        IntList slotToIngredient = placement.slotsToIngredientIndex();
        List<Ingredient> ingredients = placement.ingredients();

        List<Ingredient> distinct = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        for (int cell = 0; cell < slotToIngredient.size(); cell++) {
            int index = slotToIngredient.getInt(cell);
            if (index == PlacementInfo.EMPTY_SLOT) {
                continue;
            }
            Ingredient ingredient = ingredients.get(index);

            int existing = distinct.indexOf(ingredient);
            if (existing >= 0) {
                counts.set(existing, counts.get(existing) + 1);
            } else {
                distinct.add(ingredient);
                counts.add(1);
            }
        }

        List<Requirement> requirements = new ArrayList<>(distinct.size());
        for (int i = 0; i < distinct.size() && requirements.size() < SLOTS; i++) {
            // A tag ingredient accepts many items; the first stands in for the rest on screen.
            ItemStack display = distinct.get(i).items().findFirst()
                    .map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (!display.isEmpty()) {
                display.setCount(counts.get(i));
                requirements.add(new Requirement(distinct.get(i), display, counts.get(i)));
            }
        }
        return requirements;
    }

    /**
     * Whether a crafter set to this recipe should accept {@code stack} into {@code slot}.
     *
     * <p>Each slot belongs to exactly one ingredient, so a hopper can be pointed at a crafter
     * without a filter in front of it and the items sort themselves.
     */
    public static boolean accepts(CraftingRecipe recipe, int slot, ItemStack stack) {
        List<Requirement> requirements = requirements(recipe);
        return slot >= 0 && slot < requirements.size() && requirements.get(slot).ingredient().test(stack);
    }

    /** True if every dedicated slot holds enough for one craft. */
    public static boolean canCraft(CraftingRecipe recipe, CrafterBlockEntity crafter) {
        List<Requirement> requirements = requirements(recipe);
        for (int i = 0; i < requirements.size(); i++) {
            if (crafter.getItem(i).getCount() < requirements.get(i).count()) {
                return false;
            }
        }
        return !requirements.isEmpty();
    }

    /**
     * Builds the grid the recipe expects, drawing one item per cell from the dedicated slots.
     *
     * <p>The crafter's own layout is a list of stacks, which no recipe would match, so this
     * reconstructs the shape purely to hand to {@code matches} and {@code assemble}.
     */
    public static CraftingInput asRecipeInput(CraftingRecipe recipe, CrafterBlockEntity crafter) {
        PlacementInfo placement = recipe.placementInfo();
        IntList slotToIngredient = placement.slotsToIngredientIndex();
        List<Ingredient> ingredients = placement.ingredients();
        List<Requirement> requirements = requirements(recipe);

        int width = GRID;
        int height = GRID;
        if (recipe instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
        }

        List<ItemStack> grid = new ArrayList<>(width * height);
        for (int i = 0; i < width * height; i++) {
            grid.add(ItemStack.EMPTY);
        }

        for (int cell = 0; cell < slotToIngredient.size() && cell < grid.size(); cell++) {
            int index = slotToIngredient.getInt(cell);
            if (index == PlacementInfo.EMPTY_SLOT) {
                continue;
            }
            int source = requirements.indexOf(findRequirement(requirements, ingredients.get(index)));
            if (source < 0) {
                continue;
            }
            ItemStack held = crafter.getItem(source);
            if (!held.isEmpty()) {
                grid.set(cell, held.copyWithCount(1));
            }
        }

        return CraftingInput.of(width, height, grid);
    }

    /** Spends one craft's worth out of the dedicated slots. */
    public static void consume(CraftingRecipe recipe, CrafterBlockEntity crafter) {
        List<Requirement> requirements = requirements(recipe);
        for (int i = 0; i < requirements.size(); i++) {
            crafter.getItem(i).shrink(requirements.get(i).count());
        }
    }

    private static Requirement findRequirement(List<Requirement> requirements, Ingredient ingredient) {
        for (Requirement requirement : requirements) {
            if (requirement.ingredient().equals(ingredient)) {
                return requirement;
            }
        }
        return null;
    }
}
