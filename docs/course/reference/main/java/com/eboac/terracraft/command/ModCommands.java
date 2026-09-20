package com.eboac.terracraft.command;

import com.eboac.terracraft.menu.CraftBrowserMenu;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public class ModCommands {

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("craft").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.openMenu(new SimpleMenuProvider(
                            (containerId, inventory, p) -> new CraftBrowserMenu(containerId, inventory),
                            Component.translatable("container.terracraft.craft_browser")));
                    return 1;
                })));
    }
}
