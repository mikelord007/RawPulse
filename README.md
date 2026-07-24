# RawPulse — Live WHOOP heart rate on your Android home screen

RawPulse is a tiny Android app that shows your **live heart rate on a home‑screen
widget, updating about once per second** — something the official WHOOP app does not
offer. It also derives extra live metrics that WHOOP doesn't show in real time,
including **live HRV**.

It works by reading the standard Bluetooth **Heart Rate broadcast** that a
WHOOP 4.0 / 5.0 band emits (the same signal Peloton, Zwift and Garmin read). Nothing
goes through WHOOP's servers, there is no login, and there is **no per‑user setup** —
if you can turn on HR Broadcast in the WHOOP app, RawPulse works.

> **Unofficial.** Not affiliated with, or endorsed by, WHOOP. "WHOOP" is a trademark
> of its respective owner and is used here only to describe compatibility.

---

## Why not just use the WHOOP API?

The official WHOOP Developer API is **cycle‑based** (recovery, strain, sleep) and does
**not** expose real‑time or continuous heart rate. Per‑second HR is only available
locally, over Bluetooth, via the band's standard **Heart Rate Service** (`0x180D`,
characteristic `0x2A37`). RawPulse subscribes to that stream directly.

---

## Widgets

All widgets are square‑ish tiles you add to your home screen. They update live while
streaming is on.

| Widget | Shows |
| --- | --- |
| **Live Heart Rate** | Big BPM number, colour‑coded by heart‑rate zone (the main 1:1 tile) |
| **Live HRV** | Rolling RMSSD in ms over the last ~60s — *not shown live in the WHOOP app* |
| **HR Session** | Min / avg / max BPM and elapsed time since you started streaming |
| **HR Zone** | Percent of your max HR with a zone‑coloured bar |

Heart‑rate zones are derived from your max HR (estimated as `220 − age`, editable in the app).

---

## How it works (architecture)

- A **foreground service** holds the BLE connection to the WHOOP and pushes each new
  reading to the widgets. This is required because Android's built‑in widget refresh
  has a 30‑minute minimum — real per‑second updates have to be pushed from a service.
- A persistent notification shows the current BPM and a **Stop** button.
- `WhoopHrManager` scans for the band's HR broadcast, connects, and decodes `0x2A37`.
- `HrRepository` is the shared source of truth; `HrService` and the widgets read from it.

Trade‑offs to know:

- **One receiver at a time.** While RawPulse is connected, your WHOOP can't also
  broadcast to another device (Peloton, Zwift, a watch, etc.) simultaneously.
- **Battery.** A persistent BLE connection plus ~1 Hz widget redraws uses noticeably
  more battery than an idle phone. Stop streaming from the notification when you're done.
- **Range.** If the band goes out of Bluetooth range, the widgets show the last value /
  `--` and the app auto‑reconnects when it's back.

---

## Requirements

- A **WHOOP 4.0 or 5.0** with an active membership, with **Heart Rate Broadcast** enabled
  in the WHOOP app (device settings → HR Broadcast).
- An Android phone on **Android 12 (API 31) or newer** (e.g. Pixel 9).

---

## Install

RawPulse isn't on the Play Store (see the license/rebuild note above about zero‑setup
distribution) — install it directly:

### Option A — Obtainium (recommended, gets you auto‑updates)

[Obtainium](https://github.com/ImranR98/Obtainium) tracks an app's GitHub Releases and
notifies you (with in‑place updates) whenever a new one is published — no Play Store
needed.

1. Install Obtainium (from its own [releases page](https://github.com/ImranR98/Obtainium/releases)
   or F‑Droid).
2. In Obtainium, tap **Add App** and paste this repo's URL:
   `https://github.com/mikelord007/RawPulse`
3. Obtainium will find the latest release's APK automatically. Install it, and future
   releases will show up as updates in Obtainium.

Every release APK is signed with the same dedicated RawPulse release key, so updates
install cleanly in place without needing to uninstall first.

### Option B — Download the APK directly

Grab `app-release.apk` from the [latest GitHub release](https://github.com/mikelord007/RawPulse/releases/latest),
transfer it to your phone, tap it in Files, and allow "install from this source." You'll
need to repeat this manually for future versions (Obtainium does this part for you).

### Option C — Build from source

See [Build & install (command‑line)](#build--install-commandline-no-android-studio) below.

---

## Build & install (command‑line, no Android Studio)

You only need a JDK and the Android command‑line SDK tools — not the full IDE.

### 1. Install a JDK 17

Install **Eclipse Temurin 17** (https://adoptium.net). Confirm:

```bash
java -version    # should report 17.x
```

Set `JAVA_HOME` to the JDK folder if it isn't already.

### 2. Install the Android command‑line SDK tools

- Download "Command line tools only" from https://developer.android.com/studio#command-line-tools
- Unzip to e.g. `C:\Android\cmdline-tools\latest\` (the `bin` folder must sit directly
  under `latest`).
- Set environment variables:
  - `ANDROID_HOME = C:\Android`
  - add `C:\Android\cmdline-tools\latest\bin` and `C:\Android\platform-tools` to `PATH`
- Install the needed packages and accept licenses:

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
sdkmanager --licenses
```

### 3. Build the APK

From the project root:

```bash
# Windows PowerShell / cmd
.\gradlew.bat assembleDebug

# Git Bash / macOS / Linux
./gradlew assembleDebug
```

The APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

(The Gradle wrapper is committed, so you don't need to install Gradle separately.)

### 4. Put it on your Pixel 9

**Option A — via USB (adb):**

1. On the phone: **Settings → About phone → tap "Build number" 7 times** to unlock
   Developer options.
2. **Settings → System → Developer options → enable "USB debugging".**
3. Plug the phone into the PC, accept the "Allow USB debugging?" prompt.
4. Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Option B — copy the file:** transfer `app-debug.apk` to the phone (email, Drive, USB),
tap it in Files, and allow "install from this source".

### 5. First run

1. Open **RawPulse**, grant Bluetooth + notification permissions.
2. In the **WHOOP app**, enable **Heart Rate Broadcast**.
3. Tap **Start streaming**. The BPM should appear within a few seconds.
4. Long‑press the home screen → **Widgets** → **RawPulse** → drag the tiles you want.

**Demo mode** (in the app) feeds a simulated heart rate so you can verify the widgets
update without wearing the band.

> Prefer a GUI? **Android Studio** does steps 1–3 with one **Run** click, but it's a
> multi‑GB install; the command‑line path above is much smaller.

---

## Project layout

```
app/src/main/java/com/rawpulse/hr/
  ble/WhoopHrManager.kt      # BLE scan/connect + decode 0x2A37
  data/                      # HrRepository, Metrics (RMSSD), Settings, models
  service/HrService.kt       # foreground service, pushes widget updates + notification
  widget/                    # 4 AppWidgetProviders + WidgetUpdater + styling
  MainActivity.kt            # permissions, start/stop, settings, demo mode
```

---

## Roadmap / possible Phase 2

- **Recovery / Strain / Sleep widgets** via the WHOOP cloud API (OAuth2). Deliberately
  left out for now because it would require every user to register their own WHOOP
  developer credentials, which breaks the zero‑setup, easy‑to‑share design.

---

## Privacy

RawPulse collects no data and has no servers — everything happens on your
phone. See the [privacy policy](https://mikelord007.github.io/RawPulse/privacy/)
(or [PRIVACY.md](PRIVACY.md) directly) for the full policy.

---

## License

MIT — see [LICENSE](LICENSE).
