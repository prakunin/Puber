# Review state-path, round 1 (03265fbc)

LENS: state-path  ROUND: 1  BASE: 4c398e07

No findings.

VM: NetworkDiagnosticsVM
  onStart()            -> refreshTransport(); launch { currentServer() -> {currentServer} }
  refreshTransport()   -> launch { withContext(io) current() -> stateValue.copy(transport) }  (reads stateValue after the suspension, on Main)
  startRun(server)     -> guard running; refreshTransport(); {running=true, finished=false, recommendedServer=null}; launch run
  publish(server, run) -> {servers, currentServer, running, finished, recommendedServer}  (leaves transport alone)

No write overwrites another: every write copies stateValue on Main right before updateViewState; transport and currentServer can land in either order and both survive.
Ordering: Start twice -> second stops at the running guard; onStart read and a fast first Start read can resolve in either order, only disagree if the network changes within milliseconds (not reachable); Back mid-flight drops the result; provider throws -> caught, transport stays null, currentServer unaffected.
Focus: the line is a plain Text inside state.transport?.let with no click/focus/Surface; its group does not reset the server-card Row's remember slots; the row moves down 24 dp once; the focused card stays composed; launch focus still targets the same card; D-pad up has no focusable target in the header. The new focus test covers a transport set at first composition, not a late arrival; device check M1 step 3 covers that.
