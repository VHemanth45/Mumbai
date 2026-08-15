## Parent

`PRD-search-and-visit-reviews.md` — "Search the city, and say what you thought of it".
Fills three gaps in the PRD's own Testing Decisions list.

## What to build

Search works, but three of the promises the spec makes about it are not held anywhere by a
test. Close them at the Explore view model, which is the seam the spec names — it drives a
real database and a real repository, so these exercise the catalog as it actually ships.

The gaps are: what happens when nothing matches, whether search reaches places in every
state rather than only the lit ones, and whether the results after the first one keep the
order the map draws them in.

## Acceptance criteria

- [ ] A query that matches nothing yields no results, and leaves the places, the geometry
      and the progress on screen exactly as they were
- [ ] Search finds a place that has never been visited, a place that is wishlisted, and a
      place that is already explored
- [ ] When several places match, the one whose name starts with the query comes first and
      the remainder stay in catalog order
- [ ] The existing search tests stay green

## Blocked by

- None — can start immediately.
