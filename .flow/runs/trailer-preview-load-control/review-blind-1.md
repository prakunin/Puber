LENS: blind  ROUND: 1  BASE: 8c3117ea
### F1 [minor] The tests cannot catch the wiring or a wrong buffer setting
- Where: app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewLoadControlTest.kt:11-24
- Problem: `trailerPreview_buffer_matchesAcceptedValues` just repeats the six literals of `TRAILER_PREVIEW_BUFFER`. `buildTrailerPreviewLoadControl_doesNotThrow_withPreviewBuffer` only shows that `DefaultLoadControl.Builder` accepts the values. Nothing checks that `buildTrailerPreviewLoadControl` passes each field into the right builder slot, or that `TrailerPreviewPlayer` uses the load control at all.
- Failure scenario: someone deletes `.setLoadControl(buildTrailerPreviewLoadControl())` from the `ExoPlayer.Builder` chain (TrailerPreviewPlayer.kt:66), or swaps `minBufferMs` and `bufferForPlaybackMs` in the builder call. The preview then runs with streaming-default buffers or the wrong thresholds, and both tests stay green.
- Fix: test the built `DefaultLoadControl`'s behaviour. For example, `shouldContinueLoading` should return false once the buffered duration passes 15 s, and `shouldStartPlayback` should return true at 1 s buffered. Then the builder mapping is under test, not the constants. Cover the `setLoadControl` wiring with a device check or an instrumented check.

### Unproven
- TrailerPreviewPlayer.kt:192 `targetBufferBytes` = 12 MiB with `prioritizeTimeOverSize = false`: on a high-bitrate trailer, the byte cap stops loading before 10 s are buffered. That is the intended trade, not a stall, because playback starts once the byte target is reached. It would matter only if the trailers are high-bitrate and the device shows rebuffering.
