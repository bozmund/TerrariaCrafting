package com.eboac.terracraft.net;

import com.eboac.terracraft.TerraCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Optional;

/**
 * Server to client: which ingredient each crafter slot is dedicated to, and how many it spends
 * per craft. One entry per slot; an empty ingredient means the slot is unused.
 *
 * <p>The real {@link Ingredient} is sent rather than a representative item so the client can test
 * a stack exactly the way the server does. Sending only a display item would make the client
 * refuse birch planks from a slot showing oak, or accept iron into a redstone slot and then have
 * the server yank it back a frame later.
 */
public record CrafterNeedsPayload(List<Optional<Ingredient>> ingredients, List<Integer> counts)
        implements CustomPacketPayload {

    public static final Type<CrafterNeedsPayload> TYPE = new Type<>(TerraCraft.id("crafter_needs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrafterNeedsPayload> CODEC =
            StreamCodec.composite(
                    Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
                    CrafterNeedsPayload::ingredients,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
                    CrafterNeedsPayload::counts,
                    CrafterNeedsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
