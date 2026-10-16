CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:588-590 (with NavTelemetry.kt:33-46) - The tab binding is disposed whenever MainScreen leaves composition, and nothing restores it before the Back is recorded.
  - Scenario: a root destination such as Details covers MainScreen. Voyager's `CurrentScreen` composes only the top screen, so the host and its tab navigator are disposed and `unbind` clears the binding.
  - On Back, the flow runner pops and calls `recordNavCommand` at once, still inside the command collector. MainScreen has not recomposed, so `topOf(host)` finds no binding and `navClassOf` returns the `MainScreen` class.
  - `Telemetry.screen` and `nav.to` are therefore `MainScreen` instead of the restored tab's screen. When the host recomposes and rebinds, nothing updates `Telemetry.screen`. Frame, key and memory events stay attributed to `MainScreen` until the next nav command.
  - The new tests keep the binding alive across the root push and Back, so they do not cover this.
  - I checked the call order and the dispose behaviour in the code. I did not run it on a device. The impact is limited to attribution and is self-correcting on the next navigation, so I graded it minor, not Codex's P2/medium.
  - Fix options: keep a class-only snapshot per host across temporary disposal, or set `Telemetry.screen` again on rebind when the host is the current screen. Add a dispose and rebind test.
REJECTED:
- (none) - Both Codex passes raised only this one finding. The queued-attach skip and the `navigatorChangedBy` real-pop tests drew no complaints.
VERDICT: changes needed

Round 3 is the last bounded round, so this goes to the owner as a minor attribution issue, not a blocker.
