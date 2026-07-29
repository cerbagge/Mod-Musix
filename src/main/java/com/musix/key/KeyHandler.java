package com.musix.key;

import com.musix.MusixClient;
import com.musix.config.LastSeenContainer;
import com.musix.config.MusixConfig;
import com.musix.config.MusixStatus;
import com.musix.gui.MusixMenuScreen;
import com.musix.mixin.HandledScreenAccessor;
import com.musix.util.DebugChat;
import com.musix.volume.VolumeController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KeyHandler {
    private KeyHandler() {}

    private static Screen lastDumpedScreen = null;
    private static int dumpDelayTicks = 0;

    public static void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(KeyHandler::onTick);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GenericContainerScreen gcs) {
                ScreenKeyboardEvents.allowKeyPress(screen).register((scr, key, scancode, modifiers) ->
                        !handleScreenKey(client, gcs, key, scancode, modifiers));
                // v4.0.3: 키 떼면 자동반복 추적 reset
                ScreenKeyboardEvents.allowKeyRelease(screen).register((scr, key, scancode, modifiers) -> {
                    KeyBindings.releaseKey(key);
                    return true;
                });
                // v5.3.0: GUI 밖 좌/우클릭 = 음량 업/다운
                ScreenMouseEvents.allowMouseClick(screen).register((scr, mouseX, mouseY, button) ->
                        !handleScreenMouse(client, gcs, mouseX, mouseY, button));
                if (screen != lastDumpedScreen) {
                    dumpDelayTicks = 10;
                    lastDumpedScreen = screen;
                    KeyBindings.clearPressedKeys(); // 화면 전환 시 stale 키 상태 reset
                }
            }
        });
    }

    private static void onTick(MinecraftClient client) {
        KeyBinding menu = KeyBindings.getMenuBinding();
        if (menu != null) {
            while (menu.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new MusixMenuScreen(null));
                }
            }
        }
        if (dumpDelayTicks > 0) {
            dumpDelayTicks--;
            if (dumpDelayTicks == 0
                    && client.currentScreen instanceof GenericContainerScreen gcs
                    && MusixClient.config() != null) {
                dumpContainerSlots(gcs, MusixClient.config());
            }
        }
    }

    private static boolean handleScreenKey(MinecraftClient client, GenericContainerScreen screen,
                                           int key, int scancode, int modifiers) {
        if (client.player == null || client.interactionManager == null) return false;
        MusixConfig cfg = MusixClient.config();
        Text title = screen.getTitle();
        if (title == null || !cfg.titleMatchesPrefix(title.getString())) return false;

        String preset = cfg.activePresetForTitle(title.getString());

        // v3.12.0: 눌려있는 조합키 비트를 mods 에 덧붙임 (v5.3.0: 10종으로 확장)
        int effectiveMods = KeyBindings.augmentMods(modifiers);

        if (cfg.debugMode) DebugChat.info("[KeyHandler] OS=" + MusixClient.osName()
                + " preset=" + preset + " key=" + key + " mods=" + effectiveMods
                + (effectiveMods != modifiers ? "(+조합키)" : ""));

        // v5.3.0: 음량 조절 키는 연주 preset 과 무관하게 항상 먼저 검사
        int volAction = matchVolumeAction(key, scancode, effectiveMods);
        if (volAction >= 0) {
            // v5.3.1: 방향키(상대 조절)는 꾹 누르면 OS 자동반복을 그대로 통과시켜
            // VolumeController 의 0.1초 쿨다운 간격으로 연속 조절된다.
            // Tab + 숫자(절대 지정)는 반복해도 같은 값이라 처음 누름만 처리.
            boolean isStep = volAction == MusixConfig.VOL_ACTION_UP
                    || volAction == MusixConfig.VOL_ACTION_DOWN;
            if (!isStep && !KeyBindings.acquireKeyPress(key)) return true;
            VolumeController.runAction(volAction);
            return true;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int syncId = handler.syncId;
        // v5.4.1: 클릭 대상을 상자 영역으로만 제한한다.
        // handler.slots.size() 는 플레이어 인벤토리 36칸까지 포함하므로, 매핑이 오염되면
        // 내 인벤토리 슬롯이 클릭되어 아이템이 이동/유실될 수 있다.
        int containerSize = handler.getRows() * 9;

        for (KeyBindings.NoteEntry note : KeyBindings.getNotes(preset)) {
            if (note.matches(key, scancode, effectiveMods)) {
                // v4.0.3: 키 자동반복 방지
                if (!KeyBindings.acquireKeyPress(key)) {
                    return true; // 이미 눌린 키 (auto-repeat) → 무시
                }
                int slot = note.mapping().slot;
                if (slot < 0 || slot >= containerSize) {
                    if (cfg.debugMode) DebugChat.warn("[KeyHandler] 상자 밖 슬롯 " + slot
                            + " (상자 크기 " + containerSize + ") — 클릭 거부");
                    return true;
                }
                if (MusixConfig.BLOCKED_SLOTS.contains(slot)) {
                    if (cfg.debugMode) DebugChat.warn("[KeyHandler] 차단 슬롯 " + slot + " — 클릭 건너뜀");
                    return true;
                }
                ItemStack stack = handler.slots.get(slot).getStack();
                client.interactionManager.clickSlot(syncId, slot, cfg.clickButton,
                        parseAction(cfg.clickAction), client.player);
                MusixStatus.recordNote(note.mapping().note, slot);
                if (cfg.debugMode) {
                    String si = stack.isEmpty() ? "빈슬롯" : (stack.getName().getString() + "x" + stack.getCount());
                    DebugChat.ok("[KeyHandler] " + note.mapping().note + " slot=" + slot + " (" + si + ")");
                }
                return true;
            }
        }
        // v3.11.1: 매핑 없는 modifier 조합 (Shift+E 등) 차단 — 인벤토리 닫힘 방지.
        // v3.12.0: Space 조합도 포함.
        int relevant = effectiveMods & KeyBindings.MOD_MASK;
        if (relevant != 0 && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (cfg.debugMode) DebugChat.warn("[KeyHandler] 매핑 없는 modifier 조합 차단 key=" + key + " mods=" + effectiveMods);
            return true;
        }
        if (cfg.debugMode) DebugChat.warn("[KeyHandler] 매칭 없음 (preset=" + preset + ")");
        return false;
    }

    /** v5.3.0: volume preset 에서 매칭되는 동작 ID(slot) 를 반환. 없으면 -1. */
    private static int matchVolumeAction(int key, int scancode, int mods) {
        for (KeyBindings.NoteEntry e : KeyBindings.getNotes(MusixConfig.PRESET_VOLUME)) {
            if (e.matches(key, scancode, mods)) return e.mapping().slot;
        }
        return -1;
    }

    /**
     * v5.4.0: GUI 바깥 클릭은 마크 기본 동작(슬롯 -999 패킷)이 곧 서버의 음량 조절이므로
     * 차단하지 않고 그대로 통과시킨다. 모드는 메뉴 표시용 추적값만 갱신한다.
     * 항상 false 를 반환 = 마크 기본 처리 유지.
     */
    private static boolean handleScreenMouse(MinecraftClient client, GenericContainerScreen screen,
                                             double mouseX, double mouseY, int button) {
        if (client.player == null) return false;
        if (button != 0 && button != 1) return false; // 좌/우 클릭만
        MusixConfig cfg = MusixClient.config();
        if (cfg == null) return false;
        Text title = screen.getTitle();
        if (title == null || !cfg.titleMatchesPrefix(title.getString())) return false;

        HandledScreenAccessor acc = (HandledScreenAccessor) screen;
        int gx = acc.musix$getX();
        int gy = acc.musix$getY();
        int gw = acc.musix$getBackgroundWidth();
        int gh = acc.musix$getBackgroundHeight();
        boolean insideGui = mouseX >= gx && mouseX < gx + gw
                && mouseY >= gy && mouseY < gy + gh;
        if (insideGui) return false; // GUI 안쪽은 평소대로 슬롯 클릭

        VolumeController.notePassiveOutsideClick(button);
        if (cfg.debugMode) {
            DebugChat.info("[음량] 사용자가 GUI 밖 " + (button == 0 ? "좌클릭" : "우클릭")
                    + " → 추정 " + VolumeController.current());
        }
        return false; // 마크가 슬롯 -999 패킷을 보내도록 통과
    }

    private static void dumpContainerSlots(GenericContainerScreen gcs, MusixConfig cfg) {
        Text title = gcs.getTitle();
        if (title == null || !cfg.titleMatchesPrefix(title.getString())) return;

        GenericContainerScreenHandler handler = gcs.getScreenHandler();
        int containerRows = handler.getRows();
        int containerSize = containerRows * 9;

        List<Integer> nonEmpty = new ArrayList<>();
        Map<Integer, String> itemNames = new LinkedHashMap<>();
        for (int i = 0; i < containerSize && i < handler.slots.size(); i++) {
            if (MusixConfig.BLOCKED_SLOTS.contains(i)) continue; // v3.9.0: 네비/장식 슬롯 무시
            ItemStack stack = handler.slots.get(i).getStack();
            if (!stack.isEmpty()) {
                nonEmpty.add(i);
                itemNames.put(i, stack.getName().getString());
            }
        }
        LastSeenContainer.update(title.getString(), containerRows, nonEmpty, itemNames);

        // v5.3.0: 서버 음량을 알 수 없으므로 악기 상자를 열 때마다 기본값으로 맞춘다
        VolumeController.syncOnContainerOpen();

        // v3.8.0: 상자 열릴 때 아이템 이름 기반 자동 매핑 (옵션, 기본 ON)
        if (cfg.autoMapOnOpen && !itemNames.isEmpty()) {
            KeyBindings.AutoMapResult r = KeyBindings.autoMapFromItemNames();
            // v5.5.0: debugMode 와 무관하게 항상 알린다.
            // 매핑이 조용히 바뀌면 오염을 눈치챌 방법이 없었다.
            if (r.success()) {
                DebugChat.ok("[자동매핑] " + r.message()
                        + " (되돌리기: 고급 설정 → 매핑 슬롯 → '"
                        + com.musix.config.MappingSlots.AUTO_BACKUP_NAME + "')");
            } else {
                DebugChat.warn("[자동매핑] " + r.message());
            }
        }

        if (!cfg.debugMode) return;
        String preset = cfg.activePresetForTitle(title.getString());
        DebugChat.info("[상자열림] '" + title.getString() + "' preset=" + preset
                + " rows=" + containerRows + " 비어있지않은=" + nonEmpty.size());
        for (int row = 0; row < containerRows; row++) {
            StringBuilder rowSb = new StringBuilder();
            for (int col = 0; col < 9; col++) {
                int idx = row * 9 + col;
                if (idx >= handler.slots.size()) break;
                if (!handler.slots.get(idx).getStack().isEmpty()) {
                    if (rowSb.length() > 0) rowSb.append(",");
                    rowSb.append(idx);
                }
            }
            if (rowSb.length() > 0) DebugChat.ok("  행" + row + ": " + rowSb);
        }
        // v3.10.3: 슬롯별 아이템 이름까지 출력 (자동매핑 디버그용)
        for (Map.Entry<Integer, String> e : itemNames.entrySet()) {
            DebugChat.info("  [" + e.getKey() + "] '" + e.getValue() + "'");
        }
    }

    private static SlotActionType parseAction(String name) {
        if (name == null) return SlotActionType.PICKUP;
        return switch (name) {
            case "QUICK_MOVE" -> SlotActionType.QUICK_MOVE;
            case "SWAP"       -> SlotActionType.SWAP;
            case "CLONE"      -> SlotActionType.CLONE;
            case "THROW"      -> SlotActionType.THROW;
            case "PICKUP_ALL" -> SlotActionType.PICKUP_ALL;
            default           -> SlotActionType.PICKUP;
        };
    }
}
