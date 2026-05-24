package com.musix.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 마지막으로 열린 Musix 상자의 비어있지 않은 슬롯 인덱스 캐시.
 * KeyHandler가 음악 상자가 열려있는 동안 매 tick(또는 키 입력 시) 갱신.
 * MusixMenuScreen 의 "현재 상자로 자동 매핑" 버튼이 이 캐시를 사용.
 */
public final class LastSeenContainer {
    private static final List<Integer> NON_EMPTY_SLOTS = new ArrayList<>();
    private static String title = null;
    private static int rows = 0;
    private static long timestamp = 0L;

    private LastSeenContainer() {}

    public static synchronized void update(String title, int rows, List<Integer> nonEmptySlots) {
        LastSeenContainer.title = title;
        LastSeenContainer.rows = rows;
        NON_EMPTY_SLOTS.clear();
        NON_EMPTY_SLOTS.addAll(nonEmptySlots);
        Collections.sort(NON_EMPTY_SLOTS); // 위→아래, 왼쪽→오른쪽 정렬 보장
        timestamp = System.currentTimeMillis();
    }

    public static synchronized List<Integer> nonEmptySlots() {
        return new ArrayList<>(NON_EMPTY_SLOTS);
    }

    public static synchronized String title() { return title; }
    public static synchronized int rows() { return rows; }
    public static synchronized long timestamp() { return timestamp; }

    public static synchronized boolean isFresh(long maxAgeMillis) {
        return timestamp > 0 && System.currentTimeMillis() - timestamp < maxAgeMillis;
    }
}
