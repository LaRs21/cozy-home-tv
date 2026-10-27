# PROGRESS: picture themes on the box

Build home: the box, `/workspace/darren/tv/cozy-home-tv/repo` (see `../BOX-BUILD.md`). Not pushed to GitHub.

Base: `main` = Clean-Alpine `57a7d1b` (from the Mac bundle, on top of GitHub `abe66b5`) + the picture themes.
Other branches: `box-pictures-standalone` (the same picture work first built on abe66b5 before the Mac came back),
`wip-picture-themes-mac` (the Mac's unfinished copy, fcd7944, reference only), `wip-scene-themes-rejected` (never push).

## Plan
1. [x] Remove the old themes (Glass, Cozy, Neon, Starship, Arcade) and their art/fonts. Clean stays the default.
2. [x] PictureTheme (assets/themes/<id>/theme.json + slots.json + bg.jpg + music.mp3) and the 4 packs.
3. [x] PictureHome (painting, 4 spots in the object's style, "+ Add app", focus ring, motion, themed More apps).
4. [x] HomeActivity hooks, per-theme music (fade in, fade out on app open), `--es spots` / `--ez chooseApps` hooks.
5. [x] Settings: Choose apps, Music + volume for picture themes, About credits. Docs, CREDITS, CHANGELOG, setup.sh.
6. [x] Emulator tests + screenshots (picture-themes/shots/), memory per theme with music, APK size.
7. [x] Port Clean-Alpine (57a7d1b) from the Mac bundle; release key on the box.

## Next
- Real-TV test only with Darren's OK (install when Home or Settings is in front, never during the screensaver),
  then `../picture-themes/private/darren-spots.sh -s <TV>` for his Netflix, Disney+, YouTube, PPSSPP.
- Push to GitHub only when Darren says so (repo stays private).

## Log
- 27 Oct: box toolchain + emulator (TCG, no KVM). Picture themes built and tested on abe66b5, then ported onto
  57a7d1b after the Mac sync (10:12 MYT). Release-signed build from the box.
- 27 Oct ~10:45 MYT: release-signed 1.3.0-pre (12 MB) tested on the box emulator: all 5 themes, focus, other apps,
  More apps, Choose apps, empty spots. Music plays per theme, stops in an app, resumes back home. Fixed a bug
  where switching themes could leave the new track silent (old track's position was reused).
