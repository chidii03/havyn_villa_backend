# Design Tokens

> Status: Draft · Phase 1. Implementation-ready tokens for Tailwind theme + shadcn/ui. Light + dark.
> These feed `frontend/` Tailwind config and (later) the Expo app so web and mobile stay identical.

> Brand = **Havyn Villa Blue**, from the logo (deep marine `#003488` core + azure glow). Active/selected/focus states use `--color-brand`. See `../../brand/brand-colors.md`.

## Color — semantic (light)
```
--color-brand:        #0B5FD0   /* Havyn Blue (primary/active) */
--color-brand-hover:  #003488   /* Deep Marine (pressed/hover) */
--color-sky:          #38BDF8   /* azure highlight */
--color-accent:       #F4B740   /* Sun (sparing warm accent) */
--color-fg:           #0F1B2D   /* Ink */
--color-fg-muted:     #5B6B7F
--color-bg:           #F5F8FC   /* cool mist */
--color-surface:      #FFFFFF
--color-line:         #E1E8F0
--color-success:      #2E9E6B
--color-warning:      #D9902B
--color-danger:       #D24545
--color-focus:        #0B5FD0
```

## Color — semantic (dark)
```
--color-brand:        #4FA3F5
--color-brand-hover:  #7BC0FB
--color-sky:          #38BDF8
--color-fg:           #EAF1FA
--color-fg-muted:     #9BB0C6
--color-bg:           #0A1420
--color-surface:      #111E2E
--color-line:         #243547
```

## Typography scale (fluid, rem)
```
display-lg  3.0   / 1.05  / 600
display     2.25  / 1.1   / 600
h1          1.75  / 1.2   / 600
h2          1.375 / 1.25  / 600
h3          1.125 / 1.3   / 600
body        1.0   / 1.55  / 400
small       0.875 / 1.5   / 400
caption     0.75  / 1.4   / 500
```
Fonts: Display = Fraunces/Söhne; UI = Inter/Söhne. Numeric = tabular figures.

## Spacing (4px base)
`0,1(4),2(8),3(12),4(16),5(20),6(24),8(32),10(40),12(48),16(64),20(80),24(96)`

## Radius
`sm 6 · md 10 · lg 14 · xl 20 · pill 9999`. Default card radius = `lg`. Avoid oversized radii everywhere.

## Elevation (restrained)
`e0 none · e1 0 1 2 rgba(0,0,0,.06) · e2 0 4 12 rgba(0,0,0,.08) · e3 0 12 32 rgba(0,0,0,.12)`

## Motion
```
--ease-standard: cubic-bezier(0.2,0,0,1)
--dur-fast: 150ms · --dur: 220ms · --dur-slow: 300ms · --dur-page: 450ms
```
Honor `prefers-reduced-motion`.

## Breakpoints
`sm 640 · md 768 · lg 1024 · xl 1280 · 2xl 1536`.

## Z-index scale
`base 0 · dropdown 1000 · sticky 1100 · header 1200 · overlay 1300 · modal 1400 · toast 1500`.
