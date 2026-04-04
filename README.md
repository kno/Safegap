# SafeGap

App Android para montaje en coche que usa la camara trasera para detectar vehiculos, peatones y ciclistas, estimar distancias en tiempo real y alertar al conductor cuando hay riesgo de colision.

## Funcionalidades

- Deteccion de objetos con EfficientDet-Lite0 (TFLite + GPU)
- Estimacion de distancia por tamano aparente y plano de suelo
- Calculo de velocidad relativa y Time-to-Collision (TTC)
- Alertas visuales (HUD verde/ambar/rojo) y sonoras
- Tracking de objetos entre frames (IoU matching)
- Filtro de Kalman para suavizar estimaciones de distancia

## Requisitos

- Android 8.0+ (API 26)
- Dispositivo con camara trasera
- JDK 17+ para compilar (recomendado: Temurin 21)

## Como ejecutar

### 1. Clonar el repositorio

```bash
git clone <repo-url>
cd Safegap
```

### 2. Configurar el JDK

El proyecto requiere JDK 17 o superior. Si tu JDK por defecto no es compatible:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
```

O instala Temurin 21:

```bash
brew install --cask temurin@21
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

### 3. Compilar

```bash
./gradlew assembleDebug
```

### 4. Instalar en dispositivo

Conecta un dispositivo Android con depuracion USB activada:

```bash
./gradlew installDebug
```

### 5. Ejecutar

Abre la app "SafeGap" en el dispositivo. Concedera permiso de camara cuando se solicite.

## Stack tecnologico

| Componente | Tecnologia |
|---|---|
| Lenguaje | Kotlin |
| Build | Gradle 9.4.1, AGP 9.0.1 |
| Camera | CameraX 1.6.0 |
| ML | TFLite + GPU Delegate |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt 2.59.2 |
| Min SDK | 26 (Android 8.0) |

## Estructura de modulos

```
:app          → Shell Android, Activity, Foreground Service
:camera       → CameraX, FrameProducer
:detection    → TFLite, IoU Tracker
:estimation   → Distancia, Velocidad, Kalman
:alert        → Motor de alertas, Audio
:ui           → Compose HUD, overlays
:core         → Modelos compartidos, constantes
```

## Licencia

Pendiente de definir.
