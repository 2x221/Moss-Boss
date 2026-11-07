package com.mossbot_addon.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mossbot_addon.addon.modules.MossBot;
import com.mossbot_addon.addon.modules.MossPilot;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class MossBotCommands extends Command {

    public MossBotCommands() {
        super("mossbot", "Moss Boss commands.", "mosspilot", "mp");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            MossBot bot = Modules.get().get(MossBot.class);
            if (bot != null) {
                bot.toggle();
                info("Moss Bot: %s", bot.isActive() ? "ON" : "OFF");
            }
            return SINGLE_SUCCESS;
        });

        builder.then(literal("pilot").executes(context -> {
            MossPilot pilot = Modules.get().get(MossPilot.class);
            if (pilot != null) {
                pilot.toggle();
                info("Moss Pilot: %s", pilot.isActive() ? "ON" : "OFF");
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("spiral").executes(context -> {
            MossPilot pilot = Modules.get().get(MossPilot.class);
            if (pilot != null) {
                pilot.setPilotModeFromCommand(MossPilot.PilotMode.ExpandingSpiral);
                if (!pilot.isActive()) pilot.toggle();
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("grid").executes(context -> {
            MossPilot pilot = Modules.get().get(MossPilot.class);
            if (pilot != null) {
                pilot.setPilotModeFromCommand(MossPilot.PilotMode.LawnmowerGrid);
                if (!pilot.isActive()) pilot.toggle();
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("manual").executes(context -> {
            MossPilot pilot = Modules.get().get(MossPilot.class);
            if (pilot != null) {
                pilot.setPilotModeFromCommand(MossPilot.PilotMode.ManualControl);
                if (!pilot.isActive()) pilot.toggle();
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("reset").executes(context -> {
            MossPilot pilot = Modules.get().get(MossPilot.class);
            if (pilot != null) {
                pilot.resetGridHere();
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("restock").executes(context -> {
            MossPilot pilot = Modules.get().get(MossPilot.class);
            if (pilot != null) {
                pilot.triggerManualRestock();
            }
            return SINGLE_SUCCESS;
        }));
    }
}
