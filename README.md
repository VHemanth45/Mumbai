# City Memory

A personal city-exploration app for Android. Your city starts dark; every place you explore
lights up — not as a dot, but as the real streets and buildings around it, on real
OpenStreetMap geometry. Google Maps does the navigating — City Memory keeps the memory.

**Discover → Wishlist → Explore → Light Up → Track Progress**

MVP scope: one city (Mumbai), 80 hand-authored places, fully offline.

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
./gradlew :app:testDebugUnitTest # run all 94 tests (no device needed)
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
(18 MB debug → 2.7 MB release); `MainActivity` declares `configChanges` so a rotation does not
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
│   ├── local/{entities,dao,database,seed}   Room + the mock Mumbai catalog
│   ├── map/                    OsmCityGeometryProvider + CityMapCodec (real OSM),
│   │                           MockMumbaiGeometryProvider (fallback outline)
│   ├── mapper/                 entity ⇄ domain
│   └── repository/             PlaceRepositoryImpl
├── ui/
│   ├── map/                    CityMapView, MapPaths, MapCamera, MapStyle, GeoProjector
│   ├── screens/{explore,discover,wishlist,progress,place}   Screen + ViewModel per feature
│   ├── components/             PlaceCard, PlaceThumbnail, GlowProgressBar, states
│   ├── navigation/             routes + bottom destinations
│   └── theme/                  dark-only palette, type, category visuals
├── di/AppContainer.kt          the whole dependency graph, ~30 lines
└── util/NavigationLauncher.kt  external maps hand-off

tools/                          the map pipeline (Python, build-time only)
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

| `cities` | `places` | `user_place_state` |
|---|---|---|
| id, name, country | id, cityId, name, category, description, latitude, longitude, imageUrl, displayOrder | placeId, isVisited, isWishlisted, visitedAt, wishlistedAt |

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
in warm sodium light and masked down to a soft-edged disc 420 m across. So an explored place
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

**Getting 387,000 points to 60 fps** took five things, in `MapPaths` and `CityMapView`:

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
very first move with no slop, a tap that lands on a place is reported immediately and never
waits to become a zoom, and a one-finger drag hands its velocity to a decay so the map coasts.
`MapGestureTest` pins all of it.

`GeoProjector` handles lat/lng → pixels with a local equirectangular projection (longitude
scaled by cos(latitude) so the city is not horizontally stretched). Everything is projected
once at camera scale 1; zoom and pan are a canvas transform on top, so moving around never
rebuilds a path.

`CityMapView` still knows nothing about Mumbai, Room or the dataset — it takes geometry in
lat/lng plus a place list and draws them. Swapping the source is still one line in
`AppContainer`.

### Building the map asset

