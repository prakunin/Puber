# Review codex, round 1 (c8799377)

CODEX: ran: review
CONFIRMED:
- [minor] docs/superpowers/evidence/2026-11-26-main-dead-code-leftovers-device.md:9 - the cold-launch row says PASS, but the only pre-resume observation is a black frame at 8 s; Main appears only after HOME and relaunch. AC-4 is met by the later rows; this is wording. Fix: mark the row "inconclusive: black at 8 s, Main confirmed after resume" or add a screenshot taken before HOME.
REJECTED:
- Source regression in the removed code - none; removed items have no callers, router import still used, remember(vm) is a no-op while vm is stable, PagingListState data classes and Immutable kept, dropped imports served only PagingColumn.
VERDICT: clean
