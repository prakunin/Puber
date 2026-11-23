LENS: edge  ROUND: 3  BASE: 9158cf82

All five triage-2 items are fixed and check out against the code and the evidence tables (item 1 AC-7 split; item 2 AC-6 limit about 2330 ms on the line's clock, set-1 rows 1/5/8 grace, row 6 cap; item 3 ranges 1662-2001 and 1718-1911; item 4 GRACE always above 0, READY can be 0; item 5 step 4 matches the evidence method).

### F1 [minor] The AC-10 verdict still claims "one row per key" for 15 starts, on the same single screenshot the AC-7 split now rules out
- Where: docs/superpowers/evidence/2026-11-23-startup-reveal-gate-on-slow-tv-device.md:133
- Fix: Drop "one row per key" from the AC-10 row, or point it to the AC-7 focus clause.

### F2 [nit] The AC-7 focus clause says "checked on one start only" and then "it may be none of them"
- Where: same file:130
- Fix: "Focus clause: NOT CHECKED on the 25 counted starts; one screenshot from an uncounted or unidentified anchor A start shows one row per key."

### F3 [nit] The new AC-6 row contains HTML entities (&amp;lt;) where the rest of the file uses a plain <
- Where: same file:129
- Fix: replace with <.

### Unproven
- spec:159 "Expected: focus ring moves one row per key" still covers the 15 control starts, and no focus record of them exists; matters only if the AC-7 focus clause covers the control too (spec :24 "every start").

(Condensed by the conductor from the reviewer's return; findings, locations and fixes kept.)
