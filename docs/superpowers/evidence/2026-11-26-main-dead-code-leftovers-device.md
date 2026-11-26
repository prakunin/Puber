# main-dead-code-leftovers - device smoke (AC-4)

Build: dev debug APK of this worktree, 1.12.7-dev (2090000000), sha256 7d2bf6efb287a0bcb7a1fbb777b8093010b039d58f9dca4b5b68256f3c2178d0 (round 2, after the PagingListState removal; on-device base.apk sha256 matches), installed with tools/android-apk-install-preserve (outcome installed, compatible_replace, no destructive action). Device: owner's TV box 192.168.1.110:5555. auth-save taken before install. Package com.kino.puber.stage only.

Account is banned, so content rows show an error; the check is the Main shell.

| Step | Expected | Observed | Result |
|---|---|---|---|
| force-stop, cold launch MainActivity, screenshots at about 3, 7, 11 and 15 s, all before any HOME | Main drawn | First frame (5-cold-t0, about 3 s) black while splash/preload ran; the last frame (5-cold-t3, about 15 s, still before HOME) shows Main: rail with all tabs and Home error state (request-failed message, retry and mirror buttons). Intermediate frames t1, t2 are kept next to it | PASS |
| HOME, relaunch once (task brought to front) | Main shown after resume | Resumed activity is MainActivity, rail + Home error state in 5-relaunch (round 2); round 1 screenshot 2-resume agrees | PASS |
| D-pad: left to rail, down x2, OK | rail tab opens | Bookmarks (heart) tab selected, content loading spinner (API unavailable), screenshot 4-rail-tab | PASS |
| Crash check (round 2) | no crash in app process | process alive (pid 1897), crash buffer has 0 entries for the package | PASS |

Note: an earlier DOWN+OK from Home content opened the KinoPub mirror dialog (screenshot 3-tab), a normal Home error-state control; closed with BACK. No API calls were scripted.

Round 2: only the cold-launch and relaunch rows were redone on the new APK (screenshots 5-cold-t0..t3, 5-relaunch); the D-pad rail-tab row is round 1 on the earlier build, and the change since is a file deletion of unused code.
