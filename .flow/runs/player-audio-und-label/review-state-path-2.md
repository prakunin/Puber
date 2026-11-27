LENS: state-path  ROUND: 2  BASE: cb7fcd45

No findings.

I followed both subtitle pick paths from round 1 through the fixed `SubtitleTrackSelector.kt` and `PlaybackController.kt:906`. Both now resolve to the track that was picked.

- **Codex F1 (HLS row: player format "und", rendition "ru"):**
  1. `buildSubtitleRow` gives the row language "ru", and its `playerTrackId` is the source track id.
  2. `toPlayerTextTracks` gives the candidate `language = null`.
  3. `isSemanticallyCompatibleWith`: the row language is known, but the new `language == null` clause lets the candidate through.
  4. The forced flag passes. The label check also passes: the candidate's `formatLabel` is either null or the same `format.label` the row's `descriptiveLabel` came from.
  5. `matchByFormatId` finds that one candidate, with or without the merge prefix, so the override is set on the original track.
  6. The new test `select_resolvesManifestRowTakingRenditionLanguage_toItsUntaggedCandidate` covers this case, including a tagged "ru" side-load next to it that does not get picked.
- **State-path F1 (API row with lang "und", progressive stream, rows built from the API):**
  1. `knownLanguageOrNull("und")` is null, so every candidate passes the language check.
  2. `matchBySourceIdentity` compares the row url with the candidate's prefix-stripped `formatId` or `formatLabel` and finds the single side-load that matches.
  3. `setPreferredTextLanguage` now gets "" instead of "und". A blank row already sent "" before this change, so nothing new happens there.
  4. The test `select_resolvesUndeterminedApiRow_toItsUntaggedSideLoad` covers this case. On the next start, `restoreSubtitlePreference` (via `exactSubtitleUrlMatch`) leads to the same selector call, which now resolves.
- **What the widened filter now lets in, and why nothing breaks:**
  - A candidate with no language now passes for any tagged row. After the filter it can only be picked by an exact identity: a format id, an API url or file path, or the full group id plus track index. Each of these needs the result to be unique (`singleOrNull`).
  - `matchByLanguage` still needs a tagged candidate, so an untagged track cannot be picked by language. `select_doesNotPickUntaggedCandidateByLanguage_forTaggedRow` covers this.
  - Two candidates would have to share the same identity for a match that used to be unique to become ambiguous. That is not realistic.
  - The paths I marked as holding in round 1 (audio pick and restore, the Off shortcut, preferences saved before the fix) are not touched by this diff.
- **Focus:** this diff changes no UI, so there is no focus path to check.
