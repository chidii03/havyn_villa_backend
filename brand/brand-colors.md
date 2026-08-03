# Havyn Villa — Color System (Blue / v3)

> Supersedes the earlier green concept. Tagline: **"Stay beautiful, live better."**
>
> **v3 update:** `--brand`/`--brand-strong` (light mode) updated from `#0B5FD0`/`#003488`
> to Boeing Blue `#0039A6` + a proportionally darker `#002874` hover, per explicit
> direction. Dark mode's `--brand`/`--brand-strong` are unchanged — they're already a
> lighter blue tuned for contrast against a near-black background, and swapping in the
> darker Boeing Blue there would fail that contrast, not just look different.

## Semantic tokens — light
| Token | Hex | Use |
|---|---|---|
| `--brand` | `#0039A6` | Primary actions, links, active states (Boeing Blue) |
| `--brand-strong` | `#002874` | Hovers |
| `--brand-sky` | `#38BDF8` | Secondary/azure highlights, gradients |
| `--brand-gradient` | `linear-gradient(180deg,#003488,#2E9BEF)` | Hero/icon field |
| `--sun` | `#F4B740` | Warm accent (sparing — sun/beach) |
| `--ink` | `#0F1B2D` | Primary text |
| `--ink-muted` | `#5B6B7F` | Secondary text |
| `--bg` | `#F5F8FC` | App background (cool mist) |
| `--surface` | `#FFFFFF` | Cards/sheets |
| `--line` | `#E1E8F0` | Borders/dividers |
| `--success` | `#2E9E6B` | · |
| `--warning` | `#D9902B` | · |
| `--danger` | `#D24545` | · |

## Semantic tokens — dark
| Token | Hex |
|---|---|
| `--brand` | `#4FA3F5` |
| `--brand-strong` | `#7BC0FB` |
| `--ink` | `#EAF1FA` |
| `--ink-muted` | `#9BB0C6` |
| `--bg` | `#0A1420` |
| `--surface` | `#111E2E` |
| `--line` | `#243547` |

## Tailwind theme (drop-in)
```ts
brand:        '#0039A6',
'brand-strong':'#002874',
sky:          '#38BDF8',
sun:          '#F4B740',
ink:          '#0F1B2D',
'ink-muted':  '#5B6B7F',
bg:           '#F5F8FC',
surface:      '#FFFFFF',
line:         '#E1E8F0',
```

## Typography (unchanged direction)
Display/wordmark: warm serif (the logo uses a classic serif for HAVYN) — pair with **Fraunces** or **Playfair Display**. UI/body: **Inter** (tabular figures for prices).

## Logo assets
- `havyn-villa-logo.png` — primary logo (house + palm + wave, blue).
- `havyn-villa-favicon.png` — white line version for favicon / dark & photographic backgrounds.
- Provide the app favicon at 512/192/32/16px and an SVG outline for crisp small sizes (recommended next step).

> ⚠️ Name/domain/trademark for "Havyn Villa" remain **unverified** — clear before public launch.
