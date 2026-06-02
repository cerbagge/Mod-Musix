package com.musix.config;

public class KeyMapping {
    public String preset;     // "common" | "drum" | "instrument_01" ... "instrument_16"
    public String note;
    public int slot;
    public String defaultKey;
    public int modifiers;     // GLFW bitfield: SHIFT=1, CTRL=2, ALT=4
    // v4.2.0: 음별 보조 키 — 메인 키와 별개로 같은 슬롯을 트리거. 빈 문자열 = 미설정.
    public String secondaryKey;
    public int secondaryModifiers;

    public KeyMapping() {
        this.secondaryKey = "";
    }

    public KeyMapping(String preset, String note, int slot, String defaultKey, int modifiers) {
        this(preset, note, slot, defaultKey, modifiers, "", 0);
    }

    public KeyMapping(String preset, String note, int slot, String defaultKey, int modifiers,
                      String secondaryKey, int secondaryModifiers) {
        this.preset = preset;
        this.note = note;
        this.slot = slot;
        this.defaultKey = defaultKey;
        this.modifiers = modifiers;
        this.secondaryKey = secondaryKey == null ? "" : secondaryKey;
        this.secondaryModifiers = secondaryModifiers;
    }

    public String translationSuffix() {
        if (note == null) return "unknown_" + slot;
        return note.toLowerCase().replace("#", "s").replace(".", "_").replace("-", "_");
    }
}
