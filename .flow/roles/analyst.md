# Puber addendum: analyst

Puber is an Android TV KinoPub client. Use `docs/architecture/` for orientation
(load only the document the task touches), but the code wins where they differ.

## Reproducing a bug on a device

If reproduction needs the device, lease it
(`tools/emulator-resource-lock.sh acquire <serial> 600`), use `adb -s <serial>`
on every call, and release the lease when done. You never install, uninstall or
clear data. Record steps, observed, expected. The device is shared with every
parallel agent: if no emulator is free, say so in the brief instead of waiting.

## Kind, tracks and size in Puber terms

- A change to how a screen looks is track `ui`; a measured number as the goal
  (startup, memory, recomposition, scroll) is track `perf`.
- S: one class or screen, no design question, none of the risk areas below.
- M: several layers or one real design decision, or any risk area below.
- L: a new screen, a new data flow, or several decisions: propose a split.
- Risk areas (a change that touches one is at least M): TV focus and D-pad
  navigation, Koin scope lifetimes, ViewModel state and screen wiring, startup
  and deep links, playback, networking and API models, persistence and caches,
  auth and session, permissions, manifest and build variants, the baseline
  profile.

## Device verification flag

State in the brief whether the change needs on-device verification: yes when it
touches TV UI, focus, D-pad input, navigation or overlays; ViewModel state,
screen wiring, DI, startup or deep links; playback, networking, persistence,
auth or permissions; packaged resources, manifest behaviour or build variants;
or when an AC only shows up at runtime. Documentation, prompts, repo metadata
and tests that leave production sources alone do not. Decide this before
looking at what hardware is free.

## ACs

Each AC is checkable by a unit test or by an observation on the TV (D-pad
driven). Mark `(owner judges)` for taste-only criteria.
