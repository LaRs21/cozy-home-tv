# Live background (experimental)

A "lofi" style moving room: the scene stays almost still with a gentle looping base clip, and now and then
one short moment plays once (someone sips a drink, a cat stretches, the curtain moves) before it is calm again.
Cozy Home TV ships the player only. No clips are included: you bring your own.

## How it works

```
still picture (always underneath, so there is never a black frame)
  └ base clip: muted, loops forever (keep it short and seamless, 8-20 s)
      └ moment clips: one at a time, every gapMin..gapMax seconds, fade in, play once, fade out
```

All clips must be rendered from the same still picture and start and end on that exact frame, so the cuts are
invisible. Video plays muted (your `music.mp3` is the soundtrack).

## Pack layout

```
CozyHome/
  night.jpg
  live/night-base.mp4
  live/night-sip.mp4
  live/night-curtain.mp4
  config.json
```

```json
{
  "scenes": { "night": "night.jpg" },
  "live": {
    "enabled": true,
    "gapMinSeconds": 25,
    "gapMaxSeconds": 45,
    "night": { "base": "live/night-base.mp4", "moments": ["live/night-sip.mp4", "live/night-curtain.mp4"] }
  }
}
```

Each period (`morning`, `day`, `evening`, `night`) can have its own `base` and `moments`; periods without one
show the still picture.

## Clip guidelines

- H.264 MP4, 1920x1080 (or 1280x720 to save memory), 24-30 fps, no audio track, 6-12 Mbit/s.
- Base loop: first frame == last frame. Moments: first and last frames == the still picture.
- Two hardware video decoders run at most at the same time (base + one moment). Most TVs handle this;
  if yours stutters, drop the base clip and use moments only, or turn Settings > Little movements off.
- Video playback stops the moment an app opens.

## Status

The player is in the app (`Scene.LiveBackdrop`) and used by the home screen and the screensaver when a pack
enables it, but it has only been tested lightly. Please open an issue with your TV model if it misbehaves.
