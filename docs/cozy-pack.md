# Cozy pack format

A Cozy pack is a folder of your own pictures and (optionally) music. Cozy Home TV reads it from the TV:

| Folder on the TV | Permission | Kept after uninstall |
|---|---|---|
| `/sdcard/CozyHome/` | "All files access", granted by `scripts/push-cozy-pack.sh` (or Settings > Allow reading /sdcard/CozyHome) | yes |
| `/sdcard/Android/data/io.github.lars21.cozyhometv/files/CozyHome/` | none | no |

The first folder that has a readable file wins. Changes are picked up the next time the home screen shows.

## Simplest pack (no config needed)

```
CozyHome/
  room.jpg          one picture for all day
  music.mp3         optional, loops while the home screen is showing
```

## Time of day

```
CozyHome/
  morning.jpg   day.jpg   evening.jpg   night.jpg
```

Missing ones borrow the previous period's picture, so 2 or 3 pictures also work.
Default switch times: morning 06:00, day 11:00, evening 17:00, night 20:00 (change them in `config.json`).
`afternoon.jpg` is accepted as another name for `day.jpg`.

## Card pictures for favourites (optional)

Put 16:9 pictures in `cards/`, named after the app's package, for example `cards/com.netflix.ninja.jpg`.
They replace the app's banner in the favourites row and the grid. Find package names with
`adb shell pm list packages`.

## config.json (all keys optional)

```json
{
  "title": "Our cozy home",
  "scenes": { "morning": "morning.jpg", "day": "day.jpg", "evening": "evening.jpg", "night": "night.jpg" },
  "image": "room.jpg",
  "times": { "morning": 6, "day": 11, "evening": 17, "night": 20 },
  "music": { "file": "music.mp3", "volume": 40, "enabled": true },
  "cards": { "com.netflix.ninja": "cards/netflix.jpg" },
  "favorites": ["com.netflix.ninja", "com.google.android.youtube.tv"],
  "ambient": { "fireflies": true },
  "live": { "enabled": false }
}
```

| Key | Meaning |
|---|---|
| `title` | Text above the clock in the Cozy look. `""` hides it. |
| `scenes` | Picture per period. Paths are relative to the pack folder. |
| `image` | One picture for every period (used when `scenes` is missing). |
| `times` | Hour (0-23) each period starts. |
| `music` | A file name, or an object with `file`, default `volume` (0-100) and `enabled`. The volume can be changed in Settings. |
| `cards` | Package name to picture, for the app tiles. |
| `favorites` | Packages for the favourites row on first start (later changes are made on the TV). |
| `ambient.fireflies` | Soft glowing dots, dimmed during the day. Turned off by Settings > Little movements. |
| `live` | Experimental video background, see [live-background.md](live-background.md). |

## Picture tips

- 16:9, 1920x1080 for a Full HD TV interface, up to 3840x2160 for 4K. Bigger pictures are scaled down
  once to the screen size when loaded (no visible loss), smaller ones are stretched by the GPU.
- JPEG, PNG or WebP. Keep each file under about 5 MB.
- Keep the upper-middle area calm: the clock sits top-left and the favourites row in the lower half.
- Only use pictures and music you have the right to use.

## Memory

Only the current period's picture is in memory (as a GPU bitmap, about 8 MB at 1920x1080) plus a tiny
blurred copy for the glass panel. Music and animations stop as soon as you open an app, and the pictures are
released when Android asks for memory, so games and streaming apps get the RAM on 2 GB TVs.
