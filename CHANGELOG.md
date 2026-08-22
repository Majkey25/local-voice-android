# Changelog

## 0.3.0 - 2026-08-22

### Added

- Per-language dictionary with exact local corrections.
- Exact-trigger text snippets.
- Guided local voice-recognition calibration with reviewable suggestions.
- Raw, Light, and Polished cleanup levels.
- Bubble size and opacity controls.
- Push-to-talk with separate cancel and undo actions.

### Changed

- Personalization now persists independently for each language profile.
- Dictionary corrections run before snippets and semantic cleanup.

### Security

- Calibration audio remains in bounded RAM and is discarded after analysis.
- Dollar signs and backslashes in dictionary output are inserted literally.
