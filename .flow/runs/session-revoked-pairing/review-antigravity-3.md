ANTIGRAVITY: ran
CONFIRMED:
- none
REJECTED:
- No claims. agy reported no defects in 3968eeaa..672d3618 (SessionEndCleanup, SessionEventBus, App.kt, the AuthComponent preview, SessionEndCleanupTest). I read the diff myself and found nothing either.
- Own check: `releasePendingEnd` could release a newer end's claim if the pending value changed mid-clear. I did not keep this. It needs a second end to arrive while the first handler is being cancelled, and releasing then only allows one extra best-effort clear.
VERDICT: clean
