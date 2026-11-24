# Device evidence: cached-item-card-only-fallback

Date: 2026-11-24. Rounds 1, 2 and 3. Round 1 at HEAD 6111240e; round 2 at HEAD 9ac3dba7 (its log quote is superseded by round 3), base 4db1b061.

## Build and device

| Item | Value |
|---|---|
| Device | owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only |
| APK | worktree own app-dev-debug.apk, sha256 0d5ea942265349e244049c8e9676aea0f880ddebc2361f28b79df90e34c721c3 |
| Install | tools/android-apk-install-preserve: outcome installed, classification compatible_replace, destructive_action false |
| On-device check | sha256 of the installed base.apk equals the built APK |
| Safety | auth-save taken before install; lease held and released; db copies deleted after the queries |

## Titles tried

The box already held six item rows over 1 MiB with episodes, written by earlier builds (before the row cap). None of them is a 4K title (qualities 1080 and 480), so the 4K «Все» grid could not be the source of the opened title. Ids and sizes before the run: 17262 (1533351 B), 74866 (1302588), 15218 (1150034), 9365 (1134468), 12855 (1091191), 50344 (1079168). Title used: id 12855 (Gold Rush, 17 seasons), opened from search because it is not in the 4K grid. Game of Thrones (id 8740, 315774 B, 8 seasons) was opened first and is under the limit, so it does not exercise the fallback.

## AC-7

Steps: logcat -c; open Gold Rush details from search (online, episodes loaded, screenshot seen); BACK; force-stop; copy puber.db, -wal, -shm through run-as to a scratch path outside Git; query cached_payload.

| Check | Expected | Observed | Result |
|---|---|---|---|
| row content:v2:item:12855 exists | yes | yes | PASS |
| length(CAST(payload AS BLOB)) | <= 1048576 (before: 1091191) | 3995 | PASS |
| "episodes" in payload (instr) | 0 | 0 | PASS |
| "detailsUpdatedAt" in payload (instr) | absent or null | 0 (absent) | PASS |
| logcat Refused cached payload lines (whole session since logcat -c) | none for the id | 0 lines in total | PASS |
| crash (FATAL EXCEPTION) | none | 0 | PASS |

Second observation: after the same session id 50344 (1079168 B before) also held a card-only row of 3114 B without "episodes" or "detailsUpdatedAt". Rows over 1 MiB went from 6 to 4 by the end of the run (the remaining ones were not opened).

Limit of the check: the title is not from the 4K «Все» grid, and the 4K grid was browsed in a separate pass (below). No Timber line for a refusal can be shown as present by this run; the absence of the line is only meaningful together with the row result above.

## AC-8

Cold start after force-stop, online: Rail, 4K, «Всё в 4K». The grid showed cards with posters (screenshot seen, no error state). Then search, Gold Rush, DPAD_CENTER on the card: the details screen loaded with seasons 1 to 17 and the episode row (screenshot seen). After this pass the row of id 12855 was 3995 B, still no "episodes", no "detailsUpdatedAt" (details refetched and refused, fallback rewritten), no Refused line, no FATAL EXCEPTION.

| Check | Result |
|---|---|
| grid shows cards after cold start | PASS |
| details of the oversized series open with seasons and episodes | PASS (via search, not from the 4K grid) |

## Incident

On the first cold start key presses meant for the rail landed on the Home hero: a movie details page opened and then a resume prompt for playback appeared. It was left with BACK without choosing an option; no playback was confirmed.

## Round 2 (HEAD 9ac3dba7): positive control for the logcat check (triage T4)

Build: worktree app-dev-debug.apk, sha256 0bb46cbf3d8cf43ae132f6269528fc9b9501f9eecbb0f1e645f491ebb6d024ec; install-preserve outcome installed, compatible_replace, destructive_action false; sha256 of the installed base.apk equal. auth-save taken first; lease released; db copies deleted.

Title: id 9365 (Deadliest Catch, 22 seasons), one of the four item rows that were still over 1 MiB (before: 1134468 B, with "episodes" and "detailsUpdatedAt"). Opened from search; the first details load was online and showed seasons 1 to 22 with episodes (screenshot seen). Id 22462 (1015225 B) is under the limit and was not touched.

