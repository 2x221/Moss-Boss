package com.mossbot_addon.addon;

import com.mossbot_addon.addon.commands.MossBotCommands;
import com.mossbot_addon.addon.hud.MossBotHUD;
import com.mossbot_addon.addon.modules.MossBot;
import com.mossbot_addon.addon.modules.MossPilot;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public class mossbot_addon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("Moss Boss");

    public static final Category CATEGORY = new Category("Moss Boss", Items.MOSS_BLOCK.getDefaultStack());
    public static final HudGroup HUD_GROUP = new HudGroup("Moss Boss");

    public static final Color ADDON_COLOR = new Color(255, 255, 85);

    public mossbot_addon() {
        this.name = "Moss Boss";
        this.authors = new String[]{"JerkinOffInMyCar"};
        this.color.set(ADDON_COLOR);
    }

    @Override
    public void onInitialize() {
        this.name = "Moss Boss";
        this.authors = new String[]{"JerkinOffInMyCar"};
        this.color.set(ADDON_COLOR);

        LOG.info("Loading Moss Boss by JerkinOffInMyCar");

        MossBot mossBot = new MossBot();
        MossPilot mossPilot = new MossPilot();

        Modules.get().add(mossBot);
        Modules.get().add(mossPilot);

        // Sets module settings window header to "From: JerkinOffInMyCar"
        // while preserving "Moss Boss by JerkinOffInMyCar" on the HUD watermark
        try {
            MeteorAddon authorTag = new MeteorAddon() {
                @Override
                public void onInitialize() {}

                @Override
                public String getPackage() {
                    return "com.mossbot_addon.addon";
                }
            };
            authorTag.name = "JerkinOffInMyCar";
            authorTag.authors = new String[]{"JerkinOffInMyCar"};

            Field addonField = Module.class.getDeclaredField("addon");
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

            long offset = unsafe.objectFieldOffset(addonField);
            unsafe.putObject(mossBot, offset, authorTag);
            unsafe.putObject(mossPilot, offset, authorTag);
        } catch (Throwable t) {
            LOG.error("Failed to set module author tag", t);
        }

        Commands.add(new MossBotCommands());
        Hud.get().register(MossBotHUD.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.mossbot_addon.addon";
    }
}
