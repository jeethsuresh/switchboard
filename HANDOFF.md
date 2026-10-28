# Launcher handoff — 2026-10-27

## Current direction

The owner paused PiP after extended firmware/video experiments. Prioritize the best possible fast, icon-only launcher. Keep both experiments available, with no helper dependency in the ordinary launcher. Do not resume PiP investigation unless asked. Full video findings and recovery notes are in EXPERIMENTS.md.

## Installed result

Switchboard TV 0.2.0 (versionCode 2), package ca.jeeth.switchboard, is installed on TCL 65Q651G-CA at TV_IP:5555. Native Java Views, no third-party UI libraries or media feeds. Home starts focused in the center: Left HDMI2 (Up HDMI1 / Down HDMI3), Right 4×4 recently-used app grid, Up last used app, Down Settings. Grid pages at horizontal edges; Channel +/- also page. Hold OK opens rename/move/hide controls; custom order is optional. Utilities remain in All apps. TCL Home Passive was hidden through the UI and its preference persistence was verified.

Hold physical Home ~500ms opens the same directional controls as an accessibility overlay above HDMI/apps; Back closes it. Owner explicitly verified both behaviors in the new design. The remote guard is bound, and six existing branded mappings are preserved. Their tap/hold actions are editable individually; Learn another button captures additional keys delivered by Android. Arrows, OK/Enter, Input, Home and Back are reserved. Firmware-owned power/voice/vendor keys may not be delivered.

Resume tracks local app usage from Switchboard launches and foreground accessibility window metadata. No content is read. The service cannot retrieve window contents. TV settings/native input utility activities are excluded from new content-app recency. Episode-level Continue Watching was deliberately not invented; services control playback progress.

## Code map

* MainActivity: thin lifecycle, retained native DashboardView, catalog subscription.
* DashboardView + HomeNavigation: fixed layout, explicit focus graph, reusable Home/overlay view.
* AppRepository: process-wide async package/icon cache; input callbacks update inputs only.
* Store: labels, hidden shortcuts, optional custom ordering, recent-app timestamps, legacy-compatible remote preferences.
* Actions + LauncherSettings: shared shortcut execution, settings, tile customization and tap/hold mapping editor.
* RemoteGuardService: Home interception, per-physical-key hold gate, learning, configurable actions, app metadata history and stock Google TV Home fallback.
* OverlayMenus: Quick controls dashboard; experiment menus are reached through Settings only.
* MultiViewActivity / MultiViewController / bridge: parked video code. The failed root-container helper was snapshotted separately; active helper source is the preceding direct-freeform version.

## Validation performed

Build assembleDebug + lintDebug passed (no errors). tests/run-tests.sh passed HoldGate and HomeNavigation tests. Preserved helper BridgeLayoutTest passed. Actual TV UI dumps verified center and all four startup destinations. Final settings UI showed all six mappings with original targets, 800ms duration, and the Learn another button entry. Preference values were checked for preservation. Actual long-press tile menu and persistent Hide from Home were tested. Owner verified overlay opening/closing from apps/HDMI.

The optional shell helper was asked to restore experimental task layouts, then stopped. It is not running. Existing experimental developer settings were left available; no TV restart was requested. CEC was left enabled. Normal launcher use does not contact the helper. Android's package compiler successfully precompiled Switchboard using speed mode.

The broader physical-button remapping capability still depends on firmware delivering each key. Do not imply every hardware key was physically tested. Existing six-button behaviors were owner-verified before this redesign; the new overlay was owner-verified after it.

## Continue safely

User authorizes TERMINAL/ADB ONLY, explicitly forbids Mac desktop control. Preserve the Terminal-owned ADB server: never kill/start ADB server from Codex. Mac local-network privacy prevents Codex-owned server connectivity. No need to change Mac permissions.

After replacing the APK, the TV can leave the guard enabled but unbound. Start MainActivity first; preserve the complete enabled_accessibility_services value, remove only Switchboard, then restore the exact list plus accessibility_enabled=1, with restoration in a finally block. Verify Bound services in dumpsys accessibility. The original development workspace has a local work/rebind-remote.py script implementing this; it is not part of the repository. Do not ask the owner to repeat setup unnecessarily.

Build offline with cached tools:

```sh
./gradlew --offline --no-daemon assembleDebug lintDebug
```

JDK is Android Studio's bundled JBR; ADB is $ANDROID_HOME/platform-tools/adb. The source is tracked at https://github.com/jeethsuresh/switchboard on main. Source archive excludes generated builds, local.properties, caches and private helper keys. The debug signing key remains outside the project in the existing development environment; retain it for compatible updates and run-as helper experimentation.

The original development workspace also contains SwitchboardTV-0.2.0.apk, SwitchboardTV-source.zip and SwitchboardTV-home.png. Generated binaries and private connection details are not tracked in Git. APK SHA256: 72b48c6675ded01fb12302c09ca577dbd8e6a6c4f37e579ef708879cd46431d0.
