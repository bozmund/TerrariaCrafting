package com.eboac.terracraft.mixin;

import com.eboac.terracraft.crafter.TargetedCrafter;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the target slot to the crafter's screen. */
@Mixin(CrafterMenu.class)
public abstract class CrafterMenuMixin extends AbstractContainerMenu {

    /** Index of the slot we append, after the 9 grid slots and the 36 inventory slots. */
    private static final int TARGET_SLOT = 45;

    private CrafterMenuMixin() {
        super(null, 0);
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/inventory/ContainerData;)V",
            at = @At("TAIL"))
    private void terracraft$addTargetSlot(int containerId, Inventory inventory, CraftingContainer container,
                                          ContainerData data, CallbackInfo ci) {
        // Server side the container is the block entity, which owns the persistent target slot.
        // Client side it is a throwaway, so a loose one-slot container mirrors what the server sends.
        Container target = container instanceof TargetedCrafter targeted
                ? targeted.terracraft$targetContainer()
                : new SimpleContainer(1);

        addSlot(new Slot(target, 0, 134, 35) {
            @Override
            public int getMaxStackSize() {
                // It is a template, not storage -- one is all that is meaningful.
                return 1;
            }
        });
    }

    /**
     * Vanilla's shift-click logic knows nothing about slot 45 and would index past its own ranges.
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void terracraft$noQuickMoveIntoTarget(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index >= TARGET_SLOT) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
