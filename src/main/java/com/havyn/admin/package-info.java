/**
 * Admin platform — user management (role grant/revoke, suspend/reactivate), listing
 * moderation (suspend/reject, additive to host self-publish), host identity/KYC
 * review ({@code VerificationRequest}), booking disputes, commission/platform
 * settings, and platform-wide analytics. Implemented in prompt 18 (session 19) — see
 * project-docs/prompts/18-admin-platform.md.
 *
 * <p>Deliberately out of scope this session (real, bounded gaps, not oversights —
 * see backend/02-domain-modules.md's session 19 notes for the full reasoning):
 * review moderation (would require editing {@code reviews/}, a different prompt's
 * file scope), a hard admin-approval gate replacing host self-publish entirely
 * (would be a breaking change to an already-shipped capability, outside this
 * prompt's file scope), and "reports" (never defined anywhere in this project's
 * docs — not in the ERD, not in this prompt's own acceptance criteria).
 */
package com.havyn.admin;
