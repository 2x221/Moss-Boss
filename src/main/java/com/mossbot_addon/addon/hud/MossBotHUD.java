package com.mossbot_addon.addon.hud;

import com.mossbot_addon.addon.modules.MossBot;
import com.mossbot_addon.addon.modules.MossPilot;
import com.mossbot_addon.addon.mossbot_addon;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class MossBotHUD extends HudElement {
    public static final HudElementInfo<MossBotHUD> INFO = new HudElementInfo<>(
        mossbot_addon.HUD_GROUP,
        "moss-boss-hud",
        "Displays Moss Bot and Moss Pilot active status.",
        MossBotHUD::new
    );

    public MossBotHUD() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        MossBot bot = Modules.get().get(MossBot.class);
        MossPilot pilot = Modules.get().get(MossPilot.class);

        boolean botActive = bot != null && bot.isActive();
        String pilotStatus = pilot != null ? pilot.getStatusSummary() : "OFF";

        String text = String.format("Moss Bot: %s | Moss Pilot: %s", botActive ? "ON" : "OFF", pilotStatus);

        setSize(renderer.textWidth(text, true), renderer.textHeight(true));
        renderer.text(text, x, y, (botActive || !"OFF".equals(pilotStatus)) ? mossbot_addon.ADDON_COLOR : Color.GRAY, true);
    }
}
