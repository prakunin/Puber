Round 2

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin -> pass (check.full from round 1 stands; round 2 changed only a deleted unused file, 2e3ef77c)
DEVICE: 192.168.1.110:5555 (auth-save taken first; install-preserve outcome installed, compatible_replace; on-device base.apk sha256 equals the built APK; lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | round 1 rg check; PagingListState.kt now also deleted |
| AC-2 | PASS | round 1, unchanged |
| AC-3 | PASS | round 1 check.full green; round 2 compile green |
| AC-4 | PASS | cold launch after force-stop: frame at about 3 s black, frame at about 15 s (before any HOME) shows Main with rail and Home error state; HOME + one relaunch resumed MainActivity with Main drawn; process alive, 0 crash entries |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/main-dead-code-leftovers/docs/superpowers/evidence/2026-11-26-main-dead-code-leftovers-device.md (updated, audit passed, committed 2f78b8af; new screenshots 5-cold-t0..t3, 5-relaunch)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none

---
Round 1 (kept)

Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; most tasks UP-TO-DATE from an earlier identical-source run, lintDevDebug executed)
DEVICE: 192.168.1.110:5555 (owner's TV box; auth-save taken first; lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | rg for PagingColumn|fun onSearchClick|fun onSettingsClick over the listed paths: no match (rc=1) |
| AC-2 | PASS | MainScreenComponent.kt:77 is remember(vm) { vm::onAction } |
| AC-3 | PASS | check.full green, no new detekt/lint finding |
| AC-4 | PASS | Dev APK installed (installed, compatible_replace), Main shell opened, HOME then relaunch resumed MainActivity, Bookmarks rail tab opened via D-pad (spinner, API banned), process alive, 0 crash entries; screenshots in evidence |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/main-dead-code-leftovers/docs/superpowers/evidence/2026-11-26-main-dead-code-leftovers-device.md (audit passed, committed)
DEFECTS: none
UNAVAILABLE: none (content rows show request-failed because of the banned account; Main shell itself was reachable)
MANUAL: none
