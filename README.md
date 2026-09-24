# sBz — Android Native Professional System Audio DSP

**sBz** is a high-performance, studio-grade system audio DSP processor and 32-band equalizer built natively for Android using Android's native audio effect architecture (`android.media.audiofx.DynamicsProcessing` and `AudioEffect`).

Unlike consumer media players or screen-capture hacks, **sBz does not play audio files and never captures PCM streams via AudioRecord or MediaProjection**. Instead, it hooks directly into Android's low-latency audio effect framework to process all audio actively rendered by Spotify, YouTube, Apple Music, Tidal, VLC, games, and web browsers.

---

## Architecture

```
                       Android Audio Framework
                                  │
                  ┌───────────────┴───────────────┐
                  ▼                               ▼
       Global Output (Session 0)       Individual Audio Sessions
                  │                               │
                  └───────────────┬───────────────┘
                                  │
                                  ▼
                   AudioEffect / DynamicsProcessing
                                  │
                                  ▼
                          sBz DSP Pipeline
                                  │
       ┌──────────────────────────┴──────────────────────────┐
       │  1. Input Pre-Gain (-12 dB to +12 dB)               │
       │  2. Headroom Safeguard Protection                   │
       │  3. Hardware Bass Boost Subharmonic Expander        │
       │  4. 3-Band Analog Tone (Low Shelf, Mid, High Shelf) │
       │  5. 32-Band ISO Graphic Equalizer (0.5 dB steps)    │
       │  6. MDRC 4-Band Dynamic Range Compressor (MBC)      │
       │  7. AutoGain / Automatic Gain Control (AGC)         │
       │  8. Spatial Stereo Virtualizer Widening             │
       │  9. Master Output Gain (-24 dB to +12 dB)           │
       │ 10. Stereo Panning Balance (L <-> R)                │
       │ 11. Brickwall Limiter & Digital Protection          │
       └──────────────────────────┬──────────────────────────┘
                                  │
                                  ▼
                         Android Audio Output
```

---

## Key Features

1. **Native DynamicsProcessing Engine**
   - True hardware-accelerated DSP processing via `android.media.audiofx.DynamicsProcessing`.
   - Adaptive fallback detection: dynamically configures 32, 16, or 8 bands depending on vendor HAL memory constraints.
   - Dual-channel independent stereo matrix.

2. **32-Band ISO Equalizer**
   - 32 ISO Standard 1/3-octave frequencies:
     `20, 25, 31.5, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1k, 1.25k, 1.6k, 2k, 2.5k, 3.15k, 4k, 5k, 6.3k, 8k, 10k, 12.5k, 16k, 18k, 20k Hz`.
   - Range: **-15.0 dB to +15.0 dB** with **0.5 dB precision**.
   - Strict touch-isolated faders: consuming vertical drag gestures prevents any unwanted parent container scrolling while manipulating EQ sliders. Double-tap to zero out instantly.

3. **MDRC (Multiband Dynamic Range Compression)**
   - 4 independent bands: Sub Low, Low Mid, High Mid, High Air.
   - Per-band controls: Crossover frequency, Threshold (-60 to 0 dB), Ratio (1:1 to 20:1), Attack (0.1 to 100 ms), Release (10 to 1000 ms), and Makeup Gain.

4. **Robust Audio Session Management**
   - Session 0 (Global Output) attachment.
   - BroadcastReceiver listening for `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` and `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION`.
   - Real-time `OnControlStatusChangeListener` to reclaim DSP control whenever system audio focus or another application changes priority.
   - Responds to hardware audio device switches (Headphones, Bluetooth, Speakers).

5. **Safety Headroom & Brickwall Limiter**
   - Integrated limiter stage prevents digital distortion and speaker damage.
   - Real-time headroom safeguard calculates cumulative EQ + Tone + Bass Boost gains and applies dynamic linear pre-attenuation to prevent clipping before saturation occurs.
   - Rejection of NaN, infinite, or overflow states.

6. **Studio Presets & Persistence**
   - Factory presets: *Studio Reference Flat*, *Deep Bass Impact*, *Vocal Clarity & Presence*, *Club & Electronic EDM*, *Dynamic Rock & Metal*, *Audiophile Mastering*.
   - User preset creation, custom naming, and JSON persistence.

---

## Build & Compile Instructions

### Requirements
- JDK 17 (Eclipse Temurin or OpenJDK 17)
- Android SDK with `compileSdk = 34` and Build Tools 34.0.0+
- Gradle 8.9 (handled automatically via `./gradlew`)

### Local Build
```bash
# Clone the repository
cd sBz

# Ensure Gradle wrapper is executable
chmod +x gradlew

# Build Debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions CI/CD
The project contains a GitHub Actions workflow `.github/workflows/build.yml` that:
1. Clones repo
2. Sets up JDK 17 with Gradle caching
3. Compiles `./gradlew assembleDebug --stacktrace`
4. Automatically uploads the generated `sBz-DSP-debug.apk` as a downloadable GitHub artifact!

---

## Permissions & Security
sBz strictly respects Android security guidelines:
- **NO `RECORD_AUDIO`**
- **NO `MediaProjection`**
- **NO screen capture or microphone access**
- Uses only:
  - `MODIFY_AUDIO_SETTINGS`: Required to instantiate `AudioEffect` instances.
  - `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Required for continuous background DSP processing on Android 14+.
  - `POST_NOTIFICATIONS`: Android 13+ status notification.
