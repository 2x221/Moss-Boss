package com.mossbot_addon.addon.hud;

import com.mossbot_addon.addon.modules.MossBot;
import com.mossbot_addon.addon.mossbot_addon;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class MossBotHUD extends HudElement {
    public static final HudElementInfo<MossBotHUD> INFO = new HudElementInfo<>(
        mossbot_addon.HUD_GROUP,
        "moss-bot-hud",
        "Displays Moss Bot active status.",
        MossBotHUD::new
    );

    public MossBotHUD() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        MossBot module = Modules.get().get(MossBot.class);
        boolean active = module != null && module.isActive();
        String text = "Moss Bot: " + (active ? "ON" : "OFF");

        setSize(renderer.textWidth(text, true), renderer.textHeight(true));
        renderer.text(text, x, y, active ? mossbot_addon.ADDON_COLOR : Color.GRAY, true);
    }
}
