# memory-android

## Environment
- Runs as user `dev` on plowshareshome. No sudo, ever. Do not attempt to install system packages.
- JDK 17 via mise. Android SDK at ~/android-sdk. Gradle comes from ./gradlew only (pinned 9.5.0).
- Package: com.example.memory. compileSdk/targetSdk 37, minSdk 24. Room + Compose + Navigation, KSP.

## Commands
- Build:            ./gradlew assembleDebug
- Unit tests:       ./gradlew testDebugUnitTest
- Instrumented:     ./gradlew connectedDebugAndroidTest   (needs a booted emulator or attached device)

## Booting the emulator (do this yourself before instrumented tests)
    emulator -avd memory-test-37 -no-window -no-audio -gpu swiftshader_indirect &
    adb wait-for-device
    adb shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 1; done'
    adb devices    # must read "device", not "offline"

## Working agreement
- Work on a branch named feature/<short-name>. Never work directly on main.
- Definition of done: assembleDebug, testDebugUnitTest and connectedDebugAndroidTest all pass.
- Commit when green, with a message explaining the why. NEVER push. Karl pushes after review.
- Never read, write, or reference the signing keystore or ~/.gradle/gradle.properties.
- Never commit keystore files, gradle.properties, or anything under keys/.

## Stop and ask before
- Any change to the Room schema or entity classes (this app holds real notes — data loss is the
  one unrecoverable failure). Propose the migration explicitly.
- Adding any third-party dependency.
- Changing the navigation structure or the shape of the data model.
- Anything touching signingConfigs, versionCode, or the release build type.
- Any point where two reasonable designs exist and the choice constrains future features.

## Do not ask about
Formatting, naming, refactors internal to a file, test structure, or anything the tests can settle.

## Target device reality
The install target is a Pixel 6a (test) and a Pixel 10a (daily) running GrapheneOS: NO Google Play
Services. The emulator uses a google_apis image, so a Play-dependent library will pass on the
emulator and fail on the phone. Do not introduce any dependency on Play Services, Firebase, or
GMS-backed APIs.

## Known state
- Do not remove android.disallowKotlinSourceSets=false from gradle.properties. 
  It is required for KSP under AGP 9's built-in Kotlin — verified Sept 2 2026 
  that removing it fails configuration. Re-test only when upgrading AGP or KSP, and only if I ask.
