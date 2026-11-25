LENS: edge  ROUND: 6  BASE: 3e5248d0

No findings. Behaviour unchanged: `filter.isActive && contentFocusActive && !outgoingDuringTabPick && !headerHoldsFocus.value` became `filter.isActive && mayTakeFocus && !headerHoldsFocus.value` with `mayTakeFocus = contentFocusActive && !outgoingDuringTabPick` (ShowAllScreenContent.kt:197-202). Same inputs (plain Booleans, same effect keys), same short-circuit (`headerHoldsFocus.value` read at the same point), same keys and request call. Other files in the range are evidence and plan notes. Round 3 red/green runs on 3e5248d0 still hold.
