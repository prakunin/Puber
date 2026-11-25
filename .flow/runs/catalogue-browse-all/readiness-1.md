# Readiness 1: catalogue-browse-all

Verdict: **READY**

Spec `docs/superpowers/specs/2026-11-24-catalogue-browse-all.md` (Test design identical to `.flow/runs/catalogue-browse-all/test-design.md` apart from heading levels), plan `docs/superpowers/plans/2026-11-24-catalogue-browse-all.md` at 437b7b95, code at base e4d75215.

## AC coverage

| AC | Plan task | Test design rows |
|---|---|---|
| AC-1 | 1, 4 | TTC label and null rows (P0 red), M-1 |
| AC-2 | 4 | SRFT Up/Down and Loading gate (P1 red), M-1, M-2 |
| AC-3 | 1, 2 | CLVM `browseAll_navigatesToShowAllWithTheTabsAllConfigAndTitle`, TTC `browseEntryFor_returnsTheAllSectionOfEachCatalogueTab` (P0 red) |
| AC-4 | 2 | CLVM `browseAllAndAllShelfShowAll_openTheSameScreenKeyAndTitle` (P0 red) |
| AC-5 | 5 | SAVM guards (P1), M-4 |
| AC-6 | 1, 3 | TTC title rows, SACF header row (P0 red), M-4, M-5 |
| AC-7 | 4 | M-3 only (device) |
| AC-8 | 3 | SACF and SARC guards (P0 guard), qa-device `itest` |
| AC-9 | 1, 5 | TTC (P0 red), CLIT guard (P1) |
| AC-10 | 1, 4, 5 | SRFT and TTC whole class (P0 guard), SRFT flush row (P1 red) |
| AC-11 | 5 (evidence) | M-6 |

## Citation sample (all resolve at e4d75215)

| Citation | Found |
|---|---|
| `ContentListScreenContent.kt:71`, `:103`, `:338` | `focusedSectionIndex` saveable; `onSectionFocused = { focusedSectionIndex = it }`; `isTargetRow = index == focusedSectionIndex` |
| `ContentListScreenContent.kt:455`, `:437-438` | `LazyItemScope.SectionListItem(...)`; `sectionListItemIndex` |
| `ShowAllScreenContent.kt:48`, `:132`, `:193-199` | `GridContentPadding`; grid `contentPadding`; handoff latch |
| `FocusTargetState.kt:298-317`, `:319-324` | `RestoreItemFocusOnTabReentryEffect`; `requestAfterAnchorRestore` |
| `FlowComponent.kt:356`, `:447` | `LocalRootAnchorFocusRestored`; `PreserveLazyListAnchorOnRootReturn` |
| `ContentListVM.kt:138-145`, `ShowAllScreen.kt:28-33` | `openShowAll` (sole `ShowAllScreen(` caller); constructor and key |
| `TabTypeConfig.kt:77-83`, `:120-125`, `:185-190` | `all_$type` / `all_4k` sections, `sort = "updated-"` |
| `Makefile:23`, `values-en/strings.xml:180`, `main_screen_strings.xml:35` | `GRADLE ?= ./gradlew`; `content_section_all` twice |
| SRFT `:424`, `:460`, `:496`, `:670`; SAVM `:177`, `:234`; CLIT `:262`; CLVM `:481`, `:584-615` | named functions and helpers exist |

## Build and order

Tasks 1 to 5 follow their `Consumes` lines. Every signature change updates all its callers in the same task, checked by `git grep`: `ShowAllScreen(` and `ContentListVM(` in Task 2, `ShowAllScreenContent(` (seven androidTest sites) in Task 3, `ContentListScreenContent(` and `SectionListItem(` (default `header`) in Task 4.

## Scope

Plan stays inside Out of scope: `ContentListPagingVM.config` stays `private val`, `ContentFilter.kt` and baseline profile untouched. Brief Q1-Q3 and architect Q1-Q2 answered (spec Decisions).

## Notes for the gate (not blocking)

| # | Note |
|---|---|
| 1 | AC-7 (R1, score 6) has no automated test; M-3 is its only evidence. Without a device record AC-7 is `NOT VERIFIED` |
| 2 | Device rows marked P0 carry no `red`/`guard` mark; they are manual by nature and are judged on the evidence file |
| 3 | `baseline-prof.txt:33785-33786` names the old `ContentListScreenContent` and `SectionListItem` signatures; stale until regeneration (architect Risks) |
