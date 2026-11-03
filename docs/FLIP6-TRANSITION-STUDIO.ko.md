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


## Updates for Flip 6 testing

- The transition studio opens in manual mode at 90°, so the mock cover/inner screens are visible before Shizuku or a live sensor is connected.
- The studio includes a repeatable 0° → 180° → 0° demo and keeps the preview at the top of the screen.
- The Samsung HAL animation estimator temporarily holds a sudden jump of 18° or more when it arrives within 180 ms. The following same-source sample confirms the movement or rejects an isolated spike. Raw HAL log records are still kept separately for diagnostics.
- Flip activities declare resize/configuration handling for fold/unfold changes. One UI still owns the actual cover-screen app allowlist and physical display handoff; the app cannot force or silently change those system settings.
