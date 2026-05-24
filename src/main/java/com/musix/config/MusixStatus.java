package com.musix.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v4.1.0: 사용 통계 — 분당 히스토리, 세션 정보, 음별/preset 카운트.
 * MusixStatsScreen 에서 꺾은선/막대 차트 데이터 소스.
 */
public final class MusixStatus {
    private static volatile String lastNote = null;
    private static volatile int lastSlot = -1;
    private static volatile long lastNoteMillis = -1L;
    private static volatile long noteCount = 0L;
    /** 음별 카운트 (note 이름 → 누른 횟수) */
    private static final Map<String, Integer> COUNTS = new ConcurrentHashMap<>();
    /** preset 별 카운트 (common, drum) */
    private static final Map<String, Integer> PRESET_COUNTS = new ConcurrentHashMap<>();
    /** v4.1.0: 분당 입력 히스토리. 최근 60분 유지. [minuteIndex, count]. */
    private static final Deque<long[]> MINUTE_BUCKETS = new ArrayDeque<>();
    private static volatile long sessionStart = System.currentTimeMillis();

    private MusixStatus() {}

    public static void recordNote(String note, int slot) {
        lastNote = note;
        lastSlot = slot;
        lastNoteMillis = System.currentTimeMillis();
        noteCount++;
        if (note != null) {
            COUNTS.merge(note, 1, Integer::sum);
            String preset = guessPreset(note);
            PRESET_COUNTS.merge(preset, 1, Integer::sum);
        }
        // 분당 버킷 갱신 (최근 60분만 유지)
        long minute = System.currentTimeMillis() / 60_000L;
        synchronized (MINUTE_BUCKETS) {
            long[] last = MINUTE_BUCKETS.peekLast();
            if (last != null && last[0] == minute) {
                last[1]++;
            } else {
                MINUTE_BUCKETS.addLast(new long[]{minute, 1});
            }
            long cutoff = minute - 60;
            while (!MINUTE_BUCKETS.isEmpty() && MINUTE_BUCKETS.peekFirst()[0] < cutoff) {
                MINUTE_BUCKETS.pollFirst();
            }
        }
    }

    // === 기존 API ===
    public static String lastNote() { return lastNote; }
    public static int lastSlot() { return lastSlot; }
    public static long noteCount() { return noteCount; }
    public static long millisSinceLastNote() {
        if (lastNoteMillis < 0L) return -1L;
        return System.currentTimeMillis() - lastNoteMillis;
    }
    public static int countOf(String note) {
        if (note == null) return 0;
        Integer v = COUNTS.get(note);
        return v == null ? 0 : v;
    }

    // === v4.1.0 통계 API ===

    public static long sessionStartMillis() { return sessionStart; }

    public static long sessionElapsedMillis() {
        return System.currentTimeMillis() - sessionStart;
    }

    /** 최근 60분 분당 입력 히스토리 (오래된 순). 각 항목 [minuteIndex, count]. */
    public static List<long[]> minuteHistory() {
        synchronized (MINUTE_BUCKETS) {
            List<long[]> out = new ArrayList<>(MINUTE_BUCKETS.size());
            for (long[] b : MINUTE_BUCKETS) out.add(new long[]{b[0], b[1]});
            return out;
        }
    }

    /** 현재 진행 중인 분의 입력 수. */
    public static int currentMinuteCount() {
        long minute = System.currentTimeMillis() / 60_000L;
        synchronized (MINUTE_BUCKETS) {
            long[] last = MINUTE_BUCKETS.peekLast();
            if (last != null && last[0] == minute) return (int) last[1];
        }
        return 0;
    }

    /** 분당 평균 (전체 세션 기준). */
    public static double averagePerMinute() {
        double minutes = Math.max(1.0 / 60.0, sessionElapsedMillis() / 60_000.0);
        return noteCount / minutes;
    }

    /** 가장 많이 사용한 음 N개 (정렬됨, 내림차순). */
    public static List<Map.Entry<String, Integer>> topNotes(int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(COUNTS.entrySet());
        list.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        if (list.size() > limit) list = list.subList(0, limit);
        return list;
    }

    /** preset 별 카운트. */
    public static Map<String, Integer> presetCounts() {
        return new java.util.LinkedHashMap<>(PRESET_COUNTS);
    }

    /** 통계 초기화 (세션 reset 포함). */
    public static synchronized void reset() {
        lastNote = null;
        lastSlot = -1;
        lastNoteMillis = -1L;
        noteCount = 0L;
        COUNTS.clear();
        PRESET_COUNTS.clear();
        synchronized (MINUTE_BUCKETS) { MINUTE_BUCKETS.clear(); }
        sessionStart = System.currentTimeMillis();
    }

    /** 음 이름 형식으로 preset 추정 (common = "F#2" 등 영문, drum = 한글). */
    private static String guessPreset(String note) {
        if (note == null || note.isEmpty()) return "?";
        char c = note.charAt(0);
        if (c >= 'A' && c <= 'G') return "common";
        return "drum";
    }
}
