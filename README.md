# WaveEQ 🎧
### Motor Nativo de Ecualización Paramétrica Global (32 Bandas) con Jetpack Compose para Android 14+ (API 34) sin ROOT

[![Android](https://img.shields.io/badge/Android-14%2B%20(API%2034)-green.svg)](https://developer.android.com/about/versions/14)
[![Engine](https://img.shields.io/badge/AudioFX-DynamicsProcessing-blue.svg)](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20Material3-blueviolet.svg)](https://developer.android.com/jetpack/compose)
[![Visualizer](https://img.shields.io/badge/FFT-60%20FPS%20Realtime-cyan.svg)](https://developer.android.com/reference/android/media/audiofx/Visualizer)
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

**WaveEQ** es una solución de ingeniería de audio de alto rendimiento inspirada en herramientas profesionales como Wavelet y Viper4Android, construida con arquitectura **100% Nativa en Kotlin y Jetpack Compose**, diseñada específicamente para funcionar en **Android 14+ (API 34)** de forma global y **sin requerir permisos de superusuario (ROOT)**.

---

## 🏛️ Arquitectura 100% Nativa (Sin WebViews ni React Native)

```
┌────────────────────────────────────────────────────────────────────────┐
│                   INTERFAZ NATIVA (Jetpack Compose)                    │
│  WaveEQScreen.kt (Material 3 • Canvas Curva SPL • Canvas FFT 60 FPS)   │
│  MainActivity.kt (ComponentActivity • StateFlow • ServiceConnection)   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Android IPC / LocalBinder
┌───────────────────────────────────▼────────────────────────────────────┐
│                  MOTOR DE AUDIO CORE (Foreground Service)              │
│  AudioEQService.kt (START_STICKY • MediaProcessing • Android 14)       │
│  AudioReceiver.kt (Captura dinámica de audioSessionId de Spotify/YT)   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Hardware Effect Pipeline
┌───────────────────────────────────▼────────────────────────────────────┐
│                       DYNAMICS PROCESSING (DSP)                        │
│  32 Bandas PEQ Explícitas (20 Hz - 20 kHz)                             │
│  Limitador Dinámico Anti-Clipping (Attack: 1.0ms • Threshold: -0.5dB)  │
└────────────────────────────────────────────────────────────────────────┘
```

### Componentes Clave:
1. **`MainActivity.kt`**: Extiende `ComponentActivity()` y se vincula directamente a `AudioEQService` vía `ServiceConnection` e `IBinder` local.
2. **`WaveEQScreen.kt`**: Interfaz reactiva escrita íntegramente con componentes declarativos de Jetpack Compose:
   - 32 Faders paramétricos para las frecuencias ISO (20 Hz a 20 kHz).
   - Curva SPL interpolada en tiempo real mediante `Canvas` (`drawPath` con curvas cúbicas Bézier).
   - Visualizador espectral FFT a 60 FPS implementado con `Canvas` nativo.
   - Selector de Presets de fábrica (*Flat, Harman Target, Bass Boost, Vocal Clarity, Electronic, Studio Reference*).
   - Interruptor Maestro de Bypass / Encendido.
   - Módulo Limitador de Picos con medidor dinámico de reducción de ganancia (Gain Reduction).
3. **`AudioEQService.kt`**:
   - `START_STICKY` para recuperación inmediata si Android libera memoria RAM.
   - Foreground Service tipo `FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING` para Android 14+.
   - 32 bandas independientes L/R en `DynamicsProcessing.EqBand`.
   - Limitador dinámico anti-distorsión (Ataque: 1.0 ms, Relajación: 60.0 ms, Umbral: -0.5 dB).
4. **`AudioReceiver.kt`**:
   - Captura eventos globales `OPEN_AUDIO_EFFECT_CONTROL_SESSION` de reproductores como Spotify, YouTube, Tidal, Apple Music y Poweramp.

---

## 🚀 Compilación y Despliegue

### Compilar localmente con Android Studio o Gradle:
```bash
# Compilar el APK Debug
cd android && ./gradlew assembleDebug

# O usando el script automatizado
./deploy.sh --debug
```

### Compilar automáticamente en la nube (GitHub Actions):
El repositorio incluye el pipeline `.github/workflows/build-android.yml` que compila el APK nativo con Java 17 y Android SDK 34 en cada `push` o mediante ejecución manual (`workflow_dispatch`).
