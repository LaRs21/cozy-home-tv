# AI-assisted setup (Claude Code, Cursor, Codex or any coding agent)

`./setup.sh` already does the whole setup by itself. Use this prompt if you'd rather have a coding agent drive
it and explain each step. Open this repository in your agent and paste:

```text
Help me set up Cozy Home TV (this repo) on my Android TV / Google TV. Use the repo's scripts, not your own
adb commands, and ask me before every step that changes the TV.

1. Read README.md (Quick start, Safety, Troubleshooting) and run ./setup.sh --help.
2. Check that adb works. If not, tell me how to install Android platform-tools and wait.
3. Help me turn on developer mode and USB/network (or wireless) debugging on the TV, using the steps
   setup.sh prints, and find the TV's IP address. For Android 11+ wireless debugging, use --pair and --code.
4. Run ./setup.sh --dry-run --ip <TV> first and explain the plan to me in plain words.
5. Then run ./setup.sh --ip <TV> for real and let me answer its questions myself (theme, debloat tier,
   my own pictures and music, screensaver). Don't pick a debloat tier for me; the default is none.
6. Afterwards run ./doctor.sh --ip <TV>, show me the result and the screenshot setup.sh saved, and tell me
   exactly how to undo everything (./restore.sh --ip <TV>, or the restore-<TV>.sh it wrote).
7. Remind me to turn off network/wireless debugging on the TV when we're done.

Rules (learned on real TVs; the scripts enforce most of them):
- Never uninstall anything, and never disable the stock launcher (launcherx / tvlauncher), setupwraith,
  Google Play services, keyboards, Live TV / tuner / HDMI input apps, system updates, network/Wi-Fi apps,
  System UI or Realtek/MediaTek/MStar system packages.
- Never change the display size or density (wm size / wm density), never reboot the TV unless I ask,
  and never use `adb shell screenrecord` (it crashed a Realtek TV); use `adb exec-out screencap -p`.
- Only press keys or open apps on the TV when I say nobody is watching.
- If something fails, run ./doctor.sh and read README > Troubleshooting before trying anything else.
```

The prompt is short on purpose: the scripts carry the safety rules, the backup record and the undo.
