package com.eboac.terracraft.mixin;

import com.eboac.terracraft.crafter.CrafterNeeds;
import com.eboac.terracraft.crafter.CrafterTargeting;
import com.eboac.terracraft.crafter.TargetedCrafter;
import com.eboac.terracraft.net.CrafterNeedsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Replaces the crafter's 3x3 grid with a target slot and a list of what that target needs.
 *
 * <p>Injected into {@code addSlots} rather than a constructor on purpose. The client builds its
 * menu with the two-argument constructor and the server with the four-argument one, and those two
 * do NOT delegate to each other -- they each call this shared method. Hooking a single constructor
 * adds the slot on one side only, and a menu whose halves disagree on slot count disconnects the
 * player the moment the server sends its contents.
 */
@Mixin(CrafterMenu.class)
public abstract class CrafterMenuMixin extends AbstractContainerMenu implements CrafterNeeds {

    /** Vanilla builds 9 grid slots, 36 inventory slots and 1 result slot before we add ours. */
    private static final int VANILLA_SLOT_COUNT = 46;

    /** Sits below vanilla's result preview, which occupies (134, 35). */
    private static final int TARGET_X = 134;
    private static final int TARGET_Y = 58;

    @Shadow
    @Final
    private Player player;

    @Unique
    private List<ItemStack> terracraft$needs = List.of();

    @Unique
    private ItemStack terracraft$lastTarget = ItemStack.EMPTY;

    private CrafterMenuMixin() {
        super(null, 0);
    }

    @Override
    public void terracraft$setNeeds(List<ItemStack> needs) {
        this.terracraft$needs = needs;
    }

    @Override
    public List<ItemStack> terracraft$needs() {
        return this.terracraft$needs;
    }

    @Override
    public void terracraft$resetNeedsSync() {
        this.terracraft$lastTarget = ItemStack.EMPTY;
    }

    @Inject(method = "addSlots", at = @At("TAIL"))
    private void terracraft$addTargetSlot(Inventory inventory, CallbackInfo ci) {
        if (this.slots.size() != VANILLA_SLOT_COUNT) {
            return;
        }

        Container backing = ((CrafterMenu) (Object) this).getContainer();

        Container targetSlot = backing instanceof TargetedCrafter targeted
                ? targeted.terracraft$targetContainer()
                : new SimpleContainer(1);

        addSlot(new Slot(targetSlot, 0, TARGET_X, TARGET_Y) {
            @Override
            public int getMaxStackSize() {
                // A template, not storage -- one is all that is meaningful.
                return 1;
            }
        });
    }

    /**
     * Tells the client what the current target requires, whenever that target changes.
     *
     * <p>Recipes only exist on the server, so the screen cannot work this out for itself. Sending
     * on change rather than every tick keeps this to one packet per retarget.
     */
    @Override
    public void terracraft$pushNeeds() {
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Container backing = ((CrafterMenu) (Object) this).getContainer();
        if (!(backing instanceof TargetedCrafter targeted) || !(backing instanceof BlockEntity blockEntity)
                || blockEntity.getLevel() == null || blockEntity.getLevel().getServer() == null) {
            return;
        }

        ItemStack target = targeted.terracraft$target();
        if (ItemStack.matches(target, terracraft$lastTarget)) {
            return;
        }
        terracraft$lastTarget = target.copy();

        CraftingRecipe recipe = CrafterTargeting.recipeFor(
                blockEntity.getLevel().getServer(), blockEntity.getLevel(), target);

        ServerPlayNetworking.send(serverPlayer,
                new CrafterNeedsPayload(recipe == null ? List.of() : CrafterTargeting.needs(recipe)));
    }

    /** Vanilla's shift-click logic knows nothing about our slot and would index past its ranges. */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void terracraft$noQuickMoveIntoTarget(Player clicking, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index >= VANILLA_SLOT_COUNT) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
