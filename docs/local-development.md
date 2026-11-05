# Local development

This workspace currently has separate API (`my-app`) and frontend (`web`) repositories. The root Compose file intentionally runs only the API and its confirmed dependencies; it does not pretend the sibling frontend is part of the API repository's build context.

## API, PostgreSQL, Redis, and MailHog

From `my-app`:

```sh
cp .env.example .env
# Replace the three password/secret placeholders in .env.
docker compose up --build
```

Useful endpoints:

- API readiness: `http://localhost:8080/actuator/health/readiness`
- API liveness: `http://localhost:8080/actuator/health/liveness`
- API metrics (authenticated): `http://localhost:8080/actuator/prometheus`
- MailHog UI: `http://localhost:8025`

The Compose stack uses a named `havyn_postgres_data` volume. To remove local development data deliberately, run `docker compose down --volumes`; this deletes the local database volume and must never be used against a production environment.

Redis is intentionally non-persistent because its confirmed roles are refresh-token/session state and a best-effort search cache. Restarting Redis invalidates local sessions and cache entries; it must not delete bookings or payments, which are stored in PostgreSQL.

`RAYPROP_SYNC_ON_STARTUP` is disabled in Compose so bringing up the local stack does not call a third-party service. Use the authenticated administrative sync only when a test API key is intentionally configured.

## Frontend

From the separate `web` repository:

```sh
cp .env.example .env
npm ci
npm run dev
```

Set `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`. The backend's `WEB_BASE_URL` must remain `http://localhost:3000` for local CORS and refresh-cookie behavior.

To build the production frontend image, pass only public build-time values:

```sh
docker build -t havyn-web:local \
  --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 \
  --build-arg NEXT_PUBLIC_GOOGLE_CLIENT_ID=replace-with-client-id \
  .
```

Run that command from `web`. Do not pass Paystack, database, JWT, or other server-side secrets as frontend build arguments.

## Logs and shutdown

Use `docker compose logs -f backend`, `docker compose logs -f postgres`, or `docker compose logs -f redis` from `my-app`. Stop the stack with `docker compose down`, which preserves the database volume.

## Codespaces note

Codespaces must have Docker access for Compose/image validation. If it does not, run the API using the Gradle wrapper with a separately provisioned development PostgreSQL/Redis instance, and record that limitation rather than claiming a Compose run succeeded.
