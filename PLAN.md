# SafeGap — Plan de Implementación

Aplicación Android para montaje en coche que usa la cámara para detectar objetos,
estimar distancias y calcular velocidad relativa, alertando al conductor en tiempo real.

---

## Stack tecnológico

| Capa | Tecnología | Motivo |
|---|---|---|
| Lenguaje | Kotlin | Coroutines + Flow para el pipeline de cámara |
| Cámara | CameraX 1.4.x | Lifecycle-aware, backpressure `KEEP_ONLY_LATEST` |
| Detección ML | TFLite Task Vision 0.4.4 + NNAPI | Control total del modelo, aceleración INT8 vía DSP/NPU |
| Modelo | EfficientDet-Lite0 (INT8) | ~19-20ms con NNAPI, entrada 320x320 |
| UI | Jetpack Compose | HUD sobre canvas, sin recomposición innecesaria |
| DI | Hilt | Scoping correcto de lifecycle |
| Settings | DataStore | Umbrales configurables persistidos |
| Audio | SoundPool | Latencia mínima para alertas cortas |
| SDK mínimo | API 26 (Android 8.0) | Requerido por CameraX y AudioFocusRequest |
| SDK target | API 35 | |

---

## Arquitectura de módulos

```
:app          → shell Android, Activity, Foreground Service
:camera       → CameraX, FrameProducer (SharedFlow<ImageProxy>)
:detection    → TFLite, IoUTracker, DetectionResult
:estimation   → DistanceEstimator, SpeedTracker (Kotlin puro, sin Android)
:alert        → AlertEngine, AudioAlertPlayer
:ui           → HudScreen, DetectionOverlay, AlertBanner
:core         → modelos compartidos, constantes, utilidades
```

> **Clave de arquitectura**: `DrivingService` (Foreground Service con
> `foregroundServiceType="camera"`) es el dueño del pipeline completo. Sin esto,
> Android puede matar el proceso bajo presión de memoria mientras el coche está
> en movimiento.

---

## Pipeline de datos

```
[Sensor cámara] 15fps (target)
       |
       | ImageProxy (YUV_420_888)
       v
[FrameProducer]  SharedFlow CONFLATED
       |            ↑ descarta frames viejos si ML está ocupado
       v
[ImageConverter]  YUV→Bitmap + resize 320x320
       |
       v
[ObjectDetector]  TFLite NNAPI Delegate ~19-20ms
       |           → List<RawDetection>(bbox, class, confidence)
       v
[IoUTracker]      greedy IoU matching entre frames
       |           → List<TrackedDetection>(bbox, class, confidence, trackId)
       v
[DistanceEstimator]  por objeto, sin estado
       |              → distancia en metros
       v
[SpeedTracker]    ventana deslizante 5 muestras por trackId
       |           → velocidad relativa (m/s) + TTC (s)
       v
[AlertEngine]     umbral TTC/distancia + debounce
       |           → AlertLevel: SAFE | WARNING | CRITICAL
       |
       +---> [AudioAlertPlayer]  SoundPool
       |
       v
[HudRepository]   StateFlow<HudState>
       v
[HudViewModel]    StateFlow<HudState>
       v
[HudScreen Compose]
       +-- CameraPreviewSurface  (PreviewView hardware-accelerated)
       +-- DetectionOverlay      (Canvas: bounding boxes + etiquetas de distancia)
       +-- AlertBanner           (color: verde/amarillo/rojo + texto mínimo)
       +-- SpeedBadge            (velocidad relativa del objeto más cercano)
```

### Modelo de hilos

- `ImageAnalysis` callback → `Dispatchers.IO`
- Pipeline de procesamiento (preprocess→detect→track→alert) → `Dispatchers.Default`, un solo coroutine secuencial
- Audio → `Dispatchers.Main` (AudioFocus requiere main thread)
- UI → `Dispatchers.Main` vía `StateFlow.collect`

---

## Algoritmos

