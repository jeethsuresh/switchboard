# Switchboard TV 0.3.1

Lightweight native Android TV launcher for the TCL 65Q651G-CA (2024). Tested on the owner's Android 12 / API 31 TV. The launcher uses local icons, app history and shared Watch Next metadata with cached show artwork. No ads, analytics or background polling. PiP is optional experimental code, parked outside the normal launcher flow.

## Home and quick controls

Home starts at the center. One press in each direction reaches:

* **Left:** HDMI 2, with HDMI 1 above and HDMI 3 below. Connected CEC device names appear beneath the physical port number when exposed by TCL. Hold OK to rename a port.
* **Right:** a **4×4 app grid**, ordered by recently used apps. Move past the right edge for the next page, or past the left edge of a later page to return. Channel Down / Up also changes pages. Empty slots allow adding shortcuts. Newly installed apps appear automatically; utility apps are kept in All apps by default.
* **Up once:** **Recent app**, which opens the last used launchable app.
* **Up twice:** a horizontal **Continue Watching** row above Recent app. Use Left / Right to browse published shows and OK to resume; Down returns to Recent app.
* **Down:** **Settings**, including All apps, input/AirPlay/CEC shortcuts, remote mappings and the parked experiments.

Tap Home to return here. Hold the physical Home button for about half a second to open **Quick controls** above the current app or HDMI input. It uses the same directional layout and cached app icons. Back closes it and returns to the underlying screen. Opening an app or input from it intentionally switches to that selection. The owner verified overlay opening and Back behavior on the physical TV.

Hold OK on an app to rename, hide/show, or move it to a chosen position. Moving switches the grid to Custom order. Settings → App grid order returns to Recently used ordering or clears Switchboard's local app history. All apps includes hidden shortcuts and unavailable archived placeholders; those placeholders show a dialog instead of opening an installation page.

## Continue Watching

The top row reads searchable, browsable Android Watch Next entries published by installed streaming apps. It displays supplied thumbnail artwork (or poster artwork when no thumbnail is supplied), show title, episode information and playback progress. A dark gradient keeps labels readable, and the app icon is the fallback when artwork is missing or cannot load. It does not query streaming accounts. Google TV cloud-only entries and apps that do not publish shared rows will not appear.

Press Up twice from the center, then OK on **Enable Continue Watching** to grant Android's **Read all TV listings** permission. The ordinary launcher remains usable if permission is denied. The row has clear loading, empty, permission and provider-error states; Settings → Continue Watching offers refresh, permission information and restoration of hidden items.

Left / Right moves horizontally through the row, scrolling past its visible edges. Down returns to Recent app. Updates keep an existing focused show stable. Hold OK for details or to hide an item from Switchboard; this does not change the service or Google's row. A newly updated publication can reappear. Switchboard filters completed Continue entries, watchlist-only rows, hidden rows, duplicate publications, unavailable apps and unusable resume links. Resume intents are scoped to the publishing app so a bad link does not open another app or an installation page.

Queries run on a worker and refresh through a content observer, with no polling. Artwork loads on two background workers with bounded queues, a 6 MiB memory cache and a private disk cache capped at 24 MiB / 200 files. Decoded images are bounded to 768 pixels per dimension, downloads to 4 MiB, and disk images expire after 24 hours. Artwork URLs and viewing titles are not logged. Neither root nor the parked PiP helper is needed. Coverage and progress accuracy depend on the streaming apps. The shell itself lacks TV-listings permission, so an empty `adb shell content query` result cannot establish that the provider is empty.

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
* No clock ticker, autoplay, remote media feed or periodic refresh loop. Only publisher-supplied show artwork is fetched and cached, off the UI thread. Recent ordering is applied on returning Home, so cards do not move while navigating.
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

Optional helper build: `bridge/build-helper.sh`; start only when experimenting: `bridge/start-helper.sh TV_IP:5555`. The manifest Internet permission supports publisher-supplied artwork and the helper's authenticated loopback connection. There is no Switchboard backend. Do not export the helper's private key.

## Validation

* APK compilation and Android lint pass. Remaining lint warnings concern localization, legacy platform APIs, custom-view construction and existing manifest/theme choices; no lint errors.
* Pure Java HoldGate tests cover short taps, repeats, exact threshold, delayed callbacks and cancellation.
* Pure Java HomeNavigation tests cover all four startup destinations, HDMI ordering, all 16 grid cells, grid edges, two Up presses into Continue Watching, horizontal row movement and Down returning to Recent app.
* Pure Java WatchEntry tests cover publication ordering, duplicates, browsable flags, completed/watchlist filtering, hidden/re-published items and progress bounds.
* A read-only Android instrumentation probe found 17 usable items on the tested TCL and validated package-scoped resume links. It verified the actual native row's two-Up route, Left/Right browsing, scrolling to the end, and Down-to-recent off-screen. No playback or show titles were printed, and the screen remained asleep.
* The 0.3.1 probe loaded artwork for all four visible entries and checked the decoded size bound. Actual TV screenshots verified thumbnail appearance, focus after two Up presses, Right to the next thumbnail and Down to Recent app. No show was opened.
* TV UI dumps confirmed startup center focus; Left → HDMI 2, Up → Recent app, Right → Disney+ at the MRU grid head, Down → Settings.
* Owner verified the new quick-controls overlay opens over apps/HDMI and Back returns to the same screen.
* Actual TV settings showed all six preserved branded mappings, their tap/hold editor, and 800 ms duration. Long-press tile customization and persistent hiding were verified.
* Existing six branded mappings and hold duration are retained. Their original tap/hold/unavailable behavior was verified by the owner before this redesign; broader button delivery depends on the physical key and firmware.
* No new PiP playback claims are made. The optional shell helper was stopped after restoring experimental task layouts.

## Platform references

* [Accessibility key events](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent))
* [Android TV Watch Next programs](https://developer.android.com/training/tv/discovery/watch-next-add-programs)
* [TV Input Framework](https://source.android.com/docs/devices/tv)

### Optional on-device shared-show probe

Build `./gradlew assembleDebugAndroidTest`, install the test APK with `adb install -t`, and enable TV-listings permission for the main app. Run:

```sh
adb -s TV_IP:5555 shell am instrument -w ca.jeeth.switchboard.test/ca.jeeth.switchboard.WatchNextProbe
```

This prints provider package counts and the number of usable items; it does not print watched titles. It also renders a private, on-device PNG for visual layout checking; that PNG contains personal viewing titles and should stay out of public source control. Instrumentation can restart the app process, so reconnect the already-enabled remote guard afterward if it becomes unbound. The test APK is not needed for normal launcher use.
