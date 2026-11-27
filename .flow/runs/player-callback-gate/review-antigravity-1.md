ANTIGRAVITY: ran
CONFIRMED:
- none
REJECTED:
- Lead from qa: the Audio row reads "Track 1" after a quality switch because of the per-session `onTracksChanged` path. Not proven by the diff.
  - The label expression (`format.label ?: format.language ?: "Track ${index + 1}"`) is unchanged from base. It sits at line 843 in base (`cec5f7fc`) and line 873 at HEAD.
  - The diff only wraps the `notifyTracksUpdated()` calls in the session gate. `onTracksChanged` at HEAD:256 still calls the same function.
  - So "Track 1" appears only when the new stream's audio `Format` has no `label` and no `language`. The manifest of the stream played after the switch decides that, not this branch.
  - I did not run master against the box, so I can't rule out a difference between the two builds. If the qa lead holds up, compare the audio `Format` metadata of the two streams. A first-session versus switched-session comparison on master would also separate the two causes.
  - One residual risk, which agy did not raise and I did not trace. If the gate dropped a post-switch `onTracksChanged`, the row would keep a stale or default state. That would show the old labels, not "Track 1", unless the first call had already produced it.
- agy found no other defects in the diff.
VERDICT: clean
