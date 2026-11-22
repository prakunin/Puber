LENS: edge  ROUND: 1  BASE: 2fd851a4

No findings.

### Unproven
- `app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapper.kt:369` (`buildMovieResumeLine`): if a video sends `duration: 0` rather than leaving it out, `video?.duration ?: item.duration?.total` keeps the 0 and never reaches `item.duration.total`. `length()` then returns null, so the line says "Not started" even though there is a saved position. This becomes real if an item payload is captured where `videos[0].duration` is 0 and `duration.total` is greater than 0.
- `DetailsScreenUIMapper.kt:369`: the movie line now reads `videos.first().watching` before `item.watching`. A movie whose saved position is only in `item.watching`, while its video sends `watching` with `time = 0`, loses its resume line. This was accepted as design-delta risk 1. It becomes real if an endpoint is shown to fill `item.watching` but not `videos[].watching`.
- `DetailsScreenUIMapper.kt:386-390` (`timeLeft`): if a movie's position is at or past its length and `isItemWatched` is false, there is nothing left to show, so the line falls back to "Not started · <total>". The behaviour is the same as on base. It becomes real if a final save at the very end of a movie is shown to leave the item not marked as watched on the server.
- `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:456`: with `Result<Unit>`, any 2xx answer counts as a success, including one whose body reports an error (for example `{"status":500,"error":...}` sent with HTTP 200). This becomes real if KinoPub is seen answering marktime with HTTP 200 and an error body.

Relevant files:
- /home/user1/orca/workspaces/Puber/resume-progress-anomaly/app/src/main/java/com/kino/puber/ui/feature/details/model/DetailsScreenUIMapper.kt
- /home/user1/orca/workspaces/Puber/resume-progress-anomaly/app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt
