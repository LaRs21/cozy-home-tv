# Cozy Home TV

**A fast, clean home screen for Android TV and Google TV, with an optional cozy look made from your own
pictures and music.**

Out of the box it is a calm, Alpine-style launcher: a clock, your favourite apps, every other app in a grid,
no ads, no rows of recommendations. Switch on the **Cozy look** and your home screen becomes a picture you
love (one picture, or a different one for morning, day, evening and night) with your own music playing softly
while you choose what to watch.

![Default look](docs/screenshots/default.jpg)
![Cozy look with the sample pack](docs/screenshots/cozy.jpg)

> Screenshots from the Android TV emulator. Photos on a real TV coming soon.

## Features

- **Default look, zero setup:** dark background, clock and date, favourites row, all-apps grid with TV banners,
  smooth remote navigation. No pictures, no music, nothing running in the background.
- **Your apps, your order:** hold OK (or press Menu) on an app to add it to favourites, move it, hide it,
  open its info or uninstall it. New apps show up by themselves.
- **Cozy look (optional):** your own picture(s), time-of-day switching, looping music with a volume setting,
  your own card art for favourite apps, soft fireflies. All from a folder on the TV.
- **Screensaver:** your picture with a small clock, or a big clock on a dark background.
- **Home-button fix for Google TV (optional):** stops the Google TV home flashing up before your launcher.
- **Light on memory:** made for 2 GB TVs. Pictures are decoded once at screen size into GPU memory; music and
  animation stop the moment you open an app.
- **No network, no tracking, no ads.** About 0.2 MB. MIT licensed.

## Cozy Home TV and tv-home

