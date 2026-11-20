# TCL 55" Google TV (G03_4K_US_NF / BeyondTV4, Android 11, AR21): what was done

All commands run from the repo root. ADB must be on again for any of them (Developer options > USB/Network
debugging). On macOS, if `adb connect` says "No route to host", run `adb kill-server` from your terminal first.

    export TV_HOST=<tv ip>

## What changed
- 34 system packages **disabled** (not uninstalled), in the batches `my-tv/b1-*.txt` .. `my-tv/b7-*.txt`.
  Kept: YouTube, Chromecast, TCL firmware updates, Play Store/Services, Live TV + inputs, remote, keyboard.
- Home: `com.gathin.tvhome` (built from `launcher/`); Google TV's `launcherx` disabled.
- Screensaver: launcher's clock (`com.gathin.tvhome/.ClockDream`) instead of Google's ambient mode.
- Forgot one old hidden 2.4 GHz Wi-Fi network owned by the setup wizard (current Wi-Fi untouched).
- Backup of the original state: `backup/`. Screenshots: `shots/`.

## Re-enable one app
    tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.netflix.ninja'

## Undo everything
    my-tv/restore-all.sh

## Change the dock (order, apps) or wallpaper
Edit `launcher/app/src/main/res/values/config.xml`:
- `dock_apps`: package names in order (now SmartTube, HDMI 3, VLC, Play Store).
- The HDMI 3 tile is `com.tcl.tv` + `launch_actions` (`input:...HW1413744640`) + `app_labels` (`HDMI 3`).
  Other ports: HDMI 1 = `HW1413744128`, HDMI 2 = `HW1413744384`.
Then rebuild and install (Docker, no JDK needed):

    tools/docker-build.sh && SKIP_BUILD=1 launcher/build-and-install.sh

## Weather (personal change to the launcher)
Under the clock: now + today's high/low, and the next 6 hours, from api.open-meteo.com (no key, no account),
fetched only while Home is on screen, every 30 min. Code: `launcher/.../Weather.java`, the `weather` block in
`res/layout/activity_main.xml`, the INTERNET permission in `AndroidManifest.xml`. Settings in `config.xml`:
`weather_latitude` / `weather_longitude` (38.95 / -77.40 = McNair, Herndon VA; empty both = off, no requests)
and `weather_fahrenheit`. Rebuild with the command above after a change.

## Photo wallpapers (theme `../my-theme` on the Mac; only its config.json is saved here, in my-tv/theme/ - the family photos stay out of git)
`my-theme/config.json`: shuffle every 1 min, no dock band, no app name above the dock, no colour glow.
Pictures: `my-theme/wallpapers/01.jpg..` (1920x1080, <300 KB), made from `../wallpaper_images_raw` with a top
shade for the clock/weather (staging script, then `tools/wallpaper_prep.py --size 1920x1080 --brightness 0.8
--gradient 0.5 ../wallpaper_staging ../my-theme/wallpapers`). Push changes: `tools/theme.sh ../my-theme`.
Undo (back to the plain look): `tools/theme.sh --reset`.
No peeking row under the dock: a personal change in `MainActivity.java` (search "the next row no longer peeks")
clips the grid under the dock row while it is scrolled to the top; Down shows the rest of the apps as usual.

Wallpaper / look without rebuilding: push a theme, e.g. `tools/theme.sh themes/glass` (see `themes/README.md`;
a theme's `config.json` overrides `config.xml`, so copy the dock keys into it).
