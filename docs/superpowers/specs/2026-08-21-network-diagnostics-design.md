# Media server speed test — design

The Settings → Network action reproduces the useful behaviour of KinoPub's speed-test screen. It
compares the two media locations a user can select instead of diagnosing unrelated API mirrors.

## Measurements

The test runs only after the user selects a server card. It downloads the same 100 MiB test payload,
or as much as arrives before the 20-second deadline, from the selected endpoint:

1. `speed.ams-static-14.cdntogo.net` — Amsterdam, server-location option `1`.
2. `speed.msk-static-05.cdntogo.net` — Moscow, server-location option `2`.

Each request includes the original `ckSize=100` query and a cache-busting value. The response is
discarded in 64 KiB chunks. The screen receives a running byte count and average Mbit/s figure every
500 ms, then keeps the final figure. A timeout after bytes have arrived completes a valid partial
sample; connection failure before any byte is a failure. Leaving the screen cancels the request and
closes the response.

The shared `OkHttpClient` configuration is copied and only the speed-test call timeout is changed.
The global client, account credentials, API mirror, streaming type, and playback state are untouched.

## Screen and selection

Amsterdam and Moscow are always shown together. The currently selected `SERVER_LOCATION` is marked
using the device-settings response. The screen starts idle and has no separate Start button.

The main visual is a native Compose speedometer rather than a literal copy of the KinoPub screen.
Its logarithmic 0–100 Mbit/s dial keeps normal streaming speeds readable and divides the arc into
SD (below 5), HD (5+), Full HD (10+), and 4K (25+) zones. The needle follows the active sample and
returns to zero when the run ends; the central value retains the fastest completed result. Selectable server cards sit
to the left of the compact dial. Their stable rows show throughput, median HTTP time to first byte
as ping, and median variation between five adjacent latency samples as jitter. Latency values remain
hidden until samples exist. Selecting a card tests only that server and preserves the other card's
previous result; both cards are disabled while a run is active. Progress is indeterminate because
the run may end by the 20-second deadline before the 100 MiB cap. The subtitle discloses the maximum
25-second duration and 100 MiB traffic cost before the user starts a run. The zones use a restrained traffic-light
palette: red for SD, yellow for HD, and green for Full HD and 4K.

After two results are available, the screen proposes the other location only when it is at least 5%
faster than the current one. A working alternate is also proposed when the current location failed.
The run itself never changes settings. The current server is marked for context only.

On navigation, the current server card (or the first card if settings are unavailable) owns the
initial focus claim and does not jump when results appear. That claim survives the short window
where Compose can fall back to the left rail before the new screen is placed; after it lands, target
changes wait for an intentionally open drawer to hand focus back instead of stealing it.
Returning with Back restores the Settings Network section and explicitly focuses the Speed Test row;
the pending restore is consumed only after that row is attached and receives the request.

In Settings, Speed Test is placed immediately after Server in use so their relationship is clear.
Known Russian server-location labels returned by the API are mapped to app resources; unknown labels
remain visible as received, so a newly added backend option is never blank.

## Tests

Unit tests cover selected endpoint URLs, progress emissions, partial failure, latency calculations,
recommendation rules, preserving the other card's result, and the no-automatic-write guarantee.
Compose instrumentation covers card actions, state changes, restoration, and the initial rail handoff.
