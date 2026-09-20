package com.eboac.terracraft.client.screen;

import com.eboac.terracraft.menu.CraftBrowserMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class CraftBrowserScreen extends AbstractContainerScreen<CraftBrowserMenu> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 114;

    public CraftBrowserScreen(CraftBrowserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.inventoryLabelY = HEIGHT - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, 17, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                this.leftPos, this.topPos + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
    }
}
