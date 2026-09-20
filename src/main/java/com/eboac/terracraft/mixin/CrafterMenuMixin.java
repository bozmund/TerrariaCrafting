package com.eboac.terracraft.mixin;

import com.eboac.terracraft.crafter.TargetedCrafter;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the target slot to the crafter's screen.
 *
 * <p>Injected into {@code addSlots} rather than a constructor on purpose. The client builds its
 * menu with the two-argument constructor and the server with the four-argument one, and those two
 * do NOT delegate to each other -- they each call this shared method. Hooking a single constructor
 * adds the slot on one side only, and a menu whose two halves disagree on slot count disconnects
 * the player the moment the server sends its contents.
 */
@Mixin(CrafterMenu.class)
public abstract class CrafterMenuMixin extends AbstractContainerMenu {

    /** Vanilla builds 9 grid slots, 36 inventory slots and 1 result slot before we add ours. */
    private static final int VANILLA_SLOT_COUNT = 46;

    /** Sits below vanilla's result preview, which occupies (134, 35). */
    private static final int TARGET_X = 134;
    private static final int TARGET_Y = 58;

    private CrafterMenuMixin() {
        super(null, 0);
    }

    @Inject(method = "addSlots", at = @At("TAIL"))
    private void terracraft$addTargetSlot(Inventory inventory, CallbackInfo ci) {
        if (this.slots.size() != VANILLA_SLOT_COUNT) {
            return;
        }

        // Server side the container is the block entity, which owns the persistent target slot.
        // Client side it is a throwaway, so a loose one-slot container mirrors what the server sends.
        Container backing = ((CrafterMenu) (Object) this).getContainer() instanceof TargetedCrafter targeted
                ? targeted.terracraft$targetContainer()
                : new SimpleContainer(1);

        addSlot(new Slot(backing, 0, TARGET_X, TARGET_Y) {
            @Override
            public int getMaxStackSize() {
                // A template, not storage -- one is all that is meaningful.
                return 1;
            }
        });
    }

    /** Vanilla's shift-click logic knows nothing about our slot and would index past its ranges. */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void terracraft$noQuickMoveIntoTarget(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index >= VANILLA_SLOT_COUNT) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
