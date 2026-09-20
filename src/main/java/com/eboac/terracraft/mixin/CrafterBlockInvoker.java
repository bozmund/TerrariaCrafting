package com.eboac.terracraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes vanilla's item ejection so a replaced craft still pushes its output the same way --
 * into the container the crafter faces, or out into the world if there is none.
 */
@Mixin(CrafterBlock.class)
public interface CrafterBlockInvoker {

    @Invoker("dispenseItem")
    void terracraft$dispenseItem(ServerLevel level, BlockPos pos, CrafterBlockEntity crafter,
                                 ItemStack stack, BlockState state, RecipeHolder<?> recipe);
}
