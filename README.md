# Swiss Step

An Android app that tracks the paths you actually walk, bike, run, or take transit on, and draws them onto an offline map of Switzerland as you go.

Built as a personal project to explore offline map rendering, background location tracking, and Jetpack Compose.

## What it does

- **Live GPS tracking**: a foreground service records your location in the background (even with the app closed) and classifies your movement in real time as still, walking, running, biking, or transport (car/train/tram), based on a smoothed speed model.
- **Automatic path drawing**: recorded GPS points are snapped onto the nearest known street/path/rail segment and merged into continuous colored chains on the map, colored by movement type.
- **Offline maps**: renders from a local [Mapsforge](https://github.com/mapsforge/mapsforge) `.map` file covering all of Switzerland, so it works without a data connection. The walking/biking/transit path network is preprocessed from an OpenStreetMap extract (`tools/process_paths.py`) into a SQLite database, grid-indexed the same way the runtime segment index is, and paged into memory on demand around the user's current position rather than loaded all at once.
- **Route recording & replay**: start/stop recording a session as a named route, then replay it later with an animated progress indicator.
- **Import/export**: save routes to a file or share them, and import routes recorded elsewhere.
- **Customization**: per-movement-type path colors, toggleable raw GPS point overlay, a dark mode (switches both the app UI and the offline map's render theme), and other display preferences.

## How it works

A foreground service polls `FusedLocationProvider` for GPS updates, at full rate while the app's open and dropping to a slower background mode (or stopping entirely) once it's not, depending on whether a route is actively being recorded.

Each point's speed gets smoothed over a short window and run through a hysteresis state machine (`MovementClassifier`) that decides between still/walking/running/biking/transport. It needs several consecutive readings to agree before actually switching states, so stopping at a crosswalk or a quick downhill sprint doesn't make the classification flicker back and forth.

From there the point gets snapped onto the nearest segment of the walking/biking/transit network. That lookup (`SegmentIndex`) is grid-indexed and backed by `switzerland_paths.db`, and only pages in the grid cells near wherever you currently are instead of loading the whole country's segment graph into memory at once.

Snapped points get merged into continuous, per-movement-type "chains" (`PathStorage`), cached per zoom level (`PathOverlayLayer`) so scrolling around an already-recorded route doesn't mean recomputing its geometry every frame.

All of that is drawn on top of an offline Mapsforge map built from `switzerland.map`, styled by an XML render theme that swaps between a light and dark variant with the app's theme preference, with everything outside Switzerland's border tinted so the recordable area stays visually obvious.

Replaying a saved route just re-feeds its stored GPS points back through the same classify → snap → chain pipeline used for live tracking, minus writing anything to the foreground service's live session.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3) for UI
- **AndroidViewModel** + `mutableStateOf`/`StateFlow` for state management
- **Mapsforge** for offline map rendering
- **kotlinx.serialization** for route/path persistence
- A **foreground `Service`** for background location tracking, with battery-optimization-aware fallbacks between foreground/background update modes
- Google Play Services `FusedLocationProvider` for location updates

## Project structure

```
app/src/main/java/io/github/pwlski04/swissstep/
├── chains/       # Path storage, merging recorded GPS points into drawable "chains"
├── map/          # Mapsforge map view setup, overlays, camera/centering helpers
├── paths/        # Segment data model, windowed SQLite-backed loading, geometry helpers
├── tracking/     # Location foreground service, movement classification, live state
└── ui/
    ├── home/     # Map screen, its ViewModel, and Compose side-effects
    └── preferences/  # Settings screen
```

## Getting started

1. Clone the repo and open it in Android Studio (Giraffe or newer). The repo uses [Git LFS](https://git-lfs.com/) for the large bundled map/data files — install it (`git lfs install`) before cloning, or run `git lfs pull` afterward if you already cloned without it.
2. The app needs two bundled assets in `app/src/main/assets/` to render anything or snap GPS points to paths: `switzerland.map` (Mapsforge render data) and `switzerland_paths.db` (the segment index). Both are already committed via Git LFS, so a normal clone is enough — no separate download step.
3. Build & run like any Android project:
   ```
   ./gradlew assembleDebug
   ```
4. Grant location permission on first launch. Background tracking requires "Allow all the time" location permission on Android 10+.

### Regenerating `switzerland_paths.db`

If you need to rebuild the segment index (e.g. to pick up fresher OSM data):

```
pip install osmium
curl -L -o switzerland-latest.osm.pbf https://download.geofabrik.de/europe/switzerland-latest.osm.pbf
python tools/process_paths.py switzerland-latest.osm.pbf app/src/main/assets/switzerland_paths.db
```

This reads the whole OSM extract (walking/biking/transit-relevant ways only) and writes a SQLite database of pre-split, grid-indexed segments — takes a few minutes and produces a ~1.2GB file. `switzerland.map` (Mapsforge's own offline render data) is a separate download from [OpenAndroMaps](https://www.openandromaps.org/en/downloads/europe) and doesn't need regenerating unless you want fresher render data too.

## Running tests

```
./gradlew testDebugUnitTest
```

Unit tests cover the pure logic that doesn't need a device: segment-distance geometry (`paths/PathUtilsTest.kt`), the windowed segment cache's loading/eviction behavior (`paths/SegmentIndexTest.kt`), and the movement classification state machine (`tracking/MovementClassifierTest.kt`).

## Status

Actively developed as a personal/student project. Current focus areas: onboarding/loading-state UX and tutorial screens.

## Development note

I designed and built this app; Claude (Anthropic) was used to assist with documentation, targeted bug fixes, and specific architecture/testing improvements.

## License

© 2026 Anja Pawlowski. All rights reserved.

This is a personal project shared publicly for portfolio purposes. No license is granted to use, copy, modify, or redistribute this code without explicit permission from the author.
