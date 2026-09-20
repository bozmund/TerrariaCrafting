package com.eboac.terracraft.mixin;

import com.eboac.terracraft.net.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right-clicking a crafting table opens our browser instead of the vanilla 3x3 grid.
 *
 * <p>The table still matters -- standing near one is what unlocks recipes bigger than 2x2 --
 * but you never arrange anything in it.
 */
@Mixin(CraftingTableBlock.class)
public class CraftingTableBlockMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void terracraft$openBrowser(BlockState state, Level level, BlockPos pos, Player player,
                                        BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ModNetworking.openBrowser(serverPlayer);
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
