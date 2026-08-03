# Accessibility Standard

> Status: Draft · Phase 1. Target: WCAG 2.2 AA (floor). Applies to web now and mobile later.

## Requirements
- **Contrast:** 4.5:1 body text, 3:1 large text/UI; verify all token pairs (esp. Ember accent — not a text color on light without darkening).
- **Keyboard:** every interactive element reachable/operable; visible focus ring using `--color-focus`; logical tab order; no keyboard traps; skip-to-content link.
- **Semantics:** native elements first; correct ARIA only where needed; landmarks; labelled form fields; error messages programmatically associated.
- **Media:** alt text for property photos (host-provided + fallback); captions for videos; galleries operable by keyboard and screen reader.
- **Motion:** honor `prefers-reduced-motion`; no motion-only information.
- **Forms:** clear labels, inline validation, non-color error indication, autocomplete attributes.
- **Maps:** provide a non-map (list) equivalent; markers have accessible names.
- **Targets:** min 44×44px touch targets.

## Process
Automated checks (axe) in CI; manual keyboard + screen-reader passes (VoiceOver/NVDA) on core flows each phase; accessibility acceptance criteria included in every UI prompt.
