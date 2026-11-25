LENS: edge  ROUND: 2  BASE: c9e98129

No findings.

Checked fda01538..8f119fa2: hold seam reset (reset() clears holdTabSwap/heldTab; other callers default false); release with nothing held fails loudly and clears heldTab; heldTab written and read on the UI thread; during the hold selectedTab/loadingRoot change at pick time, the outgoing root composes ROOT_CARD_FIRST and the arriving root is absent; tag ambiguity impossible during/after the hold; timing (4 s HandingOff assertion before the 5 s budget; coerceAtLeast(0)); E2 comment accurate; plan record names the M9-same-id edit and the expected failure at :130.

### Unproven
- MainRailHandoffKindTest.kt:127 assumes the outgoing card's launch focus request lands while HandingOff; real only if the green run fails at :127.
- :129-130 the 1 s window counts only if the outgoing settle attempt fires inside it; real if M9-same-id passes :130 in qa's 3 reruns.
