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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KeyBindings {
    public static final String CATEGORY = "key.categories.musix";
    // v3.6.1: Ctrl 조합 매핑 비활성화 — Shift/Alt 만 허용
    public static final int MOD_MASK = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT;

    private static final Logger LOG = LoggerFactory.getLogger("musix/keys");
    private static final Map<String, List<NoteEntry>> NOTES_BY_PRESET = new LinkedHashMap<>();
    private static KeyBinding menuBinding;

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
            if (mapping.modifiers == 0) return base;
            StringBuilder sb = new StringBuilder();
            if ((mapping.modifiers & GLFW.GLFW_MOD_CONTROL) != 0) sb.append("Ctrl+");
            if ((mapping.modifiers & GLFW.GLFW_MOD_ALT) != 0)     sb.append("Alt+");
            if ((mapping.modifiers & GLFW.GLFW_MOD_SHIFT) != 0)   sb.append("Shift+");
            return sb + base;
        }

        /** modifier 비교 포함 매칭. currentMods 는 GLFW.glfwGetKey 콜백의 mods 인자. */
        public boolean matches(int keyCode, int scanCode, int currentMods) {
            if (isUnbound()) return false;
            InputUtil.Type type = key.getCategory();
            boolean keyOk;
            if (type == InputUtil.Type.KEYSYM) {
                keyOk = keyCode != GLFW.GLFW_KEY_UNKNOWN && key.getCode() == keyCode;
            } else if (type == InputUtil.Type.SCANCODE) {
                keyOk = keyCode == GLFW.GLFW_KEY_UNKNOWN && key.getCode() == scanCode;
            } else return false;
            if (!keyOk) return false;
            int relevant = currentMods & MOD_MASK;
            return relevant == this.mapping.modifiers;
        }
    }

    public static void register(MusixConfig config) {
        menuBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.musix.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                CATEGORY
        ));

        NOTES_BY_PRESET.clear();
        for (Map.Entry<String, List<KeyMapping>> e : config.presets.entrySet()) {
            List<NoteEntry> list = new ArrayList<>();
            for (KeyMapping m : e.getValue()) {
                list.add(new NoteEntry(m, parseKey(m.defaultKey)));
            }
            NOTES_BY_PRESET.put(e.getKey(), list);
        }
        LOG.info("[Musix] {} preset 등록", NOTES_BY_PRESET.size());
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

    /** LastSeenContainer 캐시 슬롯으로 활성 preset 의 슬롯 매핑 자동 갱신. */
    public static AutoMapResult autoMapFromLastContainer() {
        List<Integer> slots = LastSeenContainer.nonEmptySlots();
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

    public record AutoMapResult(boolean success, String message) {}
}
