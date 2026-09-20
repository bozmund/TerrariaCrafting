package com.eboac.terracraft.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kills the 2x2 crafting grid in the player's inventory screen.
 *
 * <p>{@code slotsChanged} is what recomputes the result slot after you place an ingredient.
 * Cancelling it means the output never appears, so the grid becomes four inert slots. Items put
 * there are still returned when the screen closes, so nothing can be lost.
 */
@Mixin(InventoryMenu.class)
public class InventoryMenuMixin {

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void terracraft$noVanillaCrafting(Container container, CallbackInfo ci) {
        ci.cancel();
    }
}
