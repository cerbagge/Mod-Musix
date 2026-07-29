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
    /** v5.3.0: 음량 조절 키 preset. 상자 슬롯이 아니라 동작 ID 를 slot 자리에 담는다. */
    public static final String PRESET_VOLUME = "volume";

    // v5.3.0: volume preset 의 slot 값 = 동작 ID
    public static final int VOL_ACTION_UP   = 0;
    public static final int VOL_ACTION_DOWN = 1;
    /** slot 2~11 → 음량 1~10 직접 지정. */
    public static final int VOL_ACTION_SET_BASE = 2;
    /** 서버 기본 음량. 9 이상은 음량이 아니라 가청 거리가 늘어나므로 8 이 실질 최대. */
    public static final int VOLUME_DEFAULT = 8;
    public static final int VOLUME_MIN = 1;
    public static final int VOLUME_MAX = 10;

    /**
     * 자동 매핑/dump 에서 항상 제외하는 슬롯.
     * PlanetEarth 서버 GUI 의 네비게이션/장식 슬롯이라 음표로 잘못 매칭되면 안 됨.
     * v3.10.2: 슬롯 32 는 실제 스네어-하 매핑에 사용되므로 제거 ({6, 19, 45, 53} 만 차단).
     */
    public static final Set<Integer> BLOCKED_SLOTS = Set.of(6, 19, 45, 53);

    /**
     * v5.4.1: 상자 컨테이너의 최대 슬롯 인덱스 (6줄 = 54칸 → 0~53).
     * 이 위 인덱스는 플레이어 인벤토리 영역이라 절대 클릭 대상이 되면 안 된다.
     */
    public static final int MAX_CONTAINER_SLOT = 53;

    /**
     * v5.4.1: preset 별 허용 슬롯 상한.
     * volume preset 은 slot 자리가 동작 ID(0~11) 이고, 나머지는 상자 슬롯이다.
     */
    public static int maxSlotFor(String preset) {
        return PRESET_VOLUME.equals(preset) ? (VOL_ACTION_SET_BASE + 9) : MAX_CONTAINER_SLOT;
    }

    /** v5.4.1: 저장/불러오기 시 슬롯 값 검증. 범위 밖은 신뢰할 수 없는 데이터로 취급한다. */
    public static boolean isSlotInRange(String preset, int slot) {
        return slot >= 0 && slot <= maxSlotFor(preset);
    }

    /** v5.1.0: 이조 허용 범위 (±2옥타브). */
    public static final int TRANSPOSE_MIN = -24;
    public static final int TRANSPOSE_MAX = 24;

    public static int clampTranspose(int v) {
        return Math.max(TRANSPOSE_MIN, Math.min(TRANSPOSE_MAX, v));
    }

    /** 전체 preset 순서. v3.2.0: 단순화 — drum 과 그 외 (common). v5.3.0: volume 추가. */
    public static final List<String> ALL_PRESETS = new ArrayList<>();
    static {
        ALL_PRESETS.add(PRESET_COMMON);
        ALL_PRESETS.add(PRESET_DRUM);
        ALL_PRESETS.add(PRESET_VOLUME);
    }

    private static final Logger LOG = LoggerFactory.getLogger("musix/config");

    /** preset → 기본 음 매핑 (note, slot, defaultKey). 16 악기는 common 과 같은 49음 복사. */
    private static final Map<String, Object[][]> DEFAULT_PRESETS = new LinkedHashMap<>();

    static {
        // v4.0.4: common 49음 — 사용자 export (031223.json) 패턴
        // 흰건반 = 키 단독, 검은건반 = 같은 키 + Space (modifiers=65536=MOD_SPACE)
        // {note, slot, key, modifiers}
        Object[][] common49 = new Object[][] {
                // 옥타브 2 (6): F#2~B2 → ` 1 2 3 (Space 활용, slot 0~5)
                {"F#2",   0, "key.keyboard.grave.accent", 65536},
                {"G2",    1, "key.keyboard.1",            0},
                {"G#2",   2, "key.keyboard.1",            65536},
                {"A2",    3, "key.keyboard.2",            0},
                {"A#2",   4, "key.keyboard.2",            65536},
                {"B2",    5, "key.keyboard.3",            0},
                // 옥타브 3 (12): C3~B3 → Q W E R U I O (T 건너뜀, slot 7~18, slot 6 비어있음)
                {"C3",    7, "key.keyboard.q",            0},
                {"C#3",   8, "key.keyboard.q",            65536},
                {"D3",    9, "key.keyboard.w",            0},
                {"D#3",  10, "key.keyboard.w",            65536},
                {"E3",   11, "key.keyboard.e",            0},
                {"F3",   12, "key.keyboard.r",            0},
                {"F#3",  13, "key.keyboard.r",            65536},
                {"G3",   14, "key.keyboard.u",            0},
                {"G#3",  15, "key.keyboard.u",            65536},
                {"A3",   16, "key.keyboard.i",            0},
                {"A#3",  17, "key.keyboard.i",            65536},
                {"B3",   18, "key.keyboard.o",            0},
                // 옥타브 4 (12): C4~B4 → A S D F J K L (G/H 건너뜀, slot 20~31, slot 19 비어있음)
                {"C4",   20, "key.keyboard.a",            0},
                {"C#4",  21, "key.keyboard.a",            65536},
                {"D4",   22, "key.keyboard.s",            0},
                {"D#4",  23, "key.keyboard.s",            65536},
                {"E4",   24, "key.keyboard.d",            0},
                {"F4",   25, "key.keyboard.f",            0},
                {"F#4",  26, "key.keyboard.f",            65536},
                {"G4",   27, "key.keyboard.j",            0},
                {"G#4",  28, "key.keyboard.j",            65536},
                {"A4",   29, "key.keyboard.k",            0},
                {"A#4",  30, "key.keyboard.k",            65536},
                {"B4",   31, "key.keyboard.l",            0},
                // 옥타브 5 (12): C5~B5 → Z X C V M , . (N 건너뜀, slot 33~44, slot 32 비어있음)
                {"C5",   33, "key.keyboard.z",            0},
                {"C#5",  34, "key.keyboard.z",            65536},
                {"D5",   35, "key.keyboard.x",            0},
                {"D#5",  36, "key.keyboard.x",            65536},
                {"E5",   37, "key.keyboard.c",            0},
                {"F5",   38, "key.keyboard.v",            0},
                {"F#5",  39, "key.keyboard.v",            65536},
                {"G5",   40, "key.keyboard.m",            0},
                {"G#5",  41, "key.keyboard.m",            65536},
                {"A5",   42, "key.keyboard.comma",        0},
                {"A#5",  43, "key.keyboard.comma",        65536},
                {"B5",   44, "key.keyboard.period",       0},
                // 옥타브 6 (7): C6~F#6 → 7 8 9 0 (Space 활용, slot 46~52, slot 45 비어있음)
                {"C6",   46, "key.keyboard.7",            0},
                {"C#6",  47, "key.keyboard.7",            65536},
                {"D6",   48, "key.keyboard.8",            0},
                {"D#6",  49, "key.keyboard.8",            65536},
                {"E6",   50, "key.keyboard.9",            0},
                {"F6",   51, "key.keyboard.0",            0},
                {"F#6",  52, "key.keyboard.0",            65536},
        };
        DEFAULT_PRESETS.put(PRESET_COMMON, common49);

        // 드럼 12음 (v3.10.5): 게임 GUI 표시명과 일치 — 자동매핑 100% 매칭용.
        // 실제 PlanetEarth 드럼 GUI 구조 (디버그 확인):
        //   col 3 (slot 12/21/30) = 스네어 낮은음 / 중간음 / 높은음
        //   col 4 (slot 13/22/31) = 베이스 낮은음 / 중간음 / 높은음
        //   col 5 (slot 14/23/32) = 하이햇 낮은음 / 중간음 / 높은음
        //   row 5 (slot 48/49/50) = 심벌 낮은음 / 중간음 / 높은음
        // v4.0.4: drum 12음 — 사용자 export 키 매핑
        // 행별로 같은 키 그룹: row 1 = 1/2/3, row 2 = Q/W/E, row 3 = A/S/D, row 5 = Z/X/C
        // 열별로 종류: col 3 = 스네어, col 4 = 베이스, col 5 = 하이햇, row 5 = 심벌
        DEFAULT_PRESETS.put(PRESET_DRUM, new Object[][] {
                // Row 1 (낮은음): slot 12/13/14 → key 3/1/2
                {"스네어 낮은음", 12, "key.keyboard.3"},
                {"베이스 낮은음", 13, "key.keyboard.1"},
                {"하이햇 낮은음", 14, "key.keyboard.2"},
                // Row 2 (중간음): slot 21/22/23 → key E/Q/W
                {"스네어 중간음", 21, "key.keyboard.e"},
                {"베이스 중간음", 22, "key.keyboard.q"},
                {"하이햇 중간음", 23, "key.keyboard.w"},
                // Row 3 (높은음): slot 30/31/32 → key D/A/S
                {"스네어 높은음", 30, "key.keyboard.d"},
                {"베이스 높은음", 31, "key.keyboard.a"},
                {"하이햇 높은음", 32, "key.keyboard.s"},
                // Row 5 (심벌): slot 48/49/50 → key Z/X/C
                {"심벌 낮은음",   48, "key.keyboard.z"},
                {"심벌 중간음",   49, "key.keyboard.x"},
                {"심벌 높은음",   50, "key.keyboard.c"},
        });

        // v5.3.0: 음량 조절 (/instruments 1~10). slot 자리 = 동작 ID.
        // 방향키 위/아래는 1↔10 순환, Tab + 숫자는 절대값 지정 (Tab modifiers=131072=MOD_TAB).
        DEFAULT_PRESETS.put(PRESET_VOLUME, new Object[][] {
                {"음량 +",  VOL_ACTION_UP,   "key.keyboard.up",   0},
                {"음량 -",  VOL_ACTION_DOWN, "key.keyboard.down", 0},
                {"음량 1",  VOL_ACTION_SET_BASE,     "key.keyboard.1", 131072},
                {"음량 2",  VOL_ACTION_SET_BASE + 1, "key.keyboard.2", 131072},
                {"음량 3",  VOL_ACTION_SET_BASE + 2, "key.keyboard.3", 131072},
                {"음량 4",  VOL_ACTION_SET_BASE + 3, "key.keyboard.4", 131072},
                {"음량 5",  VOL_ACTION_SET_BASE + 4, "key.keyboard.5", 131072},
                {"음량 6",  VOL_ACTION_SET_BASE + 5, "key.keyboard.6", 131072},
                {"음량 7",  VOL_ACTION_SET_BASE + 6, "key.keyboard.7", 131072},
                {"음량 8",  VOL_ACTION_SET_BASE + 7, "key.keyboard.8", 131072},
                {"음량 9",  VOL_ACTION_SET_BASE + 8, "key.keyboard.9", 131072},
                {"음량 10", VOL_ACTION_SET_BASE + 9, "key.keyboard.0", 131072},
        });
    }

    public String containerPrefix;
    public int clickButton;
    public String clickAction;
    public boolean debugMode;
    /** v3.8.0: 악기 상자가 열릴 때 슬롯 아이템 이름으로 자동 매핑할지 여부. */
    public boolean autoMapOnOpen;
    /** v4.0.0: MIDI 입력 활성화 여부. */
    public boolean midiEnabled;
    /** v4.0.0: 연결할 MIDI 장치 이름 (재시작 시 자동 재연결). */
    public String midiDeviceName;
    /** v5.1.0: 이조(반음 단위). 입력 MIDI 노트에 더해 슬롯 매칭. 범위 -24~+24 (±2옥타브). */
    public int transposeSemitones;
    /** v5.2.0: 이조 후 F#2~F#6 범위를 벗어난 음을 옥타브 단위로 접어 재생 (기본 ON). */
    public boolean octaveFold = true;
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
        config.containerPrefix = db.getSetting("containerPrefix", "악기");
        config.clickButton     = parseInt(db.getSetting("clickButton", "0"), 0);
        config.clickAction     = db.getSetting("clickAction", "PICKUP");
        config.debugMode       = "true".equalsIgnoreCase(db.getSetting("debugMode", "false"));
        config.autoMapOnOpen   = "true".equalsIgnoreCase(db.getSetting("autoMapOnOpen", "true"));
        config.midiEnabled     = "true".equalsIgnoreCase(db.getSetting("midiEnabled", "false"));
        config.midiDeviceName  = db.getSetting("midiDeviceName", "");
        config.transposeSemitones = clampTranspose(parseInt(db.getSetting("transposeSemitones", "0"), 0));
        config.octaveFold      = !"false".equalsIgnoreCase(db.getSetting("octaveFold", "true"));

        for (String preset : ALL_PRESETS) {
            List<KeyMapping> list = new ArrayList<>();
            for (MusixDatabase.MappingRow r : db.getAllMappings(preset)) {
                list.add(new KeyMapping(preset, r.note(), r.slot(), r.keyName(), r.modifiers(),
                        r.key2Name(), r.modifiers2()));
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

    /**
     * 상자 제목 매칭. v3.12.0: PlanetEarth 전용 모드라 "음악"/"악기" 는 hardcoded 항상 매칭.
     * 사용자가 prefix 를 잘못 설정해도 (예: "음악" 만) 드럼 상자 "악기 - X(드럼)" 도 잡힘.
     */
    public boolean titleMatchesPrefix(String title) {
        if (title == null) return false;
        // 1) PlanetEarth 기본 prefix — 사용자 설정과 무관하게 항상 매칭
        if (title.startsWith("음악") || title.startsWith("악기")) return true;
        // 2) 사용자 설정 prefix (커스텀 서버나 추가 케이스 대비)
        if (containerPrefix == null || containerPrefix.isEmpty()) return false;
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
    public void setMidiEnabled(boolean enabled) {
        this.midiEnabled = enabled;
        MusixDatabase.get().setSetting("midiEnabled", Boolean.toString(enabled));
    }
    public void setMidiDeviceName(String name) {
        this.midiDeviceName = name == null ? "" : name;
        MusixDatabase.get().setSetting("midiDeviceName", this.midiDeviceName);
    }
    public void setTransposeSemitones(int semis) {
        this.transposeSemitones = clampTranspose(semis);
        MusixDatabase.get().setSetting("transposeSemitones", Integer.toString(this.transposeSemitones));
    }
    public void setOctaveFold(boolean enabled) {
        this.octaveFold = enabled;
        MusixDatabase.get().setSetting("octaveFold", Boolean.toString(enabled));
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

    /** v3.11.0: DEFAULT_PRESETS 의 4번째 필드(modifiers). 없으면 0. */
    public static int lookupDefaultModifierForSlot(String preset, int slot) {
        Object[][] table = DEFAULT_PRESETS.get(preset);
        if (table == null) return 0;
        for (Object[] d : table) {
            if ((int) d[1] == slot) return d.length > 3 ? (int) d[3] : 0;
        }
        return 0;
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
            case PRESET_VOLUME -> "통합 설정";
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
        // v4.0.0: "악기" 만으로 통일 (hardcoded fallback 이 "음악" 도 매칭하므로 호환됨)
        db.setSetting("containerPrefix", "악기");
        db.setSetting("clickButton", "0");
        db.setSetting("clickAction", "PICKUP");
        for (Map.Entry<String, Object[][]> e : DEFAULT_PRESETS.entrySet()) {
            for (Object[] d : e.getValue()) {
                int mods = d.length > 3 ? (int) d[3] : 0;
                db.upsertMapping(e.getKey(), (int) d[1], (String) d[0], (String) d[2], mods);
            }
        }
        LOG.info("[Musix] DB 기본값 시드: {} preset", DEFAULT_PRESETS.size());
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
            int mods = d.length > 3 ? (int) d[3] : 0;
            db.upsertMapping(preset, (int) d[1], note, (String) d[2], mods);
            added++;
        }
        if (added > 0) LOG.info("[Musix] preset '{}' 신규 {}음 추가 (총 {}음 default)", preset, added, table.length);
    }
}
