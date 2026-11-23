CODEX: ran: review, adversarial (the adversarial pass hit the Codex usage limit midway and returned no structured findings. It stopped after one preliminary remark, which is the same device-ordering point as the review finding, so the pass is incomplete.)

CONFIRMED:
- [minor] tools/generate-baseline-profile.sh:84-85 - `ensure_paired` runs before the emulator, API 34 and private-adb checks. Those checks live only in the `baseline-profile` Make target, which `run_generation` calls last. - Scenario: `DEVICE=<unsupported target>` with the stage app unpaired, for example the owner's box or an API 33 emulator. The script builds, runs `install-preserve` on `com.kino.puber.stage` and launches the app. In non-interactive mode it then exits with code 3 before any refusal. The install keeps app data and touches only the stage package, so the damage is limited. The intended "refused: not an emulator" rule is still enforced only after the device has been modified. The fix is to move the preflight ahead of `ensure_paired`.

REJECTED:
- Codex's P2 claim that this needs separate authorization for a physical TV - AGENTS.md gives standing authorization for install-preserve of the dev APK on `com.kino.puber.stage` on the box. The defect is the ordering, not a missing authorization, so I down-graded it to minor.
- Adversarial remark that the runtime telemetry changes are narrow and the S1 result passes - this was an observation, not a defect.

VERDICT: changes needed
