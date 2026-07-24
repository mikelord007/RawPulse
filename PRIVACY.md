# Privacy Policy — RawPulse

**Last updated: 2026-07-24**

RawPulse is a small, independently developed Android app. This policy explains
what data the app touches and — more importantly — what it does not do with it.

## Short version

RawPulse does not collect, store, transmit, or share any personal data.
Everything it reads stays on your phone.

## What data RawPulse accesses

- **Heart rate (BLE).** RawPulse connects directly to your WHOOP band over
  standard Bluetooth Low Energy (the Heart Rate Service, `0x180D`) to read live
  BPM readings, the same signal your WHOOP already broadcasts to apps like
  Peloton, Zwift, or Garmin.
- **Derived metrics.** From that raw BPM stream, the app computes things like
  live HRV (RMSSD), session min/avg/max, and heart-rate zone — entirely on
  your device, in memory.
- **Age.** You may enter your age in the app to improve the estimated max-HR
  (`220 − age`) used for zone calculations. This is stored locally in app
  settings on your phone.

## What RawPulse does NOT do

- No account, sign-up, or login of any kind.
- No connection to WHOOP's cloud API or servers — RawPulse talks to your band
  directly over Bluetooth and never touches your WHOOP account or credentials.
- No analytics, crash reporting, ads, or third-party SDKs.
- No data is ever sent off your device. There are no RawPulse servers to send
  it to.
- No location data is collected. (Android requires the Bluetooth-scan
  permission to be grouped with location for BLE scanning on some OS
  versions, but RawPulse does not read or use your location.)

## Data storage

Heart-rate readings and derived metrics live only in memory while the app is
running and are discarded when you stop streaming or close the app. Your age
setting is stored locally via Android's app preferences and is removed if you
uninstall the app. Nothing is backed up to a cloud service by RawPulse itself
(though your phone's own OS-level backup, if enabled, may include local app
settings — that's controlled by your device, not by RawPulse).

## Permissions

RawPulse requests:

- **Bluetooth** (scan/connect) — required to read your WHOOP's HR broadcast.
- **Notifications** — to show the persistent streaming notification with a
  Stop button while the foreground service is active.

No other permissions are requested.

## Third parties

RawPulse is not affiliated with, endorsed by, or connected to WHOOP, Inc.
"WHOOP" is used only to describe compatibility with WHOOP devices. RawPulse
does not share any data with WHOOP or any other third party, because it does
not collect any data to share.

## Changes to this policy

If RawPulse's data practices ever change (for example, an opt-in Phase 2
integration with WHOOP's cloud API), this page will be updated first, and the
change will be reflected in the app's release notes.

## Contact

Questions about this policy can be raised via a
[GitHub issue](https://github.com/mikelord007/RawPulse/issues) on this
repository.
