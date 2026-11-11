CODEX: ran: review (incremental cfd0a1f3..4efeed73, tests-only; adversarial pass skipped as briefed)
CONFIRMED:
- none
REJECTED:
- Codex reported no findings. It said the BACK assertion checks the expected successor of the removed card. It did not run the instrumented tests.
- My own check of `neighbour = items[indexOfFirst{id==focusedId}+1]` found no out-of-range risk. `focusAwayFromTheFirstCard` moves focus down one row and right one column, so the focused card cannot be the last item. This holds only if `items` has more than one row, and I did not check that fixture size.
- My own check of `sendKeyDownUpSync(KEYCODE_BACK)` found no problem. It goes through the activity into the menu's `BackHandler`, which is the intended path. `waitUntil` handles the asynchronous focus move.
- The plan note (D16, "not run by the implementer; qa-device runs it") is honest. Whether the neighbour really takes focus after BACK is unproven until qa-device runs the test.
VERDICT: clean
