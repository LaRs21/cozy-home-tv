# Changelog

## 1.0.0 (unreleased)

First public version.

- Default look with zero setup: dark background, clock and date, a favourites row and an all-apps grid
  with 16:9 banners, smooth D-pad focus (lift + ring), no pictures and no music.
- Hold OK (or press Menu) on an app: add/remove favourite, move (reorder), hide, app info, uninstall.
  Hidden apps can be shown again in Settings.
- Apps appear and disappear by themselves when installed or removed (including installs missed while
  another app was in front).
- Cozy look (opt-in): your own picture, or one per time of day, optional looping music with a volume setting,
  optional card pictures for favourite apps and soft fireflies, read from `/sdcard/CozyHome/`
  (or the app's own folder) with an optional `config.json`.
- Pictures are decoded once at screen size (never upscaled in memory) into GPU memory; only the current
  time of day is kept. Music and animations stop as soon as an app opens; pictures are freed when the
  system asks for memory.
- Screensaver: your picture and a small clock (Cozy), or a big clock on a dark background (Default).
  The clock moves every 75 s against burn-in.
- Optional Home-button fix for Google TV (accessibility service, off until you turn it on): opens
  Cozy Home TV directly instead of flashing the Google TV home, and once after boot.
- Inputs button that opens the TV's own Live TV / HDMI input app when it has one.
- Experimental live background (looping base clip + one-shot moments) for Cozy packs. No clips included.
- Scripts: install, set as home, push a Cozy pack, reversible debloat with a protected list, restore.
