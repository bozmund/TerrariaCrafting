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
 */
@Mixin(CrafterScreen.class)
public abstract class CrafterScreenMixin extends AbstractContainerScreen<CrafterMenu> {

    /** Where vanilla's 3x3 grid sat: (26, 17) to (80, 71). */
    private static final int GRID_X = 26;
    private static final int GRID_Y = 17;
    private static final int GRID_SPAN = 54;

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

        // Cover the retired grid squares, which come from the crafter's own GUI texture.
        graphics.fill(originX + GRID_X - 1, originY + GRID_Y - 1,
                originX + GRID_X + GRID_SPAN + 1, originY + GRID_Y + GRID_SPAN + 1, PANEL);

        // The target slot we added has no square of its own either.
        int slotX = originX + TARGET_X - 1;
        int slotY = originY + TARGET_Y - 1;
        graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF373737);
        graphics.fill(slotX + 1, slotY + 1, slotX + 18, slotY + 18, 0xFFFFFFFF);
        graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF8B8B8B);

        List<ItemStack> needs = this.menu instanceof CrafterNeeds holder ? holder.terracraft$needs() : List.of();

        if (needs.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.terracraft.crafter_no_target"),
                    GRID_X, GRID_Y + 20, TEXT_IDLE);
            return;
        }

        for (int i = 0; i < needs.size() && i < 3; i++) {
            ItemStack need = needs.get(i);
            int rowY = GRID_Y + i * 18;

            graphics.item(need, originX + GRID_X, originY + rowY);

            int loaded = terracraft$countLoaded(need);
            int wanted = need.getCount();
            graphics.text(this.font, Component.literal(loaded + " / " + wanted),
                    GRID_X + 20, rowY + 5, loaded >= wanted ? TEXT_HAVE : TEXT_SHORT);
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
