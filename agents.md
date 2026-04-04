# SafeGap - Agent Guidelines

## Code Style

- Kotlin with trailing commas
- No unnecessary abstractions or over-engineering
- Prefer constructor injection with Hilt
- Keep modules focused: each module has a single responsibility

## When Modifying Build Files

- AGP 9.0 includes built-in Kotlin support — do NOT add `kotlin-android` plugin to modules
- Use `kotlin { jvmToolchain(17) }` instead of `kotlinOptions { jvmTarget }`
- Version catalog is at `gradle/libs.versions.toml` — always add new dependencies there
- All modules use `compileSdk = 36`

## Testing

- Unit tests for pure logic (estimation, alert, tracking) — no Android dependencies needed
- Use synthetic data for testing algorithms (known bboxes, distances, speeds)
- Integration tests for CameraX require a real device

## Key Architectural Decisions

- `DrivingService` is a Foreground Service with `foregroundServiceType="camera"` — required so Android doesn't kill the process while driving
- Camera frames use `SharedFlow` with `DROP_OLDEST` to handle backpressure — never block the camera thread
- `ImageProxy.toBitmap()` from CameraX 1.6 is used for frame conversion — no manual YUV conversion unless profiling shows it's needed
- Kalman filter smooths distance estimates per tracked object
- TTC (Time-to-Collision) is the primary alert trigger, not raw distance
