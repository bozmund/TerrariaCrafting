package com.eboac.terracraft.api;

import com.eboac.terracraft.TerraCraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Holds the {@link KnowledgeGate} terracraft should consult.
 *
 * <p><b>No gate installed means every recipe is known.</b> That is not a fallback, it is the
 * behaviour: Terraria Crafting on its own must behave exactly as it did before this seam existed,
 * so "nobody asked" and "everything is allowed" have to be the same answer. An add-on installs a
 * gate; a pack that wants no gating installs nothing.
 *
 * <p>Installation happens once, during mod initialisation, before any player opens a browser. The
 * first gate to install wins and a later attempt is logged rather than silently replacing it -- two
 * mods both trying to gate crafting would otherwise produce whichever loaded last, which is a
 * startup-order bug that looks like a gameplay bug.
 */
public final class TerraCraftGate {

    private static volatile KnowledgeGate installed = null;

    private TerraCraftGate() {
    }

    /** Installs a gate. Ignored, with a warning, if one is already installed. */
    public static void install(KnowledgeGate gate) {
        if (gate == null) {
            return;
        }
        synchronized (TerraCraftGate.class) {
            if (installed != null) {
                TerraCraft.LOGGER.warn(
                        "Ignoring a second crafting knowledge gate ({}); the first one installed ({}) stays in charge",
                        gate.getClass().getName(), installed.getClass().getName());
                return;
            }
            installed = gate;
            TerraCraft.LOGGER.info("Crafting knowledge gate installed: {}", gate.getClass().getName());
        }
    }

    public static KnowledgeGate installed() {
        return installed;
    }

    public static boolean hasGate() {
        return installed != null;
    }

    /**
     * The only way terracraft itself asks. With no gate installed this returns true for everything,
     * which is what keeps terracraft-alone behaviour identical.
     */
    public static boolean knows(Player player, RecipeHolder<?> recipe, ItemStack result) {
        KnowledgeGate gate = installed;
        // No gate, or no player to gate, means allowed. A gate must never be able to lock content
        // by accident: an add-on that cannot answer the question declines to gate it.
        return gate == null || player == null || gate.knows(player, recipe, result);
    }
}
