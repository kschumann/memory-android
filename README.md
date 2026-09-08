# My Memory

A simple, private note-taking app for Android. Notes are organized into lists ("memories"), reorderable by drag, and backed by a local Room database with automatic backup to a folder you choose.

Built for [GrapheneOS](https://grapheneos.org/) devices with no Google Play Services — the app has no dependency on Play Services, Firebase, or any GMS-backed API.

## Features

- **Lists and notes** — create lists, add notes to each one, drag to reorder within a list.
- **List View** — a flattened, cross-list view of every note (toggle from the Home screen's settings menu), independently reorderable from each note's position within its own list.
- **Swipe actions** — swipe a note left to delete (with confirmation), or right to archive (no confirmation). Archived notes appear below a divider at the bottom of the list and restore with a long-press.
- **Auto-backup** — every change is exported as JSON to a folder you grant access to once, debounced so rapid edits don't spam writes.

## Tech stack

- Kotlin, Jetpack Compose, Navigation Compose
- Room (with KSP) for local persistence
- No third-party network or cloud dependencies

## Building

See `CLAUDE.md` for the full development environment, commands, and working agreement. In short:

```
./gradlew assembleDebug              # build
./gradlew testDebugUnitTest          # unit tests
./gradlew connectedDebugAndroidTest  # instrumented tests (needs a booted emulator/device)
```

Package: `com.example.memory` · minSdk 24 · compileSdk/targetSdk 37
