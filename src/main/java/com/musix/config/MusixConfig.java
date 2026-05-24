package com.musix.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v3.1.0: 18 preset (common + drum + 16 악기). 각 preset 마다 독립 매핑.
 * preset 이름은 placeholder ("instrument_01"~"instrument_16"). 사용자가 메뉴에서 표시 이름 지정 예정.
 */
public class MusixConfig {
    public static final String PRESET_COMMON = "common";
    public static final String PRESET_DRUM   = "drum";

    /**
     * 자동 매핑/dump 에서 항상 제외하는 슬롯.
     * PlanetEarth 서버 GUI 의 네비게이션/장식 슬롯이라 음표로 잘못 매칭되면 안 됨.
     * v3.10.2: 슬롯 32 는 실제 스네어-하 매핑에 사용되므로 제거 ({6, 19, 45, 53} 만 차단).
     */
    public static final Set<Integer> BLOCKED_SLOTS = Set.of(6, 19, 45, 53);

    /** 전체 preset 순서. v3.2.0: 단순화 — drum 과 그 외 (common). */
    public static final List<String> ALL_PRESETS = new ArrayList<>();
    static {
        ALL_PRESETS.add(PRESET_COMMON);
        ALL_PRESETS.add(PRESET_DRUM);
    }

    private static final Logger LOG = LoggerFactory.getLogger("musix/config");

    /** preset → 기본 음 매핑 (note, slot, defaultKey). 16 악기는 common 과 같은 49음 복사. */
    private static final Map<String, Object[][]> DEFAULT_PRESETS = new LinkedHashMap<>();

    static {
        Object[][] common49 = new Object[][] {
                // 옥타브 2 (6): F#2 G2 G#2 A2 A#2 B2 — 키 1 2 3 4 5 6
                {"F#2",   0, "key.keyboard.1"},
                {"G2",    1, "key.keyboard.2"},
                {"G#2",   2, "key.keyboard.3"},
                {"A2",    3, "key.keyboard.4"},
                {"A#2",   4, "key.keyboard.5"},
                {"B2",    5, "key.keyboard.6"},
                // 옥타브 3 (12): C3~B3 — 키 7 8 9 0 - = Q W E R T Y
                {"C3",    6, "key.keyboard.7"},
                {"C#3",   7, "key.keyboard.8"},
                {"D3",    8, "key.keyboard.9"},
                {"D#3",   9, "key.keyboard.0"},
                {"E3",   10, "key.keyboard.minus"},
                {"F3",   11, "key.keyboard.equal"},
                {"F#3",  12, "key.keyboard.q"},
                {"G3",   13, "key.keyboard.w"},
                {"G#3",  14, "key.keyboard.e"},
                {"A3",   15, "key.keyboard.r"},
                {"A#3",  16, "key.keyboard.t"},
                {"B3",   17, "key.keyboard.y"},
                // 옥타브 4 (12): C4~B4 — 키 U I O P [ ] A S D F G H
                {"C4",   18, "key.keyboard.u"},
                {"C#4",  19, "key.keyboard.i"},
                {"D4",   20, "key.keyboard.o"},
                {"D#4",  21, "key.keyboard.p"},
                {"E4",   22, "key.keyboard.left.bracket"},
                {"F4",   23, "key.keyboard.right.bracket"},
                {"F#4",  24, "key.keyboard.a"},
                {"G4",   25, "key.keyboard.s"},
                {"G#4",  26, "key.keyboard.d"},
                {"A4",   27, "key.keyboard.f"},
                {"A#4",  28, "key.keyboard.g"},
                {"B4",   29, "key.keyboard.h"},
                // 옥타브 5 (12): C5~B5 — 키 J K L ; ' Z X C V B N M
                {"C5",   30, "key.keyboard.j"},
                {"C#5",  31, "key.keyboard.k"},
                {"D5",   32, "key.keyboard.l"},
                {"D#5",  33, "key.keyboard.semicolon"},
                {"E5",   34, "key.keyboard.apostrophe"},
                {"F5",   35, "key.keyboard.z"},
                {"F#5",  36, "key.keyboard.x"},
                {"G5",   37, "key.keyboard.c"},
                {"G#5",  38, "key.keyboard.v"},
                {"A5",   39, "key.keyboard.b"},
                {"A#5",  40, "key.keyboard.n"},
                {"B5",   41, "key.keyboard.m"},
                // 옥타브 6 (7): C6~F#6 — 키 , . / \ + 3음 미설정
                // v3.7.1: D#6 기본 키 \ (backslash) 추가
                {"C6",   42, "key.keyboard.comma"},
                {"C#6",  43, "key.keyboard.period"},
                {"D6",   44, "key.keyboard.slash"},
                {"D#6",  45, "key.keyboard.backslash"},
                {"E6",   46, ""},
                {"F6",   47, ""},
                {"F#6",  48, ""},
        };
        DEFAULT_PRESETS.put(PRESET_COMMON, common49);

        // 드럼 12음 (v3.10.5): 게임 GUI 표시명과 일치 — 자동매핑 100% 매칭용.
        // 실제 PlanetEarth 드럼 GUI 구조 (디버그 확인):
        //   col 3 (slot 12/21/30) = 스네어 낮은음 / 중간음 / 높은음
        //   col 4 (slot 13/22/31) = 베이스 낮은음 / 중간음 / 높은음
        //   col 5 (slot 14/23/32) = 하이햇 낮은음 / 중간음 / 높은음
        //   row 5 (slot 48/49/50) = 심벌 낮은음 / 중간음 / 높은음
        DEFAULT_PRESETS.put(PRESET_DRUM, new Object[][] {
                // 베이스: 1 / 2 / 3 (낮음→중간→높음)
                {"베이스 낮은음", 13, "key.keyboard.1"},
                {"베이스 중간음", 22, "key.keyboard.2"},
                {"베이스 높은음", 31, "key.keyboard.3"},
                // 스네어: Q / W / E
                {"스네어 낮은음", 12, "key.keyboard.q"},
                {"스네어 중간음", 21, "key.keyboard.w"},
                {"스네어 높은음", 30, "key.keyboard.e"},
                // 하이햇: A / S / D
                {"하이햇 낮은음", 14, "key.keyboard.a"},
                {"하이햇 중간음", 23, "key.keyboard.s"},
                {"하이햇 높은음", 32, "key.keyboard.d"},
                // 심벌: Z / X / C
                {"심벌 낮은음",   48, "key.keyboard.z"},
                {"심벌 중간음",   49, "key.keyboard.x"},
                {"심벌 높은음",   50, "key.keyboard.c"},
        });
    }

