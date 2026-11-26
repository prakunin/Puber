# Review tests, round 1

LENS: tests  ROUND: 1  BASE: b27d8e40
### F1 [minor] The reconnect test still passes when the preflight never reconnects
- Where: test_preflight_reconnects_missing_device; tools/generate-baseline-profile.sh:27-31
- Problem: counts every args=connect line; make's own connect on 5039 adds one, so the count reaches 2 without the preflight reconnect.
- Fix: count only preflight connects (^port= args=connect) and require >= 2, or make the fake adb leave "absent" only after a connect.

### F2 [minor] AC-7's negative case was never shown failing on base, and nothing repeatable guards it
- Where: BaselineProfileGenerator.kt:200; plan record "M2"
- Problem: the base selector was not observed to false-pass (failed at entry 5 / entry 2, flaky); D8's By.res("main_rail").hasDescendant(By.text(title)) does not test focus and relies on a collapsed rail not exposing item text (observed for Series only); M4 found D7's premise false.
- Fix: record that AC-7's base half is unproved; add hasDescendant(By.text(title).focused(true)) or document M2 as a re-run step whenever rail labels change.

### F3 [minor] The new shell suite runs in no gate
- Where: tools/tests/test-baseline-profile-tooling.sh; .flow/config.yaml check.full; CI
- Fix: add it to check.full (or a make target that check.full calls); no device, ~1 s.

### F4 [minor] Two make refusal paths are not covered by the port-only test
- Where: test_make_target_uses_only_baseline_port; Makefile:151-157
- Problem: the "did not show up within 60 s" and "lists another serial" refusals call kill-server but are not in the loop.
- Fix: add both cases with the same port assertion.

Suite state: shell tests 15/15; check.full exit 0 (cached; BaselineProfileContentTest 7/7). Every AC has a test or a recorded device/manual check; base-green P0 shell tests marked guard. Profiles sampled: HeroCarousel present in both files (signature line changed); ModulesKt startup rules 294 -> 296.

### Unproven
- run_signalled asserts make's re-raised signal exit code, not the recipe's own exit 129/130/143 traps.
