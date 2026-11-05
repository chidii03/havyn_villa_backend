# Observability & Monitoring Guide

**Phase:** 5 — Observability (Prometheus & Grafana)  
**Configuration Files:**  
- Prometheus scrape & rules: `infra/observability/prometheus.yml`, `infra/observability/alerts.yml`  
- Grafana dashboard: `infra/observability/dashboard.json`  
- Grafana provisioning: `infra/observability/grafana/provisioning/`  
- Local Docker Compose stack: `docker-compose.observability.yml`  
- Kubernetes manifests: `infra/k8s/observability/`  

---

## 1. Quick Start: Launching Locally on Your PC

You can launch the complete monitoring stack alongside the application using Docker Compose.

### Step 1: Start Application Dependencies & API
From the `my-app` directory:
```powershell
docker compose up -d
```
*(Ensure `.env` exists; if not, `cp .env.example .env` and fill required secrets)*

### Step 2: Start Prometheus & Grafana Monitoring Stack
Run Docker Compose with both the base and observability compose files:
```powershell
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
```

### Step 3: Open Dashboards in Your Browser
- **Grafana Dashboard:** [http://localhost:3001](http://localhost:3001)
  - **Credentials:** Username: `admin` / Password: `admin` (Anonymous viewing is also enabled by default).
  - The **"Havyn Villa API — Golden Signals & Business KPIs"** dashboard is pre-provisioned and automatically loaded!
- **Prometheus UI:** [http://localhost:9090](http://localhost:9090)
  - Navigate to **Status → Targets** to verify `backend:8080` (or `host.docker.internal:8080`) is in state `UP`.
  - Navigate to **Alerts** to view the loaded alert rules.

---

## 2. Pre-Provisioned Grafana Dashboard Panels

The dashboard located at `infra/observability/dashboard.json` automatically connects to Prometheus and includes 13 panels covering Google SRE Golden Signals and domain KPIs:

### Golden Signals
1. **Request Rate (by outcome)**: `sum(rate(http_server_requests_seconds_count[5m])) by (outcome)`
2. **5xx Error Rate (%)**: Ratio of HTTP 5xx responses to total requests.
3. **Latency Percentiles (p50 / p95 / p99)**: `histogram_quantile` across routes.
4. **JVM Process Memory**: Heap, non-heap, and total committed JVM memory.
5. **JVM CPU Utilization**: Container CPU vs. system load.

### Domain & Business KPIs
6. **Bookings Created**: Real-time counter of successful short-let bookings.
7. **Booking Create Failures**: Breakdown by failure reason (e.g. hold conflict, validation).
8. **Booking Hold Expirations**: Tracks sweeps and abandoned booking holds.
9. **Payment Webhook Outcomes**: Categorized by provider and result (`success`, `invalid_signature`, `unmatched_ref`).
10. **Bookings Cancelled**: Breakdown of refund vs. non-refund cancellations.

### System & Infrastructure Health
11. **HikariCP Connection Pool Utilization**: Ratio of active DB connections to pool maximum (`hikaricp_connections_active / hikaricp_connections_max`).
12. **Rate-Limit Rejections**: Spike detection across `/api/v1/auth`, `/api/v1/search`, and `/api/v1/bookings`.

---

## 3. Prometheus Alerting Rules (`alerts.yml`)

The rules defined in `infra/observability/alerts.yml` are loaded directly into Prometheus:

| Alert Rule | Condition | Duration | Severity |
| --- | --- | --- | --- |
| `HighHttpServerErrorRate` | 5xx rate > 5% | 5m | `critical` |
| `HighP95Latency` | Route p95 latency > 500ms | 10m | `warning` |
| `PaymentWebhookFailuresElevated` | Webhook verification/signature failures > 0.1/s | 5m | `critical` |
| `BookingHoldExpirationsSpike` | Expired hold rate > 0.2/s | 15m | `warning` |
| `DbPoolSaturation` | Hikari connection utilization > 80% | 5m | `warning` |
| `RateLimitRejectionsElevated` | Rate limit rejection rate > 1/s | 5m | `warning` |

---

## 4. Deploying Observability to Kubernetes

If running a local Kubernetes cluster (Minikube, Kind, k3d, or Docker Desktop Kubernetes):

```powershell
kubectl apply -k infra/k8s/observability
```

### Accessing via Port Forwarding
```powershell
kubectl port-forward svc/grafana 3001:3000
kubectl port-forward svc/prometheus 9090:9090
```
Open [http://localhost:3001](http://localhost:3001) to view the Grafana dashboard.
