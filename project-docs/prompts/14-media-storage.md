# Prompt 14 — Media Storage (Cloudinary: images + video, URL-only)

> Phase: 7 · Order: 14 · Depends on: 10, 13
> Implementation prompt for the coding agent. Execute after prerequisites are green. Build on prior prompts; obey the file rules.

## 1. Objective
Implement property media (images **and** video) via **Cloudinary**, storing only references/metadata in PostgreSQL — never the binary and never the full video. Secrets stay server-side; uploads go directly to Cloudinary via a server-signed flow.

## 2. Prerequisite documents
- `../architecture/04-integrations.md` (Cloudinary rules)
- `../architecture/02-adr-index.md` (ADR-005)
- `../security/01-security-plan.md`
- `../database/01-data-model.md` (`PropertyMedia`)

## 3. Deliverables (exact)
- `MediaStorage` port + `CloudinaryMediaStorage` adapter (config-selected).
- `POST /media/signature` — backend generates a **signed upload** (or authenticated preset) using the API secret; returns only what the client needs to upload directly to Cloudinary.
- `POST /host/listings/{id}/media` — client sends the resulting `public_id`/`secure_url`/metadata; backend validates and persists a `PropertyMedia` row: `secure_url, public_id, resource_type(image|video), format, width, height, duration, bytes, position, alt`.
- Reorder + delete media endpoints (delete also removes from Cloudinary via `public_id`).
- Delivery helpers producing CDN URLs with transformations (`f_auto,q_auto`; named card/hero/thumb sizes; video poster via `so_auto`).

## 4. Constraints
- **Never** store binaries or full videos in Postgres or proxy them through the API — videos are referenced by Cloudinary URL and streamed from Cloudinary's CDN.
- API secret only on the backend; the browser uses a signature/preset, never the secret.
- Validate allow-listed types (jpg/png/webp/mp4/mov), max size, max count per listing; reject others.
- Only the owning host (object-level authz) can add/reorder/delete a listing's media.

## 5. Acceptance criteria
- Host uploads images and a video; metadata persists; media renders from Cloudinary CDN; video plays from its URL with a generated poster.
- Oversized/invalid files rejected; deleting media removes both the DB row and the Cloudinary asset.
- No secret key is ever present in any client bundle or network response to the browser.

## 6. Files you MAY modify
- backend `media/` module + migration for `PropertyMedia`
- `../architecture/04-integrations.md`, `../database/01-data-model.md` (updates)

## 7. Files you MUST NOT modify
- `booking/`, `pricing/`, `payments/` logic (consume only)
- Any other prompt file; approved brand/design tokens without an ADR

## 8. Tests required
- Unit: signature generation, type/size validation, adapter (mock Cloudinary).
- Integration: signature → persist metadata → reorder → delete (Testcontainers).
- Security: reject bad type/size; authz (non-owner blocked); assert no secret leaks in responses.

## 9. Documentation updates required
- Update integrations + data-model docs; OpenAPI reflects media endpoints; `.env.example` documents Cloudinary keys.

## 10. Verification before completion
- Upload an image and an mp4 end-to-end; confirm DB stores URL/metadata only (no binary/video bytes).
- Tests pass; no secret in client; docs + OpenAPI updated.
