LENS: tests  ROUND: 5  BASE: e4c3b5aa

No findings.

Reviewed 9910c26e..c4519fa4. WatchStateRepositoryTest 50 and WatchStateSyncInteractorTest 78 pass, nothing skipped. `syncWindow_aNoChangeCatchUpDoesNotLetAWalksFirstChunkThrough` and `syncWindow_publishesTheLastEmissionOfACatchUpThatHeldAChangeWhenAWalkOpensRightAfter` are red on the old code and would fail if the deadline never expired / were not armed. Round 2-3 late-emission tests pass against the deadline. Mark-branch clearing (:179) untested; it only shortens the accepted window.

### Unproven
- WatchStateRepository.kt:48,250 wall clock: a backward clock change stretches the deadline; an elapsed-time clock would fix it.
