package com.musix.volume;

import com.musix.MusixClient;
import com.musix.config.MusixConfig;
import com.musix.util.DebugChat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v5.3.0: 서버 악기 음량 제어. `/instruments <1-10>` 을 채팅 명령으로 전송한다.
 * - 서버의 실제 음량은 조회할 수 없으므로, 악기 상자를 열 때마다 기본값(8)으로 강제 동기화한다.
 * - 전송이 성공했을 때만 추적값을 갱신해 "모드가 아는 값 == 마지막으로 보낸 값" 을 유지한다.
 * - 0.1초 쿨다운. 초과 입력은 버린다 (스팸 방지).
 */
public final class VolumeController {
    private static final Logger LOG = LoggerFactory.getLogger("musix/volume");

    /** 명령 전송 최소 간격. */
    public static final long COOLDOWN_MS = 100L;

    private static volatile int currentVolume = MusixConfig.VOLUME_DEFAULT;
    private static volatile long lastSentMillis = 0L;

    private VolumeController() {}

    public static int current() { return currentVolume; }

    /** 악기 상자 열림 — 서버 음량을 기본값으로 맞춘다. */
    public static void syncOnContainerOpen() {
        send(MusixConfig.VOLUME_DEFAULT);
    }

    /**
     * 방향키/마우스용 상대 조절. 1 과 10 사이를 순환한다 (10 에서 +1 → 1, 1 에서 -1 → 10).
     */
    public static void step(int delta) {
        int next = currentVolume + delta;
        if (next > MusixConfig.VOLUME_MAX) next = MusixConfig.VOLUME_MIN;
        if (next < MusixConfig.VOLUME_MIN) next = MusixConfig.VOLUME_MAX;
        send(next);
    }

    /** Tab + 숫자용 절대 지정. 범위 밖이면 무시. */
    public static void set(int volume) {
        if (volume < MusixConfig.VOLUME_MIN || volume > MusixConfig.VOLUME_MAX) return;
        send(volume);
    }

    /**
     * volume preset 의 동작 ID(slot) 를 실제 동작으로 변환.
     * 0 = 올리기, 1 = 내리기, 2~11 = 음량 1~10 지정.
     */
    public static void runAction(int actionId) {
        if (actionId == MusixConfig.VOL_ACTION_UP)   { step(+1); return; }
        if (actionId == MusixConfig.VOL_ACTION_DOWN) { step(-1); return; }
        set(actionId - MusixConfig.VOL_ACTION_SET_BASE + 1);
    }

    /** 실제 전송. 쿨다운에 걸리면 추적값도 그대로 두어 서버 상태와 어긋나지 않게 한다. */
    private static boolean send(int volume) {
        long now = System.currentTimeMillis();
        if (now - lastSentMillis < COOLDOWN_MS) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return false;
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return false;

        handler.sendChatCommand("instruments " + volume);
        lastSentMillis = now;
        currentVolume = volume;

        MusixConfig cfg = MusixClient.config();
        if (cfg != null && cfg.debugMode) {
            DebugChat.ok("[음량] /instruments " + volume);
        }
        LOG.debug("[Musix] 음량 {} 전송", volume);
        return true;
    }
}
