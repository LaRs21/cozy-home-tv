# PROGRESS: picture themes on the box

Build home: the box, `/workspace/darren/tv/cozy-home-tv/repo` (see `../BOX-BUILD.md`). Not pushed to GitHub.

Base: `main` = GitHub `abe66b5` (1.2.0), imported on the box (text files verified blob-for-blob against GitHub; the old
themes' art was not imported because those themes are being removed). The Mac-only commits (57a7d1b Clean-Alpine
1.3.0-pre, branch wip-scene-themes-rejected 167a5eb) are NOT in here yet: port Clean-Alpine from the Mac bundle when
the Mac is online.

## Plan
1. [ ] Remove the old themes (Glass, Cozy, Neon, Starship, Arcade) and their art/fonts. Clean stays the default.
2. [ ] PictureTheme (assets/themes/<id>/theme.json + slots.json + bg.jpg + music.mp3) and the 4 packs.
3. [ ] PictureHome (painting, 4 spots in the object's style, "+ Add app", focus glow, motion, themed More apps).
4. [ ] HomeActivity hooks, per-theme music (fade in, stop on app open), `--es spots` script hook.
5. [ ] Settings: Music toggle for picture themes, Choose apps screen, About credits. Docs, CREDITS, CHANGELOG.
6. [ ] Emulator tests + screenshots (picture-themes/shots/), memory per theme with music, APK size.
7. [ ] Port Clean-Alpine (57a7d1b) from the Mac bundle.

## Log
