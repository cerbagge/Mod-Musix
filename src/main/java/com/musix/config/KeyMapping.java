package com.musix.config;

public class KeyMapping {
    public String preset;     // "common" | "drum" | "instrument_01" ... "instrument_16"
    public String note;
    public int slot;
    public String defaultKey;
    public int modifiers;     // GLFW bitfield: SHIFT=1, CTRL=2, ALT=4

    public KeyMapping() {
    }

    public KeyMapping(String preset, String note, int slot, String defaultKey, int modifiers) {
        this.preset = preset;
        this.note = note;
        this.slot = slot;
        this.defaultKey = defaultKey;
        this.modifiers = modifiers;
    }

    public String translationSuffix() {
        if (note == null) return "unknown_" + slot;
        return note.toLowerCase().replace("#", "s").replace(".", "_").replace("-", "_");
    }
}
