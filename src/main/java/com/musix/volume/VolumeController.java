package com.musix.volume;

import com.musix.MusixClient;
import com.musix.config.MusixConfig;
import com.musix.util.DebugChat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.screen.slot.SlotActionType;
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

    /** 명령/클릭 전송 최소 간격. */
    public static final long COOLDOWN_MS = 100L;

    /** 마크가 "GUI 바깥 클릭" 에 쓰는 슬롯 인덱스. 서버는 이걸 음량 조절로 처리한다. */
    private static final int SLOT_OUTSIDE = -999;

    private static volatile int currentVolume = MusixConfig.VOLUME_DEFAULT;
    private static volatile long lastSentMillis = 0L;

    private VolumeController() {}

    public static int current() { return currentVolume; }

    /** 악기 상자 열림 — 서버 음량을 기본값으로 맞춘다. */
    public static void syncOnContainerOpen() {
        send(MusixConfig.VOLUME_DEFAULT);
    }

    /**
     * v5.4.0: 방향키용 상대 조절 — 서버의 "GUI 밖 클릭" 메커니즘을 그대로 쓴다.
     * 상자 화면 밖을 클릭하면 마크는 슬롯 -999 클릭 패킷을 보내고, 서버 플러그인이 그걸
     * 음량 조절로 처리한다. 커서를 실제로 옮기지 않고 같은 패킷만 보내면 결과가 동일하다.
     *
     * @param button 0 = 좌클릭(올리기), 1 = 우클릭(내리기)
     */
    public static void stepByOutsideClick(int button) {
        long now = System.currentTimeMillis();
        if (now - lastSentMillis < COOLDOWN_MS) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) return;
        if (!(client.currentScreen instanceof GenericContainerScreen gcs)) return;

        // 슬롯 -999 클릭은 커서에 든 아이템을 버리는 동작이기도 하다.
        // 뭔가 들고 있으면 아이템 손실이 나므로 건너뛴다.
        if (!gcs.getScreenHandler().getCursorStack().isEmpty()) {
            MusixConfig c = MusixClient.config();
            if (c != null && c.debugMode) DebugChat.warn("[음량] 커서에 아이템 있음 — 조절 건너뜀");
            return;
        }

        int syncId = gcs.getScreenHandler().syncId;
        client.interactionManager.clickSlot(syncId, SLOT_OUTSIDE, button,
                SlotActionType.PICKUP, client.player);
        lastSentMillis = now;

        // 서버가 실제 값을 관리하므로 추적값은 UI 표시용 추정치로만 갱신 (범위 clamp).
        int next = currentVolume + (button == 0 ? 1 : -1);
        currentVolume = Math.max(MusixConfig.VOLUME_MIN, Math.min(MusixConfig.VOLUME_MAX, next));

        MusixConfig cfg = MusixClient.config();
        if (cfg != null && cfg.debugMode) {
            DebugChat.ok("[음량] GUI 밖 " + (button == 0 ? "좌클릭(업)" : "우클릭(다운)")
                    + " slot=-999 → 추정 " + currentVolume);
        }
    }

    /**
     * v5.4.0: 사용자가 직접 GUI 밖을 클릭한 경우 — 패킷은 마크가 보내므로 추적값만 맞춰둔다.
     * (메뉴의 "현재 음량" 표시용 추정치)
     */
    public static void notePassiveOutsideClick(int button) {
        int next = currentVolume + (button == 0 ? 1 : -1);
        currentVolume = Math.max(MusixConfig.VOLUME_MIN, Math.min(MusixConfig.VOLUME_MAX, next));
    }

    /** Tab + 숫자용 절대 지정. `/instruments N` 명령을 보낸다. 범위 밖이면 무시. */
    public static void set(int volume) {
        if (volume < MusixConfig.VOLUME_MIN || volume > MusixConfig.VOLUME_MAX) return;
        send(volume);
    }

    /**
     * volume preset 의 동작 ID(slot) 를 실제 동작으로 변환.
     * 0 = 올리기(좌클릭), 1 = 내리기(우클릭), 2~11 = 음량 1~10 명령 지정.
     */
    public static void runAction(int actionId) {
        if (actionId == MusixConfig.VOL_ACTION_UP)   { stepByOutsideClick(0); return; }
        if (actionId == MusixConfig.VOL_ACTION_DOWN) { stepByOutsideClick(1); return; }
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
