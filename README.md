# City Memory

A personal city-exploration app for Android. Your city starts dark; every place you explore
lights up — not as a dot, but as the real streets and buildings around it, on real
OpenStreetMap geometry. Google Maps does the navigating — City Memory keeps the memory.

**Discover → Wishlist → Explore → Light Up → Track Progress**

MVP scope: one city (Mumbai), 31,657 places — every place OpenStreetMap has mapped inside the
city (3,191) plus everything [Overture Maps](https://overturemaps.org) adds that OSM does not
have (28,466) — plus any you add yourself. Fully offline.

---

## Running it

The project ships with a self-contained toolchain in `.toolchain/` (JDK 17, Android SDK,
Gradle 8.14.3, plus an emulator and an Android 15 arm64 system image). Nothing is installed
system-wide — deleting `.toolchain/` removes all of it.

It occupies ~9 GB, of which ~6.5 GB is the emulator half. If you only ever build and deploy
to a physical device, that half is disposable:

```bash
rm -rf .toolchain/android-sdk/emulator .toolchain/android-sdk/system-images .toolchain/avd
```

```bash
source .toolchain/env.sh        # JAVA_HOME, ANDROID_HOME, GRADLE_USER_HOME, PATH

./gradlew :app:assembleDebug    # build the APK
./gradlew :app:testDebugUnitTest # run all 192 tests (no device needed)
./gradlew :app:installDebug     # install on a running device/emulator
```

To run it on the emulator that is already configured:

```bash
source .toolchain/env.sh
export ANDROID_AVD_HOME="$TOOLCHAIN_ROOT/avd"
$ANDROID_HOME/emulator/emulator -avd citymemory -no-audio -no-boot-anim &
adb wait-for-device
./gradlew :app:installDebug
adb shell am start -n com.citymemory/.MainActivity
```

In **Android Studio**, open the `Mumbai` folder directly. `local.properties` already points at
the bundled SDK. If Studio prefers its own JDK, that is fine — any JDK 17 works.

`minSdk 26`, `targetSdk 35`, `compileSdk 35`. Release builds run R8 with resource shrinking
(24 MB debug → 7.2 MB release); `MainActivity` declares `configChanges` so a rotation does not
tear down the tree and re-project the city.

---

## Architecture

```
app/src/main/java/com/citymemory/
├── domain/                     pure Kotlin — no Android, no Room
│   ├── model/                  Place, City, PlaceCategory, Geo, Progress, Achievement
│   ├── repository/             PlaceRepository, CityGeometryProvider  (interfaces)
│   └── ExplorationSummarizer   progress + achievements, derived from the place list
├── data/
│   ├── local/{entities,dao,database}       Room
│   ├── local/seed/             PlaceCatalogCodec + DatabaseSeeder — reads the
│   │                           generated catalog out of assets/mumbai-places.tsv
│   ├── map/                    OsmCityGeometryProvider + CityMapCodec (real OSM),
│   │                           MapLabelCodec (the names),
│   │                           MockMumbaiGeometryProvider (fallback outline)
│   ├── photo/                  PhotoStore — imports, downscales and owns the files
│   ├── mapper/                 entity ⇄ domain
│   └── repository/             PlaceRepositoryImpl
├── ui/
│   ├── map/                    CityMapView, MapPaths, MapCamera, MapStyle, GeoProjector
│   ├── screens/{explore,discover,wishlist,progress,place}   Screen + ViewModel per feature
│   ├── components/             PlaceCard, PlaceThumbnail, GlowProgressBar, states
│   ├── navigation/             routes + bottom destinations
│   └── theme/                  dark-only palette, type, category visuals
├── di/AppContainer.kt          the whole dependency graph, ~30 lines
└── util/                       NavigationLauncher (external maps hand-off),
                             LocationSource (the GPS behind "use my location")

tools/                          the data pipeline (Python, build-time only)
├── curated.tsv                 human-chosen names; now only ranks, no longer admits
├── mumbai_boundary.py          boundary.geojson -> point (locality, pincode) lookup
├── dump_pois.py                OSM PBF -> every named object, candidates flagged
├── build_seed.py               candidates -> assets/mumbai-places.tsv
├── build_labels.py             boundary + catalog -> assets/mumbai-labels.tsv
├── extract_osm.py              OSM PBF -> cropped, classified intermediate
└── build_map_asset.py          intermediate -> app/src/main/assets/mumbai.map
```

### One stream, everything derived

`PlaceRepository.observePlaces(cityId)` is the single source of truth. The map, the
Discover list, the wishlist, the percentage, the category breakdown and every achievement
are all computed from that one Flow. Room re-emits it whenever either `places` or
`user_place_state` changes, so marking a place visited lights the map, moves the
percentage and unlocks achievements in the same frame — nothing can drift out of sync.

Achievements in particular are **never stored**. They are a pure function of the current
place list, recomputed on every emission, so unlocking cannot be missed or double-awarded,
and undoing a visit correctly re-locks them.

### Data model

Place facts and user state are separate tables joined on read:

| `cities` | `places` | `user_place_state` | `place_photos` |
|---|---|---|---|
| id, name, country, catalogStamp | id, cityId, name, category, description, latitude, longitude, imageUrl, displayOrder, address, isUserAdded | placeId, isVisited, isWishlisted, visitedAt, wishlistedAt, rating, note | id, placeId, fileName, addedAt |

`user_place_state` rows are **sparse** — a place the user has never touched has no row, and
the row is deleted again once it is neither visited nor wishlisted. So "untouched" and
"un-marked" stay the same thing.

Two details worth keeping if this is extended:

- The catalog is written with `@Upsert`, not `INSERT OR REPLACE`. `REPLACE` is
  delete-then-insert in SQLite, which would cascade through the foreign key and wipe the
  user's visit history on any future re-seed.
- Seeding is driven from the repository's read *and* write paths, so a collector can never
  observe an empty database, and a mutation that beats the first read cannot fail the
  foreign-key constraint.

### The map

The map is real OpenStreetMap geometry for Greater Mumbai — coastline, water, parkland, the
whole road hierarchy, building footprints — and almost all of it is hidden.

The **unlit** city is drawn barely above the background: a coastline you can just find and
the ghost of the arterials. Where you have actually been, the *same* geometry is drawn again
in warm sodium light and masked down to a soft-edged disc 100 m across. So an explored place
is not a dot on a map. It is the streets around it, legible down to the buildings, with the
rest of the city still dark. Pinch or double-tap to go from the whole city to about a metre
per pixel; the lit area only becomes streets once you are close enough for streets to mean
something.

**How the reveal is composited**, and why it is two layers rather than one:

```
saveLayer A                      the lit city
  warm wash + lit geometry
  saveLayer B (blend = DstIn)    the mask
    one soft radial disc per visited place
  restore B  -> multiplies A's alpha by the mask
restore A    -> composites what survived over the dark city
```

The mask needs its own layer because discs overlap. Punching each disc straight into A with
`DstIn` would let the transparent rim of a second disc erase the solid centre of the first,
so two nearby explored places would carve holes in each other. Accumulating them in B first
turns overlap into union, which is what "both of these are lit" should mean.

A single disc has nothing to overlap, so that case skips layer B and masks straight into A —
which is the zoomed-into-one-place case, where the frame budget is tightest. It has to be
drawn as a *rect* filled with the gradient rather than as a circle: `DstIn` only touches the
pixels a draw covers, so a circle would leave the corners of the layer at full brightness.
`RevealMaskTest` renders both paths and compares them pixel by pixel.

**Where the growth landed.** The catalog going from 177 places to 3,191 took the asset from
496,882 points to 866,542 — but the base layer did not move at all. Coastline, water, green,
rail and every road class are the same 59,834 shapes and 292,999 points they were; the whole
increase is `building` (27,970 → 73,010) and `service` (5,963 → 15,750), which are the two
kinds only carried near a place. Those draw from 6× and 10× zoom respectively, well past the
3× the overview uses, so **the overview costs exactly what it did before** and the extra
geometry is only ever touched inside a lit area you have zoomed into. `MapPaths` tiles that
at 16×16 rather than 12×12 so the most expensive single tile stays about where it was.

**The names.** The map used to draw no text at all, which made a lit area a shape you
recognise rather than one you can read. It now carries two tiers, generated at build time by
`tools/build_labels.py` into `assets/mumbai-labels.tsv`:

- **89 postal localities** from `boundary.geojson` — Bandra West, Colaba, Andheri East — set in
  letterspaced small capitals, drawn while the whole city is on screen. These are the names
  Mumbai navigates by.
- **130 major places** from the head of each category in the catalog, which is score-ordered, so
  these are the ones OSM has most to say about. They appear from 5× zoom, once a building is a
  thing you can see.

Positioning an area name is the part that needed real work. A label has to sit *inside* its own
polygon and the obvious way to place one does not: Mumbai's postal areas wrap around creeks and
the coastline, so the average of Mahim's or Trombay's vertices lands in the water. What is
computed instead is the **pole of inaccessibility** — the interior point furthest from any edge,
by Mapbox's best-first quadtree search — which is both inside the polygon and in the widest part
of it, which is where a label wants to be anyway. `build_labels.py` fails its own build if any
pole comes out outside its polygon.

Names are measured **once**, in composition, and never again: text layout is the expensive half
of drawing a string, so every subsequent frame is a blit at an offset. They are drawn last, in
screen space, so they stay upright and the same size at every zoom — and **above** the
composited reveal rather than inside it. That is the one deliberate exception to the rule that
nothing is visible until you have been there: an unlit city you cannot read is a city you cannot
navigate, and the point of writing "Bandra West" on it is to tell you where you are looking
before you have explored it. Overlaps are resolved by greedy first-come rejection in the order
the generator wrote them, so areas claim their space before places; `LabelPlacement` is a plain
object with no Compose dependency beyond geometry types precisely so that decision is testable.

**Getting 867,000 points to 60 fps** took five things, in `MapPaths` and `CityMapView`:

- **Tiling.** One `Path` per kind means Skia walks every residential street in the city to
  draw the four on screen. Shapes are bucketed into a 12×12 grid and only intersecting tiles
  are touched.
- **Two levels of detail.** At the overview the city is ~25 m to the pixel, so a coastline
  surveyed to the metre puts twenty vertices on one pixel. Below 3× zoom each tile draws a
  decimated path instead: the overview goes from ~93,000 points to a few thousand.
- **Lazy paths, built ahead of the camera.** Tiles build on first draw, so the overview never
  pays for the buildings you may never zoom into. Building one inside the draw pass drops the
  frame that needed it, though — which is the frame in the middle of the pinch that zoomed in
  far enough to want it — so `CityMapView` also prewarms them on `Dispatchers.Default` just
  ahead of the camera, and mid-gesture the draw pass skips anything not ready yet rather than
  stopping to tessellate it.
- **Off the main thread.** Projecting the city is ~110 ms of arithmetic and needs the canvas
  size, so it cannot happen before layout. `MapPaths` touches no Skia objects, so it is built
  on `Dispatchers.Default` and the map fades in — rather than freezing the first frame.
- **Nothing allocated per frame.** The reveal gradients, the two `saveLayer` paints and the
  reveal list live in a `MapRenderCache`. A `Brush.radialGradient` builds a Skia shader when
  it is drawn, so making them per reveal per frame meant three new shaders for every lit place
  sixty times a second; every reveal in a frame shares a radius, so one cached entry serves
  all of them.

**Everything that moves is read in the draw pass, not in composition** — zoom, pan, the
breathing and the bloom are all snapshot state read inside `drawBehind`, so a pinch
invalidates drawing and nothing else. Reading any of them in the composable body instead is
the easy mistake: it recomposes the whole map on every frame of every gesture, rebuilding the
modifier chain while the user is trying to zoom.

Above 4× zoom the breathing animation stops being read at all, so the map stops redrawing
entirely: a map you are reading should hold still, and it should not re-rasterise every
building sixty times a second to pulse them by three percent.

**Gestures** live in `MapGestures.kt`, as one detector rather than Compose's
`detectTransformGestures` and `detectTapGestures` stacked on each other. That arrangement cost
three things: `detectTapGestures` consumed the down and then held every tap for the double-tap
timeout before reporting it; transform gestures do not begin until the touch slop is crossed,
which on a pinch swallows the first few millimetres and then jumps (measured: a true 2.0×
pinch reported as 1.33×); and nothing had momentum. So a second finger starts the zoom on its
very first move with no slop, a tap is reported without being held for the double-tap window,
and a one-finger drag hands its velocity to a decay so the map coasts. `MapGestureTest` pins
all of it. The detector still reports whether a tap hit anything, but `CityMapView` no longer
hit-tests: touching the map only moves the camera, and places are opened from the search box.

`GeoProjector` handles lat/lng → pixels with a local equirectangular projection (longitude
scaled by cos(latitude) so the city is not horizontally stretched). Everything is projected
once at camera scale 1; zoom and pan are a canvas transform on top, so moving around never
rebuilds a path.

`CityMapView` still knows nothing about Mumbai, Room or the dataset — it takes geometry in
lat/lng plus a place list and draws them. Swapping the source is still one line in
`AppContainer`.

### Building the assets

`app/src/main/assets/mumbai-places.tsv` (3.9 MB, 31,657 places),
`app/src/main/assets/mumbai-labels.tsv` (9 kB, 219 names) and
`app/src/main/assets/mumbai.map` (5.0 MB) are all committed, so **you do not need to run this** to
build the app. To regenerate it from a [Geofabrik](https://download.geofabrik.de/asia/india.html)
extract:

```bash
python3 -m venv .venv-osm && .venv-osm/bin/python3 -m pip install osmium
# The catalog, from OSM and the postal boundary. Re-run after changing the
# rules in build_seed.py; --report lists each category and what heads it.
# It writes a stamp into the file's header, which is how an installed app
# notices an update shipped a different catalog and re-seeds itself.
.venv-osm/bin/python3 tools/dump_pois.py western-zone-260813.osm.pbf     # ~50 s
.venv-osm/bin/python3 tools/build_seed.py --report

# The names drawn over the map: postal localities and the head of each
# category. Cheap, and depends on the catalog, so it goes straight after it.
.venv-osm/bin/python3 tools/build_labels.py

# The map itself. Must be re-run after the catalog changes: detail geometry
# is only kept near a catalogued place, and CityMapAssetTest fails if the
# places left without any climb above one in a thousand.
.venv-osm/bin/python3 tools/extract_osm.py western-zone-260813.osm.pbf   # ~65 s
.venv-osm/bin/python3 tools/build_map_asset.py
```

Pass 1 crops the extract to a Mumbai bounding box and classifies ways into render kinds.
Buildings and footpaths are kept only within 520 m of a seeded place — they are only ever
drawn inside a lit area, so keeping them city-wide would multiply the asset for geometry
no one can see, and it would let the unlit layer leak where the places are.

Pass 2 simplifies (Douglas–Peucker, tighter for buildings than for roads) and writes a
format whose only trick is that it is honest about what a map is: consecutive vertices of a
way are metres apart, so coordinates are stored as zigzag-varint deltas at 1e-6°. That is
~4.2 bytes per point instead of the sixteen a pair of doubles would cost — 867,000 points in
3.6 MB, decoded in one linear pass with no allocation per point.

### The Overture pipeline

A second, independent source for the catalog: [Overture Maps](https://overturemaps.org)
places for the Mumbai metro box. It exists because OpenStreetMap's long tail is thin — of four
Bandra/BKC landmarks checked by hand, only Colaba Market was in OSM as itself; Overture has
271,071 places in the same box against OSM's 3,191.

**This is the catalog that ships.** `app/src/main/assets/mumbai-places.tsv` is now the merged
one: 31,657 places, of which 3,191 are the OpenStreetMap catalog and 28,466 came from
Overture. The pipeline still writes to `build/` and adopting is still a deliberate copy — see
*Adopting a catalog* below.

```bash
python3 -m venv .venv-overture && .venv-overture/bin/python3 -m pip install overturemaps certifi

python3 tools/places_pipeline.py status     # what exists and what is stale
python3 tools/places_pipeline.py all        # run every stage that needs it
python3 tools/places_pipeline.py verify     # check every artefact
```

| stage | output | |
|---|---|---|
| `fetch` | `data/mumbai_places.geojson` | 309 MB, 271,071 places |
| `map` | `data/category_map.json`, `data/seed_category_map.json` | 1,306 Overture categories → 12 display buckets, and → the 6 `PlaceCategory` constants |
| `db` | `data/mumbai_places.db` | 71.7 MB SQLite + FTS5, sub-millisecond name search |
| `seed` | `build/mumbai-places-merged.tsv` | 31,657 places in the CMPL format the app already reads |
| `analyze` | stdout | coverage report over the raw extract |

Both category maps are JSON and are meant to be edited: the pipeline reads them and never
re-derives a mapping. Overture ships a second taxonomy alongside `categories.primary` — a
`taxonomy.hierarchy` path — and every one of the 1,306 categories resolves to exactly one
hierarchy root, which is what the generated mapping is derived from rather than keyword
guesswork. The `PlaceCategory` half deliberately mirrors the OSM tag sets in `build_seed.py`,
so an Overture cafe lands where an OSM cafe would.

`seed` **merges**, it does not replace. Every one of the 3,191 existing places is kept with
its id untouched, and Overture places are appended only where a same-named place is not
already within 150 m. Ids matter more than they look: `DatabaseSeeder` upserts and never
deletes, so a catalog that renamed an existing id would not replace that place, it would
duplicate it, and the user's visits and ratings would stay attached to the orphan.

`places_pipeline.py verify` re-asserts, in Python, every claim `PlaceCatalogAssetTest` makes
about the shipped asset — an address on every row, every coordinate inside Mumbai, unique
ids, all six categories present, same-named places told apart. A catalog that could not be
adopted fails there rather than in the Kotlin suite after it has been copied into place.

#### Adopting a catalog

The catalog is not the only asset that depends on the catalog, which is what makes adopting
one a three-step job rather than a copy:

```bash
python3 tools/places_pipeline.py all                        # writes build/mumbai-places-merged.tsv
python3 tools/places_pipeline.py verify                     # 20 checks, including the ones
                                                            # PlaceCatalogAssetTest makes
cp build/mumbai-places-merged.tsv app/src/main/assets/mumbai-places.tsv

.venv-osm/bin/python3 tools/build_labels.py                 # labels are the head of each category
.venv-osm/bin/python3 tools/extract_osm.py western-zone-260813.osm.pbf   # ~64 s
.venv-osm/bin/python3 tools/build_map_asset.py              # detail follows the seeded places
```

The map is the step that is easy to forget. `extract_osm.py` keeps building and footpath
detail only within `DETAIL_RADIUS_M` of a *seeded* place, and `CityMapAssetTest` asserts that
essentially every seeded place has geometry inside its lit area. Against the merged catalog,
roughly 5,933 places fell outside the detail kept for the original 3,191 — so without the
re-extract they would light an empty warm circle, and the test would fail. Re-running it took
`mumbai.map` from 3.61 MB to 5.25 MB (867k → 1.27M points). Measured end to end, the release
APK went from 4.53 MB to 7.16 MB, and decoding the catalog on the JVM costs about 33 ms.

`build_seed_overture.py` reads the *shipped* asset as its merge base, so once a merged catalog
is adopted the base is itself merged. It strips any row whose id it wrote (`ov-` prefix)
before merging, which makes re-running idempotent and means the pristine OpenStreetMap
catalog is always recoverable from the shipped one — verified: stripping the 28,466 Overture
rows reproduces the original 3,191 field-for-field and its exact stamp, `857f6f177ced7512`.

**Ordering is load-bearing.** Explore's search floats names that start with the query, keeps
file order for the rest, and takes the first 8. So the merged catalog appends Overture after
the whole existing catalog rather than interleaving it by category — every query that
returned a curated place still returns it in the same position. Interleaving buried
"Chhatrapati Shivaji Maharaj Vastu Sangrahalaya" under 133 other Chhatrapati-somethings and
failed `ExploreViewModelTest`.

One thing still worth measuring on a real device: `PlaceCatalogCodec.decode` reads the whole
asset with `readLines()` and builds one `PlaceEntity` per row before a single upsert. It was
written for 3,191 rows and says so; 31,657 is ten times that, once, on first launch. Nothing
about the format breaks — the writer here is byte-identical to `build_seed.py` — but
`--max-places` is there to trade rows for launch time.

### Visual language

- **Warm sodium light** = explored. Not a marker but a lit *area*: real streets, buildings
  and parks, fading softly back into the dark at its edge.
- **Cool cyan ring** = wishlisted. A light not yet switched on.
- **Dim slate** = undiscovered.

At the overview an explored area is barely there at all: the whole city on screen is ~25 m to
the pixel, so a 100 m reveal is about four pixels across — smaller than the 1.9 dp marker dot
sitting on it. That is the deliberate cost of the radius. **The map only tells its story once
you zoom in**, where the marker glow fades out and the lit streets take over from it.

Category hues are used only for thumbnails and small glyphs, never for status, so colour can
never be mistaken for progress. The app is deliberately **dark-only** — a light theme would
erase the entire metaphor.

---

## Implemented

- **Explore** — real OpenStreetMap Mumbai on a Compose Canvas, near-black, with the streets
  and buildings around every visited place lit in warm sodium light, and the city's postal
  localities named over the top so the dark half is still navigable. Pinch/pan/double-tap
  zoom from the whole city down to ~1 m/px, cyan rings for wishlisted, gentle breathing at
  the overview, light spreading outward when a place is newly lit. Touching the map only
  moves the camera — it selects nothing.
- **Search and record, on the map** — a search box under the progress bar finds a place by
  name or category, flies the camera to it at street level, and opens a card that marks it
  visited, scores it out of five and takes a written opinion. All three are committed
  together by one Save. The rating and the opinion are shown back read-only on the place's
  detail screen.
- **Discover** — all 31,657 places, search across name/address/description/category, four state filters,
  six category filters with counts, empty state.
- **Place details** — hero, description, address, coordinates, wishlist / navigate /
  mark-visited, visit date, undo via button and via snackbar action. The address is editable
  in place on any place, catalogued or not: OSM has a street address for about a quarter of
  them, and if you went there you know it better than the extract does.
- **Add a place** — the catalog is missing the stall that opened last month. Search for it,
  and the result list offers to add it under the name you already typed: a category, an
  optional address, and a location you set by moving the map under a ring — or by one tap on
  **use my location**, if you are standing in it. It lands marked explored, counts towards
  progress and lights the map like any other place, and no future catalog update can take it
  away. Yours can be removed again; the catalog's cannot, because a deletion would come back
  on the next regeneration.
- **Photos** — add your own pictures to any place from the system photo picker, several at a
  time, and open one full screen. They are the only real imagery in the app. The bytes are
  copied into the app's own storage on import, downscaled to 1600 px and turned upright from
  their EXIF tag, so a photo added today still opens next year whatever happens to the
  original in the gallery.
- **Wishlist** — split into "to explore" and "already explored", navigate and remove inline.
- **Progress** — overall percentage, explorer level with title and distance to next level,
  per-category progress, five achievements.
- **Navigation** — `NavigationLauncher` tries `google.navigation:` (turn-by-turn), then
  `geo:` (any maps app, including offline ones), then a `https://google.com/maps/dir/` web
  fallback, reporting which rung handled it.
- **Offline** — no `INTERNET` permission is requested at all. Everything is local. The one
  permission the app does declare is location, asked for only when you tap "use my location"
  while adding a place, and satisfied by GPS — satellites, not a network — which is exactly
  why the platform `LocationManager` is used rather than Play services' fused provider.

### Adding a place, and the coordinate it lands on

Two things had to be true before the ring over the map could be trusted, and
neither was obvious from looking at the screen.

**At the overview the map cannot pan at all.** `MapCamera.constrain` clamps the
offset into `viewport - viewport * scale .. 0`, which at `MIN_SCALE = 1` is
`0f..0f` — correct, because the whole city exactly fills the viewport and there
is nowhere to go. But it means anything reading "the coordinate under the ring"
reads *one fixed coordinate forever*, so every place added without zooming in
first was written at the same latitude and longitude. `CityMapView` now zooms to
14× when the form opens, unless the user has already zoomed past it; a camera
they chose is left alone. `MapCameraTest` pins both halves.

**The ring is not in the middle of the screen.** The form covers the bottom, so
a ring in the dead centre would be behind it — you would be aiming something you
cannot see. It sits at `PICK_ANCHOR_FRACTION`, a third of the way down, and both
`ExploreScreen` and the map read that one constant so the ring and the reported
point cannot drift apart.

The same invariant is what makes **use my location** subtle. A fix does *not*
set the pin: it asks the camera to fly, and whatever the ring reports on arrival
is what gets saved — so the two can never disagree. The fly request carries a
token (`FlyTarget`), because pressing the button twice from the same doorway
produces the same coordinate, a `StateFlow` conflates equal values, and without
it the second press moved nothing while still reporting success. That left the
ring showing one place and Save writing another, which is exactly the failure
the anchor work exists to prevent.

## Verification

192 unit tests, all passing, none requiring a device (`./gradlew :app:testDebugUnitTest`):

| Suite | Tests | Covers |
|---|---|---|
| `ExplorationSummarizerTest` | 12 | percentages, levels, category rollups, achievement derivation |
| `PlaceRepositoryTest` | 20 | seeding, visit/wishlist toggles, ratings and notes, sparse rows, reactivity |
| `UserPlaceTest` | 12 | adding, removing and addressing your own places; no catalog update can take one away |
| `PlaceCatalogAssetTest` | 16 | the real shipped catalog — ids, addresses, categories, and the codec's failure modes |
| `PersistenceAcrossRestartTest` | 3 | on-disk DB closed and reopened; re-seed never clears state |
| `MigrationTest` | 8 | real v1, v2 and v3 files opened through the app's own builder; state survives 1 → 2 → 3 → 4 |
| `GeoProjectorTest` | 12 | orientation, centring, aspect ratio, the inverse used to drop a pin, every place in bounds |
| `CityMapAssetTest` | 10 | the real shipped map asset — see below |
| `MapCameraTest` | 12 | pinch anchoring, drift over 30 steps, clamping, pan bounds, and why picking a location has to zoom first |
| `MapGestureTest` | 8 | pinch loses nothing to slop, taps are not delayed, drag slop, fling velocity |
| `MapLabelAssetTest` | 11 | the real shipped label list — all 89 areas present, in Mumbai, no two stacked |
| `LabelPlacementTest` | 11 | which names give way when they collide, that an off-screen one takes no space, and that a place name never covers its own marker |
| `PlacePhotoTest` | 12 | importing, ordering, deleting, the per-place cap, and that removing a place takes its photo files too |
| `RevealMaskTest` | 3 | the one-disc mask fast path is pixel-identical to the layer it replaces |
| `NavigationLauncherTest` | 5 | all three fallback rungs, no-handler case, locale-safe coordinates |
| `ScreenInteractionTest` | 14 | Compose UI on Robolectric — search, filters, toggles, progress |
| `ExploreViewModelTest` | 23 | Explore state including search, saving a visit, and the whole use-my-location path against a fake fix |

`CityMapCodec` takes an `InputStream` rather than an `AssetManager` specifically so
`CityMapAssetTest` can assert against the same 5.0 MB that ships in the APK, not a fixture.
It checks that it decodes, that it is framed on Mumbai, that every seeded place falls inside
it — and, the one that matters, that **essentially every place has street-level detail within
its lit radius**, so marking one visited lights up something worth looking at rather than an
empty warm circle. At 100 m across the merged 31,657, 462 have no buildings or paths in range
(ceiling 633) and 52 have nothing at all (ceiling 158): Madh Chowpatty, Manori Aum Beach, the
Racecourse Ground, Vashi Bridge — sand, grass and water, where OpenStreetMap is right that
there is nothing there. The thresholds are ceilings rather than zeroes for that reason, and
are still tight enough that a broken detail radius in `extract_osm.py` — which would put
*thousands* there — fails the build. The flat count sits at about three-quarters of its
ceiling, so a much larger catalog would need the detail radius revisited.

`PlaceCatalogCodec` is arranged the same way, and `PlaceCatalogAssetTest` asserts against the
real 31,657 rows: unique ids, an address on every one, and that the address is what tells the
6,501 places sharing a name with another apart — 1.4% of them are not separated even by that,
against a 15% allowance.

Also verified by hand on an Android 15 arm64 emulator with GPU acceleration: install, cold
start, seed, all four tabs, place detail, mark-visited through the real UI (confirmed landing
in SQLite with a timestamp), **persistence across a cold start**, and the map itself at both
the overview and street level. Draw-pass timings on that emulator (which is several times
slower than a real phone): **0.8 ms p50 at the overview, 9.9 ms p50 / 22 ms p90 at 16× zoom
over a lit district**, and a one-off 110 ms projection that runs off the main thread.

## Known limitations

- **Ordering is "best mapped", not "best".** Nothing is curated away any more — every place
  OSM has mapped inside Mumbai ships — so `curated.tsv` and `score()` no longer decide who is
  in, only what a category list opens with. That ranking is evidence in the data: an
  encyclopaedia entry, a heritage listing, how much detail a mapper added, how big a park is.
  For forts, museums and beaches it ranks well. For food there is no such signal in OSM and
  there never will be, so past the 69 curated names the restaurants and cafes are ordered by
  how thoroughly somebody mapped them.
- **An uncapped catalog is a directory as much as a guide.** 1,120 restaurants and 728 hidden
  gems is not a list anyone reads top to bottom. That is the trade for never having to type in
  a place that exists: search and the address carry the weight the curation used to.
- **Franchise outlets are filtered, by evidence rather than by a blocklist.** 446 of them, on
  two signals: OSM's own `brand` tag, and a name at three or more separate sites. Both are
  scoped to the food categories first, because repetition means franchise for a coffee shop
  and means nothing at all for a temple — the catalog keeps its 15 Hanuman Mandirs and 9 BMC
  Parks. A fuzzy pass catches the misspelled tail (`Dominoes`, `Baskin Robins`, `McDonalds`),
  which is mapped once each and carries no brand tag. What survives is deliberate: "Crafters
  above KFC" is a bar above a KFC and is somewhere to go.
- **Eleven curated names go unresolved**, and that is the pipeline working. Some are absent
  from the extract (Gajalee, Ram Ashraya, Grandmama's Cafe); others had a match and it was
  refused — Kanheri Caves was landing 800 m away on "Kanheri Caves Road", Colaba Causeway on
  "Colaba Bus Station", Girgaon Chowpatty on the neighbourhood called "Girgaon". A place with
  no coordinate is better than a place with the wrong one, so these are dropped and printed
  on every run rather than resolved approximately. It costs less than it did: unresolved now
  only means the name loses its ranking bonus, not that the place is missing.
- **Addresses are mostly a locality and a pin code.** OSM carries a street address for 784 of
  the 3,191; the rest get "Bandra West, Mumbai 400050" from the postal boundary. That tells
  311 of the 334 same-named places apart, and leaves 23 that only a street address would
  separate. Editing one by hand is the answer the app ships; deriving one from the nearest
  named road is the answer it should.
- **Descriptions are assembled from tags, so they are dry.** "Fort in Sion (400022). Has an
  encyclopaedia entry." is what the data supports. OSM's own `description` tag is deliberately
  ignored: in Mumbai it is mostly postal addresses and phone-book entries, not prose.
- **The postal boundary defines Mumbai for generated places only.** `boundary.geojson` is 89
  delivery areas — a good definition of the city, a bad one of "reachable from it". Elephanta
  Caves sits 4.7 km outside the nearest polygon, further out than Ghodbandar Fort in Thane,
  so no distance rule separates them. Generated places must fall inside (plus
  `BOUNDARY_TOLERANCE_M` for the slivers between rounds); curated ones bypass the check,
  which is how Elephanta is in the app and Ghodbandar is not.
- **Name matching between the curated list and OSM is fuzzy, and fuzzy is never done.** Three
  tiers — same name, one name inside the other, enough words in common — which is what pairs
  "Asiatic Society Library" with OSM's "Asiatic Society Town Hall". It also once paired
  "Gajalee" with a shop mapped as "G". Every pairing is exclusive and printed by `--report`,
  and it is the part of this pipeline most worth checking by eye after a re-run.
- **No images ship with the catalog.** `imageUrl` is null throughout and the UI renders a
  generated category tile instead; the only real pictures in the app are the ones the user
  adds themselves. The field and the call site exist for a dataset that bundles imagery.
- **Photos are copied, not referenced, and that costs disk.** A ~1600 px JPEG per photo, a few
  hundred kilobytes each. The alternative — keeping the `content://` URI the picker returns —
  is smaller and does not work: that grant lasts as long as the process, and a photo that
  lives in the gallery leaves this app the day the user tidies up their camera roll.
- **A photo can be orphaned by a process death mid-delete.** Both delete paths read the file
  names before the rows go, so nothing leaks in the ordinary course of things; being exact
  about the pathological case would mean a startup sweep, and the Application goes out of its
  way not to open the database at launch.
- **The location shortcut needs GPS, not a network.** The platform `LocationManager` is used
  rather than Play services' fused provider, because this project has no Play services
  dependency and should not need one on the device. Indoors that can mean no fix inside the
  twelve-second timeout, which reports itself and leaves the ring where the user put it. A
  cached fix under two minutes old is accepted instantly rather than waiting for satellites.
- **The lit radius is fixed at 100 m**, the same for a beach and a temple courtyard. A lit
  area is a circle because a circle needs no data; what it *should* be is the walkable extent
  of the place, which nothing in the dataset knows. It has come down twice — 420 m, 340 m,
  100 m — because at 3,191 places wide discs merge into one smear as soon as you explore a
  neighbourhood properly. At 100 m what lights up is the building and the street outside it, so
  a district reads as a constellation of separate points rather than one glow, at the price of
  the overview showing almost nothing.
- **The map asset carries far more detail than the light can reach.** `DETAIL_RADIUS_M` is
  still 440 m while the reveal is 100 m, which is deliberate: it keeps the radius a
  one-constant change instead of a two-minute regeneration. It also means most of the 3.6 MB
  of building geometry in the APK can never be seen at the current setting. Dropping it to
  ~200 m would cut the asset substantially, and is worth doing once the radius settles.
- **Detail geometry only exists near the catalogued places**, which now covers most of the
  built-up city — but a place *you* add can land outside it, and its lit area will then show
  ground and arterials with no streets or buildings. Nothing can fix that at runtime: the
  buildings are not in the asset to draw. Regenerating from `tools/` picks it up only if the
  place is also in OSM.
- **OSM building coverage is uneven.** Mumbai is well mapped in the south and patchier
  further out, so how rich a lit area looks varies by neighbourhood. That is the data, not
  the renderer.
- **Only Mumbai is in the asset.** The bounding box is hardcoded in `tools/extract_osm.py`;
  a second city means a second extract and a second asset file.
- **The map does not rotate or tilt**, and only two tiers of name are drawn — 89 postal
  localities and 130 major places. No street names, so a lit area is still a shape you
  recognise more than one you can read at the level of "which road is that".
- **ExploreScreen has no Compose UI test.** Its map runs a permanent breathing animation, so
  the Compose test clock never reaches idle and assertions would hang. Its state is covered
  by `ExploreViewModelTest` and its geometry by `GeoProjectorTest`; the screen itself was
  verified manually on the emulator. Testing it properly needs either an idling-resource
  opt-out or a way to disable the infinite transition under test.
- **Filtering is in-memory** over the whole catalog. Correct and fast at 3,191 places; a
  city with tens of thousands would need it pushed into SQL.
- **Single city, hardcoded.** `MumbaiSeed.CITY_ID` is the default argument on every
  ViewModel. Multi-city means threading a selected city id through, not restructuring.
- **Schema version 4**, `exportSchema = true`, schemas written to `app/schemas`. Every
  migration so far is additive — `MIGRATION_1_2` added `rating` and `note`, `MIGRATION_2_3`
  added `address`, `isUserAdded` and `catalogStamp`, `MIGRATION_3_4` created `place_photos` —
  so no table has ever been rebuilt and no row rewritten. A change that is not additive still
  needs a real migration or a documented wipe.
- **Search is over the catalog, not the map.** The shipped geometry carries no names and the
  app requests no INTERNET permission, so the search box can only find catalogued places —
  which is why anything missing can be added by hand.
  Typing a Mumbai street or a place that is not in the dataset finds nothing, by design.

## Suggested next steps

1. Rank the catalog for reading, now that it is not curated for size. 1,120 restaurants
   ordered by how well they happen to be mapped is the weakest part of the app; sorting a
   category list by distance from where the map is looking would beat it outright, and needs
   no data the app does not have.
2. Push a street address onto the ~2,400 places that only carry a locality and pin code.
   `dump_pois.py` already reads every named road in the box and throws the names away; the
   nearest one is a better address line than "Andheri East, Mumbai 400069", and it is what
   would separate the 23 same-named places the postal area cannot.
3. Street names inside lit areas. The labels are the one thing that would turn "I recognise
   this" into "I remember this", and OSM already carries them.
4. Catalog imagery. `imageUrl` is still null throughout; OSM's `wikimedia_commons` and
   `image` tags are on some of the seeded places already and are the obvious first source,
   now that there is a photo strip to put them in.
5. Shape the lit area to the place rather than to a fixed circle — walkable extent, or at
   least a radius that varies by category.
6. Optional GPS check-in as a *shortcut* for marking visited, never a requirement.
7. Multi-city once there is a second dataset worth having.
