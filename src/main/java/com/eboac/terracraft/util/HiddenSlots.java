package com.eboac.terracraft.util;

import net.minecraft.world.Container;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Containers whose slots should be invisible and untouchable.
 *
 * <p>Used to retire the 2x2 grid in the player's inventory. Deleting those slots outright would
 * renumber everything after them and desync the client, so instead the slots stay exactly where
 * they are and simply report themselves inactive -- vanilla then skips them when drawing and when
 * working out what the mouse is over.
 *
 * <p>Keyed weakly: a menu's containers should not be kept alive by this set after it closes.
 */
public final class HiddenSlots {

    private static final Set<Container> HIDDEN =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private HiddenSlots() {
    }

    public static void hide(Container container) {
        HIDDEN.add(container);
    }

    public static boolean isHidden(Container container) {
        return HIDDEN.contains(container);
    }
}
