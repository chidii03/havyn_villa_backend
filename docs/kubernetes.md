# Kubernetes Deployment Documentation

**Phase:** 4 — Kubernetes  
**Manifests location:** `infra/k8s/`  
**Scope:** Workload orchestration, service discovery, configuration, health probes, resource sizing, and rollback procedures.

---

## 1. Architecture & Directory Layout

The Kubernetes infrastructure uses **Kustomize** to maintain DRY manifests across staging and production environments:

```text
infra/k8s/
├── base/
│   ├── backend-deployment.yaml        # API Deployment (2 replicas, non-root, read-only FS)
│   ├── backend-service.yaml           # ClusterIP Service on port 8080
│   ├── backend-configmap.yaml         # Non-sensitive configuration (DB host, ports, logs)
│   ├── backend-secret.example.yaml    # Secret template (passwords, JWT secrets, Paystack keys)
│   ├── backend-hpa.yaml               # HorizontalPodAutoscaler (CPU/Memory targets)
│   ├── ingress.yaml                   # Ingress rules with TLS configuration
│   └── kustomization.yaml             # Base resource aggregator
├── overlays/
│   ├── staging/                       # Namespace: havyn-staging, 1 replica
│   │   └── kustomization.yaml
│   └── production/                    # Namespace: havyn-production, 2-6 replicas, higher resources
│       └── kustomization.yaml
├── learning/                          # Single-namespace demonstrative manifests
└── validate.mjs                       # CI/CD manifest structural verification script
```

---

## 2. Workload Specifications

### Spring Boot Backend (`havyn-api`)
- **Base Replicas**: 2 (staging: 1; production: 2 with autoscaling up to 6).
- **Security Context**:
  - `runAsNonRoot: true`
  - `runAsUser: 1000`
  - `readOnlyRootFilesystem: true` (with writable `/tmp` emptyDir volume)
  - `allowPrivilegeEscalation: false`
  - `capabilities.drop: ["ALL"]`
- **Probes**:
  - **Readiness Probe**: `GET /actuator/health/readiness` on port 8080 (initial delay: 25s, period: 10s). Distinguishes application startup and database/Flyway readiness.
  - **Liveness Probe**: `GET /actuator/health/liveness` on port 8080 (initial delay: 45s, period: 15s).
- **Resources**:
  - Requests: CPU 250m, Memory 512Mi.
  - Limits: CPU 500m, Memory 1Gi (production: CPU 1000m, Memory 2Gi).

### Database & Redis Integration
- Spring Boot uses connection strings provided via `ConfigMap` (`POSTGRES_HOST`, `POSTGRES_PORT`, `REDIS_HOST`, `REDIS_PORT`) and credentials provided via the Kubernetes Secret `havyn-api-secrets`.
- Database schema migrations are handled automatically during pod bootstrap via Flyway (`spring.flyway.enabled: true`). The readiness probe prevents traffic from routing to the pod until migrations finish and the context is healthy.

---

## 3. Deployment Commands

### Validate Manifests Locally
```sh
cd infra/k8s
npm install
node validate.mjs
```

### Deploy to Staging
```sh
kubectl apply -k infra/k8s/overlays/staging
kubectl rollout status deployment/havyn-api -n havyn-staging
```

### Deploy to Production
```sh
kubectl apply -k infra/k8s/overlays/production
kubectl rollout status deployment/havyn-api -n havyn-production
```

---

## 4. Rollout Verification & Troubleshooting

### Check Pod Status
```sh
kubectl get pods -n havyn-staging -l app=havyn-api
```

### Inspect Pod Logs
```sh
kubectl logs -f deployment/havyn-api -n havyn-staging
```

### Rollback Failed Deployment
If a deployment fails readiness checks or encounters a runtime defect:
```sh
kubectl rollout undo deployment/havyn-api -n havyn-staging
kubectl rollout status deployment/havyn-api -n havyn-staging
```
