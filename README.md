# Havyn Villa — API

The backend service for Havyn Villa, a property rental platform for the Nigerian shortlet market. Built with Spring Boot, it handles authentication, property listings, bookings, payments, and third-party integrations.

## Tech Stack

- **Language / Runtime:** Java 24
- **Framework:** Spring Boot 3.5.3
- **Database:** PostgreSQL 16
- **Migrations:** Flyway
- **Cache / Sessions:** Redis (Memurai for local Windows development)
- **ORM:** Spring Data JPA / Hibernate
- **Auth:** Custom JWT authentication (access + refresh tokens), Google OAuth (Google Identity Services)
- **Payments:** Paystack
- **Property Data:** RayProp API (third-party shortlet listings provider)
- **Build Tool:** Gradle

## Prerequisites

- Java 24 (JDK)
- PostgreSQL 16+ running locally or accessible remotely
- Redis-compatible server running locally (Memurai on Windows, or native Redis on macOS/Linux)
- Gradle Wrapper (included — no separate Gradle install needed)

## Getting Started

### 1. Clone and navigate to the API module

```bash
cd my-app/apps/api
```

### 2. Configure environment variables

Copy the example environment file and fill in real values:

```bash
cp .env.example .env
```

Required variables:

| Variable | Description |
|---|---|
| `POSTGRES_USER` | PostgreSQL username |
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `POSTGRES_DB` | Database name (e.g. `havyn_villa`) |
| `JWT_ACCESS_SECRET` | Secret for signing access tokens |
| `JWT_REFRESH_SECRET` | Secret for signing refresh tokens (must differ from access secret) |
| `GOOGLE_CLIENT_ID` | Google OAuth Web Client ID (must match frontend's `NEXT_PUBLIC_GOOGLE_CLIENT_ID`) |
| `RAYPROP_API_KEY` | API key for RayProp property data provider |
| `RAYPROP_BASE_URL` | RayProp API base URL (default: `https://api.rayprop.io`) |
| `PAYSTACK_SECRET_KEY` | Paystack secret key for payment initialization |
| `REDIS_HOST` | Redis host (default: `localhost`) |
| `REDIS_PORT` | Redis port (default: `6379`) |

> ⚠️ `.env` is gitignored and must never be committed. Only `.env.example` (with placeholder values) belongs in version control.

### 3. Start dependencies

Make sure PostgreSQL and Redis are running locally before starting the app. On startup, Flyway will automatically apply all pending migrations found in `src/main/resources/db/migration`.

### 4. Run the application

```bash
./gradlew.bat bootRun      # Windows
./gradlew bootRun          # macOS/Linux
```

The API starts on **http://localhost:8080** by default.

### 5. Verify it's running

```bash
curl http://localhost:8080/actuator/health
```

Should return `{"status":"UP"}`.

## Project Structure

```
apps/api/
├── src/main/java/com/havyn/
│   ├── admin/           # Admin operations (disputes, verification requests)
│   ├── amenities/       # Property amenities
│   ├── audit/           # Audit logging
│   ├── auth/            # Authentication, JWT, Google OAuth verification
│   ├── booking/         # Booking lifecycle, hold expiry sweeping
│   ├── common/          # Shared reference data (roles, etc.)
│   ├── favorites/       # User favorites/wishlist
│   ├── media/            # Property media (images)
│   ├── messaging/        # Conversations and messages
│   ├── notifications/    # User notifications
│   ├── payments/         # Payments, payouts, refunds, transactions
│   ├── pricing/          # Platform pricing settings
│   ├── properties/       # Property domain, RayProp integration
│   │   └── rayprop/      # RayProp API client, sync service, DTOs
│   ├── reviews/          # Property reviews
│   └── users/            # User profiles
└── src/main/resources/
    ├── application.yml   # Main configuration
    └── db/migration/     # Flyway SQL migrations (V1, V2, ...)
```

## Key Integrations

### RayProp Property Sync

Properties are imported from RayProp (a third-party African shortlet inventory provider) rather than created manually. The sync is admin-triggered, not scheduled:

```
POST /api/v1/admin/rayprop/sync
```

Requires `ROLE_ADMIN`. This walks RayProp's paginated `/listings` endpoint, maps results onto the local `Property` schema, and upserts by `(external_source, external_id)` — safe to re-run.

RayProp sandbox keys are subject to a daily unique-listing quota (500/day on Free tier); the sync stops gracefully rather than failing when the quota is hit.

### Authentication

- Manual signup/login issues JWT access + refresh token pairs.
- Google Sign-In uses Google Identity Services (client-side GSI, not a server-side OAuth redirect) — the frontend sends an ID token to the backend, which verifies its audience against `GOOGLE_CLIENT_ID`.
- Redis is used for refresh-token/session state — the API will not authenticate correctly if Redis is unreachable.

### Payments

Payment initialization is handled via Paystack. The booking flow holds a reservation, then requires successful payment confirmation to convert the hold into a confirmed booking. Live secret keys are required for real transactions — never commit these.

## Running Tests

```bash
./gradlew.bat test                                              # full suite
./gradlew.bat test --tests "com.havyn.properties.rayprop.*"     # targeted package
```

Some tests (e.g. `RayPropLiveSmokeTest`) make real network calls and are opt-in only, guarded behind an environment flag (`RAYPROP_LIVE_SMOKE=true`) — they do not run as part of the normal suite.

## Deployment Notes

- Target platform: **Render**
- Ensure all secrets (`JWT_*_SECRET`, `RAYPROP_API_KEY`, `PAYSTACK_SECRET_KEY`, `GOOGLE_CLIENT_ID`, DB credentials) are set as environment variables in the hosting platform's dashboard — never baked into the image or committed to the repo.
- Confirm the deployed Postgres and Redis instances are reachable from the deployed API before going live.
- Remove or gate any temporary diagnostic logging (e.g. startup key-length diagnostics) behind a dev-only profile before production deploys.

## Troubleshooting

| Symptom | Likely Cause |
|---|---|
| Startup log shows `apiKeyLength=0` for RayProp | `.env` missing or `RAYPROP_API_KEY` not set — Spring does not read `.env.example` |
| RayProp calls return `404 requested path is invalid` | Check `RAYPROP_BASE_URL` — the correct route is `https://api.rayprop.io/listings` (no `/v1` prefix) |
| Signup/login fails silently or hangs | Redis is not running/reachable — confirm with `Get-Service Memurai` (Windows) or `redis-cli ping` |
| Google Sign-In fails with `GSI_LOGGER` origin error | Add the exact frontend origin (e.g. `http://localhost:3000`) under **Authorised JavaScript origins** in Google Cloud Console for the OAuth Client ID in use |
| `Using generated security password` warning at startup | Expected — this is Spring Security's default in-memory fallback; the app's real auth path uses a custom `JwtAuthenticationFilter`, not this fallback |