| Check | Expected | Observed | Result |
|---|---|---|---|
| row content:v2:item:9365 after force-stop | card-only, <= 1048576 B | 3075 B | PASS |
| "episodes" in payload | 0 | 0 | PASS |
| "detailsUpdatedAt" in payload | absent | 0 (absent) | PASS |
| logcat (whole session since logcat -c, filtered by the message text) | warn line for the key, no payload | "Refused cached payload content:v2:item:9365: 1127606 chars, fallback persisted (2613 chars)": sizes only, no payload | PASS (positive control) |
| FATAL EXCEPTION | 0 | 0 | PASS |
| cold start, Deadliest Catch details from search | seasons and episodes shown | seasons 1 to 15+ tabs and the episode row shown (screenshot seen) | PASS |
| cold start, Serials section | cards shown | cards with posters, no error state | PASS |
| remaining rows over 1 MiB | 17262, 74866, 15218 | unchanged (not opened); 22462 is under the limit | info |

The warn line appears once per refused write; it was seen twice after the cold-start open (a refetch refused again, row rewritten at 3075 B). Rows over 1 MiB on the box: 6 before round 1, 3 now.

Status of the manual row: the AC-7 device run is a guard on base, not a red-to-green proof. On base the refused item was dropped and the title had no card-only row; the red runs of this behaviour are the unit rows AC-2 to AC-4 (ContentCacheRepositoryTest), which fail on base. The log line is new in this change, so its presence is the positive control of the logcat check; the earlier "no Refused line" result of round 1 is superseded by it.

Incident (round 2): twice a DPAD_CENTER pressed on cold start landed on a Home card; details of a series opened and, once, a resume prompt ("Continue from 9:59?"). Left with BACK without choosing; no playback was started or confirmed.

## Round 3 (HEAD b8b5e344): reworded fallback log line (triage-2 U1)

Round 2 observed the fallback line as "Refused cached payload ... fallback persisted (...)". That quote is superseded: the prefix "Refused cached payload" is now reserved for "nothing persisted", and the fallback path logs its own message. The round 2 row results are unaffected.

Build: worktree app-dev-debug.apk, sha256 767f81ef2db9ac3d2765c40b27eda4ef9148da31f0484da6f0be943ece236283; install-preserve outcome installed, compatible_replace, destructive_action false; sha256 of the installed base.apk equal. auth-save taken first; lease released; db copies and screenshots deleted. Unit: testDevDebugUnitTest --tests '*CachedFeed*' green (CachedFeedTest 67, CachedFeedMemoryWeightTest 2, 0 failures, 0 skipped).

Title: id 15218 (PAW Patrol, 13 seasons), one of the three rows still over 1 MiB (before: 1150034 B, with "episodes" and "detailsUpdatedAt"). Opened from search; the first load showed seasons 1 to 13 and 26 episodes in season 1 (screenshot seen).

| Check | Expected | Observed | Result |
|---|---|---|---|
| row content:v2:item:15218 after force-stop | card-only, <= 1048576 B | 3616 B | PASS |
| "episodes" in payload | 0 | 0 | PASS |
| "detailsUpdatedAt" in payload | absent | 0 (absent) | PASS |
| logcat (since logcat -c, filtered by message text) | new fallback line for the key | "Cached payload content:v2:item:15218 over the row limit (1141033 chars); stored the fallback (2815 chars)": sizes only, no payload | PASS |
| "Refused cached payload" lines | 0 for the key | 0 | PASS |
| FATAL EXCEPTION | 0 | 0 | PASS |
| cold start (force-stop, launch), details of the same title from search | seasons and episodes shown | seasons 1 to 13 and episode row shown over the card-only row (screenshot seen); row stays 3616 B, same fallback line again after the refetch | PASS |
| remaining rows over 1 MiB | 17262, 74866 | unchanged (not opened); 22462 (1015225 B) is under the limit | info |

Incident (round 3): one key sequence on cold start landed on a Home hero (series details "Outlander" opened); left with BACK, nothing started or confirmed.
