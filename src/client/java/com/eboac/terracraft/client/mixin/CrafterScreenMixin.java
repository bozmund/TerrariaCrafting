package com.eboac.terracraft.client.mixin;

import com.eboac.terracraft.crafter.CrafterNeeds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Turns the crafter's 3x3 grid into a shopping list.
 *
 * <p>The slots behind it are already hidden by the menu mixin, so this paints over the sunken
 * squares from the GUI texture and draws, for each ingredient, how many the target recipe wants
 * and how many are currently loaded.
 *
 * <p>Everything here is in absolute screen coordinates. {@code extractBackground} is not
 * translated to the panel's origin the way {@code extractLabels} is, so anything drawn at a bare
 * local coordinate lands in the top-left corner of the window instead of inside the GUI.
 */
@Mixin(CrafterScreen.class)
public abstract class CrafterScreenMixin extends AbstractContainerScreen<CrafterMenu> {

    /** The area vanilla's 3x3 grid and its arrow occupied. */
    private static final int AREA_X = 25;
    private static final int AREA_Y = 16;
    private static final int AREA_WIDTH = 104;
    private static final int AREA_HEIGHT = 56;

    private static final int COLUMN_WIDTH = 52;
    private static final int ROW_HEIGHT = 18;
    private static final int ROWS_PER_COLUMN = 3;
    private static final int MAX_SHOWN = ROWS_PER_COLUMN * 2;

    private static final int TARGET_X = 134;
    private static final int TARGET_Y = 58;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int TEXT_HAVE = 0xFF3F7F3F;
    private static final int TEXT_SHORT = 0xFF7F3F3F;
    private static final int TEXT_IDLE = 0xFF707070;

    private CrafterScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void terracraft$drawRequirements(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                             float partialTick, CallbackInfo ci) {
        int originX = this.leftPos;
        int originY = this.topPos;

        // Cover the retired grid squares and the arrow, which come from the crafter's GUI texture.
        graphics.fill(originX + AREA_X, originY + AREA_Y,
                originX + AREA_X + AREA_WIDTH, originY + AREA_Y + AREA_HEIGHT, PANEL);

        // The target slot we added has no square of its own either.
        int slotX = originX + TARGET_X - 1;
        int slotY = originY + TARGET_Y - 1;
        graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF373737);
        graphics.fill(slotX + 1, slotY + 1, slotX + 18, slotY + 18, 0xFFFFFFFF);
        graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF8B8B8B);

        List<ItemStack> needs = this.menu instanceof CrafterNeeds holder ? holder.terracraft$needs() : List.of();

        if (needs.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.terracraft.crafter_no_target"),
                    originX + AREA_X + 2, originY + AREA_Y + 24, TEXT_IDLE);
            return;
        }

        for (int i = 0; i < needs.size() && i < MAX_SHOWN; i++) {
            ItemStack need = needs.get(i);
            int x = originX + AREA_X + 1 + (i / ROWS_PER_COLUMN) * COLUMN_WIDTH;
            int y = originY + AREA_Y + 1 + (i % ROWS_PER_COLUMN) * ROW_HEIGHT;

            graphics.item(need, x, y);

            int loaded = terracraft$countLoaded(need);
            int wanted = need.getCount();
            graphics.text(this.font, Component.literal(loaded + "/" + wanted),
                    x + 19, y + 5, loaded >= wanted ? TEXT_HAVE : TEXT_SHORT);
        }
    }

    /** How many of this ingredient are sitting in the crafter's nine (now hidden) grid slots. */
    private int terracraft$countLoaded(ItemStack need) {
        int loaded = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack held = this.menu.slots.get(slot).getItem();
            if (!held.isEmpty() && ItemStack.isSameItem(held, need)) {
                loaded++;
            }
        }
        return loaded;
    }
}
