package com.eboac.terracraft.client.mixin;

import com.eboac.terracraft.crafter.CrafterNeeds;
import com.eboac.terracraft.net.RequestCrafterNeedsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.network.chat.Component;
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

    /** Slots the current target has no use for. */
    private static final int UNUSED_WASH = 0x90404040;

    private static final int TEXT_HAVE = 0xFF6FCF6F;
    private static final int TEXT_SHORT = 0xFFFF7F7F;

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

        if (!(this.menu instanceof CrafterNeeds holder)) {
            return;
        }
        List<java.util.Optional<net.minecraft.world.item.crafting.Ingredient>> ingredients =
                holder.terracraft$ingredients();
        List<Integer> counts = holder.terracraft$counts();
        if (ingredients.size() < GRID_SLOTS || counts.size() < GRID_SLOTS) {
            return;
        }

        for (int i = 0; i < GRID_SLOTS; i++) {
            // A tag ingredient accepts many items; the first stands in for the rest on screen.
            ItemStack wanted = ingredients.get(i)
                    .flatMap(ingredient -> ingredient.items().findFirst())
                    .map(ItemStack::new)
                    .orElse(ItemStack.EMPTY);
            int required = counts.get(i);
            Slot slot = this.menu.slots.get(i);

            // Slot coordinates are panel-local; this method draws in screen space.
            int x = this.leftPos + slot.x;
            int y = this.topPos + slot.y;

            if (wanted.isEmpty()) {
                // This slot belongs to no ingredient of the target, so grey it out as unusable.
                graphics.fill(x, y, x + 16, y + 16, UNUSED_WASH);
                continue;
            }

            ItemStack held = slot.getItem();
            if (held.isEmpty()) {
                graphics.item(wanted, x, y);
                graphics.fill(x, y, x + 16, y + 16, GHOST_WASH);
            }

            // How many this slot gives up per craft, so a stack of 64 iron reads as "5 a time".
            boolean enough = held.getCount() >= required;
            graphics.text(this.font, Component.literal("x" + required),
                    x + 1, y + 10, enough ? TEXT_HAVE : TEXT_SHORT);
        }
    }
}
