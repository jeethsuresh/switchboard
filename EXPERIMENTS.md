# Parked video experiments — 2026-10-27

The owner paused PiP work to focus on the launcher. Do not resume probing, change CEC, or require the shell helper for normal launcher features unless asked. Both experiment menus remain in Settings → Experiments.

## Hardware and reproducible results

TCL 65Q651G-CA (2024), Android 12 / API 31, logical display 1920×1080 at density 320. Does not advertise Android PiP. Root HDMI inputs: com.tcl.tvinput/.TvPassThroughService/HW15 (1), HW16 (2), HW17 (3). CEC aliases are child inputs, not separate video pipelines. ADB: TV_IP:5555.

* Native experiment uses two TvView sessions, no helper or remote guard. A single main HDMI picture works. Adding/changing the inset now preserves the main session, and waits for main video before tuning the inset. With two HDMI sources, the owner reports one always black and the other intermittent; both devices stay awake. Changing HDMI sometimes reacquires the signal. Firmware may have one usable input path; this is not proven. CEC can take over fullscreen.
* Android no-video reason 2 is WEAK_SIGNAL, a provider report of no usable signal, not proof of a cable fault. Reason 6 indicates insufficient resources.
* App-window experiment uses the ADB shell helper and Android freeform windows, not native PiP. Owner saw Netflix above HDMI with both videos visible. Netflix inset was distorted, with a large black bottom region even during playback; other apps may behave similarly. YouTube initially took over fullscreen.
* YouTube redirects ShellActivity to MainActivity. Helper matching now follows package redirects, while matching our own HDMI host separately from the launcher.
* Freeform enforces minimum height ~440 px; a 576×324 request becomes 576×440. Stable helper grows the frame to a 16:9 minimum (~784×441). This alone does not remove the black region.
* Diagnostic hierarchy (retained in the original development workspace as work/netflix-views.txt) showed DecorCaptionView 784×441, content/gibbon/SurfaceView 784×357, caption 784×84; ITYPE_CAPTION_BAR reserved 84 px. The TCL freeform caption reservation is a likely cause, not a proven universal fix.

## Last unfinished approach (preserved, not active)

experiments/2026-10-27-pip/ShellBridge-root-container-unfinished.java.txt contains the failed attempt to put a fullscreen app inside a freeform root task to avoid DecorCaptionView. Two probes returned “TV did not expose the new inset container.” Matching createRootTask's Binder launch cookie through getRootTasks did not find it. No app was reparented. Do not deploy this snapshot as the working helper. Investigate root-task lifecycle/cookie identity only if PiP work is explicitly resumed. Do not delete unknown system roots.

bridge/ShellBridge.java retains the preceding direct-freeform approach, including redirected-app matching, layout fitting, reuse, authenticated transport and restoration. Its caption defect remains. BridgeLayoutTest and BridgeAuthTest exercise pure layout/matching/authentication. TV playback needs human verification.

## Helper and recovery

The optional helper runs as shell, binds TV localhost:38473, and uses a private random key with mutual HMAC authentication. Never export the key. Start with bridge/build-helper.sh then bridge/start-helper.sh TV_IP:5555. It does not survive reboot. Connection refused means helper is not running, not a video limitation.

The app stores previousFreeform, previousResize, cecBeforeLock and cecLocked in private preferences. Restore task windows before stopping a running helper. Keep ordinary apps fullscreen. CEC should normally remain enabled. No experimental settings are changed automatically by the launcher.

## Connection and accessibility gotchas

User authorizes terminal/ADB only, and forbids Mac desktop control. Do not use computer automation. Mac Codex lacks local-network access: preserve the Terminal-owned ADB server. NEVER kill/restart ADB server from Codex. If needed the user starts it in Terminal; Codex clients can then use localhost.

TCL keeps Google TV as preferred Home. Remote guard intercepts Home and redirects stock Home metadata. After APK replacement the service can be enabled but unbound: start MainActivity, preserve the complete enabled_accessibility_services list, remove only Switchboard, then restore the full list and accessibility_enabled=1. Verify Bound services, not just the enabled setting. Keep restoration in a finally block; user reboot once interrupted restoration.

Six saved branded key mappings: Netflix 4062:242; Prime 4099:752; YouTube 4065:245; TCL TV+ 4020:227; Apple TV 4160:652; Pluto 4163:649. Owner verified short taps do not launch installed apps, ~800ms holds launch, unavailable apps show overlays, and hold Home opens controls above HDMI/apps. Existing mappings must survive updates.
