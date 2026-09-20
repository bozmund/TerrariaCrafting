package com.eboac.terracraft.net;

import com.eboac.terracraft.menu.CraftBrowserMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;

public class ModNetworking {

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(OpenBrowserPayload.TYPE, OpenBrowserPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(OpenBrowserPayload.TYPE, (payload, context) ->
                context.player().openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new CraftBrowserMenu(containerId, inventory),
                        Component.translatable("container.terracraft.craft_browser"))));
    }
}
