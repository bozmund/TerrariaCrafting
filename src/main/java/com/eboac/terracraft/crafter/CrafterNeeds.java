package com.eboac.terracraft.crafter;

import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Optional;

/** Implemented by {@code CrafterMenu} through a mixin so the screen and its slots can read it. */
public interface CrafterNeeds {

    void terracraft$setNeeds(List<Optional<Ingredient>> ingredients, List<Integer> counts);

    /** One entry per crafter slot; empty where that slot is unused by the current target. */
    List<Optional<Ingredient>> terracraft$ingredients();

    /** How many that slot gives up per craft, parallel to {@link #terracraft$ingredients()}. */
    List<Integer> terracraft$counts();

    /**
     * Server side: send the current requirements to the viewing player if the target has changed
     * since last time. Called once per tick from the menu's change broadcast.
     */
    void terracraft$pushNeeds();

    /**
     * Forgets what was last sent, so the next push goes out even though the target has not
     * changed. Used when a client reopens the screen and needs the state again.
     */
    void terracraft$resetNeedsSync();
}