    public String containerPrefix;
    public int clickButton;
    public String clickAction;
    public boolean debugMode;
    /** v3.8.0: 음악/악기 상자가 열릴 때 슬롯 아이템 이름으로 자동 매핑할지 여부. */
    public boolean autoMapOnOpen;
    /** preset 이름 → 사용자가 정한 표시 이름 (메뉴 표시용). 없으면 preset 이름 그대로. */
    public Map<String, String> presetDisplayNames = new LinkedHashMap<>();
    /** preset 이름 → 매칭할 상자 제목 부분 문자열 (예: "하프"). 매칭되면 그 preset 활성. */
    public Map<String, String> presetTitleMatch = new LinkedHashMap<>();
    public Map<String, List<KeyMapping>> presets = new LinkedHashMap<>();

    public static MusixConfig load() {
        MusixDatabase db = MusixDatabase.get();
        if (db.mappingCount() == 0) {
            seedAllPresets(db);
        } else {
            migrateLegacyNoteNames(db); // v3.9.1: 옛 placeholder 이름 정리
            for (String p : ALL_PRESETS) ensurePresetExists(db, p);
        }

        MusixConfig config = new MusixConfig();
        config.containerPrefix = db.getSetting("containerPrefix", "음악,악기");
        config.clickButton     = parseInt(db.getSetting("clickButton", "0"), 0);
        config.clickAction     = db.getSetting("clickAction", "PICKUP");
        config.debugMode       = "true".equalsIgnoreCase(db.getSetting("debugMode", "false"));
        config.autoMapOnOpen   = "true".equalsIgnoreCase(db.getSetting("autoMapOnOpen", "true"));

        for (String preset : ALL_PRESETS) {
            List<KeyMapping> list = new ArrayList<>();
            for (MusixDatabase.MappingRow r : db.getAllMappings(preset)) {
                list.add(new KeyMapping(preset, r.note(), r.slot(), r.keyName(), r.modifiers()));
            }
            config.presets.put(preset, list);
            // 표시 이름 / 매칭 문자열 (사용자가 메뉴에서 설정)
            config.presetDisplayNames.put(preset,
                    db.getSetting("presetName." + preset, defaultDisplayName(preset)));
            config.presetTitleMatch.put(preset,
                    db.getSetting("presetMatch." + preset, defaultTitleMatch(preset)));
        }
        int total = config.presets.values().stream().mapToInt(List::size).sum();
        LOG.info("[Musix] DB 로드. 총 {}음 / {} preset, prefix='{}', click={}/{}",
                total, ALL_PRESETS.size(), config.containerPrefix, config.clickButton, config.clickAction);
        return config;
    }

