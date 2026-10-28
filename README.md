# Switchboard TV 0.2.0

Lightweight native Android TV launcher for the TCL 65Q651G-CA (2024). Tested on the owner's Android 12 / API 31 TV. The launcher uses local icons and app history, with no media feed, artwork downloads, ads, analytics or background polling. PiP is optional experimental code, parked outside the normal launcher flow.

## Home and quick controls

Home starts at the center. One press in each direction reaches:

* **Left:** HDMI 2, with HDMI 1 above and HDMI 3 below. Connected CEC device names appear beneath the physical port number when exposed by TCL. Hold OK to rename a port.
* **Right:** a **4×4 app grid**, ordered by recently used apps. Move past the right edge for the next page, or past the left edge of a later page to return. Channel Down / Up also changes pages. Empty slots allow adding shortcuts. Newly installed apps appear automatically; utility apps are kept in All apps by default.
* **Up:** **Resume**, which opens the last used launchable app. Streaming services retain responsibility for their own episode/playback progress; this does not claim universal Continue Watching data.
* **Down:** **Settings**, including All apps, input/AirPlay/CEC shortcuts, remote mappings and the parked experiments.

Tap Home to return here. Hold the physical Home button for about half a second to open **Quick controls** above the current app or HDMI input. It uses the same directional layout and cached app icons. Back closes it and returns to the underlying screen. Opening an app or input from it intentionally switches to that selection. The owner verified overlay opening and Back behavior on the physical TV.

Hold OK on an app to rename, hide/show, or move it to a chosen position. Moving switches the grid to Custom order. Settings → App grid order returns to Recently used ordering or clears Switchboard's local app history. All apps includes hidden shortcuts and unavailable archived placeholders; those placeholders show a dialog instead of opening an installation page.

## Remote customization

Settings → Remote buttons lists the six existing TCL branded mappings: Netflix, Prime Video, YouTube, TCL TV+, Apple TV and Pluto TV. Existing mappings survive an update.

* Default: short tap gives a hold hint, or an unavailable-app overlay; hold for 800 ms opens the installed app. Hold duration can be 600, 800 or 1200 ms.
* Select a mapping to assign independent **tap** and **hold** actions: an installed app, a TV input, Quick controls, Switchboard Settings, All apps, All inputs, or do nothing. A tap may also keep the original hold-hint/unavailable-overlay behavior.
* **Learn another button** captures additional Android key events. Volume, mute, voice, menu, channel, media and color keys are eligible if Android delivers them. TCL/Android may consume power, voice or other vendor buttons first; those cannot be guaranteed.
* Arrows, OK/Enter, Input, Home and Back remain reserved for navigation and recovery. Home behavior is controlled separately.
* **Restore original button behavior** removes the entire mapping and returns that button to TCL/Android. Mapping a button replaces its normal action; there is no privileged key injection or PiP helper involved.

The remote guard uses Accessibility key filtering and foreground window metadata. It **cannot retrieve screen contents**. App-use timestamps stay in private TV preferences. No usage-access permission is needed. Foreground app history is most complete while the guard is connected; launches from Switchboard are tracked directly too.

TCL keeps Google TV as preferred system Home on this firmware, even after set-home-activity reports success. The guard handles Home and redirects stock Google TV Home metadata. Disable Home override in Settings → Remote guard / Home behavior to stop that behavior. Disable the service in TV Accessibility settings to stop all interception.

## Performance design

* Native Java Views, fixed directional focus graph, no WebView or third-party UI framework.
* The same Home view hierarchy is reused; app launches and returning Home do not rescan packages.
* A process-wide background catalog discovers apps and decodes bounded 96×96 icons once. Package-change broadcasts invalidate app data; TV input callbacks refresh input data without reloading app icons.
* No clock ticker, media feed, network fetches or periodic refresh loop. Recent ordering is applied on returning Home, so cards do not move while navigating.
* The optional PiP helper is not started or contacted during normal launcher use.

## Parked experiments and handoff

Settings → **Experiments · parked** retains both HDMI-only PiP (no helper or remote guard needed) and app-window multiview (optional shell helper needed). Current launcher state is in [HANDOFF.md](HANDOFF.md). Video results, failures, caption/aspect-ratio investigation, connection recovery and unfinished source are documented in [EXPERIMENTS.md](EXPERIMENTS.md). The failed root-container attempt is preserved separately; the working helper source has been restored to the preceding direct-freeform approach. Both approaches remain experimental.

## Build and install

Requires JDK 17+, Android SDK platform 36 and Gradle 8.14.6; AGP 8.11.1. No third-party runtime dependencies.

```sh
./gradlew assembleDebug lintDebug
adb connect TV_IP:5555
adb -s TV_IP:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s TV_IP:5555 shell am start -n ca.jeeth.switchboard/.MainActivity
```

On the development Mac, preserve the Terminal-owned ADB server: **do not kill or restart it from Codex**. Only terminal/ADB use is authorized; no Mac desktop automation. See EXPERIMENTS.md for connection details and accessibility rebind steps after an APK replacement.

Optional helper build: `bridge/build-helper.sh`; start only when experimenting: `bridge/start-helper.sh TV_IP:5555`. The helper's authenticated loopback connection accounts for the manifest Internet permission. There are no external endpoints. Do not export its private key.

## Validation

* APK compilation and Android lint pass. Remaining lint warnings concern localization, legacy platform APIs, custom-view construction and existing manifest/theme choices; no lint errors.
* Pure Java HoldGate tests cover short taps, repeats, exact threshold, delayed callbacks and cancellation.
* Pure Java HomeNavigation tests cover all four startup destinations, HDMI ordering, all 16 grid cells and grid edges.
* TV UI dumps confirmed startup center focus; Left → HDMI 2, Up → Resume Disney+, Right → Disney+ at the MRU grid head, Down → Settings.
* Owner verified the new quick-controls overlay opens over apps/HDMI and Back returns to the same screen.
* Actual TV settings showed all six preserved branded mappings, their tap/hold editor, and 800 ms duration. Long-press tile customization and persistent hiding were verified.
* Existing six branded mappings and hold duration are retained. Their original tap/hold/unavailable behavior was verified by the owner before this redesign; broader button delivery depends on the physical key and firmware.
* No new PiP playback claims are made. The optional shell helper was stopped after restoring experimental task layouts.

## Platform references

* [Accessibility key events](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent))
* [Android TV Watch Next programs](https://developer.android.com/training/tv/discovery/watch-next-add-programs)
* [TV Input Framework](https://source.android.com/docs/devices/tv)
