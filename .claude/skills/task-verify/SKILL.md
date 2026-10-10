---
name: task-verify
description: Use when a Puber task's code is written and needs to be proven — static checks, tests, and the on-device verification AGENTS.md requires. Run inside the task's worktree.
---

# Verify a Task

Verification is evidence, not a claim. Each step either passes with output you
saw or the task stays where it is.

## 1. Static gate

In a worktree everything goes through `./tools/agentw` (the guard hook refuses
`./gradlew` there). Set `AGENT_GRADLE_SERIAL=1` if another worktree may build.

```bash
./tools/agentw :app:compileDevDebugKotlin
./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug
```

New behaviour gets unit tests (JUnit 5, `MainDispatcherExtension`,
`FakeResourceProvider`); see `docs/architecture/testing.md`.

## 2. Does it need a device?

Decide **before** looking at what hardware is free. It does if the diff touches
TV UI, focus, D-pad, navigation or overlays; ViewModel state, screen wiring, DI,
startup or deep links; playback, networking, persistence, auth or permissions;
resources, manifest or build variants; or an acceptance criterion only visible
at runtime. Docs, prompts, repo metadata and test-only changes do not.

## 3. On device (when needed)

```bash
export PATH="$HOME/Android/Sdk/cmdline-tools/latest/bin:$(ls -d $HOME/Android/Sdk/build-tools/* | sort -V | tail -1):$PATH"
# install-preserve needs apkanalyzer (cmdline-tools) and apksigner (build-tools)
tools/emulator-resource-lock.sh adb-emulators tv       # what exists
tools/emulator-resource-lock.sh acquire <serial> 600   # prints a token: keep it
./tools/agentw :app:assembleDevDebug
tools/android-apk-install-preserve install-preserve --serial <serial> \
  --package com.kino.puber.stage --apk app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s <serial> shell am start -n com.kino.puber.stage/com.kino.puber.MainActivity
```

- Install this worktree's own APK, never a main-checkout build. Don't pipe
  install-preserve into `tail`/`grep`: that hides its exit code. Read its JSON
  `outcome`.
- The host emulator is AVD `puber_tv_36` (`emulator-5554`), paired; if its login
  is lost while the app is still installed, `make DEVICE=emulator-5554 auth-restore`.
- Instrumented tests only via `make DEVICE=<serial> itest [TESTS=<fqcn>]`.
- A failed install blocks the task. It is never a reason to uninstall, clear
  data, allow a downgrade or swap the signer — those need the user's explicit say.
- A physical TV (`adb-physical-devices`) needs explicit permission each time.
- Release the lease when done: `tools/emulator-resource-lock.sh release <serial> <token>`.

Write what you checked and saw to
`docs/superpowers/evidence/<date>-<slug>-device.md` (steps, expected, observed,
screenshots if any). Then run
`tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage`;
no raw logcat, tokens, or broad UI dumps.

**No device available?** Do not skip silently. Set the board stage to
`needs-device`, say so to the user, and ask.

## 4. Hand back

Update the board row: stage `review`, evidence link. Report the commands run and
their results, including anything that failed.
