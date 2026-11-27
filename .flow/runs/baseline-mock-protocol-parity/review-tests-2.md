# Review tests, round 2

LENS: tests  ROUND: 2  BASE: 08f50b81

### F1 [major] The AC-5/AC-7 evidence file is still missing (triage #3 is not done)
- Where: docs/superpowers/evidence/ (no 2026-11-26-baseline-mock-protocol-parity-device.md at 8bda22ae)
- Fix: qa-device writes and audits it from run 4.

### F2 [minor] browseScenario_requiresItemFoldersRoute is not traced in the spec's Test design
- Fix: add a row (AC-4/AC-6, self-test, P1 red by construction).

### F3 [minor] The Browse rest is a fixed sleep tied to a production constant (NEIGHBOUR_DWELL_WAIT_MS 1_500L vs DetailsPrefetcher.neighbourDwell 750 ms); fails loudly if the gap closes.
- Fix: cross-reference in the DetailsPrefetcher KDoc or a constant comment; keep >= 2x margin.

### F4 [nit] itemFixtures_carryWatchedAndBookmarkedMovie name no longer matches.

Checked and passing: BaselineProfileContentTest run and green; profile.* tests UP-TO-DATE green; self-tests device-only (26/0 in run 4); deliberate item.json assertion reversals follow the amended AC-4 with checks moved to items.json and the series; folder guard matches the committed profile (1 getItemBookmarkFolders line, 21 BookmarkFoldersResponse) and is correctly a guard; red runs recorded; chunkedBodies() inside the lock; Date self-test covers media and DoH; empty_bookmark_folders no longer referenced.

### Unproven
- bookmarkFoldersFixture test checks only folders.isNotEmpty().
