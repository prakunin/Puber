# Review codex, round 1

CODEX: ran: review, adversarial
CONFIRMED:
- [minor] docs/design/content/index.src.html:862-865 - plotSteps drops the floor; defaults offer only 590 and 520 while the app ships 590/520/460; stand-only defect.
- [minor] index.src.html:960-966 - stand decides plotScrollable from plotLines > capLines, ignoring the 2 dp border; exactly 6 lines at 18 dp with a 108 dp share: stand says visible/unfocusable, app measures 110 > 108, stays 590 and scrolls/takes focus.
- [minor] index.src.html:1170-1172 - switch handler runs focusChain() before render() recomputes plotScrollable; focus ring stays on a plot that no longer scrolls until the next key press (stand preview only).
REJECTED:
- TV comparison against master still open - not a code defect; conductor decides on AC-2/AC-6.
- No finding against DetailsScreenContent.kt or PlotWidth.kt (intrinsic measure, Constraints.fixed at the widest step, coerceIn clamp match D2-D6).
VERDICT: changes needed (stand fixes only, then rebuild index.html)
