# Review edge, round 2

LENS: edge  ROUND: 2  BASE: b53bf631

No findings. Reviewed c50f506c..4268236c (triage-1 #1-#5):
- #1 both profile rules now name create(Lkotlin/jvm/functions/Function1;Z), matching create(listeners, useDoh); the only main caller (modules.kt:72) passes both arguments, so no create$default rule is needed; no old signature left in either file.
- #2 normalizeDomain("https:///path") -> Empty; "bad_host.example" -> Invalid; both before save and evict (ApiDomainInteractor.kt:84-85); evictCalls reset in @BeforeEach.
- #3 with useDoh = false only the "app" listener is requested and set; the test checks both.
- #4, #5 comments only.
