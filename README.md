# CineMotion Editor (Android / Kotlin)

A native Android starting project for a cinematic montage editor. It uses Jetpack Compose for the UI and AndroidX Media3 Transformer for MP4 rendering.

## What this version implements
- Native Android document picker for selecting multiple photos/videos.
- Timeline list with move up, move down, and remove controls.
- Adjustable duration for still photos.
- Optional background music selection.
- Media3 composition export to H.264/AAC MP4.
- Export is copied into `Movies/CineMotion` through Android MediaStore.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact.

## Important scope note
This is a working MVP foundation, not yet a full professional editor. Crossfade transitions between clips are not implemented because Media3 Composition currently does not support crossfading tracks. This version does not yet provide a rendered preview of the complete timeline, beat detection, editable text overlays, trimming handles, or a multi-track interactive timeline. Do not describe those features as complete until implemented and tested.

## File map
- `settings.gradle.kts`: project and module names.
- `build.gradle.kts`: root Android/Kotlin plugin versions.
- `gradle.properties`: Gradle and AndroidX settings.
- `app/build.gradle.kts`: app SDK, Compose and Media3 dependencies.
- `app/src/main/AndroidManifest.xml`: app entry point.
- `app/src/main/res/values/styles.xml`: Android theme.
- `app/src/main/java/com/cinemotion/editor/MainActivity.kt`: app screen, picker, timeline controls.
- `app/src/main/java/com/cinemotion/editor/MediaClip.kt`: timeline clip model.
- `app/src/main/java/com/cinemotion/editor/MediaEditor.kt`: export and save-to-Movies logic.
- `.github/workflows/build-apk.yml`: cloud build workflow.

## Build with GitHub Actions
1. Upload the contents of this folder to the root of a new GitHub repository. Keep the folder structure exactly as shown.
2. Ensure the default branch is named `main` (or change the workflow branch filter to match yours).
3. Open **Actions** → **Build CineMotion APK** → **Run workflow**.
4. When the workflow succeeds, open its run and download the `CineMotion-debug-apk` artifact.

The source is not claimed to have been compiled on a real Android SDK in this environment. Use the first GitHub Actions result as the build verification; if it fails, share the first actual `FAILURE`/`e:` block rather than the whole log.
