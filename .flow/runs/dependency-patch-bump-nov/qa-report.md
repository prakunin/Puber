Round 1

CHECK: check.full from .flow/config.yaml -> pass (re-run at 876912c9, exit 0); :app:assembleProdRelease -> pass; :app:assembleDevDebug -> pass
DEVICE: 192.168.1.110:5555 (owner's box, model KM6); lease acquired (waited behind player-dpad-seek-coalesce), released, status unlocked
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | git diff b1b3c51a -- gradle/libs.versions.toml: only agp 9.5.3, ksp 2.3.14, lifecycleRuntimeKtx 2.11.1, ktor 3.6.2; composeBom 2026.09.00 |
| AC-2 | PASS | properties diff is only distributionUrl -> gradle-9.9.3-bin.zip; jar sha256 621008af...daf8 equals the published 9.9.3 wrapper sha256; git diff --stat shows gradlew and gradlew.bat untouched |
| AC-3 | PASS | 2 deprecation warnings at base, same 2 after (setVisible, Project-as-dependency; only the docs URL version differs); I re-ran help --warning-mode all after the change (2), the base log is the implementer's warnings-base.log |
| AC-4 | PASS | I deleted app/build/generated/ksp and ran kspDevDebug and kspProdRelease: 7 _Impl files per variant (database, 3 auto-migrations, 3 DAOs), diff against ksp-impl-base.txt empty; app/schemas unchanged |
| AC-5 | PASS | check.full green; assembleProdRelease BUILD SUCCESSFUL; git diff b1b3c51a over app/build.gradle.kts, config, app/src, app/schemas, buildSrc is empty |
| AC-6 | PASS | itest on the box for WatchStateDaoTest, CachedPayloadDaoTest, PersistentPayloadStoreDbTest, PuberDatabaseMigrationTest (6 tests, 0 failures): all BUILD SUCCESSFUL |
| AC-7 | PASS with a gap | worktree APK installed (install-preserve, hash matches on device), logged in, Home posters, badges, details backdrop, 40 s playback advancing, Developer settings toggle flips, survives HOME and relaunch, 0 crashes. Gap: the share-log path that runs lifecycle currentStateFlow (DeviceSettingsContent.kt:213-214) showed no system chooser on the box, so that exact code was not exercised |
TESTS ADDED: none (no production or test source changed)
EVIDENCE: /home/dev/orca/workspaces/Puber/dependency-patch-bump-nov/docs/superpowers/evidence/2026-11-28-dependency-patch-bump-nov-device.md (mobile-evidence-audit passed; committed 16588c3f in wt)
DEFECTS: none
UNAVAILABLE: none
MANUAL: optional, for the currentStateFlow path: on the box open Settings > Developer, press "Share log" and, if a chooser appears, close it and confirm focus returns to the row. Also note: auth-save snapshot of the box was taken before the install under the gitignored .auth/; the app now runs the worktree dev build 1.12.8-dev.
