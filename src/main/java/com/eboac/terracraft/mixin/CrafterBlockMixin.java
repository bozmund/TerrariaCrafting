package com.eboac.terracraft.mixin;

import com.eboac.terracraft.crafter.CrafterTargeting;
import com.eboac.terracraft.crafter.TargetedCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the craft for a targeted crafter, spending whole counts out of its dedicated slots.
 *
 * <p>Vanilla's version reads the 3x3 as a literal grid and takes exactly one item from every
 * non-empty cell. A targeted crafter is not a grid -- slot 0 might hold sixty-four iron ingots and
 * a single craft should spend five of them -- so the vanilla path is replaced outright rather than
 * coaxed. Ejection still goes through vanilla's own {@code dispenseItem}, so the output lands
 * wherever it always did.
 */
@Mixin(CrafterBlock.class)
public class CrafterBlockMixin {

    /** Matches vanilla: how long the block shows its crafting animation. */
    private static final int CRAFTING_TICKS = 6;

    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void terracraft$craftFromTarget(BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (!(level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter)
                || !(crafter instanceof TargetedCrafter targeted)) {
            return;
        }

        ItemStack target = targeted.terracraft$target();
        if (target.isEmpty()) {
            return;
        }

        // From here on this crafter is ours: even a failure must not fall through to vanilla,
        // which would read the dedicated slots as a grid and craft something unintended.
        ci.cancel();

        RecipeHolder<CraftingRecipe> holder = CrafterTargeting.recipeFor(level.getServer(), level, target);
        if (holder == null || !CrafterTargeting.canCraft(holder.value(), crafter)) {
            level.levelEvent(LevelEvent.SOUND_CRAFTER_FAIL, pos, 0);
            return;
        }

        CraftingRecipe recipe = holder.value();
        CraftingInput input = CrafterTargeting.asRecipeInput(recipe, crafter);
        if (!recipe.matches(input, level)) {
            level.levelEvent(LevelEvent.SOUND_CRAFTER_FAIL, pos, 0);
            return;
        }

        ItemStack result = recipe.assemble(input);
        if (result.isEmpty()) {
            level.levelEvent(LevelEvent.SOUND_CRAFTER_FAIL, pos, 0);
            return;
        }

        result.onCraftedBySystem(level);

        crafter.setCraftingTicksRemaining(CRAFTING_TICKS);
        level.setBlock(pos, state.setValue(CrafterBlock.CRAFTING, true), 2);

        CrafterBlockInvoker ejector = (CrafterBlockInvoker) this;
        ejector.terracraft$dispenseItem(level, pos, crafter, result, state, holder);

        NonNullList<ItemStack> remainders = recipe.getRemainingItems(input);
        for (ItemStack remainder : remainders) {
            if (!remainder.isEmpty()) {
                ejector.terracraft$dispenseItem(level, pos, crafter, remainder, state, holder);
            }
        }

        CrafterTargeting.consume(recipe, crafter);
        crafter.setChanged();
    }
}
