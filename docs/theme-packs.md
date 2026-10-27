# Picture themes (theme packs)

A picture theme is one painted 1920x1080 scene with **4 spots** where your favourite apps appear. Each spot is drawn
in the style of the object it sits on: printed on a sail or banner, glowing inside a crystal, lit in a lantern or
window, or set in a ring. Any app works in any spot. The launcher draws the app's own icon in that style, so the
picture never has logos painted into it.

Cozy Home TV ships four picture themes: Sky Kingdom, Moonlit Whale, Desert Kingdom and World Tree. You can add more
by adding a pack folder and one line of code.

## Where a pack lives

Each pack is a folder named after its id (lower case, `a-z 0-9 _`) in `assets/themes/<id>/`. Add it to
`Themes.ALL` in `Themes.java` (`new PictureTheme("<id>", "Name", "One line for the picker.")`) and rebuild with
`./build.sh`. (Loading packs from a folder on the TV without a rebuild is not supported yet.)

A folder only counts as a pack if it has a `theme.json`.

## Files

| File | Needed | What it is |
|---|---|---|
| `theme.json` | yes | name, colours, font, music settings |
| `slots.json` | yes | the 4 spots, the More apps position, and the motion layers |
| `bg.jpg` | yes | the painting, 1920x1080 (other 16:9 sizes are scaled). Paint the objects **empty**: no logos, clock, gear or "More apps" text, because those are drawn live |
| `thumb.jpg` | bundled packs | 320x180 picture for the theme picker |
| `music.mp3` | no | a quiet loop. It plays while home is visible, fades in, and stops when an app opens or the screensaver starts. Keep it small (64 kbps mono is plenty) |

## theme.json

```json
{
  "name": "Sky Kingdom",
  "order": 1,
  "description": "One line for the picker.",
  "picture": "bg.jpg",
  "music": "music.mp3",
  "musicVolume": 35,
  "musicCredit": "Title, author, licence (shown in Settings > About)",
  "font": "serif",
  "textColor": "#FFFFFFFF",
  "accent": "#FFFFF0C8",
  "sheetTint": "#E60E1E3A",
  "clockSize": 44
}
```

* `font` is a system font family: `serif`, `sans-serif`, `sans-serif-medium`, `sans-serif-condensed`, `cursive` and so on.
* `accent` is the colour of the focus ring and pills. `sheetTint` is the colour behind More apps (ARGB, so keep it mostly opaque).
* `musicVolume` (0–100) is used until the user sets a volume in Settings.

## slots.json

All positions are in the 1920x1080 design space (pixels of the painting at full size).

```json
{
  "slots": [
    { "key": "sail", "name": "sail", "style": "cloth", "x": 448, "y": 172, "w": 130, "h": 122,
      "rotate": -2, "curve": 0.55, "labelDy": 228, "glow": "#FFFFF2CC" },
    { "key": "crystal", "name": "crystal", "style": "crystal", "x": 1240, "y": 650, "w": 84, "h": 150, "labelDy": 112, "glow": "#FF9FD8FF" }
  ],
  "more": { "x": 960, "y": 1010 },
  "motion": {
    "twinkles": { "rect": [0, 0, 1920, 420], "count": 22, "color": "#FFFFFFFF" },
    "mist":   [[700, 760, 900, 220, 60, 48]],
    "falls":  [[1184, 797, 46, 190]],
    "lights": [[400, 330, 22]],
    "lightColor": "#FFFFD08A",
    "mistColor": "#FFFFFFFF"
  }
}
```

### Spots

| Field | Meaning |
|---|---|
| `x`, `y` | centre of the object's empty area |
| `w`, `h` | size of that area. The icon is drawn about 75–80% of the smaller side |
| `style` | `cloth` (sails, banners, tapestries: bent and shaded like printed fabric), `crystal` (see-through with a sheen), `glow` (lantern, window, bell or portal: lit from above), `ring` (round, with a dark rim) |
| `round` | clip the icon to a circle (always on for `ring`) |
| `rotate` | degrees, to follow a tilted object |
| `curve` | 0–1, how much cloth bows (only `cloth`) |
| `labelDy` | how far below the centre the app name sits. Put it on a calm part of the picture |
| `glow` | colour of the focus glow (lit styles also glow softly, with a flicker, when not focused) |
| `name` | what the spot is called in menus ("Put Netflix in the sail") |

Up to 4 spots are used. The apps in them are shared by all picture themes, so switching themes keeps your
choice. A new install starts with empty spots showing **+ Add app**.

### Motion

Motion is all optional and very cheap: it is drawn in one layer at about 30 fps, only while home is visible, and not
at all when Settings > Little movements is off.

* `twinkles`: little stars or sparkles in a rectangle `[x, y, w, h]`, up to 60.
* `mist`: soft bands `[x, y, w, h, amplitude px, period s]` that sway slowly sideways (clouds, fog, spray).
* `falls`: rectangles `[x, y, w, h]` over waterfalls, with light sliding down them.
* `lights`: warm points `[x, y, radius]` that flicker (windows, torches, lanterns).

## Choosing apps

* On home: OK on an empty spot (**+ Add app**) opens the app list. Hold OK (or press Menu) on a spot to change its app, move it to another spot, or clear it.
* In More apps: hold OK on an app and pick **Put in a spot**.
* In Settings: **Choose apps** lists the 4 spots; OK on one opens the app list, hold OK clears it. Also from a script: `--ez chooseApps true`.
* From a script (for example your own setup): `am start -n io.github.lars21.cozyhometv/.HomeActivity --es spots com.netflix.ninja,com.disney.disneyplus,,com.google.android.youtube.tv`.
  The packages go in spot order, and an empty entry leaves that spot empty.

## Licences

Only add pictures and music you are allowed to share. Credit them in `CREDITS.md` and in `musicCredit`. The four
bundled themes are credited in `CREDITS.md`.
