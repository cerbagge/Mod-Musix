# Musix

[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)
![Fabric](https://img.shields.io/badge/Loader-Fabric-orange)
![Client](https://img.shields.io/badge/Side-Client%20only-blue)

**🌐 언어**: [English](README.md) · **한국어** (이 페이지)

> ⚠ **서버 전용 모드** — **PlanetEarth Server (`planetearth.kr`)** 에서만 작동합니다. 다른 서버에서는 아무 기능도 작동하지 않습니다.

PlanetEarth 서버의 상자 GUI 기반 악기(`음악-하프`, `악기 - 베이스(드럼)` 등)를 키보드로 연주할 수 있게 해주는 클라이언트용 Fabric 모드. 키를 누르면 모드가 해당 슬롯을 클릭하고, 서버 플러그인이 음을 재생합니다.

## 주요 기능

- **키 입력 → 슬롯 클릭** (상자 제목이 `음악` 또는 `악기`로 시작하면 자동 작동)
- **2개 preset 자동 선택**
  - `공통` — 49음 (F#2 ~ F#6) — 일반 멜로디 악기용
  - `드럼` — 9음 (베이스 / 하이햇 / 스네어 상중하) — `(드럼)` 상자용
- **자동 매핑** — 상자 한 번 열고 메뉴에서 "자동 매핑" 클릭 → 슬롯 위치 자동 정렬
- **인게임 키 재설정** — 좌클릭 변경, 우클릭 미설정, ESC 기본값. Shift / Alt 조합 지원
- **충돌 검사** + 빨간 강조 (중복 키 거부)
- **음별 사용 횟수** (× N) 매핑 표 우측에 표시
- **preset 탭** (공통 / 드럼)
- **H2 영속 저장** (shade — Lunar Client / Ichor 호환)
- **클릭 옵션** — PICKUP / QUICK_MOVE / SWAP / ... + 좌/우/중클릭
- **한국어 + 영어** 지원

## 사용 방법

1. mods 폴더에 jar 설치 (Fabric 1.20.1 + Fabric API 필수)
2. **PlanetEarth 서버** 접속 (`planetearth.kr`)
3. 음악 상자(예: `음악-하프`) 열기 → 모드가 슬롯 자동 캐시
4. **M** → 메뉴 → **자동 매핑** 클릭 → 슬롯 정렬
5. 다시 상자 열고 매핑된 키 누르면 음 재생
6. 다른 악기로? 자동 매핑 다시

## 기본 키 배치 (공통 preset, 49음)

| 줄 | 키 | 음 |
|---|---|---|
| 1 줄 | `1 2 3 4 5 6 7 8 9 0 - =` | F#2 → F3 |
| Q 줄 | `Q W E R T Y U I O P [ ]` | F#3 → F4 |
| A 줄 | `A S D F G H J K L ; '` | F#4 → E5 |
| Z 줄 | `Z X C V B N M , . /` | F5 → D6 |
| 미설정 | — | D#6, E6, F6, F#6 (메뉴에서 직접 할당) |

## 드럼 preset (제목에 `(드럼)` 포함 시 자동 활성)

- `1 2 3` → 베이스 상 / 중 / 하 (슬롯 11 / 13 / 15)
- `Q W E` → 하이햇 (슬롯 20 / 22 / 24)
- `A S D` → 스네어 (슬롯 29 / 31 / 33)

## 빌드

```sh
./gradlew build
```

산출물: `build/libs/musix-<version>.jar`

GitHub Actions가 push마다 자동 빌드하고, `v*` 태그 push 시 Release 자동 생성합니다.

## 호환

- Minecraft **1.20.1**, Fabric Loader 0.15+, Fabric API
- **Lunar Client** (Ichor) 검증 완료 — H2 DB shade 처리로 ClassLoader 격리 회피
- 라이센스: **CC-BY-NC-SA-4.0**

## 라이센스

이 작업물은 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) 라이센스를 따릅니다.
공유 및 수정 가능, 단 출처 표시 + 비영리 + 동일 조건 공유 의무.
