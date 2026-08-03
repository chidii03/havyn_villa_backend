# Required GitHub Actions secrets & variables

The single source of truth for every `secrets.*`/`vars.*` this repo's workflows
reference (excluding `secrets.GITHUB_TOKEN`, which GitHub provides automatically —
nothing to configure). `infra/github/validate.mjs` cross-checks every workflow
file against this table in both directions: nothing referenced-but-undocumented,
nothing documented-but-unused. Set these via repo/environment Settings once a real
GitHub repo exists — none of them exist anywhere yet (this repo has no git remote,
confirmed via `git remote -v`).

## Secrets

| Name | Used by | Purpose |
|---|---|---|
| `KUBE_CONFIG_STAGING` | `deploy.yml`, `rollback.yml` | Base64 kubeconfig for the staging cluster. Absent = those jobs skip cleanly (see each workflow's own gate step) rather than fail. |
| `KUBE_CONFIG_PRODUCTION` | `deploy.yml`, `rollback.yml` | Same, for production. |

## Variables

Repo/environment *variables* (`vars.*`), not secrets — none of these are sensitive
(a base URL, a referrer-restricted Maps browser key — see
`architecture/04-integrations.md#2`, "public by design").

| Name | Used by | Purpose |
|---|---|---|
| `STAGING_API_BASE_URL` | `deploy.yml` | Staging's public API URL — build arg for the web image and the target for post-deploy synthetic checks. |
| `PRODUCTION_API_BASE_URL` | `deploy.yml`, `rollback.yml` | Same, for production. |
| `SYNTHETIC_BASE_URL` | `synthetic-checks.yml` | Which environment the scheduled (every-15-min) synthetic check targets — independent of the two above so it can point anywhere (typically production). |
| `GOOGLE_MAPS_BROWSER_KEY` | `deploy.yml` | Baked into the web image at build time (`NEXT_PUBLIC_GOOGLE_MAPS_BROWSER_KEY`). |
| `GOOGLE_MAPS_MAP_ID` | `deploy.yml` | Same, `NEXT_PUBLIC_GOOGLE_MAPS_MAP_ID`. |

## Why variables, not secrets, for the non-sensitive ones

GitHub Actions has always had `secrets.*` (values are always masked in logs), but
also supports `vars.*` (`repository variables` — plain, visible values meant for
non-sensitive config). Using `vars.*` for a public API URL or a referrer-restricted
key that's meant to ship in a client bundle anyway is more honest than routing it
through `secrets.*` — it isn't a secret, and treating it as one implies a
confidentiality guarantee this project doesn't actually need there.
