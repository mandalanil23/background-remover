# Background Remover – PNG Maker

A production-ready Android app that removes an image background with **real on-device AI
segmentation** and exports a true transparent PNG.

- **Package:** `com.bgremover.pngmaker`
- **Language:** Kotlin 2.0 · **UI:** Jetpack Compose + Material 3
- **minSdk 24 · targetSdk 35 · compileSdk 35**
- **No internet permission.** Images never leave the device.

---

## 1. Quick start

```bash
# Open the project folder in Android Studio (Ladybug 2024.2.1 or newer) and let it sync,
# or build straight from the command line:

chmod +x gradlew                 # only needed if you unzipped the project
./gradlew assembleDebug          # debug APK for testing
./gradlew testDebugUnitTest      # unit tests
```

There is also a static consistency check that needs no Android SDK at all:

```bash
python3 tools/verify_project.py
```

The first sync downloads the Android Gradle Plugin, AndroidX and ML Kit from
`google()` and `mavenCentral()` — an internet connection is required **for the build**,
never for the app at runtime.

If Android Studio reports a missing SDK, install **Android SDK Platform 35** and
**Build-Tools 35.0.0** from the SDK Manager, or set `sdk.dir` in `local.properties`:

```properties
sdk.dir=/Users/you/Library/Android/sdk
```

---

## 2. Producing a signed APK and AAB

### 2.1 Create a keystore (once — keep it safe forever)

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias bgremover
```

Put `release.jks` in the project root. **Losing this file means you can never update the
app on Play**, so back it up somewhere durable.

### 2.2 Point the build at it

Copy `keystore.properties.sample` to `keystore.properties` (git-ignored) and fill it in:

```properties
storeFile=release.jks
storePassword=…
keyAlias=bgremover
keyPassword=…
```

Alternatively export `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`
as environment variables — that is what CI uses.

### 2.3 Build

```bash
./gradlew clean bundleRelease     # AAB  → app/build/outputs/bundle/release/app-release.aab
./gradlew assembleRelease         # APK  → app/build/outputs/apk/release/app-release.apk
```

Or run the helper script, which does both and prints the output paths:

```bash
./build-release.sh
```

Upload the **.aab** to Google Play; use the **.apk** for direct installation and testing.

> If no signing config is found, the release build still succeeds but produces an
> **unsigned** artifact. Play will reject an unsigned bundle, so check the console output.

### 2.4 Verify the signature

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

---

## 3. Build it without installing anything (GitHub Actions)

`.github/workflows/android.yml` builds a signed APK **and** AAB on every push and on
demand. Push this repo to GitHub, then add four repository secrets under
**Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` (macOS: `base64 -i release.jks`) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | e.g. `bgremover` |
| `KEY_PASSWORD` | key password |

Run the workflow from the **Actions** tab. Three artifacts come out:

| Artifact | Needs secrets? | Use |
| --- | --- | --- |
| `app-debug-apk` | **No** | Installs on any phone straight away — grab this to try the app |
| `app-release-apk` | Yes | Signed APK for distribution outside Play |
| `app-release-aab` | Yes | Upload this to the Play Console |

Without the secrets the workflow still succeeds, but the *release* artifacts come out
unsigned and Android refuses to install an unsigned APK — use `app-debug-apk` for testing
until you have a keystore.

---

## 4. How the background removal actually works

There is no fake cut-out anywhere in this project. The pipeline is:

```
pick image
   ↓  CropScreen        optional: crop, rotate, mirror — writes a reframed JPEG
   ↓  PhotoDecoder      decode at the configured pixel budget, apply EXIF rotation
   ↓  scaleForSegmentation   working copy, longest side ≤ 1536 px
   ↓  BackgroundRemover  ML Kit → per-pixel foreground confidence mask
   ↓  MaskCompositor     blur → smoothstep → bilinear upsample → alpha on the FULL-res photo
   ↓  PNG (lossless, straight alpha) written to the cache
   ↓  MediaStore export / share sheet
```

### Engines

| Engine | Library | Model delivery | Notes |
| --- | --- | --- | --- |
| General subject | `com.google.android.gms:play-services-mlkit-subject-segmentation` | Google Play services, downloaded once | People, animals, food, products |
| People (offline) | `com.google.mlkit:segmentation-selfie` | **bundled in the APK** | Works on a device that has never been online |

`EngineMode.AUTO` (the default) tries the general model and silently falls back to the
bundled one, so the app is never dead in the water. The user can pin either engine in
Settings.

