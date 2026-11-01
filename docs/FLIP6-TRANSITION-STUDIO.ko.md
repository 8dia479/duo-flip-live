# Flip 6 transition studio

## What this branch adds

- A dedicated transition-studio screen for Galaxy Z Flip 6.
- Read-only live hinge input using Android's public hinge sensor, with the existing Shizuku Samsung HAL reader preferred while fresh data is available.
- A manual 0°–180° slider for devices or firmware where live angle readings are unavailable.
- An in-app cover/inner display handoff preview with cross-fade, slide, and scale effects.
- Saved effect style and smoothing duration.
- Unit tests for angle validation, preview posture ranges, interpolation, and duration bounds.

## Important boundary

This is an **in-app preview engine**, not a system display handoff implementation. The 65°–115° handoff interval is a visual tuning range only; it is not claimed to match Samsung One UI switching thresholds. The studio does not capture the screen, control display power, launch or move other apps, change wallpaper, or claim that the operating system has switched panels.

## Next Flip 6 validation steps

1. Run the studio on a physical SM-F741 device.
2. Compare raw HAL readings, the estimated angle, and public-sensor fallback while slowly folding and unfolding.
3. Record actual display-added/removed/changed events and note when One UI hands the foreground app between inner and cover displays.
4. Only after those observations, design a separate opt-in system integration path. Keep this preview available as a safe fallback.
