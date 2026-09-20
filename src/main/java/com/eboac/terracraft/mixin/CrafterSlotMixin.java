package com.eboac.terracraft.mixin;

import com.eboac.terracraft.crafter.CrafterNeeds;
import com.eboac.terracraft.crafter.TargetedCrafter;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CrafterSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Stops the wrong ingredient going into a dedicated crafter slot.
 *
 * <p>{@code CrafterSlot.mayPlace} only asks whether the slot is switched off; it never consults
 * the container's {@code canPlaceItem}. Hoppers go through {@code canPlaceItem} and were already
 * filtered, but clicking an item in by hand bypassed that entirely.
 *
 * <p>Injected at RETURN so vanilla's own answer still stands: if it already said no, this never
 * turns that into a yes.
 */
@Mixin(CrafterSlot.class)
public abstract class CrafterSlotMixin extends Slot {

    @Shadow
    @Final
    private CrafterMenu menu;

    private CrafterSlotMixin() {
        super(null, 0, 0, 0);
    }

    @Inject(method = "mayPlace", at = @At("RETURN"), cancellable = true)
    private void terracraft$respectTarget(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        // Server: the container is the block entity, which knows the target recipe outright and
        // can test the ingredient properly, tags and all.
        if (this.container instanceof TargetedCrafter targeted && !targeted.terracraft$target().isEmpty()) {
            cir.setReturnValue(this.container.canPlaceItem(this.getContainerSlot(), stack));
            return;
        }

        // Client: no block entity, but the server sent the real Ingredients, so the same test can
        // be run here. Matching the server exactly is what stops an item visibly dropping in --
        // or shift-clicking across -- only to be yanked back a frame later.
        if (!(this.menu instanceof CrafterNeeds holder)) {
            return;
        }
        List<java.util.Optional<net.minecraft.world.item.crafting.Ingredient>> ingredients =
                holder.terracraft$ingredients();
        int slot = this.getContainerSlot();
        if (slot >= ingredients.size()) {
            return;
        }
        cir.setReturnValue(ingredients.get(slot).map(ingredient -> ingredient.test(stack)).orElse(false));
    }
}
