package com.eboac.terracraft.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The seam an add-on uses to say "this player is not allowed to make that yet".
 *
 * <p>Terraria Crafting deliberately ships no idea of its own about gated progression. It knows what
 * a player <em>can afford</em>; whether they are <em>allowed to know</em> a recipe is a different
 * question with a different answer in every pack, so the mod asks instead of deciding.
 *
 * <p>Three rules an implementation must keep:
 *
 * <ul>
 *   <li><b>It is asked on the server, and it must be re-asked at the craft.</b> A gate that only
 *       filters what the browser lists is decoration: the same recipe can be crafted by hand in a
 *       vanilla grid, or reached as an intermediate step of a chain. terracraft consults the gate
 *       both when it builds a list and again immediately before it assembles anything.</li>
 *   <li><b>Returning false must not make a recipe invisible.</b> A locked recipe is still shown,
 *       dimmed, with its materials listed -- the player is meant to see what they are missing and
 *       go and find it. {@code known} is a separate axis from {@code craftable}.</li>
 *   <li><b>It must be cheap and side-effect free.</b> It is called once per recipe per scan, which
 *       in a large pack means thousands of calls whenever a browser opens or refreshes.</li>
 * </ul>
 *
 * @see TerraCraftGate
 */
public interface KnowledgeGate {

    /**
     * Whether this player may craft this recipe right now.
     *
     * @param recipe the recipe being considered
     * @param result the item it produces, already resolved; passed so an implementation can key on
     *               the result rather than parsing the recipe, which is what a curated list keyed
     *               by item actually wants
     */
    boolean knows(Player player, RecipeHolder<?> recipe, ItemStack result);
}
