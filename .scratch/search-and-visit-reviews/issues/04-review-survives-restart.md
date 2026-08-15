## Parent

`PRD-search-and-visit-reviews.md` — "Search the city, and say what you thought of it".
The restart-seam addition the Testing Decisions section asks for.

## What to build

Ratings and notes are as durable as the lights are. Write a verdict, close the app, open it
again, and it is still there.

The migration itself is already covered — a real version 1 file opened through the app's own
builder, with its state intact and the new columns arriving empty. What is not covered is
the other half: that a review written *after* the update survives a restart at all. This is
the app's real close-and-reopen seam and it is where that belongs.

## Acceptance criteria

- [ ] A rating and a note are saved, the database is closed and reopened through the same
      builder the app uses, and both read back unchanged
- [ ] A visit, a wishlist and their timestamps saved alongside a review survive the same
      round trip
- [ ] The existing restart tests stay green

## Blocked by

- None — can start immediately.
