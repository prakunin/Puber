# Gate - catalogue-filters-country-year-status - round 2

**Decision:** PASS
**Head:** f28a8437   **Base:** 90e69aff

## Done as asked

Unchanged from [judge-1.md#done-as-asked](judge-1.md). Since e286d189 the diff touches only the device evidence file (`git diff --stat e286d189 HEAD`). Re-read `TvSidePanel.kt`: `fixedWidth == null` keeps `widthIn(min, max).width(IntrinsicSize.Max)` in the old order.

## Traceability

Rows unchanged from judge-1 are not repeated: AC-1, AC-2, AC-6, AC-9 stay PASS. Device halves below come from evidence Round 4 and the screenshots in `/tmp/claude-1000/-home-dev-Puber/5257593d-23a9-490e-b468-6f40de2c5fe8/scratchpad/` (read here).

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-3 | unit and itest (judge-1); M3 with Великобритания · 2010-е · Завершённые | `w-25-filtered-grid.png`: header summary in order, 12 cards 2011-2019, British titles (Игра престолов, Хинтерланд, Невиновный, Супермаркет); Буркина-Фасо gives the filtered-empty state (`w-23`). Country not shown on cards | PASS |
| AC-4 | unit, itest; Год page | `w-14-year.png`: Любой год, 2026, 2025, 2020-е ... 1980-е, до 1980; door reads «2010-е» after Back | PASS |
| AC-5 | unit, itest; M1 root, M3 root, M6, Статус page | `w-7-root4` 4 doors, `w-13-root5` 5 doors, `w-37-4k-root` 4 doors, `w-18-status-page` two rows | PASS |
| AC-7 | unit; M4 restart | evidence Round 4: summary on the first frame, focus on the first card (`w-30-restored`) | PASS |
| AC-8 | unit; M4 Сброс | evidence Round 4, `w-31-reset` | PASS |
| AC-10 | `ShowAllFilterFocusTest` 28/28 on the box (Round 2) incl. `countryPage_downReachesTheLastCountry`; real 102-row list scrolled (`w-8`, `w-9`) | last-row Down on the real list not walked; covered by the itest | PASS |
| AC-11 | screenshots of root (4, 5 doors), Страна, Год, Статус, summary, empty state | captured; owner judges against `docs/design/showall/README.md` | NOT CHECKED (owner) |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| M1-M6 and AC-11 not run on the device | review-tests-1 F1 (major), triage-1 #1, triage-2 #4 | fixed, except M5 player panels and the AC-11 look | evidence Round 4, f28a8437 |
| all other review and triage rows | review-*-1/2, triage-1/2 | as in [judge-1.md#findings](judge-1.md); spot-checks of the two codex rejections stand | e286d189 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | No change since judge-1: lazy country list, 30-min payload cache, options computed once |
| Reliability | As judge-1; restart and reset now seen on the box. Open, unproven: a 200 `countries` body without `items` caches an empty list with no Retry |
| Maintainability | As judge-1; no dead code |
| Security / privacy | `tools/mobile-evidence-audit.sh` on `docs/superpowers/evidence`: passed (362 files). Screenshots stay outside the repo |

check.full re-run here at f28a8437 (qa-report 14:24:36 older than the commit 14:24:59): `BUILD SUCCESSFUL`, exit 0.

## Manual checks for the owner

| Item | Steps | Expected |
|------|-------|----------|
| AC-11 | open `w-7-root4`, `w-13-root5`, `w-8-country-top`, `w-9-country-far`, `w-14-year`, `w-18-status-page`, `w-23-filtered-grid`, `w-25-filtered-grid`, `w-37-4k-root` (.png, scratchpad above) next to `docs/design/showall/README.md` «catalogue-filters-country-year-status starting values» | sizes and materials match the stand |
| M5 player panels (R10) | on the box, start playback of any title, open player settings; open a series' Episodes panel; same on a master build | widths identical |

## Must fix

none

## Concerns for the owner

none
