package com.musix.key;

import com.musix.MusixClient;
import com.musix.config.KeyMapping;
import com.musix.config.LastSeenContainer;
import com.musix.config.MusixConfig;
import com.musix.config.MusixDatabase;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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

public final class KeyBindings {
    public static final String CATEGORY = "key.categories.musix";
    // v3.6.1: Ctrl 조합 매핑 비활성화 — Shift/Alt 만 허용
    public static final int MOD_MASK = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT;

    private static final Logger LOG = LoggerFactory.getLogger("musix/keys");
    private static final Map<String, List<NoteEntry>> NOTES_BY_PRESET = new LinkedHashMap<>();
    private static KeyBinding menuBinding;
    /** v3.10.4: KeyBindingHelper.registerKeyBinding 은 init 시점에 1회만 호출 가능. */
    private static boolean keyBindingRegistered = false;

    private KeyBindings() {}

    public static final class NoteEntry {
        private final KeyMapping mapping;
        private InputUtil.Key key;

        public NoteEntry(KeyMapping mapping, InputUtil.Key key) {
            this.mapping = mapping;
            this.key = key;
        }

        public KeyMapping mapping() { return mapping; }
        public InputUtil.Key key()  { return key; }
        public int modifiers()      { return mapping.modifiers; }

        public void setKey(InputUtil.Key newKey, int newModifiers) {
            this.key = newKey;
            String translationKey = newKey.getTranslationKey();
            this.mapping.defaultKey = translationKey;
            this.mapping.modifiers = newModifiers & MOD_MASK;
            MusixDatabase.get().updateMappingKey(this.mapping.preset, this.mapping.slot,
                    translationKey, this.mapping.modifiers);
        }

        public boolean isUnbound() {
            return key == null
                    || key.equals(InputUtil.UNKNOWN_KEY)
                    || (key.getCategory() == InputUtil.Type.KEYSYM && key.getCode() == GLFW.GLFW_KEY_UNKNOWN);
        }

        public String displayKey() {
            String base = key == null ? "?" : key.getLocalizedText().getString();
            // v3.7.1: Left/Right Shift, Alt, Ctrl 표기 통합 — 좌/우 구분 없이 한 이름으로.
            base = normalizeLeftRightLabel(base);
            if (mapping.modifiers == 0) return base;
            StringBuilder sb = new StringBuilder();
            if ((mapping.modifiers & GLFW.GLFW_MOD_CONTROL) != 0) sb.append("Ctrl+");
            if ((mapping.modifiers & GLFW.GLFW_MOD_ALT) != 0)     sb.append("Alt+");
            if ((mapping.modifiers & GLFW.GLFW_MOD_SHIFT) != 0)   sb.append("Shift+");
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

        /** modifier 비교 포함 매칭. currentMods 는 GLFW.glfwGetKey 콜백의 mods 인자. */
        public boolean matches(int keyCode, int scanCode, int currentMods) {
            if (isUnbound()) return false;
            InputUtil.Type type = key.getCategory();
            boolean keyOk;
            if (type == InputUtil.Type.KEYSYM) {
                keyOk = keyCode != GLFW.GLFW_KEY_UNKNOWN
                        && equalKeyCode(key.getCode(), keyCode);
            } else if (type == InputUtil.Type.SCANCODE) {
                keyOk = keyCode == GLFW.GLFW_KEY_UNKNOWN && key.getCode() == scanCode;
            } else return false;
            if (!keyOk) return false;
            int relevant = currentMods & MOD_MASK;
            return relevant == this.mapping.modifiers;
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
            menuBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.musix.open_menu",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_M,
                    CATEGORY
            ));
            keyBindingRegistered = true;
        }

        NOTES_BY_PRESET.clear();
        for (Map.Entry<String, List<KeyMapping>> e : config.presets.entrySet()) {
            List<NoteEntry> list = new ArrayList<>();
            for (KeyMapping m : e.getValue()) {
                list.add(new NoteEntry(m, parseKey(m.defaultKey)));
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
        note.setKey(parseKey(defaultKey), 0);
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
                    note.mapping().modifiers));
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
                    note.mapping().modifiers));
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
