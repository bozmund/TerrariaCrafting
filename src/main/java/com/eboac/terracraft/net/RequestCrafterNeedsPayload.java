package com.eboac.terracraft.net;

import com.eboac.terracraft.TerraCraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: "I have just opened a crafter screen, send me its requirements again."
 *
 * <p>The server pushes requirements when a target changes, but the first push happens while the
 * menu is being built -- before the client has been told to open the screen -- so it arrives with
 * no menu to apply it to and is dropped. Asking once the screen exists is what makes the display
 * survive closing and reopening.
 */
public record RequestCrafterNeedsPayload() implements CustomPacketPayload {

    public static final Type<RequestCrafterNeedsPayload> TYPE =
            new Type<>(TerraCraft.id("request_crafter_needs"));

    public static final StreamCodec<Object, RequestCrafterNeedsPayload> CODEC =
            StreamCodec.unit(new RequestCrafterNeedsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
