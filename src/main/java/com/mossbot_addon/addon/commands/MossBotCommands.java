package com.mossbot_addon.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mossbot_addon.addon.modules.MossBot;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class MossBotCommands extends Command {

    public MossBotCommands() {
        super("mossbot", "Toggles or checks Moss Bot status.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            MossBot module = Modules.get().get(MossBot.class);
            if (module != null) {
                module.toggle();
                info("Moss Bot is now %s.", module.isActive() ? "ON" : "OFF");
            }
            return SINGLE_SUCCESS;
        });
    }
}
