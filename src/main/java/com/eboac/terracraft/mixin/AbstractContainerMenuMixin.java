package com.eboac.terracraft.mixin;

import com.eboac.terracraft.crafter.CrafterNeeds;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CrafterMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the crafter's requirement sync.
 *
 * <p>This lives here rather than on {@code CrafterMenu} because {@code broadcastChanges} is
 * declared on {@code AbstractContainerMenu} and never overridden -- Mixin can only inject into
 * methods a target class actually declares, so a CrafterMenu-targeted injection finds nothing
 * and fails the whole mixin at load.
 */
@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void terracraft$pushCrafterNeeds(CallbackInfo ci) {
        if ((Object) this instanceof CrafterMenu && this instanceof CrafterNeeds needs) {
            needs.terracraft$pushNeeds();
        }
    }
}
