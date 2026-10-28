# Switchboard TV

Native Android TV launcher built for the TCL 65Q651G-CA (2024), tested on the owner's Android 12 / API 31 firmware. This is a development build, with experimental video features.

## Launcher and remote

- Home, All apps, Favorites, and TV inputs; all app and icon discovery is local.
- Hold OK on a tile to move, rename, pin, or unpin it. Ordering and labels persist.
- Archived app placeholders are recognized and open an unavailable-app dialog instead of their reinstall activity.
- Tap Home to open Switchboard. Hold Home for approximately half a second to open an overlay above the current app or input.
- The overlay contains app and input shortcuts and two separate video experiments.
- Six-button setup covers Netflix, Prime Video, YouTube, TCL TV+, Apple TV, and Pluto TV. Mapped app buttons require a configurable hold (800 ms by default); short taps show a hint or unavailable-app overlay. The owner verified these behaviors on the physical remote.
- Home interception and branded-button filtering use an Accessibility service that cannot retrieve screen contents. Home interception can be switched off in Switchboard Settings.

TCL keeps Google TV as its preferred system Home on the tested firmware even after `set-home-activity` reports success. The remote guard handles Home and redirects Google TV Home window events. Google TV remains installed.

## Experiment 1: HDMI-only PiP (no helper)

From Switchboard Home or Inputs, select **HDMI PiP · no helper** → **Choose two HDMI sources and start**. This entry does not require the remote guard. Hold Home offers the same menu when the guard is connected. The experiment also has a **PiP controls** button and a Menu-key shortcut.

This is the original approach: two Android `TvView` sessions render two passthrough inputs inside one activity. It does not embed a streaming app. It offers source selection, swap, inset position, and remote focus selection. No ADB helper or Accessibility service is needed to request the two video sessions or to exit the native experiment. **Single HDMI baseline · no inset** tests one video session before adding a second. From the running experiment, choose **Add / change inset · keep main session**. Removing or changing an inset preserves a working main session; the second session waits until the main reports video available. Changing the main input or leaving the activity still releases/retunes it. The app-window and native source selections are kept separate.

The firmware has previously reported no video, switched to a full-screen CEC source, and put a connected device in standby during testing. The experiment is retained at the owner's request. The owner confirmed video from a single main input; adding an inset failed in the previous build. Two simultaneous live pictures are not yet verified. A second TV-input session may be unsupported even if CEC interference is removed.

Reason 2 is Android's `VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL`: the input provider reports weak/no usable video. It does not diagnose a bad cable or prove which part of the HDMI path failed. Reason 6 means insufficient video resources.

CEC can be changed manually through **CEC settings / fullscreen switching**. Power both devices on, temporarily disable HDMI Control in TV settings, and compare the single-input baseline and two-input experiment. Restore the preferred CEC setting afterward. The owner observed that both devices remained awake while TCL eventually opened its own fullscreen HDMI player; CEC involvement is still a hypothesis pending the comparison. The helper's optional CEC lock is another way to isolate CEC behavior while testing this experiment.

## Experiment 2: app-window multiview (helper required)

Hold Home → **App-window multiview · helper required**.

The source picker permits HDMI or an installed app in either position. This requests Android freeform app windows; it is not native Android PiP. The TV does not advertise Android PiP support. Streaming playback, resizing, audio, remote focus, and combined HDMI/video behavior require actual testing per app.

1. Install the launcher and enable its remote guard.
2. Start the scoped ADB helper from the computer:

   ```sh
   bridge/start-helper.sh TV_IP:5555
   ```

3. In the app-window setup menu, check helper status.
4. Select **Enable freeform app windows**, if needed. This explicitly changes the Android freeform and forced-resize developer settings and saves their previous values.
5. Restart the TV, reconnect ADB, and run `start-helper.sh` again. The helper does not survive a TV reboot; restart it after launcher updates too. The startup script verifies an authenticated status response, rather than relying only on a process ID. The setup menus are accessible directly from the launcher without the remote guard.
6. Choose main and inset sources and select **Start / apply experiment**.
7. Hold Home for **Control inset with remote**, **Control main with remote**, size, corner, swap, or exit.

