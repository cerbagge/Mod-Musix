package com.musix.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class MusixStatus {
    private static volatile String lastNote = null;
    private static volatile int lastSlot = -1;
    private static volatile long lastNoteMillis = -1L;
    private static volatile long noteCount = 0L;
    /** 음별 카운트 (note 이름 → 누른 횟수) */
    private static final Map<String, Integer> COUNTS = new ConcurrentHashMap<>();

    private MusixStatus() {}

    public static void recordNote(String note, int slot) {
        lastNote = note;
        lastSlot = slot;
        lastNoteMillis = System.currentTimeMillis();
        noteCount++;
        if (note != null) {
            COUNTS.merge(note, 1, Integer::sum);
        }
    }

    public static String lastNote() { return lastNote; }
    public static int lastSlot() { return lastSlot; }
    public static long noteCount() { return noteCount; }

    public static long millisSinceLastNote() {
        if (lastNoteMillis < 0L) return -1L;
        return System.currentTimeMillis() - lastNoteMillis;
    }

    /** 그 음을 몇 번 쳤는지. 한 번도 안 쳤으면 0. */
    public static int countOf(String note) {
        if (note == null) return 0;
        Integer v = COUNTS.get(note);
        return v == null ? 0 : v;
    }
}
