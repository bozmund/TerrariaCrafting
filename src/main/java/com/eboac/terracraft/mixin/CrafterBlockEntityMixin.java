package com.eboac.terracraft.mixin;

import com.eboac.terracraft.crafter.CrafterTargeting;
import com.eboac.terracraft.crafter.TargetedCrafter;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives every crafter a target slot: put the item you want it to produce in there, and it will
 * only accept that recipe's ingredients from then on.
 *
 * <p>The template item is held in its own one-slot container rather than in the 3x3 grid, so
 * hoppers cannot reach it and crafting never consumes it.
 */
@Mixin(CrafterBlockEntity.class)
public abstract class CrafterBlockEntityMixin extends BlockEntity implements TargetedCrafter {

    @Unique
    private final SimpleContainer terracraft$target = new SimpleContainer(1);

    private CrafterBlockEntityMixin() {
        super(null, null, null);
    }

    @Override
    public Container terracraft$targetContainer() {
        return terracraft$target;
    }

    @Override
    public ItemStack terracraft$target() {
        return terracraft$target.getItem(0);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void terracraft$saveTarget(ValueOutput output, CallbackInfo ci) {
        ItemStack target = terracraft$target.getItem(0);
        if (!target.isEmpty()) {
            output.store("terracraft_target", ItemStack.CODEC, target);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void terracraft$loadTarget(ValueInput input, CallbackInfo ci) {
        terracraft$target.setItem(0, input.read("terracraft_target", ItemStack.CODEC).orElse(ItemStack.EMPTY));
    }

    /**
     * The point of the feature: with a target set, refuse anything that is not one of its
     * ingredients, and refuse more copies of an ingredient than the recipe has cells for.
     */
    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void terracraft$onlyIngredients(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        ItemStack target = terracraft$target.getItem(0);
        if (target.isEmpty() || this.level == null || this.level.getServer() == null) {
            return;
        }

        CraftingRecipe recipe = CrafterTargeting.recipeFor(this.level.getServer(), this.level, target);
        if (recipe == null) {
            return;
        }

        CrafterBlockEntity self = (CrafterBlockEntity) (Object) this;
        if (!CrafterTargeting.accepts(recipe, self, slot, stack)) {
            cir.setReturnValue(false);
        }
    }
}
