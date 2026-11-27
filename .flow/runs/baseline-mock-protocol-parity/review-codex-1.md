# Review codex, round 1

CODEX: ran: review, adversarial
CONFIRMED:
- [minor] baselineprofile/.../BaselineMockSelfTest.kt:136-151 - everyResponse_carriesDateHeader requests only textResponse routes; binaryResponse (media and DoH) is never asserted; dropping Date from binaryResponse keeps the test green. Fix: also request /media/poster.jpg, assert 200 and a parseable Date.
REJECTED:
- Route checks accept the wrong hostname on the correct port - needs a DoH misconfiguration nothing shows; AC-3 pins the DoH URL and origins; loopback-only listeners, no egress. Unproven.
- close() holds the dispatcher lock while closing both servers - same pattern as base; checkLate awaits quiescence first. Unproven.
- No implementation defect, egress, misrouted origin, lookup-starting fixture key or instrumentation code outside its source set found.
VERDICT: changes needed
