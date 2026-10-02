# Havyn Villa API on Kubernetes (learning only)

These plain manifests are for KillerCoda/Kubernetes practice: nodes, pods,
services, deployments, and pod self-healing. They are not the production
deployment path. Production Railway uses `apps/api/railway.json` and the same
portable `apps/api/Dockerfile`.

The repository's existing `infra/k8s/base` and `infra/k8s/overlays` Kustomize
setup is kept separate. These files are intentionally standalone for copy/paste
testing in KillerCoda.

## Before applying

1. Push an API image to a registry reachable by the cluster.
2. Replace the placeholder image in `deployment.yaml`.
3. Create the namespace and real Secret out-of-band. Do not apply
   `secret.yaml`; it is a placeholder template only.

```bash
kubectl apply -f infra/k8s/learning/namespace.yaml
kubectl -n havyn-villa create secret generic havyn-api-secrets \
  --from-literal=POSTGRES_USER='...' \
  --from-literal=POSTGRES_PASSWORD='...' \
  --from-literal=JWT_ACCESS_SECRET='...' \
  --from-literal=JWT_REFRESH_SECRET='...'
kubectl apply -f infra/k8s/learning/configmap.yaml -f infra/k8s/learning/deployment.yaml -f infra/k8s/learning/service.yaml
```

For a disposable exercise where placeholder values are acceptable, all
non-secret manifests can also be applied together:

```bash
kubectl apply -f infra/k8s/learning/namespace.yaml -f infra/k8s/learning/configmap.yaml -f infra/k8s/learning/deployment.yaml -f infra/k8s/learning/service.yaml
```

## Inspect the cluster

```bash
kubectl get nodes
kubectl -n havyn-villa get pods
kubectl -n havyn-villa get svc
kubectl -n havyn-villa get deployments
kubectl -n havyn-villa rollout status deployment/havyn-api
```

## Demonstrate self-healing

```bash
kubectl -n havyn-villa get pods -l app=havyn-api
kubectl -n havyn-villa delete pod "$(kubectl -n havyn-villa get pod -l app=havyn-api -o jsonpath='{.items[0].metadata.name}')"
kubectl -n havyn-villa get pods -l app=havyn-api -w
```

The Deployment should create a replacement pod and return to two replicas.

## View logs

```bash
kubectl -n havyn-villa logs deployment/havyn-api --all-containers=true --tail=100
kubectl -n havyn-villa logs -f pod/<pod-name> -c api
```
