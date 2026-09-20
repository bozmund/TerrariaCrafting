package com.eboac.terracraft.net;

import com.eboac.terracraft.TerraCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server to client: what a targeted crafter needs before it can run.
 *
 * <p>Each stack is one ingredient with its count set to how many grid cells the recipe wants it
 * in. The client cannot work this out for itself -- recipes live on the server.
 */
public record CrafterNeedsPayload(List<ItemStack> needs) implements CustomPacketPayload {

    public static final Type<CrafterNeedsPayload> TYPE = new Type<>(TerraCraft.id("crafter_needs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrafterNeedsPayload> CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), CrafterNeedsPayload::needs,
                    CrafterNeedsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
