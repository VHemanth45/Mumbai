## Parent

`PRD-search-and-visit-reviews.md` — "Search the city, and say what you thought of it".
The camera-seam addition the Testing Decisions section asks for.

## What to build

Choosing a search result flies the map across the city and ends framed on that place at
street level. Where that flight *ends* is the part a test can hold, and right now nothing
holds it — the fly-to has no coverage at all, while every other camera behaviour (pinch
anchoring, drift, clamping, pan bounds, double-tap) is pinned.

The flight's feel — that it travels and zooms together, that it takes a beat longer than a
double-tap — is verified by hand on the emulator, as the spec says. Its endpoint should not
be.

## Acceptance criteria

- [ ] From an arbitrary starting camera, flying to a given world point ends with that point
      centred in the viewport
- [ ] The flight ends at detail scale
- [ ] The camera it leaves behind sits within the same clamps every other camera operation
      respects
- [ ] The existing camera tests stay green

## Blocked by

- None — can start immediately.
