# Swiss Step

An Android app that tracks the paths you actually walk, bike, run, or take transit on, and draws them onto an offline map of Switzerland as you go.

Built as a personal project while studying at ETH Zurich, to explore offline map rendering, background location tracking, and Jetpack Compose.

## What it does

- **Live GPS tracking** — a foreground service records your location in the background (even with the app closed) and classifies your movement in real time as still, walking, running, biking, or transport (car/train/tram), based on a smoothed speed model.
- **Automatic path drawing** — recorded GPS points are snapped onto the nearest known street/path/rail segment and merged into continuous colored chains on the map, colored by movement type.
- **Offline maps** — renders from local [Mapsforge](https://github.com/mapsforge/mapsforge) `.map` files, so it works without a data connection. Map data is preprocessed from OpenStreetMap exports (`process_paths.py`) into a lightweight segment index used for snapping and path storage.
- **Route recording & replay** — start/stop recording a session as a named route, then replay it later with an animated progress indicator.
- **Import/export** — save routes to a file or share them, and import routes recorded elsewhere.
- **Customization** — per-movement-type path colors, toggleable raw GPS point overlay, and other display preferences.

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
├── paths/        # Static path/segment data model, loading, geometry helpers
├── tracking/     # Location foreground service, movement classification, live state
└── ui/
    ├── home/     # Map screen, its ViewModel, and Compose side-effects
    └── preferences/  # Settings screen
```

## Getting started

1. Clone the repo and open it in Android Studio (Giraffe or newer).
2. The app needs offline map data in `app/src/main/assets/` (`switzerland.map`, `zurich.map`, and the preprocessed `utilized_paths_0.json` segment index) to render anything or snap GPS points to paths. These are large binary/data files — see `app/src/main/assets/process_paths.py` for how the segment index is generated from an OpenStreetMap export.
3. Build & run like any Android project:
   ```
   ./gradlew assembleDebug
   ```
4. Grant location permission on first launch. Background tracking requires "Allow all the time" location permission on Android 10+.

## Running tests

```
./gradlew testDebugUnitTest
```

Unit tests cover the pure logic that doesn't need a device: segment-distance geometry (`paths/PathUtilsTest.kt`) and the movement classification state machine (`tracking/MovementClassifierTest.kt`).

## Status

Actively developed as a personal/student project. Current focus areas: map data coverage beyond Zurich, and onboarding/loading-state UX.

## Development note

I designed and built this app; Claude (Anthropic) was used to assist with documentation, targeted bug fixes, and specific architecture/testing improvements.

## License

© 2026 Anja Pawlowski. All rights reserved.

This is a personal project shared publicly for portfolio purposes. No license is granted to use, copy, modify, or redistribute this code without explicit permission from the author.
