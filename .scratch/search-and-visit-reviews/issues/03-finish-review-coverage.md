## Parent

`PRD-search-and-visit-reviews.md` — "Search the city, and say what you thought of it".
Fills the review half of the PRD's own Testing Decisions list.

## What to build

The repository proves most of these already. The spec asks for them at the Explore view
model too, and it is right to: that is the seam the card actually saves through, and it is
the only place that proves the form as a whole behaves — not just the write underneath it.

The one that matters most is un-marking a visit. The sparse-row rule was widened
specifically so that correcting a mis-tap does not delete what someone wrote, and nothing
at this level currently guards it.

## Acceptance criteria

- [ ] Saving only a note, with no rating, keeps the note and leaves the place unrated
- [ ] A note of only spaces reads back as no note
- [ ] A rating outside one to five is pulled into range
- [ ] Saving again over an existing verdict replaces it
- [ ] A rating can be cleared back to unrated after having been set once
- [ ] Un-marking a visit keeps both the rating and the note
- [ ] A place that has been reviewed but never visited stays unvisited and does not move the
      percentage

## Blocked by

- #01 — Commit a visit and its verdict in one transaction. The save path these assert
  against changes shape there.
