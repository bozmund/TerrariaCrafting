package com.eboac.terracraft.mixin;

import com.eboac.terracraft.util.HiddenSlots;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Retires the 2x2 crafting grid in the player's inventory.
 *
 * <p>Two things happen here. {@code slotsChanged} is cancelled so the result is never computed,
 * and the grid's containers are marked hidden so the slots stop rendering and stop responding to
 * the mouse. The slots themselves stay in place -- removing them would renumber every slot after
 * them, and a menu whose client and server halves disagree about slot numbering disconnects the
 * player.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    private InventoryMenuMixin() {
        super(null, 0);
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void terracraft$noVanillaCrafting(Container container, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void terracraft$hideCraftingGrid(Inventory inventory, boolean active, Player player, CallbackInfo ci) {
        for (int i = InventoryMenu.RESULT_SLOT; i < InventoryMenu.CRAFT_SLOT_END; i++) {
            HiddenSlots.hide(this.slots.get(i).container);
        }
    }
}
