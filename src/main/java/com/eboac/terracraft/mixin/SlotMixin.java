package com.eboac.terracraft.mixin;

import com.eboac.terracraft.util.HiddenSlots;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes slots belonging to a hidden container invisible and unclickable. */
@Mixin(Slot.class)
public class SlotMixin {

    @Shadow
    @Final
    public Container container;

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void terracraft$hideRetiredSlots(CallbackInfoReturnable<Boolean> cir) {
        if (HiddenSlots.isHidden(this.container)) {
            cir.setReturnValue(false);
        }
    }
}
