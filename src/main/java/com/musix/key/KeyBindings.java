package com.musix.key;

import com.musix.MusixClient;
import com.musix.config.KeyMapping;
import com.musix.config.LastSeenContainer;
import com.musix.config.MusixConfig;
import com.musix.config.MusixDatabase;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyBindings {
    public static final String CATEGORY = "key.categories.musix";

    // === 조합키 비트 ===
    // Shift / Ctrl / Alt / Win 은 GLFW 가 mods 인자로 직접 넘겨준다 (좌우 통합).
    // 그 외 키는 modifier 비트가 없으므로 glfwGetKey 로 눌림을 직접 조회해 high bit 를 부여한다.
    /** v3.12.0: Space 조합 modifier (GLFW 표준 비트와 충돌 안 하는 high bit). */
    public static final int MOD_SPACE     = 0x00010000;
    /** v5.3.0: Space 와 같은 방식으로 확장한 조합키들. */
    public static final int MOD_TAB       = 0x00020000;
    public static final int MOD_CAPSLOCK  = 0x00040000;
    public static final int MOD_ENTER     = 0x00080000;
    public static final int MOD_BACKSLASH = 0x00100000;
    public static final int MOD_BACKSPACE = 0x00200000;

    // v5.3.0: Ctrl / Win 조합 허용 (v3.6.1 에서 Ctrl 을 뺐던 제한 해제).
    public static final int MOD_MASK =
            GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER
            | MOD_SPACE | MOD_TAB | MOD_CAPSLOCK | MOD_ENTER | MOD_BACKSLASH | MOD_BACKSPACE;

    /** 조합키로 쓰이는 키들 — 단독으로는 음/동작에 매핑할 수 없다. */
    private static final int[] MODIFIER_KEYS = {
            GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_CAPS_LOCK,
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER,
            GLFW.GLFW_KEY_BACKSLASH, GLFW.GLFW_KEY_BACKSPACE,
            GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
            GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
            GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT,
            GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER,
    };

    /** 해당 키가 조합키 전용인지 (단독 매핑 거부 대상). */
    public static boolean isModifierKey(int keyCode) {
        for (int k : MODIFIER_KEYS) if (k == keyCode) return true;
        return false;
    }

    /**
     * v4.0.3: 현재 눌려있는 키 추적. OS 자동 반복 (keyPressed 중복 호출) 방지용.
     * 키 처음 누를 때 add(true) → 처리, 자동반복 시 add(false) → 무시. release 시 remove.
     */
    private static final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();

    /** 처음 누른 키면 true 반환 (acquire 성공), 자동반복이면 false. */
    public static boolean acquireKeyPress(int keyCode) {
        return pressedKeys.add(keyCode);
    }

    /** 키 떼었을 때 호출 — 다음 누름이 다시 acquire 가능하게. */
    public static void releaseKey(int keyCode) {
        pressedKeys.remove(keyCode);
    }

    /** 모든 키 상태 reset (화면 전환/포커스 잃을 때 stale 상태 방지). */
    public static void clearPressedKeys() {
        pressedKeys.clear();
    }

    /** v3.12.0: 특정 키 눌림 여부 (GLFW 직접 조회). */
    public static boolean isKeyHeld(int glfwKey) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return false;
        long window = client.getWindow().getHandle();
        if (window == 0L) return false;
        try {
            return GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS;
        } catch (Exception e) { return false; }
    }

    /**
     * v5.3.0: GLFW 가 mods 로 안 주는 조합키(Space/Tab/CapsLock/Enter/\/Backspace)를
     * 직접 조회해 비트로 덧붙인다. Shift/Ctrl/Alt/Win 은 rawMods 에 이미 들어있다.
     */
    public static int augmentMods(int rawMods) {
        int m = rawMods;
        if (isKeyHeld(GLFW.GLFW_KEY_SPACE))      m |= MOD_SPACE;
        if (isKeyHeld(GLFW.GLFW_KEY_TAB))        m |= MOD_TAB;
        if (isKeyHeld(GLFW.GLFW_KEY_CAPS_LOCK))  m |= MOD_CAPSLOCK;
        if (isKeyHeld(GLFW.GLFW_KEY_ENTER)
                || isKeyHeld(GLFW.GLFW_KEY_KP_ENTER)) m |= MOD_ENTER;
        if (isKeyHeld(GLFW.GLFW_KEY_BACKSLASH))  m |= MOD_BACKSLASH;
        if (isKeyHeld(GLFW.GLFW_KEY_BACKSPACE))  m |= MOD_BACKSPACE;
        return m;
    }

    private static final Logger LOG = LoggerFactory.getLogger("musix/keys");
    private static final Map<String, List<NoteEntry>> NOTES_BY_PRESET = new LinkedHashMap<>();
    private static KeyBinding menuBinding;
    /** v3.10.4: KeyBindingHelper.registerKeyBinding 은 init 시점에 1회만 호출 가능. */
    private static boolean keyBindingRegistered = false;

    private KeyBindings() {}

    public static final class NoteEntry {
        private final KeyMapping mapping;
        private InputUtil.Key key;
        private InputUtil.Key key2; // v4.2.0: 보조 키

        public NoteEntry(KeyMapping mapping, InputUtil.Key key, InputUtil.Key key2) {
            this.mapping = mapping;
            this.key = key;
            this.key2 = key2;
        }

        public KeyMapping mapping() { return mapping; }
        public InputUtil.Key key()  { return key; }
        public InputUtil.Key key2() { return key2; }
        public int modifiers()          { return mapping.modifiers; }
        public int secondaryModifiers() { return mapping.secondaryModifiers; }

        public void setKey(InputUtil.Key newKey, int newModifiers) {
            this.key = newKey;
            String translationKey = newKey.getTranslationKey();
            this.mapping.defaultKey = translationKey;
            this.mapping.modifiers = newModifiers & MOD_MASK;
            MusixDatabase.get().updateMappingKey(this.mapping.preset, this.mapping.slot,
                    translationKey, this.mapping.modifiers);
        }

        /** v4.2.0: 보조 키 갱신 (메인 키 보존). 해제는 InputUtil.UNKNOWN_KEY 전달. */
        public void setSecondaryKey(InputUtil.Key newKey, int newModifiers) {
            this.key2 = newKey;
            String translationKey = newKey.getTranslationKey();
            this.mapping.secondaryKey = translationKey;
            this.mapping.secondaryModifiers = newModifiers & MOD_MASK;
            MusixDatabase.get().updateMappingSecondaryKey(this.mapping.preset, this.mapping.slot,
                    translationKey, this.mapping.secondaryModifiers);
        }

        public boolean isUnbound()          { return keyIsUnbound(key); }
        public boolean isSecondaryUnbound() { return keyIsUnbound(key2); }

        /** 키가 미설정(null/UNKNOWN)인지. */
        static boolean keyIsUnbound(InputUtil.Key k) {
            return k == null
                    || k.equals(InputUtil.UNKNOWN_KEY)
                    || (k.getCategory() == InputUtil.Type.KEYSYM && k.getCode() == GLFW.GLFW_KEY_UNKNOWN);
        }

        public String displayKey() {
            return formatKeyLabel(key, mapping.modifiers);
        }

        /** v4.2.0: 보조 키 표시. 미설정이면 "-". */
        public String displaySecondaryKey() {
            if (isSecondaryUnbound()) return "-";
            return formatKeyLabel(key2, mapping.secondaryModifiers);
        }

        /** 키 + modifier 비트를 사람이 읽는 라벨로. (메인/보조 공용) */
        private static String formatKeyLabel(InputUtil.Key k, int mods) {
            String base = k == null ? "?" : k.getLocalizedText().getString();
            // v3.7.1: Left/Right Shift, Alt, Ctrl 표기 통합 — 좌/우 구분 없이 한 이름으로.
            base = normalizeLeftRightLabel(base);
            if (mods == 0) return base;
            StringBuilder sb = new StringBuilder();
            if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) sb.append("Ctrl+");
            if ((mods & GLFW.GLFW_MOD_SUPER) != 0)   sb.append("Win+");
            if ((mods & GLFW.GLFW_MOD_ALT) != 0)     sb.append("Alt+");
            if ((mods & GLFW.GLFW_MOD_SHIFT) != 0)   sb.append("Shift+");
            if ((mods & MOD_SPACE) != 0)             sb.append("Space+");
            if ((mods & MOD_TAB) != 0)               sb.append("Tab+");
            if ((mods & MOD_CAPSLOCK) != 0)          sb.append("CapsLock+");
            if ((mods & MOD_ENTER) != 0)             sb.append("Enter+");
            if ((mods & MOD_BACKSLASH) != 0)         sb.append("\\+");
            if ((mods & MOD_BACKSPACE) != 0)         sb.append("Backspace+");
            return sb + base;
        }

        /** "Left Shift"/"Right Shift"/"왼쪽 Shift"/"오른쪽 Shift" 등을 단일 "Shift" 표기로 정규화. */
        private static String normalizeLeftRightLabel(String label) {
            if (label == null) return "?";
            String lower = label.toLowerCase();
            if (lower.contains("shift")) return "Shift";
            if (lower.contains("control") || lower.contains("ctrl")) return "Ctrl";
            if (lower.contains("alt"))   return "Alt";
            return label;
        }

        /** modifier 비교 포함 매칭. 메인 키 또는 보조 키 중 하나라도 맞으면 true. */
        public boolean matches(int keyCode, int scanCode, int currentMods) {
            return matchesMain(keyCode, scanCode, currentMods)
                    || matchesSecondary(keyCode, scanCode, currentMods);
        }

        /** 메인 키만 매칭. */
        public boolean matchesMain(int keyCode, int scanCode, int currentMods) {
            return matchesOne(key, mapping.modifiers, keyCode, scanCode, currentMods);
        }

        /** 보조 키만 매칭. */
        public boolean matchesSecondary(int keyCode, int scanCode, int currentMods) {
            return matchesOne(key2, mapping.secondaryModifiers, keyCode, scanCode, currentMods);
        }

        /** 단일 키+modifier 매칭. currentMods 는 GLFW.glfwGetKey 콜백의 mods 인자. */
        private static boolean matchesOne(InputUtil.Key k, int kMods,
                                          int keyCode, int scanCode, int currentMods) {
            if (keyIsUnbound(k)) return false;
            InputUtil.Type type = k.getCategory();
            boolean keyOk;
            if (type == InputUtil.Type.KEYSYM) {
                keyOk = keyCode != GLFW.GLFW_KEY_UNKNOWN
                        && equalKeyCode(k.getCode(), keyCode);
            } else if (type == InputUtil.Type.SCANCODE) {
                keyOk = keyCode == GLFW.GLFW_KEY_UNKNOWN && k.getCode() == scanCode;
            } else return false;
            if (!keyOk) return false;
            int relevant = currentMods & MOD_MASK;
            return relevant == kMods;
        }

        /** v3.7.1: Left/Right Shift, Alt, Ctrl 을 같은 키로 취급. */
        private static boolean equalKeyCode(int a, int b) {
            if (a == b) return true;
            return canonicalModifierKey(a) == canonicalModifierKey(b);
        }

        private static int canonicalModifierKey(int code) {
            if (code == GLFW.GLFW_KEY_RIGHT_SHIFT)   return GLFW.GLFW_KEY_LEFT_SHIFT;
            if (code == GLFW.GLFW_KEY_RIGHT_CONTROL) return GLFW.GLFW_KEY_LEFT_CONTROL;
            if (code == GLFW.GLFW_KEY_RIGHT_ALT)     return GLFW.GLFW_KEY_LEFT_ALT;
            return code;
        }
    }

    /**
     * v3.10.4: KeyBindingHelper 는 init 시점 1회만, NOTES_BY_PRESET 은 매번 갱신.
     * 매핑 슬롯 불러오기 같은 런타임 재로드에서도 안전.
     */
    public static void register(MusixConfig config) {
        if (!keyBindingRegistered) {
            // v4.1.3: 메뉴 열기 기본 키 M → \ (backslash)
            menuBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.musix.open_menu",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_BACKSLASH,
                    CATEGORY
            ));
            keyBindingRegistered = true;
        }

        NOTES_BY_PRESET.clear();
        for (Map.Entry<String, List<KeyMapping>> e : config.presets.entrySet()) {
            List<NoteEntry> list = new ArrayList<>();
            for (KeyMapping m : e.getValue()) {
                list.add(new NoteEntry(m, parseKey(m.defaultKey), parseKey(m.secondaryKey)));
            }
            NOTES_BY_PRESET.put(e.getKey(), list);
        }
        LOG.info("[Musix] {} preset 등록 (keyBinding={})",
                NOTES_BY_PRESET.size(), keyBindingRegistered ? "기존" : "신규");
    }

    private static InputUtil.Key parseKey(String name) {
        if (name == null || name.isEmpty()) return InputUtil.UNKNOWN_KEY;
        try { return InputUtil.fromTranslationKey(name); }
        catch (IllegalArgumentException e) { return InputUtil.UNKNOWN_KEY; }
    }

    public static List<NoteEntry> getNotes(String preset) {
        return Collections.unmodifiableList(NOTES_BY_PRESET.getOrDefault(preset, new ArrayList<>()));
    }

    public static String activePresetName() {
        String title = LastSeenContainer.title();
        MusixConfig cfg = MusixClient.config();
        if (cfg == null) return MusixConfig.PRESET_COMMON;
        return cfg.activePresetForTitle(title);
    }

    public static List<NoteEntry> getActiveNotes() {
        return getNotes(activePresetName());
    }

    public static NoteEntry getNote(String preset, int index) {
        List<NoteEntry> list = NOTES_BY_PRESET.get(preset);
        if (list == null || index < 0 || index >= list.size()) return null;
        return list.get(index);
    }

    public static KeyBinding getMenuBinding() { return menuBinding; }

    public static int findNoteIndexByKey(String preset, int keyCode, int scanCode, int currentMods) {
        List<NoteEntry> list = NOTES_BY_PRESET.get(preset);
        if (list == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).matches(keyCode, scanCode, currentMods)) return i;
        }
        return -1;
    }

    public static boolean menuKeyMatches(int keyCode, int scanCode) {
        KeyBinding mb = menuBinding;
        return mb != null && mb.matchesKey(keyCode, scanCode);
    }

    public static void resetNote(NoteEntry note) {
        if (note == null) return;
        String defaultKey = MusixConfig.lookupDefaultKeyForSlot(note.mapping().preset, note.mapping().slot);
        if (defaultKey == null) return;
        int defaultMods = MusixConfig.lookupDefaultModifierForSlot(note.mapping().preset, note.mapping().slot);
        note.setKey(parseKey(defaultKey), defaultMods);
        note.setSecondaryKey(InputUtil.UNKNOWN_KEY, 0); // v4.2.0: 전체 초기화 시 보조 키 해제
    }

    public static void resetPreset(String preset) {
        List<NoteEntry> list = NOTES_BY_PRESET.get(preset);
        if (list == null) return;
        for (NoteEntry note : list) resetNote(note);
        LOG.info("[Musix] preset '{}' 키 기본값 복원", preset);
    }

    /** LastSeenContainer 캐시 슬롯으로 활성 preset 의 슬롯 매핑 자동 갱신. 차단 슬롯 제외. */
    public static AutoMapResult autoMapFromLastContainer() {
        List<Integer> slots = LastSeenContainer.nonEmptySlots();
        slots.removeIf(MusixConfig.BLOCKED_SLOTS::contains); // v3.9.0: 차단 슬롯 안전망
        String preset = activePresetName();
        List<NoteEntry> notes = NOTES_BY_PRESET.get(preset);
        if (notes == null) return new AutoMapResult(false, "preset '" + preset + "' 없음");
        int needed = notes.size();
        int got = slots.size();
        if (got == 0) return new AutoMapResult(false, "캐시된 상자 없음");
        if (got != needed) {
            return new AutoMapResult(false, "슬롯 수 불일치 ('" + preset + "'): 필요 " + needed + " / 캐시 " + got);
        }
        List<MusixDatabase.MappingRow> rows = new ArrayList<>();
        for (int i = 0; i < needed; i++) {
            NoteEntry note = notes.get(i);
            int newSlot = slots.get(i);
            note.mapping().slot = newSlot;
            rows.add(new MusixDatabase.MappingRow(newSlot, note.mapping().note,
                    note.mapping().defaultKey == null ? "" : note.mapping().defaultKey,
                    note.mapping().modifiers,
                    note.mapping().secondaryKey == null ? "" : note.mapping().secondaryKey,
                    note.mapping().secondaryModifiers));
        }
        MusixDatabase.get().replaceMappings(preset, rows);
        LOG.info("[Musix] '{}' 자동 매핑 완료 ({}음)", preset, needed);
        return new AutoMapResult(true, "'" + preset + "' " + needed + "음 자동 매핑");
    }

    /**
     * v3.8.0: 슬롯 아이템 이름으로 자동 매핑.
     * - LastSeenContainer 의 slot→itemName 매핑을 이용.
     * - 활성 preset 의 각 NoteEntry 에 대해 mapping.note 와 일치하는 아이템 이름을 가진 슬롯을 찾아 할당.
     * - 매칭 실패한 음은 기존 슬롯 유지 (덮어쓰지 않음).
     * - 같은 슬롯이 두 음에 동시 매칭되지 않도록 사용된 슬롯은 제외.
     */
    public static AutoMapResult autoMapFromItemNames() {
        Map<Integer, String> names = LastSeenContainer.itemNames();
        String preset = activePresetName();
        List<NoteEntry> notes = NOTES_BY_PRESET.get(preset);
        if (notes == null) return new AutoMapResult(false, "preset '" + preset + "' 없음");
        if (names.isEmpty()) return new AutoMapResult(false, "캐시된 상자 아이템 없음");

        Set<Integer> usedSlots = new HashSet<>();
        int matched = 0;
        List<MusixDatabase.MappingRow> rows = new ArrayList<>();
        for (NoteEntry note : notes) {
            String noteName = note.mapping().note;
            Integer found = findSlotByItemName(names, noteName, usedSlots);
            if (found != null) {
                note.mapping().slot = found;
                usedSlots.add(found);
                matched++;
            }
            rows.add(new MusixDatabase.MappingRow(
                    note.mapping().slot,
                    note.mapping().note,
                    note.mapping().defaultKey == null ? "" : note.mapping().defaultKey,
                    note.mapping().modifiers,
                    note.mapping().secondaryKey == null ? "" : note.mapping().secondaryKey,
                    note.mapping().secondaryModifiers));
        }
        MusixDatabase.get().replaceMappings(preset, rows);
        LOG.info("[Musix] 이름 기반 자동 매핑: '{}' {}/{} 매칭", preset, matched, notes.size());
        // v3.10.3: 매칭 실패 시 LOG 에 상세 정보 (사용자 디버그 채팅과 별개로 로그 파일에 남김)
        if (matched < notes.size()) {
            LOG.warn("[Musix] 매핑 못한 음 (preset='{}'):", preset);
            for (NoteEntry note : notes) {
                if (!usedSlots.contains(note.mapping().slot)
                        || !names.containsValue(note.mapping().note)) {
                    LOG.warn("  찾는 음 '{}' (정규화: '{}')",
                            note.mapping().note, normalizeName(note.mapping().note));
                }
            }
            LOG.warn("[Musix] 슬롯의 실제 아이템 이름:");
            for (Map.Entry<Integer, String> e : names.entrySet()) {
                LOG.warn("  [{}] '{}' (정규화: '{}')",
                        e.getKey(), e.getValue(), normalizeName(e.getValue()));
            }
        }
        return new AutoMapResult(matched > 0,
                "'" + preset + "' 이름 매칭: " + matched + "/" + notes.size());
    }

    /** noteName 과 가장 잘 맞는 슬롯 인덱스 검색. 정확 일치 우선, 없으면 부분 일치. 차단 슬롯 제외. */
    private static Integer findSlotByItemName(Map<Integer, String> names, String noteName, Set<Integer> exclude) {
        if (noteName == null || noteName.isEmpty()) return null;
        String target = normalizeName(noteName);
        if (target.isEmpty()) return null;

        // 1순위: 정규화 후 완전 일치
        for (Map.Entry<Integer, String> e : names.entrySet()) {
            if (exclude.contains(e.getKey())) continue;
            if (MusixConfig.BLOCKED_SLOTS.contains(e.getKey())) continue;
            if (target.equals(normalizeName(e.getValue()))) return e.getKey();
        }
        // 2순위: 아이템 이름 안에 target 이 포함 (예: "음악-하프 - F#2")
        for (Map.Entry<Integer, String> e : names.entrySet()) {
            if (exclude.contains(e.getKey())) continue;
            if (MusixConfig.BLOCKED_SLOTS.contains(e.getKey())) continue;
            String n = normalizeName(e.getValue());
            if (n.contains(target)) return e.getKey();
        }
        // 3순위: target 안에 아이템 이름이 포함 (역방향, 드물지만 안전)
        for (Map.Entry<Integer, String> e : names.entrySet()) {
            if (exclude.contains(e.getKey())) continue;
            if (MusixConfig.BLOCKED_SLOTS.contains(e.getKey())) continue;
            String n = normalizeName(e.getValue());
            if (!n.isEmpty() && target.contains(n)) return e.getKey();
        }
        return null;
    }

    /** 공백 제거 + 대문자 통일 + 마인크래프트 §형식코드 제거. */
    private static String normalizeName(String s) {
        if (s == null) return "";
        // §0~§r 등 색상/포맷 코드 제거
        String stripped = s.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
        return stripped.trim().toUpperCase().replaceAll("\\s+", "");
    }

    public record AutoMapResult(boolean success, String message) {}
}
