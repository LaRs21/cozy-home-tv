# One-prompt setup (Claude Code, Cursor, Codex or any coding agent)

Open this repository in your coding agent and paste:

```text
Set up Cozy Home TV (this repo) on my Android/Google TV over Wi-Fi. Find the TV on my network and connect
with adb (I have turned on USB/network debugging and will accept the prompt). Build the APK with ./build.sh
(or use the newest one in dist/), install it with scripts/install.sh and make it the home screen with
scripts/set-home.sh. Ask me before each step that changes the TV, and only send key presses or open apps
when I say nobody is watching.

Then ask me:
1. Do I want the Google TV Home-button fix (scripts/set-home.sh --home-fix)?
2. Do I want Cozy Home TV as the screensaver (--screensaver)?
3. Do I want the Cozy look? If yes, help me make a folder with 1 picture or 4 time-of-day pictures (16:9,
   1920x1080 or bigger) and optional music, following docs/cozy-pack.md, and push it with
   scripts/push-cozy-pack.sh --cozy. Or use examples/sample-cozy-pack to try it.
4. Do I want to disable apps I never use? If yes, go through examples/debloat/google-tv-common.txt with me
   line by line, then run scripts/debloat.sh as a dry run, show me the list, and only use --apply after I agree.
   Keep the log so it can be undone.

Rules learned on real TVs: never disable or uninstall the stock launcher (on Google TV it runs the
Settings-button panel), the Live TV/tuner app (it switches HDMI inputs), system updates, Google Play services
or the Play Store. Never change the display size or density (wm size / wm density). Don't use
`adb shell screenrecord` (it crashed a Realtek-based TV); use `adb exec-out screencap -p` for screenshots.
Check each step with a screenshot and give me the undo command for everything you change.
```

The prompt is short on purpose. The scripts and README have the details if the agent gets stuck.
