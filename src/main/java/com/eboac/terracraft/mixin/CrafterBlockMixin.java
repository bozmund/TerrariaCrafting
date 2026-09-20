package com.eboac.terracraft.mixin;

import com.eboac.terracraft.crafter.CrafterTargeting;
import com.eboac.terracraft.crafter.TargetedCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Just before a targeted crafter fires, shuffles its grid so the items sit where the target
 * recipe expects them.
 *
 * <p>Vanilla matches the grid by shape, so a hopper dropping ingredients into whatever slot
 * happened to be free would never line up. Rearranging here and then letting vanilla run means
 * the actual crafting, item ejection, sounds and advancements are all still vanilla's.
 */
@Mixin(CrafterBlock.class)
public class CrafterBlockMixin {

    @Inject(method = "dispenseFrom", at = @At("HEAD"))
    private void terracraft$arrangeForTarget(BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (!(level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter)
                || !(crafter instanceof TargetedCrafter targeted)) {
            return;
        }

        ItemStack target = targeted.terracraft$target();
        if (target.isEmpty()) {
            return;
        }

        CraftingRecipe recipe = CrafterTargeting.recipeFor(level.getServer(), level, target);
        if (recipe != null) {
            CrafterTargeting.arrange(recipe, crafter);
        }
    }
}
