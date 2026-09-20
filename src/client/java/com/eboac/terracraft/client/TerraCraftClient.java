package com.eboac.terracraft.client;

import com.eboac.terracraft.client.screen.CraftBrowserScreen;
import com.eboac.terracraft.menu.CraftBrowserMenu;
import com.eboac.terracraft.net.BrowserStatePayload;
import com.eboac.terracraft.net.OpenBrowserPayload;
import com.eboac.terracraft.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class TerraCraftClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.CRAFT_BROWSER, CraftBrowserScreen::new);
		ModKeyMappings.init();

		ClientPlayNetworking.registerGlobalReceiver(BrowserStatePayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof CraftBrowserMenu menu) {
				menu.acceptState(payload.totalEntries(), payload.scrollRow(),
						payload.showUncraftable(), payload.craftableMask(), payload.chainMask());
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(com.eboac.terracraft.net.CrafterNeedsPayload.TYPE,
				(payload, context) -> {
					if (context.player().containerMenu instanceof com.eboac.terracraft.crafter.CrafterNeeds needs) {
						needs.terracraft$setNeeds(payload.needs());
					}
				});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// while, not if: a laggy tick can queue several presses and we must drain them all.
			while (ModKeyMappings.openBrowser.consumeClick()) {
				ClientPlayNetworking.send(new OpenBrowserPayload());
			}
		});
	}
}