[tv-home](https://github.com/mihirgupta0900/tv-home) debloats a slow Android TV over Wi-Fi and gives it a
tiny Apple TV-style home with nature wallpapers, set up with one prompt to a coding agent. Cozy Home TV takes
the same "make my TV fast and clean" idea and focuses on the launcher itself:

| | tv-home | Cozy Home TV |
|---|---|---|
| Home screen | Dock + grid over rotating nature photos | Favourites row + grid (Default), or your own scene (Cozy) |
| Your own pictures | Replace files in the source and rebuild | Drop them in a folder on the TV, no rebuild |
| Time-of-day pictures | – | Morning / day / evening / night |
| Music | – | Your own track, looped, volume setting |
| Reorder / hide apps on the TV | Edit `config.xml` and rebuild | Hold OK on an app |
| Screensaver | – | Picture + clock, or big clock |
| Google TV Home flash | Disable the setup app | Optional accessibility fix, stock apps untouched |
| Debloat | Main feature, per-model lists | Optional script with a protected list and undo log |
| One-prompt agent setup | Yes | Yes ([PROMPT.md](PROMPT.md)) |

Thanks to tv-home for the idea and for documenting its gotchas.

## Install

You need a computer with `adb` (macOS: `brew install android-platform-tools`) on the same Wi-Fi as the TV.

1. **On the TV:** Settings > System > About > press OK on **Build** 7 times. Back > Developer options >
   turn on **USB debugging** (and **Network/Wireless debugging** if listed). Note the TV's IP address
   (Settings > Network).
2. **Get the APK:** download `cozy-home-tv-<version>.apk` from [Releases](../../releases), or build it (below).
3. **Install and set as home:**

   ```bash
   scripts/install.sh 192.168.1.50                 # accept "Allow debugging?" on the TV the first time
   scripts/set-home.sh 192.168.1.50                # make Cozy Home TV the home screen
   scripts/set-home.sh 192.168.1.50 --home-fix     # optional: Google TV Home-button fix
   scripts/set-home.sh 192.168.1.50 --screensaver  # optional: use the Cozy Home TV screensaver
   ```

   Without the scripts:

   ```bash
   adb connect 192.168.1.50:5555
   adb install -r cozy-home-tv-1.0.0.apk
   adb shell cmd package set-home-activity io.github.lars21.cozyhometv/.HomeActivity
   adb shell cmd role add-role-holder android.app.role.HOME io.github.lars21.cozyhometv   # Android 10+
   ```

4. Press **Home** on the remote.

### Do it with a coding agent

Open this repo in Claude Code, Cursor or another coding agent and paste the prompt in **[PROMPT.md](PROMPT.md)**.
It finds the TV, builds and installs the app, and asks you about the Home-button fix, the screensaver,
the Cozy look and debloating before changing anything.

## Using it

| Remote | Does |
|---|---|
| Arrows | Move around. The focused app lifts up. |
| OK | Open the app |
| Hold OK, or Menu | App menu: add/remove favourite, Move, Hide, App info, Uninstall |
| Back | Back to the top of the home screen |
| Gear (top right) | Settings |
| Inputs (top right) | The TV's own Live TV / HDMI input app (shown when the TV has one) |

**Reorder favourites:** hold OK on a favourite > Move > use left/right > OK.
**Show hidden apps:** Settings > Hidden apps.

## Cozy look

1. Make a folder with your pictures and optional music:

   ```
   my-cozy-pack/
     morning.jpg  day.jpg  evening.jpg  night.jpg      (or just room.jpg)
     music.mp3                                         (optional)
     cards/com.netflix.ninja.jpg                       (optional card art, 16:9)
     config.json                                       (optional)
   ```

2. Send it to the TV and switch the look on:

   ```bash
   scripts/push-cozy-pack.sh 192.168.1.50 my-cozy-pack --cozy
   ```

   Or switch it on yourself: Settings > Look > Cozy.

The folder ends up in `/sdcard/CozyHome/` on the TV. The full format, `config.json` keys and picture tips
are in **[docs/cozy-pack.md](docs/cozy-pack.md)**.

Want to try it first? `examples/sample-cozy-pack/` has four free (CC0) cozy living-room pictures, no music.

Settings in the Cozy look: time of day (follow the clock, or fix one), music on/off, music volume,
little movements (fireflies), navigation sounds, clock format.

**Live background (experimental):** a lofi-style moving room made of a looping clip plus short one-time
moments. Bring your own clips, see [docs/live-background.md](docs/live-background.md).

## Screensaver

Pick it on the TV (Settings > System > Ambient mode / Screensaver > **Cozy Home TV**) or run
`scripts/set-home.sh <TV-IP> --screensaver`. Cozy look: your current picture with a small clock in a corner.
Default look: a big clock and the date on a dark background. The clock moves every 75 seconds so nothing burns in.

## Home-button fix (Google TV)

On Google TV the Home button always goes to the Google TV home first, even when another launcher is the default,
so you see it flash before Cozy Home TV appears. The optional fix is a tiny accessibility service that:

- catches only the Home button and opens Cozy Home TV directly (hold Home for the system panel),
- brings Cozy Home TV back if the Google TV home still shows up,
- opens Cozy Home TV once after the TV starts.

It does not read anything on screen and does not touch any other button. It stays off until you turn it on:
TV Settings > Accessibility > **Cozy Home TV: Home button**, the in-app Settings > Home button fix, or
`scripts/set-home.sh <TV-IP> --home-fix`.

## Optional: debloat

`scripts/debloat.sh` disables apps you don't use, reversibly (never uninstalls) and writes a log for undo.
It is a dry run unless you add `--apply`.

```bash
cp examples/debloat/google-tv-common.txt my-list.txt   # delete the lines you want to keep
scripts/debloat.sh 192.168.1.50 my-list.txt            # see what would happen
scripts/debloat.sh 192.168.1.50 my-list.txt --apply
scripts/debloat.sh 192.168.1.50 --undo debloat-20261027-120000.log
```

It refuses to touch these, even if listed:

- **The stock launcher.** On Google TV it also runs the Settings-button quick panel. Keep it installed and
  enabled; Cozy Home TV just takes over the Home screen.
- **Live TV / tuner / TV input apps.** They switch HDMI inputs.
- **System updates (OTA), Google Play services, Play Store, setup, TV settings, remote/Bluetooth, keyboards.**

## Uninstall / restore

```bash
scripts/restore.sh 192.168.1.50               # stock home, screensaver and accessibility back to normal
scripts/restore.sh 192.168.1.50 --uninstall   # ...and remove the app
scripts/restore.sh 192.168.1.50 --uninstall --remove-pack   # ...and delete /sdcard/CozyHome
```

## Build it yourself

Needs JDK 17 and the Android SDK (`platforms;android-34`, any `build-tools` 34+). No Gradle.

```bash
./build.sh                                   # debug-signed: dist/cozy-home-tv-<version>-debug.apk

# release-signed with your own key (keep the keystore outside the repo)
keytool -genkeypair -keystore ~/cozy-release.keystore -alias cozyhometv -keyalg RSA -keysize 4096 -validity 10000
COZY_KEYSTORE=~/cozy-release.keystore COZY_KEYSTORE_PASS_FILE=~/cozy-release-password.txt ./build.sh
```

Variables: `COZY_KEYSTORE`, `COZY_KEYSTORE_PASS` or `COZY_KEYSTORE_PASS_FILE`, `COZY_KEY_ALIAS` (default
`cozyhometv`), `COZY_KEY_PASS`, `ANDROID_SDK`, `JAVA_HOME`, `BUILD_TOOLS`.
An install signed with one key can't be updated with an APK signed by another key: uninstall first.

Code map: `src/io/github/lars21/cozyhometv/` has `HomeActivity` (home screen), `SettingsActivity`,
`CozyPack` (folder + config), `Scene` (image decoding, still/live backgrounds), `CozyDream` (screensaver),
`HomeButtonService` (Home-button fix), `AppRepo` (app list, banners), `Prefs`, `Ui`.

## Gotchas learned on real TVs

- **Don't disable the Google TV launcher.** It looks like dead weight once you have another home screen, but on
  Google TV its dashboard is what opens when you press the remote's Settings button.
- **Don't force the display size or density** (`wm size`, `wm density`). On one Google TV that made
  Google TV complain about an unsupported resolution and greyed out the resolution setting until it was reset.
- **Don't use `adb shell screenrecord`** on Realtek-based TVs: it caused a kernel panic and reboot. Use
  `adb exec-out screencap -p > screen.png` for screenshots.
- **The adb port can change after a reboot** when using wireless debugging. Run `adb tcpip 5555` once while
  connected to keep 5555.
- **Live TV is not bloat.** On many TVs the Live TV app is what switches HDMI inputs.
- **Home-button flash on Google TV** comes from a system intent filter, not from your launcher. Use the
  Home-button fix rather than disabling Google apps.
- **Test from inside an app**, and only send remote key presses (`adb shell input keyevent`) when nobody is watching.

## Supported TVs

Android TV and Google TV 8.0+ (API 26+): Sony, TCL, Hisense, Philips, Panasonic, Xiaomi, Nvidia Shield,
Chromecast with Google TV and others. Not Samsung (Tizen), LG (webOS) or Fire TV (untested).

## Credits and licence

Code: MIT, see [LICENSE](LICENSE). Caveat font (used in the Cozy look): SIL Open Font License 1.1, see
[assets/caveat-OFL.txt](assets/caveat-OFL.txt). Sample pictures: CC0. Inspired by
[tv-home](https://github.com/mihirgupta0900/tv-home).