The helper checks the resulting task window mode and reports a refused request as a failure. A successful window request still needs visual verification and playback testing. App-specific minimum window sizes and activity behavior may prevent the desired layout.

### Optional CEC lock

The app-window controls include **Lock CEC during experiment**. This temporarily disables HDMI-CEC as a whole; it also affects CEC remote, volume, and power control. The previous value is saved, and **Unlock CEC** or **Exit experiment** requests restoration. Power devices on before locking if they rely on CEC to wake.

If a crash or reboot interrupts the experiment, verify HDMI Control in TV Settings. The recovery command below enables CEC. A helper failure is not evidence of a video hardware limit.

### Helper access

The helper runs as the ADB shell, without root. Its commands are limited to task layout/focus, the explicit freeform settings, and the explicit CEC lock. Android blocked the initial cross-UID Unix-socket transport, so the helper now listens only on `127.0.0.1:38473`. Requests use mutual HMAC-SHA256 challenge authentication with a random key stored in the launcher's private app files; the key is never sent over the connection. The Internet permission is used for this on-device connection. There are no analytics or external network endpoints in the launcher.

## Build and install

Requires JDK 17 or newer (JDK 21 used here), Android SDK platform 36, Android build tools, and Gradle 8.14.6. The project uses Android Gradle Plugin 8.11.1 and no third-party runtime UI libraries.

```sh
./gradlew assembleDebug lintDebug
bridge/build-helper.sh
adb connect TV_IP:5555
adb -s TV_IP:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s TV_IP:5555 shell am start -n ca.jeeth.switchboard/.MainActivity
```

The helper scripts default to this Mac's installed SDK and Android Studio JDK. Set `ANDROID_HOME`, `JAVA_HOME`, or `ADB` for another environment.

## Recovery

Switchboard Settings → **Home button behavior** can disable Home interception. Disable **Switchboard remote guard** in the TV's Accessibility settings to stop all interception.

```sh
# Restore the stock Home selection (also disable Switchboard's Home override):
adb shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity

# Enable CEC if an interrupted experiment left it disabled:
adb shell cmd hdmi_control cec_setting set hdmi_cec_enabled 1
```

The app-window setup menu restores the previously saved freeform/forced-resize settings. Restart the TV afterward. Existing launcher preferences and remote mappings are retained when updating with `adb install -r`.

## Validation

- APK compilation and Android lint pass (warnings remain for prototype localization, compatibility, and TV layout choices).
- Hold-gate tests cover short taps, repeats, exact hold thresholds, single launch, release, and cancellation.
- Helper authentication tests cover a valid client and rejection of a wrong key.
- The physical remote's six branded-button mappings and short-tap/hold behavior were verified by the owner.
- The owner confirmed that holding Home opens the overlay.
- The HDMI experiment menu was verified on the TV with the remote guard disabled and the helper stopped, then both were restored.
- The inset-preservation fix compiles and passes lint; playback after this change still needs a physical test.
- After the owner enabled app windows and restarted the TV, the helper passed an authenticated status check with freeform and forced resize both set to 1. The remote guard was also confirmed bound after reboot.
- The helper accepted a freeform window for Switchboard itself after reboot (mode 5, requested bounds `[1320,80][1896,404]`, actual bounds `[1320,80][1896,520]`). The firmware imposed a taller minimum window. Fullscreen and saved layouts were restored afterward. This does not verify streaming playback or two-source video.
- Simultaneous HDMI pictures, streaming playback in an inset, and focus routing remain experimental and are not claimed as verified.

## Platform references

- [Android TV multitasking](https://developer.android.com/training/tv/get-started/multitasking)
- [TV Input Framework](https://source.android.com/docs/devices/tv)
- [HDMI-CEC service and privileged access](https://source.android.com/docs/devices/tv/hdmi-cec)
- [Android input video-unavailable codes](https://developer.android.com/reference/android/media/tv/TvInputManager#VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL)
