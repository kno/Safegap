# SafeGap - Claude Code Guidelines

## Project Overview

Android app for car mounting that uses the camera to detect objects, estimate distances, and calculate relative speed, alerting the driver in real-time.

## Build & Run

```bash
# Build (requires JDK 17+; foojay-resolver auto-provisions the toolchain JDK)
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew assembleDebug

# Install on connected device
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew installDebug

# Run tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew test
```

## Stack

- **Language**: Kotlin (built-in via AGP 9.0)
- **Build**: Gradle 9.4.1, AGP 9.0.1, Kotlin 2.2.10
- **Camera**: CameraX 1.6.0
- **ML**: TFLite Task Vision 0.4.4 + NNAPI Delegate (EfficientDet-Lite0 INT8)
- **UI**: Jetpack Compose (BOM 2026.03.00)
- **DI**: Hilt 2.59.2 (KSP 2.2.10-2.0.2)
- **Settings**: DataStore Preferences 1.1.3
- **Min SDK**: 26 (Android 8.0), **Target SDK**: 35, **Compile SDK**: 36

## Module Structure

```
:app          → Activity, Foreground Service, DI wiring
:camera       → CameraX binding, FrameProducer (SharedFlow)
:detection    → TFLite ObjectDetector, IoU Tracker, DetectionPipeline
:estimation   → DistanceEstimator, KalmanFilter1D, SpeedTracker, TTC
:alert        → AlertEngine (thresholds + debounce), AudioAlertPlayer (ToneGenerator)
:ui           → HudScreen, SettingsScreen, DetectionOverlay, AlertBanner, SpeedBadge, DebugOverlay
:core         → Shared models, constants, HudRepository, SettingsRepository (DataStore)
```

## Code Conventions

- All code in Kotlin, package root: `com.safegap.<module>`
- Use trailing commas in Kotlin
- Use `@Singleton` + `@Inject constructor` for DI (constructor injection preferred over modules)
- Coroutines: IO for camera callbacks, Default for pipeline processing, Main for UI/audio
- Spanish for user-facing strings, English for code/comments

## Architecture

- `DrivingService` (Foreground Service) owns the complete pipeline
- `FrameProducer` uses SharedFlow with DROP_OLDEST backpressure
- Pipeline: Camera → Detect → Track → Estimate → Alert → UI
- UI observes `StateFlow<HudState>` from ViewModel
