package com.eboac.terracraft.net;

import com.eboac.terracraft.menu.CraftBrowserMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;

public class ModNetworking {

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(OpenBrowserPayload.TYPE, OpenBrowserPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BrowserActionPayload.TYPE, BrowserActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BrowserStatePayload.TYPE, BrowserStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CrafterNeedsPayload.TYPE, CrafterNeedsPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(OpenBrowserPayload.TYPE, (payload, context) ->
                openBrowser(context.player()));

        ServerPlayNetworking.registerGlobalReceiver(BrowserActionPayload.TYPE, (payload, context) -> {
            // Only act if the player really has our browser open. A client could send this at any
            // time; reaching into whatever menu happens to be open would be a bug at best.
            if (context.player().containerMenu instanceof CraftBrowserMenu menu) {
                menu.applyClientState(payload.scrollRow(), payload.showUncraftable(), payload.search());
            }
        });
    }

    public static void openBrowser(net.minecraft.server.level.ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new CraftBrowserMenu(containerId, inventory),
                Component.translatable("container.terracraft.craft_browser")));
    }
}
