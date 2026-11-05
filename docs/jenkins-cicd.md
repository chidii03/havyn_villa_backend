# Jenkins CI/CD Pipeline Documentation

**Phase:** 3 — Automated testing and CI  
**File:** `Jenkinsfile` at repository root  
**Scope:** Automated validation, frontend & backend compilation, testing, coverage gating, Docker image creation, security scanning, manifest validation, gated staging deployment, and production approval.

---

## 1. Pipeline Overview & Architecture

The Jenkins pipeline (`Jenkinsfile`) implements the 14 continuous integration and continuous deployment stages defined in `AGENTS.md` §10:

```text
[1. Checkout] 
       │
       ▼
[2. Tool & Environment Validation]
       │
       ├─────────────────────────────────┐
       ▼                                 ▼
[3. Install Frontend Deps]        [6. Compile Backend]
       │                                 │
       ▼                                 ▼
[4. Frontend Lint, Types & Tests] [7. Backend Tests & Coverage Gate]
       │                                 │
       ▼                                 ▼
[5. Build Next.js App]            [8. Build Container Images]
       │                                 │
       └────────────────┬────────────────┘
                        ▼
           [9. Security & Secret Checks]
                        │
                        ▼
           [10. Publish Container Images] (Main/Release branch)
                        │
                        ▼
           [11. Validate Kubernetes Manifests & Observability]
                        │
                        ▼
           [12. Deploy to Staging Cluster] (Gated on credentials)
                        │
                        ▼
           [13. Deployment Smoke Tests]
                        │
                        ▼
           [14. Manual Approval & Production Deploy]
```

---

## 2. Pipeline Stages

| Stage | Action | Failure Behavior |
| --- | --- | --- |
| **1. Checkout** | Fetches repository revision and derives immutable `GIT_COMMIT` SHA (`IMAGE_TAG`). | Halts pipeline immediately on Git failure. |
| **2. Validate Environment** | Verifies Java 21, Node 20, npm, Docker CLI, and identifies frontend source tree. | Alerts if tooling is missing on agent. |
| **3. Install Frontend Deps** | Executes `npm ci --prefer-offline` in `web`. | Halts on dependency or package lock mismatch. |
| **4. Frontend Lint & Tests** | Executes TypeScript (`npm run typecheck`), ESLint, and Vitest suite (`npm run test`). | Halts on type errors or unit test failures. |
| **5. Build Frontend** | Compiles Next.js standalone application (`npm run build`). | Halts on production compilation errors. |
| **6. Compile Backend** | Runs Gradle wrapper (`./gradlew compileJava compileTestJava`). | Halts on Java syntax or compilation errors. |
| **7. Backend Tests & Coverage** | Executes JUnit tests and JaCoCo coverage check (`./gradlew test jacocoTestReport jacocoTestCoverageVerification`). | **Strict Quality Gate**: Halts if tests fail or if code coverage falls below configured thresholds (`0.65` line coverage for domain/pricing/booking). |
| **8. Build Container Images** | Multi-stage Docker builds for `havyn-api` and `havyn-web`, tagging with Git SHA and `latest`. | Halts on Dockerfile build errors. |
| **9. Security Checks** | Hadolint linter on Dockerfile; scans source tree to prevent committed private keys or secrets. | Halts if private keys are found in resources. |
| **10. Publish Container Images** | Pushes images to configured registry (GHCR/DockerHub) using `container-registry-credentials`. | Gracefully skips if registry credentials are not provisioned in Jenkins. |
| **11. Validate Manifests** | Runs `node infra/k8s/validate.mjs` and `node infra/observability/validate.mjs` to check YAML well-formedness and cross-references. | Halts if Kubernetes or alert definitions have broken references. |
| **12. Deploy to Staging** | Applies Kustomize staging overlay (`kubectl apply -k infra/k8s/overlays/staging`) and waits for rollout. | Gracefully skips if `k8s-staging-kubeconfig` credential is unconfigured. |
| **13. Smoke Tests** | Runs synthetic checks (`node infra/observability/synthetic-checks.mjs`) against `STAGING_BASE_URL`. | Halts if health check fails on deployed service. |
| **14. Production Gate** | Requires manual operator approval (`input`) before applying production overlay (`kubectl apply -k infra/k8s/overlays/production`). | Never deploys automatically to production. |

---

## 3. Jenkins Agent Prerequisites

To execute the complete pipeline, the Jenkins controller or build agent requires:

1. **JDK 21**: Configured under *Manage Jenkins → Global Tool Configuration → JDK* named `JDK-21`.
2. **NodeJS 20.x**: Configured under *Manage Jenkins → Global Tool Configuration → NodeJS* named `NodeJS-20`.
3. **Docker Engine**: Docker socket mounted or Docker-in-Docker (DinD) enabled on the Jenkins agent.
4. **Kubectl** *(optional for deployment stages)*: Installed on the agent PATH for cluster deployments.

### Required Jenkins Plugins
- `Pipeline` (Workflow Aggregator)
- `NodeJS Plugin`
- `Docker Pipeline`
- `JUnit Plugin`
- `HTML Publisher Plugin` (for JaCoCo coverage reports)
- `AnsiColor`
- `Credentials Binding Plugin`

---

## 4. Credential Configuration

Configure the following credentials in Jenkins (*Manage Jenkins → Credentials*):

| Credential ID | Type | Description |
| --- | --- | --- |
| `container-registry-credentials` | Username with password | GitHub Personal Access Token (or Docker Hub token) for pushing container images. |
| `k8s-staging-kubeconfig` | Secret file | Kubeconfig file with RBAC access to the `havyn-staging` namespace. |
| `k8s-production-kubeconfig` | Secret file | Kubeconfig file with RBAC access to the `havyn-production` namespace. |

*Note: In accordance with `AGENTS.md` §10, no secrets or tokens are hardcoded inside the `Jenkinsfile`.*

---

## 5. Running the Pipeline

1. In Jenkins, create a new **Pipeline** or **Multibranch Pipeline** job:
   - **Name:** `HavynVilla-CI-CD`
   - **Definition:** Pipeline script from SCM
   - **SCM:** Git
   - **Repository URL:** *(Your repository remote URL)*
   - **Script Path:** `Jenkinsfile`
2. Click **Save** and select **Build Now**.
3. View the pipeline stage graph in Blue Ocean or the classic Jenkins UI.
4. Access the generated **JaCoCo Coverage Report** and **Test Result Trends** directly from the Jenkins build dashboard.

---

## 6. Failure Simulation & Verification

The pipeline has been designed to fail fast:
1. **Broken Test**: Introducing a failing test in `apps/api/src/test` blocks the pipeline at Stage 7 before any container image is built.
2. **Coverage Drop**: Dropping test coverage below 65% triggers JaCoCo verification failure at Stage 7.
3. **Broken K8s Manifest**: Any typo in ConfigMap names or Ingress paths triggers a failure at Stage 11 via `node infra/k8s/validate.mjs`.
4. **Production Protection**: Pull requests and staging commits can never trigger production deployments; only the `main` branch can reach Stage 14, and only after interactive human sign-off.