### 1. Estimación de distancia — Tamaño aparente (método principal)

```
distancia_m = (altura_real_m × focal_length_px) / altura_bbox_px
```

donde:

```
focal_length_px = (imagen_alto_px × focal_length_mm) / sensor_alto_mm
```

Los parámetros de cámara se obtienen de `CameraCharacteristics` vía `Camera2Interop`.

**Tabla de alturas reales por clase COCO:**

| Clase | Altura real (m) |
|---|---|
| car | 1.5 |
| truck | 3.5 |
| bus | 3.2 |
| person | 1.7 |
| bicycle | 1.1 |
| motorcycle | 1.2 |

Descarte de estimaciones no fiables:
- `bbox_top < 5%` del alto del frame (objeto recortado por borde superior)
- `confidence < 0.55`

### 2. Estimación de distancia — Plano de suelo (método secundario)

Para peatones y ciclistas, se fusiona la posición del borde inferior del bbox con
la altura de montaje de la cámara y el ángulo de cabeceo (IMU `TYPE_ROTATION_VECTOR`):

```
delta_y = bottom_y_bbox - y_horizonte
distancia = (h_camara × focal_length_px) / (delta_y × cos(θ) + focal_length_px × sin(θ))
```

### 3. Filtro de Kalman 1D (por objeto rastreado)

Suaviza el ruido en la serie de distancias estimadas.

```
Estado:       x = [distancia, velocidad]^T
Transición:   F = [[1, -dt], [0, 1]]
Observación:  H = [1, 0]   (solo observamos distancia)
Ruido R:      mayor a distancias grandes
```

### 4. Velocidad relativa

Ventana deslizante de N=5 muestras `(timestamp_ms, distancia_m)` por objeto rastreado:

```kotlin
velocidad_mps = (distancia_oldest - distancia_newest) / delta_t_s
// positivo = objeto acercándose
```

### 5. Time-to-Collision (TTC)

```
TTC_s = distancia_actual_m / velocidad_cierre_mps
```

TTC es mejor disparador de alertas que la distancia pura: un coche a 20m alejándose
es seguro; un coche a 40m acercándose a 30 m/s no lo es.

### 6. Lógica de alertas

| Condición | Nivel |
|---|---|
| TTC < 2.0s  ó  distancia < 5m | CRITICAL |
| TTC < 4.0s  ó  distancia < 15m | WARNING |
| resto | SAFE |

Para `person`, el umbral CRITICAL se activa con TTC < 4.0s (colisión con peatón
es más grave).

**Debounce**: el nivel sube inmediatamente; baja solo tras 3 frames consecutivos
en nivel menor. Evita alertas parpadeantes.

### 7. Tracking IoU

Matching greedy entre detecciones de frames consecutivos de la misma clase:

1. Calcular IoU por pares (mismo class)
2. Asignar matches donde IoU > 0.3
3. IDs persistentes para matches; nuevos IDs para detecciones sin match
4. Grace period de 5 frames: tracks sin match se mantienen con dead-reckoning
   (proyección con última velocidad conocida)

---

## Estructura de ficheros