`app/src/main/assets/mumbai.map` (1.8 MB) is committed, so **you do not need to run this** to
build the app. To regenerate it from a [Geofabrik](https://download.geofabrik.de/asia/india.html)
extract:

```bash
python3 -m venv .venv-osm && .venv-osm/bin/python3 -m pip install osmium
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
~4.6 bytes per point instead of the sixteen a pair of doubles would cost — 387,000 points in
1.8 MB, decoded in one linear pass with no allocation per point.

### Visual language

- **Warm sodium light** = explored. Not a marker but a lit *area*: real streets, buildings
  and parks, fading softly back into the dark at its edge.
- **Cool cyan ring** = wishlisted. A light not yet switched on.
- **Dim slate** = undiscovered.

At the overview an explored area is only a glow, because 420 m is seventeen pixels wide when
the whole city is on screen; the marker glow fades out as you zoom in and the lit streets
take over from it.

Category hues are used only for thumbnails and small glyphs, never for status, so colour can
never be mistaken for progress. The app is deliberately **dark-only** — a light theme would
erase the entire metaphor.

---

## Implemented

- **Explore** — real OpenStreetMap Mumbai on a Compose Canvas, near-black, with the streets
  and buildings around every visited place lit in warm sodium light. Pinch/pan/double-tap
  zoom from the whole city down to ~1 m/px, cyan rings for wishlisted, gentle breathing at
  the overview, light spreading outward when a place is newly lit, tap-a-light-to-peek card.
- **Discover** — all 80 places, search across name/description/category, four state filters,
  six category filters with counts, empty state.
- **Place details** — hero, description, coordinates, wishlist / navigate / mark-visited,
  visit date, undo via button and via snackbar action.
- **Wishlist** — split into "to explore" and "already explored", navigate and remove inline.
- **Progress** — overall percentage, explorer level with title and distance to next level,
  per-category progress, five achievements.
- **Navigation** — `NavigationLauncher` tries `google.navigation:` (turn-by-turn), then
  `geo:` (any maps app, including offline ones), then a `https://google.com/maps/dir/` web
  fallback, reporting which rung handled it.
- **Offline** — no `INTERNET` permission is requested at all. Everything is local.

## Verification

94 unit tests, all passing, none requiring a device (`./gradlew :app:testDebugUnitTest`):

| Suite | Tests | Covers |
|---|---|---|
| `ExplorationSummarizerTest` | 12 | percentages, levels, category rollups, achievement derivation |
| `PlaceRepositoryTest` | 13 | seeding, visit/wishlist toggles, sparse rows, reactivity |
| `PersistenceAcrossRestartTest` | 3 | on-disk DB closed and reopened; re-seed never clears state |
| `GeoProjectorTest` | 10 | orientation, centring, aspect ratio, all 80 places in bounds |
| `CityMapAssetTest` | 10 | the real shipped asset — see below |
| `MapCameraTest` | 10 | pinch anchoring, drift over 30 steps, clamping, pan bounds |
| `MapGestureTest` | 8 | pinch loses nothing to slop, taps are not delayed, drag slop, fling velocity |
| `RevealMaskTest` | 3 | the one-disc mask fast path is pixel-identical to the layer it replaces |
| `NavigationLauncherTest` | 5 | all three fallback rungs, no-handler case, locale-safe coordinates |
| `ScreenInteractionTest` | 14 | Compose UI on Robolectric — search, filters, toggles, progress |
| `ExploreViewModelTest` | 6 | Explore state, which its own UI test cannot reach (see below) |

`CityMapCodec` takes an `InputStream` rather than an `AssetManager` specifically so
`CityMapAssetTest` can assert against the same 1.8 MB that ships in the APK, not a fixture.
It checks that it decodes, that it is framed on Mumbai, that every seeded place falls inside
it — and, the one that matters, that **every one of the 80 places has street-level detail
within its lit radius**, so marking any of them visited lights up something worth looking at
rather than an empty warm circle. A regenerated asset that lost its buildings fails the build
instead of shipping as a dark screen.

Also verified by hand on an Android 15 arm64 emulator with GPU acceleration: install, cold
start, seed, all four tabs, place detail, mark-visited through the real UI (confirmed landing
in SQLite with a timestamp), **persistence across a cold start**, and the map itself at both
the overview and street level. Draw-pass timings on that emulator (which is several times
slower than a real phone): **0.8 ms p50 at the overview, 9.9 ms p50 / 22 ms p90 at 16× zoom
over a lit district**, and a one-off 110 ms projection that runs off the main thread.

## Known limitations

- **Mock data.** 80 real Mumbai places, but coordinates are hand-entered and approximate —
  good enough to drop a maps app on the right spot, not survey-grade. Descriptions are
  written, not sourced.
- **No images.** `imageUrl` is null throughout and the UI renders a generated category tile
  instead. The field and the call site exist for a dataset that bundles real imagery.
- **The lit radius is fixed at 420 m**, the same for a beach and a temple courtyard. A lit
  area is a circle because a circle needs no data; what it *should* be is the walkable extent
  of the place, which nothing in the dataset knows.
- **Detail geometry only exists near the 80 seeded places.** Adding an 81st place needs the
  asset regenerated (`tools/`) or its lit area will show ground and arterials but no streets
  or buildings. `CityMapAssetTest` fails loudly if that happens, but it is a build-time
  coupling between the catalog and the asset that a larger dataset would want to remove.
- **OSM building coverage is uneven.** Mumbai is well mapped in the south and patchier
  further out, so how rich a lit area looks varies by neighbourhood. That is the data, not
  the renderer.
- **Only Mumbai is in the asset.** The bounding box is hardcoded in `tools/extract_osm.py`;
  a second city means a second extract and a second asset file.
- **The map does not rotate or tilt**, and there are no labels — no street or place names are
  drawn, so a lit area is a shape you recognise rather than one you can read.
- **ExploreScreen has no Compose UI test.** Its map runs a permanent breathing animation, so
  the Compose test clock never reaches idle and assertions would hang. Its state is covered
  by `ExploreViewModelTest` and its geometry by `GeoProjectorTest`; the screen itself was
  verified manually on the emulator. Testing it properly needs either an idling-resource
  opt-out or a way to disable the infinite transition under test.
- **Filtering is in-memory** over the whole catalog. Correct and instant at 80 places; a
  city with tens of thousands would need it pushed into SQL.
- **Single city, hardcoded.** `MumbaiSeed.CITY_ID` is the default argument on every
  ViewModel. Multi-city means threading a selected city id through, not restructuring.
- **No migrations.** Schema version 1, `exportSchema = true`, schemas written to
  `app/schemas`. The first real dataset change needs a migration or a documented wipe.

## Suggested next steps

1. Real dataset — replace `MumbaiSeed`, add a Room migration, wire images. Regenerate the map
   asset alongside it so the new places have detail geometry.
2. Street names inside lit areas. The labels are the one thing that would turn "I recognise
   this" into "I remember this", and OSM already carries them.
3. A "recently lit" trail, and a fly-to animation when a place is opened from another screen
   (`MapCamera.centerOn` exists and is tested; nothing calls it yet).
4. Shape the lit area to the place rather than to a fixed circle — walkable extent, or at
   least a radius that varies by category.
5. Optional GPS check-in as a *shortcut* for marking visited, never a requirement.
6. Multi-city once there is a second dataset worth having.
