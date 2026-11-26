# Review edge, round 2 (03265fbc..58773294)

LENS: edge  ROUND: 2  BASE: 4c398e07

No findings. The three round-1 fixes hold:
- Overlapping reads (#3): refreshTransport cancels the previous read; a read cancelled before its IO block never calls the provider; a read blocked inside current() has its result dropped when withContext returns; each onStart/Start cancels the read before it; cancellation rethrown, not logged; newer read throwing keeps the old label; Start during a run returns before refreshTransport.
- Race test holds exactly two reads, releases the newer first, then switches to Ethernet before releasing the older; without the cancel the result would be Ethernet.
- Module test (#1) resolves the VM through the real buildModule and ConnectivityManager lookup, closes Koin in finally.
- buildModule internal: still only passed to DIScope; key and scope wiring unchanged.
- Error handler check (#2) is a real assertion.
Round-2 device evidence predates 7f81684c, but that itest covers only the layout, so nothing needs re-running.
