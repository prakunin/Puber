# Baseline evidence: grid focus restore after Back (base e9184b79)

Slug: grid-focus-restore-after-back. Spec: `docs/superpowers/specs/2026-11-11-grid-focus-restore-after-back.md` (AC-1, AC-2, AC-6, AC-8; Test design Manual 1-3).

## Build and device

| Item | Value |
|---|---|
| Worktree HEAD | 511590c1 (base e9184b79 plus the spec doc; production code identical to base) |
| APK | `app/build/outputs/apk/dev/debug/app-dev-debug.apk`, sha256 `af8c09454dc4912851eb0351d6cf573441d2c27c33d949f0c890c3456ac63cde`, 1.10.0-dev |
| Install | `tools/android-apk-install-preserve`, outcome `installed` (`compatible_replace`, data preserved) |
| Device | `emulator-5554` (AVD `puber_tv_36`, Android 16), leased and released |
| Method | D-pad keyevents (DOWN 20, RIGHT 22, LEFT 21, OK 23, BACK 4), 1 s between keys, a screenshot after each step; "same card" = the two screenshots are pixel-identical outside the focus ring animation |

Preparation: the Bookmarks and Collections tabs are hidden by default (`TabType.enabled = false`). They were switched on in Settings > Navigation for the run and switched off again afterwards; the rail is back to its original sections. No bookmark, watched state or account data was changed. A "new version" dialog on first launch was dismissed with BACK.

## Results on base

Card in a screenshot is named by its title. Layout is 3 columns, so "second row, RIGHT once" is row 2 column 2. For the rail checks the card is in column 1 (LEFT from column 2 only moves to column 1; the rail opens from column 1).

| AC | Screen | Sequence | Focused before | Focused after | Repeats | Result on base |
|---|---|---|---|---|---|---|
| AC-1 | Collections | DOWN x3, RIGHT, OK (CollectionDetail), BACK | "Основанное на реальных событиях" | same card, same viewport | 3 (plus 1 deeper: DOWN x6 more, "Клуб Веселых человечков") | Restored. Symptom not reproduced |
| AC-6 | Collections | card in column 1 ("Вселенная Warhammer"), LEFT (rail opens), RIGHT | same card | same card | 3 | Same card, no row shift |
| AC-2 (from Collections) | CollectionDetail "Вселенная Warhammer" | DOWN x2, RIGHT, OK (Details "Дознаватель"), BACK | "Дознаватель / Interrogator" | no card focused; first DOWN lands on the first card "Ультрамарины" and scrolls the grid to the top | 3 | Symptom reproduced |
| AC-2 (from Home) | CollectionDetail "Формула-1", opened from Home Collections row | DOWN x2, RIGHT, OK (Details "Сенна"), BACK | "Сенна / Senna" | no card focused (no focus ring; grid scroll kept) | 3 | Symptom reproduced |
| AC-8 | Bookmarks | DOWN, RIGHT, OK (Details), BACK | "Самый громкий голос / The Loudest Voice" (row 2, column 2) | same card | 3 (plus 1 on "Чужестранка / Outlander", column 1, row 3) | Restored, not affected |
| AC-8 | Bookmarks | card in column 1 ("Чужестранка / Outlander"), LEFT (rail opens), RIGHT | same card | same card | 3 | Same card |
| AC-8 | History | DOWN, RIGHT, OK (Details), BACK | "Season 2 · Episode 4" (row 2, column 2) | same card, viewport unchanged | 3 | Restored, not affected |
| AC-8 | History | card in column 1 ("Season 2 · Episode 5"), LEFT (rail opens), RIGHT | same card | same card | 3 | Same card |

Extra: BACK from CollectionDetail opened from Home returned focus to the opener card "Формула-1" in the Home Collections row (once, AC-3 base behaviour).

## Reading

- The CollectionDetail symptom (no focus memory) is real on base, from both origins (3 of 3 each). The Collections tab itself restores the opener card on base, for all 4 runs; its rail close is stable.
- Bookmarks and History are not affected on base. This confirms the code reading (spec Root cause 4) and does not reopen scope (state.md Q1).
- Show all and Home restore are outside this baseline.

## Screenshots

Kept in the session scratchpad, not committed (they show account titles: continue-watching and bookmarks). The pixel comparisons above come from them.