```
Safegap/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
│
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/safegap/app/
│           ├── SafeGapApplication.kt
│           ├── MainActivity.kt
│           ├── di/
│           │   ├── AppModule.kt
│           │   ├── CameraModule.kt
│           │   └── AudioModule.kt
│           └── service/
│               ├── DrivingService.kt          ← dueño del pipeline completo
│               └── DrivingServiceBinder.kt
│
├── camera/
│   └── src/main/java/com/safegap/camera/
│       ├── CameraManager.kt
│       ├── FrameProducer.kt                   ← backpressure crítico
│       └── ImageConverter.kt                  ← YUV_420_888 → Bitmap
│
├── detection/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── efficientdet_lite0.tflite
│   │   └── java/com/safegap/detection/
│   │       ├── ObjectDetector.kt              ← componente más sensible al rendimiento
│   │       ├── DetectorConfig.kt
│   │       ├── model/
│   │       │   ├── RawDetection.kt
│   │       │   └── DetectionClass.kt
│   │       └── tracking/
│   │           ├── IoUTracker.kt
│   │           └── TrackedDetection.kt
│
├── estimation/
│   └── src/main/java/com/safegap/estimation/
│       ├── DistanceEstimator.kt               ← algoritmo de seguridad principal
│       ├── SpeedTracker.kt
│       ├── KalmanFilter1D.kt
│       ├── CameraIntrinsics.kt
│       ├── KnownObjectHeights.kt
│       └── model/
│           ├── TrackedObject.kt
│           └── DistanceSample.kt
│
├── alert/
│   └── src/main/java/com/safegap/alert/
│       ├── AlertEngine.kt                     ← define el comportamiento de seguridad
│       ├── AlertLevel.kt
│       ├── AlertConfig.kt
│       └── AudioAlertPlayer.kt
│
├── ui/
│   └── src/main/java/com/safegap/ui/
│       ├── HudViewModel.kt
│       ├── HudState.kt
│       ├── screen/
│       │   └── HudScreen.kt
│       ├── components/
│       │   ├── CameraPreviewSurface.kt
│       │   ├── DetectionOverlay.kt
│       │   ├── AlertBanner.kt
│       │   └── SpeedBadge.kt
│       └── theme/
│           └── HudTheme.kt                    ← alto contraste, texto grande
│
└── core/
    └── src/main/java/com/safegap/core/
        ├── Constants.kt
        ├── extensions/
        │   ├── BitmapExt.kt
        │   └── RectFExt.kt
        └── util/
            ├── MovingAverage.kt
            └── FrameRateMonitor.kt
```

### AndroidManifest.xml — declaraciones clave

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<service
    android:name=".service.DrivingService"
    android:foregroundServiceType="camera"
    android:exported="false" />
