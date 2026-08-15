## Parent

`PRD-search-and-visit-reviews.md` — "Search the city, and say what you thought of it".
The design question the PRD leaves open under Further Notes.

## What to build

The map currently takes two parameters that are always handed the same place: one drives
the selected-place styling, the other drives the flight. The spec flags this as a small
design question the screen work would answer, and keeps them separate to leave room for a
selection that should not move the camera — a case that does not exist today.

Answer it. Either collapse them into one parameter, or write down why they stay separate
and close this without a code change. Both are acceptable outcomes; leaving the question
open is not.

## Acceptance criteria

- [ ] The map takes one parameter for the place that is both styled as selected and flown
      to — or the reason for keeping two is recorded and this closes unchanged
- [ ] Flying stays keyed on the place's id, so saving a rating while the card is open does
      not restart the flight
- [ ] Selected-place styling behaves exactly as it does today
- [ ] The map still knows nothing about the city, the database or the catalog — it takes
      geometry plus a place list and draws them
- [ ] The existing map and Explore view-model tests stay green

## Blocked by

- #05 — Pin where the flight lands. Changing the parameter that drives the flight wants the
  flight's endpoint under test first.