### Why the mask is resampled instead of the image being upscaled

Running segmentation at full resolution is slow and memory-hungry; running it small and
returning a small image loses detail. Instead the model runs on a ≤1536 px working copy
and the resulting mask is bilinearly resampled onto the original pixels, so a 12 MP photo
comes back as a 12 MP transparent PNG. Compositing walks the image in 256-row bands, so
peak memory is one band plus the output bitmap.

### Swapping in a different engine (including a remote API)

1. Implement `com.bgremover.pngmaker.engine.BackgroundRemover` (three functions).
2. Add a case to `EngineMode` and return your engine from `EngineFactory.enginesFor()`.

Nothing else changes — the compositor, the UI and the export path are engine-agnostic.

**If you add a remote API:** do not put the key in the APK. Anything in `BuildConfig`,
`strings.xml`, or the native libs can be extracted from a published app in minutes. Route
the request through a small server you control that holds the key and forwards the image.
You would also need to add `<uses-permission android:name="android.permission.INTERNET"/>`
to the manifest and update the privacy policy and the Play Data safety form, because
images would then leave the device.

---

## 5. Project layout

```
app/src/main/java/com/bgremover/pngmaker/
├── BackgroundRemoverApp.kt      Application: DI init, temp-file purge, crash logging
├── MainActivity.kt              Single activity, handles SEND / VIEW intents
├── di/ServiceLocator.kt         Four singletons, no codegen
├── data/
│   ├── SettingsRepository.kt    DataStore preferences + export counter
│   ├── RecentImagesRepository.kt Private PNG store + JSON index
│   └── model/                   AppSettings, ProcessedImage, enums
├── engine/
│   ├── BackgroundRemover.kt     The interface every engine implements
│   ├── SubjectSegmentationRemover.kt
│   ├── PersonSegmentationRemover.kt
│   ├── EngineFactory.kt         Engine selection + native resource cache
│   └── BackgroundRemovalService.kt  Orchestration, stages, timeouts
├── imaging/
│   ├── CropGeometry.kt          Crop maths — normalised rects, aspect locking (no Android)
│   ├── ImageCropper.kt          Applies crop/rotate/mirror to real pixels
│   ├── PhotoDecoder.kt          Safe decode, EXIF, downsampling, OOM retry
│   ├── MaskCompositor.kt        Mask → alpha, banded, bilinear, smoothstep
│   ├── PngExporter.kt           MediaStore (Q+) / legacy gallery save
│   ├── ShareHelper.kt           FileProvider share sheet
│   └── TempFiles.kt             Scratch-file lifecycle
├── nav/                         Routes + NavHost with transitions
├── ui/
│   ├── EditorViewModel.kt       The whole one-image workflow
│   ├── CropViewModel.kt         Crop editor state, preview orientation, export
│   ├── SettingsViewModel.kt · RecentImagesViewModel.kt
│   ├── components/              Checkerboard, zoom, before/after, scaffold, dialogs
│   ├── screens/                 The 11 screens
│   └── theme/                   Material 3 colour, type, shape
└── util/                        AppError, formatting helpers
```

### The look

The identity is one three-stop ramp — violet → fuchsia → cyan — defined once in
`ui/theme/Color.kt` and used only through `ui/theme/Gradients.kt`. Gradients are
deliberately the same in light and dark: what changes between themes is the surface behind
them, not the brand. `AuroraBackground` draws three drifting radial clouds in a single
`drawBehind` pass behind the main screens, and `Modifier.gradientFill` paints text with the
ramp by flooding an offscreen layer with `SrcAtop`.

Wallpaper-derived dynamic colour is therefore **off by default** — it would replace the
brand with the user's wallpaper on Android 12+. The Settings toggle still offers it.


### The 11 screens

1. **Splash** – system splash (`core-splashscreen`) → branded animated splash
2. **Home** – logo, tagline, big **+ Upload Image**, recents strip, navigation
3. **Image Selection** – Photo Picker / file picker, preview, file info
4. **Crop & Rotate** – optional. Drag-to-reframe with rule-of-thirds guides, locked
   aspect ratios (1:1, 4:3, 3:4, 16:9, 9:16), quarter-turn rotation and a mirror. The
   cropped file becomes the source, so nothing downstream knows cropping happened.
