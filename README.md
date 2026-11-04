# EyeDroid

EyeDroid allows the user to **control an Android&trade; device by tracking the face**.

Based on the movement of the face captured through the **front camera**, the app moves a
pointer on the screen (i.e., like a mouse), which provides direct access to most elements of
the user interface. It is a fork of [EVA Facial Mouse](https://github.com/cmauri/eva_facial_mouse)
rebranded as `com.qali.eyedroid`.

## How it works

* Frames are captured with the legacy `android.hardware.Camera` API and converted to
  bitmaps with no native computer-vision dependency.
* [MediaPipe Face Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker)
  (FaceMesh) runs on-device and provides the landmarks, eye blendshapes and head pose used
  by the app. No OpenCV is required any more, which removes the missing
  `libopencv_java*.so` / `libvisionpipeline.so` failures on devices that could not load the
  old native pipeline.
* Head direction is derived from the landmarks (nose tip vs. cheek and forehead/chin
  midpoints, normalized by face size), so the cursor keeps working while the head is held
  still as well as when it moves.
* The pupil position inside the eye is used for fine cursor movement, and eye blinks drive
  clicking: a half blink of one eye is a click/hold, and looking at the screen edge scrolls.

## Features

* Circular cursor with blink feedback.
* Selectable tracking eye (left or right).
* Adjustable head-tracking strength (default 10) and typed numeric inputs for thresholds.
* Built-in simplified keyboard for Android versions where third-party keyboards do not work.

## For whom is EyeDroid?

EyeDroid is intended for those who cannot use a touchscreen. For instance, some people with
amputations, cerebral palsy, spinal cord injury, muscular dystrophy, multiple sclerosis,
amyotrophic lateral sclerosis (ALS) or other disabilities may benefit from this app.

## Requirements

* Mobile phone or tablet
* Android 7.0 (API 24) or higher
* Front camera
* Dual-core processor or higher

## Limitations

Due to pre-existing restrictions of the Android platform, there are currently some limitations.

* It cannot be used simultaneously with other applications that make use of the camera.
* On some Android versions most standard keyboards do not work with the app, so a basic
  keyboard is provided. Such a keyboard needs to be manually activated after installation.
* Does not work with most games.
* Browsers do not handle properly some actions (Chrome is recommended).
* Applications such as Maps, Earth and Gallery work with restrictions.

## Building

### Requirements

* JDK 17
* Android SDK with platform `android-34` and build-tools `34.0.0`
* Gradle 8.11.1 (the wrapper in this repository already points to it)
* No NDK is needed: the old OpenCV native sources under `eviacam/src/main/jni` are no longer
  built, and the MediaPipe natives ship prebuilt in the `tasks-vision` AAR.

### Setup

1. Install the Android SDK and point the build at it, either with `ANDROID_HOME` /
   `ANDROID_SDK_ROOT` or by creating `local.properties` next to this file:

   ```properties
   sdk.dir=/path/to/android-sdk
   ```

2. Make sure the required SDK packages are installed:

   ```bash
   sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
   ```

Dependencies are resolved from `https://maven.myket.ir` (a mirror that proxies Google Maven
and Maven Central) with `google()` and `mavenCentral()` as fallbacks, and the MediaPipe model
`eviacam/src/main/assets/face_landmarker.task` is committed, so no extra download step is
needed.

### Build commands

Debug build:

```bash
./gradlew :eviacam:assembleDebug
```

Signed release build:

```bash
./gradlew :eviacam:assembleRelease
```

The APK is written to:

```
eviacam/build/outputs/apk/release/eviacam-release.apk
```

Install it on a connected device with:

```bash
adb install -r eviacam/build/outputs/apk/release/eviacam-release.apk
```

If the wrapper cannot download Gradle, an installed Gradle 8.11.1 works as well:

```bash
gradle :eviacam:assembleRelease --no-daemon
```

### Signing

The release build is signed with the keystore committed at
`eviacam/eyedroid-release.jks` (alias `eyedroid`, password `eyedroid2024`, configured in
`eviacam/build.gradle`). That makes local builds installable, but it also means the key is
public: replace the keystore and credentials before publishing your own release.

The version code lives in `version.properties` (`VERSION_CODE=42`); bump it before shipping
an update.

## Licence

EyeDroid is released under GNU/GPL v3.0, like the original EVA Facial Mouse.

Original app developed by [Cesar Mauri](mailto:cesar@crea-si.com) and the
[CREA-Si](http://www.crea-si.com) team.
