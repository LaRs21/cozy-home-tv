# Changelog

## 1.1.0 (unreleased)

- New default look, the **Glass** theme (Apple TV-like): eight bundled public-domain (CC0) cozy wallpapers
  that crossfade about every 60 s with a gentle Ken Burns drift (Settings > Change picture, and Little movements),
  the clock top-right in a clean system font, a dock of large rounded tiles over a frosted strip near the bottom,
  and the all-apps grid on the same frosted panel when you scroll down. The background dims a little as you go down.
- Focus: the tile scales up 1.1x with a soft shadow, tilts slightly towards where you came from and settles,
  the picture nudges for a parallax feel, and a light sweep runs across it. App names appear under the focused tile.
- Themes are pluggable (`Theme` interface: background provider, layout, card style, clock font, sounds;
  registered in `Themes.ALL`). The 1.0 look is kept as the **Classic** theme. Settings > Theme.
- Screensaver: rotates the same wallpapers with a small corner clock (Glass). Classic and Cozy are unchanged.
- Memory: only the wallpaper on screen is decoded (at screen size, into GPU memory); the next one is decoded just
  before its crossfade and the old one is freed when the fade ends. All of them are freed when the system asks.
- Cozy mode is unchanged and still replaces the wallpapers with your own pack.
- CREDITS.md with photographer, source and licence for every picture.

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