5. **Processing** – staged progress, cancellable, never blocks the UI thread
6. **Before/After Preview** – checkerboard, pinch-zoom, drag-to-compare, reset
7. **Save & Share** – Save PNG, Share, Process Another
8. **Recent Images** – grid of previous results, share/delete
9. **Settings** – engine, output size, edge softness, theme, storage
10. **Privacy Policy** – full text, offline
11. **About** – version, credits

---

## 6. Permissions — and why there are almost none

| Permission | When | Why |
| --- | --- | --- |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) | Only when the user taps Save on Android 9 or older | Legacy gallery write |

That is the complete list.

- Image input uses the **Android Photo Picker** and the **Storage Access Framework**, which
  grant access to exactly the one file the user chose — no `READ_MEDIA_IMAGES`, no
  `READ_EXTERNAL_STORAGE`.
- Saving on Android 10+ uses **MediaStore**, which needs no permission.
- There is **no `INTERNET` permission**. The bundled model runs offline; the optional
  general model is fetched by Google Play services in its own process.

---

## 7. Error handling

Every failure path returns a `com.bgremover.pngmaker.util.AppError`, which maps to a plain
sentence in `strings.xml`. Nothing throws to the UI.

| Situation | What the user sees |
| --- | --- |
| Not really an image | "This file could not be opened as an image…" |
| Unsupported format | "This image format is not supported…" |
| Very large image | Processed at a safe size, with a note |
| Out of memory | Decoder retries at half resolution up to 4 times, then explains |
| No subject detected | "We could not find a clear subject…" |
| Model still downloading | "…being prepared by Google Play services" |
| Model never downloaded | Falls back to the offline engine automatically |
| Processing timeout (45 s) | "Processing took too long…" |
| Storage permission denied | Explains and offers the prompt again |
| Disk full | Checked before writing, explained before failing |
| Anything unexpected | `AppError.from(throwable)` → "Unable to process this image." |

A last-resort `UncaughtExceptionHandler` logs and purges scratch files before the platform
handler runs.

---

## 8. Release configuration

- `isMinifyEnabled = true`, `isShrinkResources = true`, R8 full mode
- `proguard-rules.pro` keeps ML Kit's reflective entry points and strips `Log.v/d/i`
- Debug builds get `.debug` applicationId suffix, so both can be installed side by side
- No debug-only components ship in release (`ui-tooling` is `debugImplementation`)
- Adaptive launcher icon + themed (monochrome) icon
- App Bundle splits by density and ABI; language splitting disabled

### Targeting Android 16 (API 36)

Google Play raises the required `targetSdk` each August. To move up:

1. Install **Android SDK Platform 36**.
2. In `app/build.gradle.kts` set `compileSdk = 36` and `targetSdk = 36`.
3. In `gradle/libs.versions.toml` bump `agp` to a version that supports API 36
   (**8.9.0 or newer**) and, if Android Studio asks, `kotlin` to a matching release.
4. Re-run `./gradlew clean bundleRelease` and re-test the picker and save flows.

Nothing in this app relies on behaviour that changed in API 36 — it uses the Photo Picker,
MediaStore and FileProvider, all of which are the forward-compatible APIs.

---

## 9. Play Store submission checklist

- [ ] `versionCode` incremented, `versionName` updated in `app/build.gradle.kts`
- [ ] Signed **AAB** built and verified
- [ ] Store listing text from `play-store/store-listing.md`
- [ ] `play-store/graphics/play_store_icon_512.png` (512×512, no alpha) uploaded
- [ ] `play-store/graphics/feature_graphic_1024x500.png` uploaded
- [ ] At least 2 phone screenshots (take them from a real run — see below)
- [ ] Privacy policy hosted at a public URL — use `PRIVACY_POLICY.md`
- [ ] **Data safety** form: no data collected, no data shared, processed on device
- [ ] Content rating questionnaire completed
- [ ] Target audience: 13+ (general utility)

Screenshots: run the app on a device or emulator (1080×1920 or larger), capture Home,
Preview with the checkerboard, and the Save & Share screen.

---

## 10. Regenerating the artwork

```bash
python3 tools/generate_icons.py      # requires Pillow
```

Rewrites every `mipmap-*/ic_launcher*.png`, the 512×512 Play icon and the feature graphic
from one definition. The adaptive icon itself is vector
(`res/drawable/ic_launcher_{background,foreground,monochrome}.xml`) and is not touched.

---

## 11. Licence and attribution

The app uses Google ML Kit (Apache 2.0) for on-device segmentation. Everything else is
AndroidX / Jetpack Compose, also Apache 2.0. Add your own licence file before publishing.
