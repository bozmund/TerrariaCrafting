package com.eboac.terracraft.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Retires the crafting recipe book in the player's inventory.
 *
 * <p>With the 2x2 grid gone the book has nothing to fill, and it is not free: it rebuilds its
 * recipe collections whenever the server syncs recipes, and keeps a searchable index of them.
 *
 * <p>Scoped to {@link InventoryScreen} on purpose. This same base class backs the furnace, blast
 * furnace and smoker screens, whose recipe books still do a useful job for smelting.
 */
@Mixin(AbstractRecipeBookScreen.class)
public class RecipeBookScreenMixin {

    /** Without the button there is no way to open the panel in the first place. */
    @Inject(method = "initButton", at = @At("HEAD"), cancellable = true)
    private void terracraft$noRecipeBookButton(CallbackInfo ci) {
        if ((Object) this instanceof InventoryScreen) {
            ci.cancel();
        }
    }

    /** Skips rebuilding the book's recipe collections, which is the costly half. */
    @Inject(method = "recipesUpdated", at = @At("HEAD"), cancellable = true)
    private void terracraft$noRecipeBookRebuild(CallbackInfo ci) {
        if ((Object) this instanceof InventoryScreen) {
            ci.cancel();
        }
    }
}
