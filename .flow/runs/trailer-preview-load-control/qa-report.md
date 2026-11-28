Round 1

CHECK: check.full -> pass (all steps exit 0; TrailerPreviewLoadControlTest 2 tests, 0 failures)
DEVICE: 192.168.1.110:5555
Evidence: docs/superpowers/evidence/2026-11-28-trailer-preview-load-control-device.md
AC-1..AC-5 all PASS (see return).

# Round 2

CHECK: not re-run (production code unchanged since round 1; this round is the triage-1 row 3 device check only)
DEVICE: 192.168.1.110:5555 (lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-4 (trailer plays to its end, no error stop; triage-1 row 3) | PASS | Preview audio 08:56:33.6 to 08:57:34.6 = 61.0 s; trailer length about 61 s (player shows 0:05 / -0:56); stage-pid log filtered to ExoPlayer and error tags has no error line |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/trailer-preview-load-control/docs/superpowers/evidence/2026-11-28-trailer-preview-load-control-device.md
DEFECTS: none
UNAVAILABLE: none
MANUAL: none
Note: the on-device base.apk was a different build at the start (sha256 a05e05b5...); reinstalled via install-preserve, sha256 then matched 491dcfff....
