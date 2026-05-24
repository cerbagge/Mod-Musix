package com.musix;

import com.musix.config.MusixConfig;
import com.musix.input.MusixMidi;
import com.musix.key.KeyBindings;
import com.musix.key.KeyHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
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
        KeyBindings.register(config); // v3.10.4: 내부에서 keyBinding 재등록 가드
        LOG.info("[Musix] 설정 재로드 완료");
    }

    // === v3.10.4: OS 감지 ===
    public static boolean isMacOS()   { return Util.getOperatingSystem() == Util.OperatingSystem.OSX; }
    public static boolean isWindows() { return Util.getOperatingSystem() == Util.OperatingSystem.WINDOWS; }
    public static boolean isLinux()   { return Util.getOperatingSystem() == Util.OperatingSystem.LINUX; }
    /** "WINDOWS" / "OSX" / "LINUX" / "SOLARIS" / "UNKNOWN" */
    public static String osName()     { return Util.getOperatingSystem().name(); }

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

        // v4.0.0: 활성화되어 있고 장치명이 저장되어 있으면 MIDI 자동 재연결
        if (config.midiEnabled && config.midiDeviceName != null && !config.midiDeviceName.isEmpty()) {
            // 비동기 — MIDI 장치 스캔에 시간이 걸릴 수 있어 메인 init 지연 방지
            new Thread(() -> {
                boolean ok = MusixMidi.connect(config.midiDeviceName);
                LOG.info("[Musix] MIDI 자동 재연결 {}: '{}'", ok ? "성공" : "실패", config.midiDeviceName);
            }, "musix-midi-init").start();
        }
    }
}
