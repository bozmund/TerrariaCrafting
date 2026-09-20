package com.eboac.terracraft.client.mixin;

import com.eboac.terracraft.net.OpenBrowserPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paints over the retired 2x2 grid and puts a button opening the crafting browser in its place.
 *
 * <p>The slots are already invisible by this point, but the sunken squares and the arrow between
 * them are part of the inventory's background texture, so they have to be covered up.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {

    // The vanilla 2x2 sits at (98, 18) and its result at (154, 28).
    private static final int COVER_X = 96;
    private static final int COVER_Y = 16;
    private static final int COVER_WIDTH = 78;
    private static final int COVER_HEIGHT = 39;

    private static final int BUTTON_X = 97;
    private static final int BUTTON_Y = 26;
    private static final int BUTTON_WIDTH = 76;
    private static final int BUTTON_HEIGHT = 18;

    private InventoryScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void terracraft$addBrowserButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.terracraft.open_browser"),
                        button -> ClientPlayNetworking.send(new OpenBrowserPayload()))
                .bounds(this.leftPos + BUTTON_X, this.topPos + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void terracraft$coverCraftingGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                              float partialTick, CallbackInfo ci) {
        int x = this.leftPos + COVER_X;
        int y = this.topPos + COVER_Y;
        graphics.fill(x, y, x + COVER_WIDTH, y + COVER_HEIGHT, 0xFFC6C6C6);
    }
}
