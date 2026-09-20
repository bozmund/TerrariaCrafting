package com.eboac.terracraft.client.mixin;

import com.eboac.terracraft.crafter.CrafterNeeds;
import com.eboac.terracraft.net.RequestCrafterNeedsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Shows what a targeted crafter still needs, as faded ghosts in its empty grid slots.
 *
 * <p>The nine grid slots stay real and usable -- items can be taken back out by hand -- and the
 * ghosts simply mark the cells the target recipe wants filled. One ghost per cell means the count
 * is visible at a glance without any text: five iron ghosts is five iron ingots.
 *
 * <p>Everything here is in absolute screen coordinates. {@code extractBackground} is not
 * translated to the panel's origin the way {@code extractLabels} is, so anything drawn at a bare
 * local coordinate lands in the top-left corner of the window instead of inside the GUI.
 */
@Mixin(CrafterScreen.class)
public abstract class CrafterScreenMixin extends AbstractContainerScreen<CrafterMenu> {

    private static final int GRID_SLOTS = 9;

    private static final int TARGET_X = 134;
    private static final int TARGET_Y = 58;

    /** Panel grey at low alpha, laid over a ghost item to wash it out. */
    private static final int GHOST_WASH = 0xB0C6C6C6;

    private CrafterScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void terracraft$requestNeeds(CallbackInfo ci) {
        // The server pushes requirements while building the menu, which is before this screen
        // exists, so that first packet is dropped. Asking again now is what makes the ghosts
        // reappear after closing and reopening.
        ClientPlayNetworking.send(new RequestCrafterNeedsPayload());
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void terracraft$drawTargetSlotAndGhosts(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                    float partialTick, CallbackInfo ci) {
        // The target slot we added has no square of its own in the crafter's GUI texture.
        int slotX = this.leftPos + TARGET_X - 1;
        int slotY = this.topPos + TARGET_Y - 1;
        graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF373737);
        graphics.fill(slotX + 1, slotY + 1, slotX + 18, slotY + 18, 0xFFFFFFFF);
        graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF8B8B8B);

        List<ItemStack> needs = this.menu instanceof CrafterNeeds holder ? holder.terracraft$needs() : List.of();
        if (needs.size() < GRID_SLOTS) {
            return;
        }

        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack wanted = needs.get(i);
            Slot slot = this.menu.slots.get(i);
            if (wanted.isEmpty() || !slot.getItem().isEmpty()) {
                continue;
            }

            // Slot coordinates are panel-local; this method draws in screen space.
            int x = this.leftPos + slot.x;
            int y = this.topPos + slot.y;
            graphics.item(wanted, x, y);
            graphics.fill(x, y, x + 16, y + 16, GHOST_WASH);
        }
    }
}
