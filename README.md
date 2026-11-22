# Island — Dynamic Island for Android

**Island** is an iPhone-style Dynamic Island overlay for Android built with **Kotlin, Jetpack Compose, Material 3, and DataStore**, paired with a **React + Vite + Tailwind** interactive web simulator and source code explorer.

---

## Project Structure

- `android-project/` — Complete Android Studio project (Min SDK 26, Target SDK 35) with foreground overlay service, spring-physics Jetpack Compose cards, live `MediaSessionManager` playback controls, app-exit morph transitions, and DataStore preferences.
- `src/` — Interactive web simulator mirroring `IslandStateManager.kt` in `src/island/islandReducer.ts`, plus a lazy-loaded Android Studio project explorer and `.zip` exporter.

---

## Features

- **Spring-physics floating capsule**: Morphs width, height, and corner radius smoothly above all apps via `TYPE_APPLICATION_OVERLAY` with tap-to-expand, long-press full view, swipe-up collapse, and haptic feedback.
- **12 Live System & Media Event Cards**:
  - Charging started (filling ring + bolt), charging stopped, battery full, low / critical battery (20% & 10%)
  - Wi-Fi connected / disconnected, personal hotspot on / off
  - Bluetooth connected / disconnected (headphone icon for audio devices, optional *Audio devices only* filter + 5-second per-device debounce)
  - Airplane mode, silent / vibrate / ring profile changes
  - Incoming calls with Accept / Decline actions (falls back safely to the system dialer if call-answering permission is unavailable)
  - Music player (Spotify, YouTube Music, etc.) with compact album art + GPU-driven equalizer, expanded seekbar updated locally via `withFrameMillis`, and play/pause/skip controls
- **App-Exit Morph Animation**: Detects when the active media app moves to the background (via Accessibility Service or Usage Access fallback), extracts the dominant album-art color with `Palette`, and morphs a rounded card smoothly into the island capsule.
- **Live Geometry & Behavior Controls**: Customize X/Y offset, idle width, height, corner radius, display duration (2s / 4s / 6s), auto-start on boot, fullscreen auto-hide, and per-event toggles.
- **Crash & Non-Fatal Diagnostics**: Retains the last 5 diagnostic entries (`[FATAL]` and `[NON-FATAL]`) in `Settings` with one-tap **Share** and **Dismiss**.

---

## Android Permissions & Why Each Is Needed

Island requests only `POST_NOTIFICATIONS` on first launch. Optional runtime permissions are requested on demand with an explanation dialog when you enable the related event card, and cards continue working with generic labels if declined.

| Permission | Purpose |
| --- | --- |
| `SYSTEM_ALERT_WINDOW` | Required to draw the floating Dynamic Island capsule above other apps. |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the overlay service alive reliably while showing a minimal silent notification. |
| `POST_NOTIFICATIONS` | Allows displaying the required foreground service notification on Android 13+. |
| `RECEIVE_BOOT_COMPLETED` | Restores the floating island automatically after reboot when *Auto-start on boot* is enabled. |
| `ACCESS_NETWORK_STATE` & `ACCESS_WIFI_STATE` | Detects Wi-Fi and hotspot state changes. |
| `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION` | Optional — required by Android to read the connected Wi-Fi network name (SSID). Falls back to `"Wi-Fi Network"` if denied. |
| `BLUETOOTH_CONNECT` | Optional (Android 12+) — reads connected Bluetooth device names and audio class. Falls back to `"Bluetooth Device"` if denied. |
| `READ_PHONE_STATE` & `ANSWER_PHONE_CALLS` | Optional — detects incoming calls and allows Accept / Decline buttons inside the capsule. Opens the system dialer if unavailable. |
| `PACKAGE_USAGE_STATS` | Optional fallback to detect foreground app changes for the app-exit animation if Accessibility is not enabled. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Granted via Notification Access settings; lets `MediaListenerService` discover active media controllers (Spotify, YouTube Music, etc.) via `MediaSessionManager`. |
| `BIND_ACCESSIBILITY_SERVICE` | Optional — watches `TYPE_WINDOW_STATE_CHANGED` package switches only (never reads screen text) for instant app-exit morph animations. |

---

## Running the Web Simulator

```bash
npm install
npm run dev
```

Open `http://localhost:3000` in your browser to test all 12 event cards, queue merging, spring physics, app-exit transitions, position/size sliders, and browse/export the Android project.

Run the reducer unit tests and type-check:

```bash
npm test
npm run lint
npm run build
```

---

## Opening `android-project/` in Android Studio

1. Open **Android Studio** (Ladybug / Jellyfish or newer with JDK 17+).
2. Choose **Open** and select the `android-project/` directory (or export it as a `.zip` from the web simulator header and extract it).
3. Wait for Gradle Sync to finish, connect an Android 8.0+ (API 26+) device or emulator, and click **Run**.
4. Inside the app, grant **Display over other apps**, and optionally enable **Notification & Media access** (for Spotify / YouTube Music) and **App-exit detection** (Accessibility or Usage Access).

---

## OEM Battery Optimization Notes

Aggressive background-process managers on some Android skins may kill the foreground overlay service or unbind the notification listener after the screen turns off. To keep Island running reliably:

- **Xiaomi / HyperOS / MIUI**: Open *Settings → Apps → Manage apps → Island*:
  - Enable **Autostart**
  - Set **Battery saver** to **No restrictions**
  - Enable **Other permissions → Display pop-up windows while running in the background**
- **Samsung (One UI)**: Open *Settings → Apps → Island → Battery* and choose **Unrestricted**. Also remove Island from *Settings → Battery → Background usage limits → Sleeping apps / Deep sleeping apps*.
- **Oppo / Realme / OnePlus (ColorOS / OxygenOS)**: Open *Settings → Apps → App management → Island → Battery usage*:
  - Enable **Allow foreground activity**, **Allow background activity**, and **Allow auto-launch**
  - Lock Island in the Recents screen if the service still gets paused during idle periods.
