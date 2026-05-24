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

    /** v3.10.0: DB 가 외부에서 변경된 후 (예: 슬롯 불러오기) 메모리 캐시 재로드. */
    public static void reloadConfig() {
        config = MusixConfig.load();
        KeyBindings.register(config);
        LOG.info("[Musix] 설정 재로드 완료");
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
