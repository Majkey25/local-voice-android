# Changelog

## 0.4.0 - 2026-08-25

### Added

- The speech-language picker always offers English, Czech, German, French, and Spanish.
- Supported system and keyboard languages appear first without hiding other choices.

### Changed

- The complete application interface, accessibility disclosure, overlay feedback, errors, and controls now use English by default.
- New installations default to English speech recognition while saved language profiles remain unchanged.

## 0.3.2 - 2026-08-22

### Fixed

- Recalibration applies confirmed dictionary entries before proposing corrections, so accepted terms are not suggested again.
- Calibration accuracy now reports raw recognition against cumulative personal corrections.

## 0.3.1 - 2026-08-22

### Changed

- Tier-one calibration scripts now contain 180 to 260 words for roughly two minutes of natural reading.
- Consecutive recognition mistakes in a name or product are proposed as one phrase correction.

### Safety

- Calibration rejects correction phrases longer than four words instead of creating broad dictionary rules.

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
