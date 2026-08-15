## Parent

`PRD-search-and-visit-reviews.md` — "Search the city, and say what you thought of it".
User story 29.

## What to build

Someone who used to open a place by tapping its light needs to be told, on screen, that
places are opened from the search box now. Otherwise they hunt for a tap target that has
been deliberately removed and conclude the app is broken.

A screen-reader user is already told — the map's accessibility description points at the
search box. A sighted user is not: the only hint on the map explains zooming and nothing
else. That asymmetry is the bug.

## Acceptance criteria

- [ ] The Explore screen tells a sighted user that places are opened from the search box
- [ ] The existing hint about pinching and double-tapping to zoom in is still there
- [ ] The map's accessibility description is left alone — it already says this
- [ ] The hint does not cover the city or the card

## Blocked by

- None — can start immediately.
