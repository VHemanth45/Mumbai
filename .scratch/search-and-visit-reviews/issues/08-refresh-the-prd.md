## Parent

`PRD-search-and-visit-reviews.md` — "Search the city, and say what you thought of it".

## What to build

The spec describes a working tree that no longer exists. Anyone picking it up is told the
build is broken and the Explore screen is unwritten; both were true when it was written and
neither is true now. A spec that lies about the state of the code is worse than no spec,
because it is believed.

Bring it in line with what shipped, and record the decisions that were left open rather
than quietly dropping them.

## Acceptance criteria

- [ ] The status header reflects that the feature is implemented, building and tested
- [ ] The Further Notes no longer claim the Explore screen is unwritten or that the module
      does not compile
- [ ] The catalog size in the Problem Statement matches the seed rather than the older,
      smaller hand-written one
- [ ] The open decision on save ordering records what was decided and why
- [ ] The Out of Scope list is reconciled with the fact that the place detail screen now
      shows the rating and the note read-only — either the scope line goes, or the reason
      the implementation went past it is written down
- [ ] The Testing Decisions section describes the coverage that now exists

## Blocked by

- #01, #02, #03, #04, #05, #06, #07 — this describes the finished state, so it lands last.