    /** 상자 제목으로 활성 preset 결정. drum 매칭 키워드는 콤마 다중 지원. */
    public String activePresetForTitle(String title) {
        if (title == null) return PRESET_COMMON;
        String drumMatch = presetTitleMatch.getOrDefault(PRESET_DRUM, "(드럼),드럼");
        if (!drumMatch.isEmpty()) {
            for (String token : drumMatch.split(",")) {
                String t = token.trim();
                if (!t.isEmpty() && title.contains(t)) return PRESET_DRUM;
            }
        }
        return PRESET_COMMON;
    }

    /** 상자 제목이 containerPrefix 의 어느 항목으로든 시작하면 true. 콤마 구분 다중 prefix 지원. */
    public boolean titleMatchesPrefix(String title) {
        if (title == null || containerPrefix == null || containerPrefix.isEmpty()) return false;
        for (String p : containerPrefix.split(",")) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty() && title.startsWith(trimmed)) return true;
        }
        return false;
    }

    public List<KeyMapping> mappingsFor(String preset) {
        return presets.getOrDefault(preset, new ArrayList<>());
    }

    public String displayNameOf(String preset) {
        return presetDisplayNames.getOrDefault(preset, preset);
    }

    public void save() {}

    public void setContainerPrefix(String prefix) {
        this.containerPrefix = prefix == null ? "" : prefix;
        MusixDatabase.get().setSetting("containerPrefix", this.containerPrefix);
    }
    public void setClickButton(int button) {
        this.clickButton = Math.max(0, Math.min(2, button));
        MusixDatabase.get().setSetting("clickButton", Integer.toString(this.clickButton));
    }
    public void setClickAction(String action) {
        this.clickAction = action == null || action.isEmpty() ? "PICKUP" : action;
        MusixDatabase.get().setSetting("clickAction", this.clickAction);
    }
    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
        MusixDatabase.get().setSetting("debugMode", Boolean.toString(enabled));
    }
    public void setAutoMapOnOpen(boolean enabled) {
        this.autoMapOnOpen = enabled;
        MusixDatabase.get().setSetting("autoMapOnOpen", Boolean.toString(enabled));
    }
    public void setPresetDisplayName(String preset, String name) {
        this.presetDisplayNames.put(preset, name);
        MusixDatabase.get().setSetting("presetName." + preset, name);
    }
    public void setPresetTitleMatch(String preset, String match) {
        this.presetTitleMatch.put(preset, match);
        MusixDatabase.get().setSetting("presetMatch." + preset, match);
    }

    public static String lookupDefaultKeyForSlot(String preset, int slot) {
        Object[][] table = DEFAULT_PRESETS.get(preset);
        if (table == null) return null;
        for (Object[] d : table) {
            if ((int) d[1] == slot) return osAdjustKey((String) d[2]);
        }
        return null;
    }

    /**
     * v3.10.5: Mac 에서 일부 키가 Windows 와 다르게 인식되는 케이스를 자동 대체.
     * 현재 알려진 차이는 거의 없음 (GLFW 가 표준 키를 OS 무관하게 동일 코드로 변환).
     * 다만 Mac 일부 키보드의 backslash 위치 문제 대비.
     */
    public static String osAdjustKey(String keyName) {
        if (keyName == null || keyName.isEmpty()) return keyName;
        if (com.musix.MusixClient.isMacOS()) {
            // Mac 영문 키보드는 backslash 가 표준 위치에 있음 (Return 위).
            // 한글 키보드의 ₩ 키도 GLFW_KEY_BACKSLASH 와 동일.
            // 별도 OS 대체 필요한 케이스가 발견되면 여기 추가.
            return keyName;
        }
        return keyName;
    }

    public static Object[][] defaultsFor(String preset) {
        return DEFAULT_PRESETS.getOrDefault(preset, new Object[0][]);
    }

    public void resetSettingsToDefaults() {
        setContainerPrefix("음악");
        setClickButton(0);
        setClickAction("PICKUP");
        setDebugMode(false);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static String defaultDisplayName(String preset) {
        return switch (preset) {
            case PRESET_COMMON -> "공통 (드럼 외)";
            case PRESET_DRUM   -> "드럼";
            default -> preset;
        };
    }

    private static String defaultTitleMatch(String preset) {
        return switch (preset) {
            case PRESET_DRUM -> "(드럼),드럼"; // 다중: 괄호 표기 또는 단순 단어
            default -> "";
        };
    }

    /**
     * 옛 음 이름을 게임 GUI 표시명과 일치하도록 변환. 항목별 안전 UPDATE.
     * v3.10.5: "베이스-상/중/하" → "베이스 낮은음/중간음/높은음" 등.
     *   "상" = 위쪽 슬롯 = 낮은음, "중" = 중간음, "하" = 아래쪽 슬롯 = 높은음.
     * 키 매핑 / modifiers 는 보존됨 (renameNote 는 음 이름만 변경).
     */
    private static void migrateLegacyNoteNames(MusixDatabase db) {
        // v3.9.1/v3.10.0 "크래시-*" → "심벌-*" 1단계
        db.renameNote(PRESET_DRUM, "크래시-상", "심벌-상");
        db.renameNote(PRESET_DRUM, "크래시-중", "심벌-중");
        db.renameNote(PRESET_DRUM, "크래시-하", "심벌-하");
        // v3.10.5 2단계: "X-상/중/하" → "X 낮은음/중간음/높은음"
        String[] types = { "베이스", "스네어", "하이햇", "심벌" };
        String[][] suffixes = {
                { "-상", " 낮은음" },
                { "-중", " 중간음" },
                { "-하", " 높은음" },
        };
        for (String t : types) {
            for (String[] s : suffixes) {
                db.renameNote(PRESET_DRUM, t + s[0], t + s[1]);
            }
        }
    }

    private static void seedAllPresets(MusixDatabase db) {
        db.setSetting("containerPrefix", "음악,악기");
        db.setSetting("clickButton", "0");
        db.setSetting("clickAction", "PICKUP");
        for (Map.Entry<String, Object[][]> e : DEFAULT_PRESETS.entrySet()) {
            for (Object[] d : e.getValue()) {
                db.upsertMapping(e.getKey(), (int) d[1], (String) d[0], (String) d[2], 0);
            }
        }
        LOG.info("[Musix] DB 기본값 시드: 18 preset");
    }

    /**
     * v3.9.0: 음 단위로 체크 → 기존 사용자가 옛 버전 음 개수로 저장된 상태에서
     * 신규 추가된 음(예: 드럼 심벌 3음)만 골라서 추가. 기존 슬롯/키 매핑은 보존.
     */
    private static void ensurePresetExists(MusixDatabase db, String preset) {
        Object[][] table = DEFAULT_PRESETS.get(preset);
        if (table == null) return;
        Set<String> existingNotes = new HashSet<>();
        for (MusixDatabase.MappingRow r : db.getAllMappings(preset)) {
            existingNotes.add(r.note());
        }
        int added = 0;
        for (Object[] d : table) {
            String note = (String) d[0];
            if (existingNotes.contains(note)) continue;
            db.upsertMapping(preset, (int) d[1], note, (String) d[2], 0);
            added++;
        }
        if (added > 0) LOG.info("[Musix] preset '{}' 신규 {}음 추가 (총 {}음 default)", preset, added, table.length);
    }
}
