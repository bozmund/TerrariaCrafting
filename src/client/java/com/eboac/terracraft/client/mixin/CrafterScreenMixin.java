package com.eboac.terracraft.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.world.inventory.CrafterMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the target slot's background square.
 *
 * <p>The slot's contents are rendered for us -- vanilla draws every slot the menu owns -- but the
 * sunken square behind it comes from the crafter's GUI texture, which has no such slot.
 */
@Mixin(CrafterScreen.class)
public abstract class CrafterScreenMixin extends AbstractContainerScreen<CrafterMenu> {

    private static final int SLOT_X = 134;
    private static final int SLOT_Y = 58;

    private CrafterScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void terracraft$drawTargetSlot(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                           float partialTick, CallbackInfo ci) {
        int x = this.leftPos + SLOT_X - 1;
        int y = this.topPos + SLOT_Y - 1;
        graphics.fill(x, y, x + 18, y + 18, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }
}
