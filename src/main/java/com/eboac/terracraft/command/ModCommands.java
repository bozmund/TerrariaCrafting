package com.eboac.terracraft.command;

import com.eboac.terracraft.net.ModNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;

public class ModCommands {

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("craft").executes(context -> {
                    ModNetworking.openBrowser(context.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
