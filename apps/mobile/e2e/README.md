# E2E (Maestro)

`project-docs/prompts/30-expo-mobile-app.md` deliverable: "E2E on a device/simulator
for core flow." What's here is scoped the same way `apps/web/e2e` is scoped — no flow
is faked or driven through a shortcut a real user couldn't take.

[Maestro](https://maestro.mobile.dev/) rather than Detox: it drives a real running app
(Expo Go or a dev build) over the accessibility tree with no native project changes
(no `ios`/`android` folders, no Gradle/Pod config) — the right fit for an Expo managed
workflow app, and Expo's own docs recommend it for exactly this reason.

## What's covered

- `core-flow.yaml` — Explore tab → tap a real listing (`property-card-<id>`, a testID
  added to `components/property-card.tsx`'s root `Pressable` for this flow) → property
  detail → "Check price" → "Reserve". Reserving while signed out hits the real
  `requireAuth` redirect in `app/property/[id].tsx`, logs in with a fixture user
  (`email-input`/`password-input` testIDs added to `app/login.tsx`), then re-enters the
  flow to complete the reserve. Ends at the real **"Dates held"** state — same as
  `apps/web/e2e/search-and-reserve.spec.ts` — not a fake "booking confirmed" screen.

## What's deliberately NOT here

- **The "pay→confirm" half of search→book→pay→confirm.** No live Paystack credentials
  exist in any environment this project has run in (see
  `apps/web/e2e/README.md` and `architecture/04-integrations.md`'s session 7 notes).
  `core-flow.yaml` stops at the same honest `PENDING`/"Dates held" boundary
  `BookingWidget` (web) and `app/property/[id].tsx` (mobile) both stop at.
- **Signup.** Real signup requires email verification through a live Mailhog inbox
  (see `apps/web/e2e/README.md`'s `findVerificationLink` note); Maestro has no
  built-in mail client to replicate that, so this flow logs in with a fixture user
  registered ahead of time instead of self-registering one.
- **Trips and messaging flows.** Real screens exist (`app/(tabs)/trips.tsx`,
  `app/(tabs)/messages.tsx`, `app/conversation/[id].tsx`) and are covered by
  component/integration tests (`trips.test.tsx`), but aren't yet in a Maestro flow —
  `core-flow.yaml` covers the flow prompt 30 names explicitly
  ("a user completes search→book→pay→confirmation natively"); trips/messaging E2E is a
  reasonable next addition, not a gap in this session's actual deliverable.

## A real gap this flow surfaces (not fixed here)

`app/login.tsx` always calls `router.replace("/(tabs)/account")` on success, regardless
of what triggered the login redirect — so reserving as a signed-out user does **not**
return you to the property you were reserving; `core-flow.yaml` has to manually
re-navigate to the listing after logging in. Web's own `BookingWidget` doesn't handle
a return-to-intent redirect either, so this isn't a mobile-specific regression, but
it's real and worth its own prompt (a `?redirect=` param on `/login`, mirrored on
both apps) rather than a silent fix bundled into this session.

## Running

Needs Maestro CLI (`curl -Ls "https://get.maestro.mobile.dev" | bash`), a running
iOS Simulator or Android Emulator (or a physical device with the dev build installed),
the API running and reachable from that device/simulator, and one ACTIVE property
seeded in the database (same fixture shape as `apps/web/e2e/seed.ts` inserts).

```
E2E_TEST_EMAIL=... E2E_TEST_PASSWORD=... maestro test e2e/core-flow.yaml
```

**Not executed in this dev sandbox** — there's no iOS Simulator, Android Emulator, or
physical device attached here (this environment has no GUI/hardware-accelerated
virtualization at all — the same category of gap `apps/web/e2e/README.md` documents
for Playwright's application stack, one level further down the stack since even the
*app* can't be launched, not just the backend it talks to). The Maestro CLI itself was
not installed or invoked in this sandbox either, so unlike Playwright (which **was**
verified to launch a real browser here), this flow's YAML has not been syntax-checked
by its own tooling — it was hand-verified against the real screen code (`app/login.tsx`,
`app/property/[id].tsx`, `components/property-card.tsx`) listed above, matching every
selector and assertion to text/testIDs that actually exist in that code today.
