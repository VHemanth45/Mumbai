## Parent

`PRD-search-and-visit-reviews.md` — "Search the city, and say what you thought of it".
Closes the decision the PRD left open under Further Notes.

## What to build

When you save the card — marking a place explored, giving it stars, writing what you
thought — the light on the map, the percentage in the header and the stars on the card all
change together. There is no moment you can observe where the place has lit up but the
rating has not landed yet.

Today the save writes the visit and then the review as two separate calls, so the state
updates twice and the light can appear a frame before the stars. That is exactly the
flicker the single-call review interface was shaped to avoid, reintroduced one layer up at
the view model.

The resolution: the repository gains a single entry point that commits a visit and a
review together inside the existing read-modify-write transaction, and the Explore save
calls only that. The two-call path goes away rather than being reordered — reordering
moves the flicker somewhere less noticeable, it does not remove it.

## Acceptance criteria

- [ ] Saving a visit together with a rating and a note never exposes a state in which the
      place reads as visited while its rating is still missing
- [ ] The visit timestamp behaves as it does today — kept on a repeat mark, cleared on undo
- [ ] A rating outside one to five is still pulled into range, and a blank or
      whitespace-only note is still stored as nothing written
- [ ] The sparse-row rule still holds: a row that ends up holding no visit, no wishlist, no
      rating and no note is deleted
- [ ] Two saves racing on the same place still serialise instead of clobbering each other's
      fields
- [ ] The existing repository and Explore view-model tests stay green

## Blocked by

- None — can start immediately.
