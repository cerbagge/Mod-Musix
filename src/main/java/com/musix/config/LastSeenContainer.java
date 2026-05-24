package com.musix.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 마지막으로 열린 Musix 상자의 슬롯 상태 캐시.
 * - 비어있지 않은 슬롯 인덱스 목록 (정렬됨)
 * - 슬롯 → 아이템 표시 이름 매핑 (v3.8.0: 아이템 이름 기반 자동 매핑용)
 * KeyHandler 가 상자 열림 시 갱신.
 */
public final class LastSeenContainer {
    private static final List<Integer> NON_EMPTY_SLOTS = new ArrayList<>();
    private static final Map<Integer, String> ITEM_NAMES = new LinkedHashMap<>();
    private static String title = null;
    private static int rows = 0;
    private static long timestamp = 0L;

    private LastSeenContainer() {}

    public static synchronized void update(String title, int rows,
                                           List<Integer> nonEmptySlots,
                                           Map<Integer, String> itemNames) {
        LastSeenContainer.title = title;
        LastSeenContainer.rows = rows;
        NON_EMPTY_SLOTS.clear();
        NON_EMPTY_SLOTS.addAll(nonEmptySlots);
        Collections.sort(NON_EMPTY_SLOTS); // 위→아래, 왼쪽→오른쪽 정렬 보장
        ITEM_NAMES.clear();
        if (itemNames != null) ITEM_NAMES.putAll(itemNames);
        timestamp = System.currentTimeMillis();
    }

    public static synchronized List<Integer> nonEmptySlots() {
        return new ArrayList<>(NON_EMPTY_SLOTS);
    }

    public static synchronized Map<Integer, String> itemNames() {
        return new LinkedHashMap<>(ITEM_NAMES);
    }

    public static synchronized String title() { return title; }
    public static synchronized int rows() { return rows; }
    public static synchronized long timestamp() { return timestamp; }

    public static synchronized boolean isFresh(long maxAgeMillis) {
        return timestamp > 0 && System.currentTimeMillis() - timestamp < maxAgeMillis;
    }
}