```

---

## Retos y soluciones

### 1. Conversión YUV→Bitmap lenta
- Reducir resolución en `ImageAnalysis` a 640x480 (el modelo necesita 320x320 de todas formas)
- Usar `ImageProxy.toBitmap()` de CameraX 1.3+ (usa rutas de hardware buffer)
- Fallback: JNI wrapper sobre `libyuv` (<5ms)

### 2. Init GPU Delegate tarda ~500ms
- Inicializar en `DrivingService.onCreate()` en `Dispatchers.Default`
- Bloquear pipeline con `CompletableDeferred` hasta que el intérprete esté listo
- Mostrar estado "Iniciando..." en UI

### 3. Pérdida de ID de tracking por oclusión
- Grace period de 5 frames con dead-reckoning
- Matching restringido por clase (un coche no puede reasignarse a una persona)
- Para producción: considerar ByteTrack o SORT (implementables en Kotlin puro)

### 4. Batería y throttling térmico
- Target 15fps en `ImageAnalysis` (suficiente para seguridad, no 30fps)
- Monitorizar `PowerManager.ThermalStatus` (API 29+)
- En `THERMAL_STATUS_SEVERE`: bajar a 10fps y cambiar de GPU delegate a NNAPI

### 5. Intrínsecos de cámara no disponibles
- Primario: `Camera2CameraInfo.extractCameraCharacteristics()`
- Fallback: valores por defecto (focal 3.6mm, sensor alto 4.55mm)
- Mejor: pantalla de calibración manual (objeto de altura conocida a distancia conocida)

### 6. Distracción al conducir
- Solo 3 estados de alerta (color + 1 línea de texto, ej. "VEHÍCULO CERCA — 8m")
- `derivedStateOf` en Compose: recomponer solo cuando cambia algo significativo
- Pantalla atenuada por defecto; brillo normal solo en CRITICAL
- `FLAG_KEEP_SCREEN_ON` gestionado desde el service

---

## Fases de implementación

### Fase 1 — Fundamentos ✓
- Setup Gradle multi-módulo + version catalog (`libs.versions.toml`)
- Modelos core: `RawDetection`, `TrackedObject`, `AlertLevel`
- Módulo `:camera`: binding CameraX, `FrameProducer` con SharedFlow
- `DrivingService` básico con notificación foreground
- `MainActivity` con `CameraPreviewSurface` — confirmar que el preview funciona
- Scripts de compilación y despliegue en dispositivo real local

### Fase 2 — Detección ✓
- ✅ Integrar TFLite Task Vision 0.4.4 en `:detection`
- ✅ Cargar modelo EfficientDet-Lite0 INT8, implementar `ObjectDetector`
- ✅ `ImageConverter` no necesario: `ImageProxy.toBitmap()` de CameraX + TensorImage maneja el resize
- ✅ Cablear `FrameProducer` → `DetectionPipeline` → `ObjectDetector` → `IoUTracker` → logs en `DrivingService`
- ✅ Implementar `IoUTracker` (greedy IoU, clase restringida, grace period 5 frames) + 12 tests unitarios
- ✅ Rendimiento medido en dispositivo: ~19-20ms por frame

> **Nota**: El modelo INT8 no es compatible con GPU Delegate (falla en inferencia).
> Se usa NNAPI Delegate (acelera INT8 vía DSP/NPU) con fallback a CPU.

### Fase 3 — Estimación ✓
- ✅ `CameraIntrinsics` (focal/sensor con defaults) + `KnownObjectHeights` (alturas COCO)
- ✅ `DistanceEstimator` (tamaño aparente, filtros de confianza y clipping) + 9 tests
- ✅ `KalmanFilter1D` (estado [distancia, velocidad], ruido R adaptativo) + 5 tests
- ✅ `SpeedTracker` (ventana deslizante N=5, TTC, Kalman por track) + 8 tests
- ✅ Cableado en `DrivingService`: detect → track → estimate → log distancia/speed/TTC

### Fase 4 — Alertas y UI ✓
- ✅ `AlertEngine` con umbrales TTC/distancia, persona elevada (TTC<4s→CRITICAL), debounce 3 frames + 14 tests
- ✅ `AudioAlertPlayer` con ToneGenerator (WARNING: beep 800ms, CRITICAL: beep 400ms)
- ✅ `HudRepository` en `:core` — StateFlow compartido Service↔ViewModel
- ✅ `DetectionOverlay` — Canvas nativo con bboxes coloreados (verde/ámbar/rojo) + etiquetas distancia
- ✅ `AlertBanner` — banner superior animado, solo WARNING/CRITICAL, texto "VEHÍCULO CERCA — 8m"
- ✅ `SpeedBadge` — velocidad relativa en km/h del objeto más cercano
- ✅ Pipeline completo end-to-end: Camera → Detect → Track → Estimate → Alert → Audio + UI

### Fase 5 — Pulido ✓
- ✅ `SettingsRepository` con DataStore: umbrales alerta (TTC/distancia), calibración cámara (focal, sensor, altura)
- ✅ `SettingsScreen` Compose con sliders para todos los parámetros, navegación desde HUD
- ✅ `AlertEngine` lee umbrales desde settings en tiempo real
- ✅ Respuesta a throttling térmico: `PowerManager.ThermalStatus`, skip 1/2 frames en SEVERE
- ✅ Debug overlay FPS + conteo objetos + estado térmico (solo builds debug, `BuildConfig.DEBUG`)
- ✅ Contador FPS en DrivingService con ventana de 1s
- Pendiente: pruebas en coche real y ajuste de umbrales

---

## Permisos requeridos

| Permiso | Motivo |
|---|---|
| `CAMERA` | Acceso a cámara |
| `FOREGROUND_SERVICE` | Ejecutar pipeline en background |
| `FOREGROUND_SERVICE_CAMERA` | Acceso a cámara desde service (Android 14+) |
| `WAKE_LOCK` | Evitar que la CPU duerma durante la conducción |
