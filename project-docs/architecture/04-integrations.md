# External Integrations — Selected Providers

> Status: Decided for MVP (supersedes "Proposed" in ADR-009). All providers stay behind abstractions so they remain swappable. **No secret keys on the frontend.**

## 1. Media — Cloudinary (images + video)
**Decision:** Cloudinary is the MVP media provider for both property **images and videos**.

Rules:
- The database stores **only references/metadata — never the binary and never the full video**. Persist: `secure_url`, `public_id`, `resource_type` (image|video), `format`, `width`, `height`, `duration` (video), `bytes`, `position`, `alt`. (`PropertyMedia` table.)
- **Upload flow:** backend issues a **signed upload** (signature generated server-side with the API secret) or an authenticated upload preset; the client uploads directly to Cloudinary; the client returns the `public_id`/`secure_url` to the backend, which validates and persists the metadata. Secrets never reach the browser/app.
- **Delivery:** serve via Cloudinary CDN URLs with transformations (responsive `f_auto,q_auto`, named sizes for cards/hero/thumb). Videos are referenced by URL and streamed from Cloudinary — we do **not** store or proxy the video bytes.
- **Validation:** allow-list types (jpg/png/webp/mp4/mov), max size, max count per listing; reject others; generate a poster/thumbnail for videos (`so_auto`).
- Backend port: `MediaStorage` (adapter: `CloudinaryMediaStorage`). Env: `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `CLOUDINARY_UPLOAD_PRESET`.

**Implemented (prompt 14, session 7):** the **signed-upload** path specifically, not
an upload preset — `CLOUDINARY_UPLOAD_PRESET` is provisioned but unused by the current
implementation (signed uploads don't need a dashboard-configured preset). Signature
generation (sorted params + API secret, SHA-1) is pure local computation, real and
unit-tested independent of a live account. The actual HTTP round-trip to Cloudinary
(upload verification, asset deletion) has never been exercised against a real account
— no credentials are configured in this environment. See backend/02-domain-modules.md's
session 7 notes.

## 2. Maps — Google Maps
**Decision:** Google Maps for MVP (markers, price pins, detail location preview, listing-wizard pin placement).

Rules:
- **Frontend** uses the Google Maps JavaScript API via a React wrapper (`@vis.gl/react-google-maps` or `@react-google-maps/api`) with a **browser key restricted by HTTP referrer** and limited to the Maps/Places APIs actually used. This key is public-by-design but must be referrer-locked.
- **Places Autocomplete** powers the "Where" search typeahead; store the resolved `lat`/`lng` (+ place name) on search and on listings.
- **Results map:** clustered price-pin markers synced with the list (hover highlights card ↔ marker). Provide a **list fallback** for accessibility.
- **Property detail:** an approximate-location map (privacy: show a circle/area, not the exact pin, until booked).
- **Geocoding** (address → lat/lng) runs **server-side** with a separate **server key** when saving listings, so that key/secret stays off the client.
- Env: `NEXT_PUBLIC_GOOGLE_MAPS_BROWSER_KEY` (referrer-restricted), `GOOGLE_MAPS_SERVER_KEY` (backend geocoding).

## 3. Payments — provider-agnostic (Paystack/Flutterwave/Stripe)
Per ADR-004: `PaymentProvider` port + adapters, selected by config. For a Nigeria/Africa-first launch, **Paystack or Flutterwave** are natural defaults; Stripe for other markets. Secrets server-side; webhooks signature-verified and idempotent.

**Implemented (prompt 13, session 7):** Paystack only (`PaystackPaymentProvider`) —
Flutterwave/Stripe remain structurally-identical additions behind the same port, not
yet built. **Correction to this doc's original config summary below:** Paystack signs
webhooks with the *same* secret key used for API auth (`HMAC-SHA512` of the raw
request body), not a separate webhook-signing secret — `PAYSTACK_WEBHOOK_SECRET`
(provisioned in session 1, before this was verified against Paystack's real API docs)
has been removed from `.env.example` as inaccurate. Request/response shaping and
signature verification are real and unit-tested (including against
`MockRestServiceServer`-verified real HTTP request shapes); the live round-trip to
`api.paystack.co` has never been exercised — no account is configured here.

## 4. Email — provider abstraction
`Mailer` port; local = Mailhog; prod = a transactional provider (e.g., Resend/SES/Postmark). No secrets on client.

## 5. Config summary (.env.example keys)
```
# Media
CLOUDINARY_CLOUD_NAME= / CLOUDINARY_API_KEY= / CLOUDINARY_API_SECRET=
CLOUDINARY_UPLOAD_PRESET=   # provisioned, unused — this implementation uses signed uploads, not a preset
# Maps
NEXT_PUBLIC_GOOGLE_MAPS_BROWSER_KEY=   # referrer-restricted
GOOGLE_MAPS_SERVER_KEY=                # backend geocoding
# Payments
PAYMENTS_PROVIDER=paystack|flutterwave|stripe   # only paystack is implemented (prompt 13)
PAYSTACK_SECRET_KEY=                            # also signs webhooks — no separate webhook secret exists
PAYSTACK_CALLBACK_URL=
FLUTTERWAVE_SECRET_KEY= / FLUTTERWAVE_WEBHOOK_SECRET= / STRIPE_SECRET_KEY= / STRIPE_WEBHOOK_SECRET=  # unimplemented
# Email
MAIL_PROVIDER= / MAIL_API_KEY=
```
> Never commit real values. `.env.example` documents keys only.
