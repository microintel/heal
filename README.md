<p align="center">
  <img src="https://microintel.github.io/heal/heal.png" alt="Heal logo" width="120">
</p>

# Heal — Frequency Sound Generator

**Live:** [microintel.github.io/heal](https://microintel.github.io/heal)

A single-page, installable web app for playing custom frequencies or your own audio — with a live 3D visualizer set against a deep-space backdrop.

> Built just for fun by **Microintel** — not a medical or therapeutic product.

## Features

- **Custom frequency generator** — any tone from 20–20,000 Hz
- **Song upload** — load your own audio file and watch its live waveform
- **Microphone input** — visualize live sound picked up from the mic
- **View dropdown** — one button switches the visualizer between the 3D brain scene, normal waves, and a 3D spectrogram (time × frequency × loudness, jet-colored; drag to orbit)
- **Light/dark theme** — auto-detects system preference, remembers your choice
- **Installable PWA** — add-to-home-screen support via `manifest.json` and app icons
- **Live 3D visualizer** (Three.js), including:
  - A scrolling waveform "waterfall" traveling from a speaker to an ear
  - A reactive brain/neuron display that pulses with the audio
  - Traveling sound-wave rings between the speaker and ear
  - A painted deep-space skybox with a twinkling starfield, Milky Way haze, and soft nebula clouds

## Tech Stack

- Vanilla HTML/CSS/JS — no build step, no framework
- [Three.js](https://threejs.org/) r128 (+ `OrbitControls`) for the 3D scene, loaded via CDN
- Web Audio API for tone generation, song playback, and microphone analysis
- Google Fonts (Bricolage Grotesque, Inter, JetBrains Mono) and Bootstrap Icons, loaded via CDN

## Browser Support

Requires a modern browser with WebGL and Web Audio API support (current Chrome, Safari, Firefox, Edge). Microphone input requires user permission and a secure context (`https://` or `localhost`).

## Notes

- The visualizer's 3D elements (waveform, brain, rings, starfield) are all procedurally generated in-code — no external textures or models are loaded.

---

Made for fun by [Microintel](https://microintel.github.io/heal).
