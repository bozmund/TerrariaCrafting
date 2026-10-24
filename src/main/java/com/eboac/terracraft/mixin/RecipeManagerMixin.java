package com.eboac.terracraft.mixin;

import com.eboac.terracraft.craft.RecipeScanner;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops the cached recipe previews whenever the recipe set is (re)loaded.
 *
 * <p>{@code RecipeScanner.PREVIEW_CACHE} is keyed by recipe id and holds the item each recipe
 * produces. After a {@code /reload} or a datapack change that redefines a recipe, the previous
 * result is still cached, so the browser keeps showing an item the recipe no longer makes -- and it
 * keeps offering it as craftable.
 *
 * <p>This is also where the scan epoch is bumped, which is how an already-open browser notices that
 * the list it is holding describes recipes that no longer exist.
 */
@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    @Inject(method = "finalizeRecipeLoading", at = @At("TAIL"))
    private void terracraft$dropStalePreviews(FeatureFlagSet flags, CallbackInfo ci) {
        RecipeScanner.invalidate();
    }
}
