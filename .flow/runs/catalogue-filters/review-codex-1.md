CODEX: ran: review, adversarial
CONFIRMED:
- [major] ContentListInteractor.kt:182 - isVisible ignores the Show Anime preference for FollowPreference; with Show Anime on and Hide Watched on, picking the Anime genre on Movies/Series gives an empty grid. Fix: pass showAnime into isVisible; FollowPreference is no filter when on; test both preferences on + Anime.
- [minor] ContentListPagingVM.kt:206 (and :294) - generation check and publish() are separate steps on Dispatchers.Default; an old page that passed the check can be queued after Restart. Fix: tag actions with a generation in the Paginator reducer, or lock check+publish with the changeQuery increment.
REJECTED: skipped Loading frame (unproven, tests + device); 4K reset error (deferred cached-payload-row-too-big); process notes.
VERDICT: changes needed
