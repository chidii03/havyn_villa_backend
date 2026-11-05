# Docker images

## API

Build from `my-app`:

```sh
docker build -t havyn-api:local ./apps/api
```

The API Dockerfile uses a Java 21 multi-stage build, excludes local `.env` files and build output from the context, runs as a non-root user, and sets memory percentages suitable for a container limit. Credentials are runtime environment variables only.

## Frontend

Build from the separate `web` repository:

```sh
docker build -t havyn-web:local \
  --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 \
  .
```

The frontend image uses Next.js standalone output and a non-root runtime user. `NEXT_PUBLIC_*` values are intentionally embedded in browser output and must therefore never contain secrets.

## Validation and troubleshooting

After a successful API image build, run it through `docker compose up --build` from `my-app` and wait for the backend readiness health check. A failed readiness check normally means PostgreSQL, Redis, or MailHog is unavailable; inspect `docker compose logs -f backend` first.

Docker image builds were not run during Phase 2 in this environment because the available sandbox previously blocked Gradle's dependency download and does not provide confirmed Docker daemon access. A successful Docker build must be recorded separately before being represented as verified.
