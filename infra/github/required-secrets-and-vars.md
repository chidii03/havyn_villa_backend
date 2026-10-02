# Required GitHub Actions secrets & variables

The single source of truth for every `secrets.*`/`vars.*` this backend repo's
workflows reference (excluding `secrets.GITHUB_TOKEN`, which GitHub provides
automatically). `infra/github/validate.mjs` cross-checks every workflow file
against this table in both directions.

## Secrets

| Name | Used by | Purpose |
|---|---|---|
| `KUBE_CONFIG_STAGING` | `deploy.yml`, `rollback.yml` | Base64 kubeconfig for the staging cluster. Absent = those jobs skip cleanly (see each workflow's own gate step) rather than fail. |
| `KUBE_CONFIG_PRODUCTION` | `rollback.yml` | Base64 kubeconfig for an optional manually managed production-like cluster. |

## Variables

Repo/environment *variables* (`vars.*`), not secrets — none of these are sensitive
(a base URL, a referrer-restricted Maps browser key — see
`architecture/04-integrations.md#2`, "public by design").

| Name | Used by | Purpose |
|---|---|---|
| `STAGING_API_BASE_URL` | `rollback.yml` | Public backend URL used for the post-rollback synthetic check. |
| `PRODUCTION_API_BASE_URL` | `rollback.yml` | Public backend URL used for the production rollback synthetic check. |
| `SYNTHETIC_BASE_URL` | `synthetic-checks.yml` | Which backend environment the scheduled synthetic check targets. |

## Why variables, not secrets, for the non-sensitive ones

GitHub Actions has always had `secrets.*` (values are always masked in logs), but
also supports `vars.*` (`repository variables` — plain, visible values meant for
non-sensitive config). Using `vars.*` for a public API URL or a referrer-restricted
key that's meant to ship in a client bundle anyway is more honest than routing it
through `secrets.*` — it isn't a secret, and treating it as one implies a
confidentiality guarantee this project doesn't actually need there.
