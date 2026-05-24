package com.musix;

import com.musix.config.MusixConfig;
import com.musix.key.KeyBindings;
import com.musix.key.KeyHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusixClient implements ClientModInitializer {
    public static final String MOD_ID = "musix";
    public static final Logger LOG = LoggerFactory.getLogger("musix");

    private static MusixConfig config;

    public static MusixConfig config() {
        return config;
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public void onInitializeClient() {
        config = MusixConfig.load();
        KeyBindings.register(config);
        KeyHandler.registerEvents();
        int total = 0;
        for (String p : MusixConfig.ALL_PRESETS) total += KeyBindings.getNotes(p).size();
        LOG.info("[Musix] 초기화 완료. containerPrefix='{}', notes={} ({} preset)",
                config.containerPrefix, total, MusixConfig.ALL_PRESETS.size());
    }
}
