# PulseTile — Overview

**Live WHOOP heart rate on your Android home screen.**

An unofficial native Android app (package `com.pulsetile.hr`) whose home-screen widgets show your **live heart rate, updating ~once per second** — something the official WHOOP app doesn't offer. It also derives live metrics WHOOP hides in real time, including **live HRV (RMSSD)**.

## How it works
- Reads the standard Bluetooth **Heart Rate broadcast** (`0x180D` / char `0x2A37`) that a WHOOP 4.0 / 5.0 emits — the same signal Peloton, Zwift and Garmin read.
- **No login, no WHOOP cloud, no per-user setup.** If you can enable HR Broadcast in the WHOOP app, PulseTile works.
- A **foreground service** holds the BLE connection and pushes each reading to the widgets (Android's native widget refresh caps at 30 min, so real-time updates must be pushed). A persistent notification shows current BPM + a Stop button.

## Widgets (square home-screen tiles)
- **Live Heart Rate** — big BPM number, colour-coded by HR zone
- **Live HRV** — rolling RMSSD (ms) over ~60s *(not shown live in WHOOP)*
- **HR Session** — min / avg / max BPM + elapsed time
- **HR Zone** — % of max HR with a zone-coloured bar

Zones derive from max HR (`220 - age`, editable).

## Trade-offs
- **One receiver at a time** — while connected, the band can't also broadcast to Peloton/Zwift/a watch.
- **Battery** — a persistent BLE connection + ~1 Hz redraws drains faster; stop streaming when done.
- **Range** — out of Bluetooth range shows last value / `--` and auto-reconnects.

## Requirements
- WHOOP 4.0 / 5.0 with active membership + **HR Broadcast enabled**
- Android 12 (API 31)+ — e.g. Pixel 9

## Status
- **v1.1** shipped: true 1:1 square widgets, live trend sparklines (HR/HRV/Zone).
- Built as a full Gradle project (command-line build, no Android Studio needed).
- **Phase 2 (not built):** recovery/strain/sleep widgets via WHOOP cloud OAuth — deferred because per-user dev credentials would break zero-config sharing.

> Unofficial. Not affiliated with or endorsed by WHOOP.
