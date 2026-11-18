# Healer

Native Android (Kotlin + Jetpack Compose + Media3) music player.

- Scans all audio on the device (MediaStore)
- **Folders** tab: every folder containing music; tap to open
- **Songs** tab: all songs; Play all / Shuffle
- Bottom navigation: Folders · Songs · Visualizer; top menu (⋮) with theme (Light / Dark / System) and About
- Visualizers: 3D wave, 2D wave, Frequencies (live FFT spectrum)
- Mini player + full Now Playing screen (seek, prev/next, shuffle, repeat)
- Background playback with notification / lock-screen controls
- 3D waveform waterfall (from Heal) on Now Playing, driven by the live audio; drag to rotate, corner icon toggles album art
- Splash screen (animated wave, "By Microintel")
- Opens with a short "heal by music" quote notification (24 random quotes)
- About screen (info icon, top right) – developed by Microintel
- Search: accent-insensitive, ranked, typo-tolerant (songs, artists, albums, folders)
- Heal theme (teal accent, black dark mode), follows system light/dark

## Build
1. Android Studio (Koala or newer) → File ▸ Open → select this folder
2. Wait for Gradle sync
3. Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)
   (APK ends up in app/build/outputs/apk/debug/)

minSdk 26 (Android 8.0), targetSdk 35.
