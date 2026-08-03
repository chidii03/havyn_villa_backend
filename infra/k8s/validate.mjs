import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import yaml from "js-yaml";

/**
 * prompt 27's "container smoke tests... health-probe verification" — the structural
 * half achievable without a real kubectl/kustomize (see README.md's "What's verified
 * vs. not"). Checks: every manifest is well-formed YAML with the required top-level
 * fields, and every cross-reference between manifests (envFrom -> ConfigMap/Secret
 * names, HPA -> Deployment names, Ingress -> Service names, kustomization.yaml ->
 * files that actually exist, overlay patch targets -> resources that actually
 * exist) points at something real, not a typo. Run via `node validate.mjs` (after
 * `npm install`); wired into .github/workflows/ci.yml.
 */

const BASE_DIR = join(import.meta.dirname, "base");
const OVERLAY_DIRS = [join(import.meta.dirname, "overlays", "staging"), join(import.meta.dirname, "overlays", "production")];

function loadYaml(path) {
  return yaml.load(readFileSync(path, "utf8"));
}

function loadResourceFiles(dir) {
  return readdirSync(dir)
    .filter((f) => f.endsWith(".yaml") && f !== "kustomization.yaml" && !f.endsWith(".example.yaml"))
    .map((f) => ({ file: f, doc: loadYaml(join(dir, f)) }));
}

function fail(message) {
  console.error(`FAIL: ${message}`);
  process.exitCode = 1;
}

function main() {
  const resources = loadResourceFiles(BASE_DIR);
  console.log(`base/: ${resources.length} resource manifests parsed OK`);

  const byKindName = new Map();
  for (const { file, doc } of resources) {
    if (!doc?.apiVersion || !doc?.kind || !doc?.metadata?.name) {
      fail(`${file}: missing apiVersion/kind/metadata.name`);
      continue;
    }
    byKindName.set(`${doc.kind}/${doc.metadata.name}`, doc);
  }

  // envFrom -> ConfigMap/Secret name cross-check. havyn-api-secrets is deliberately
  // NOT a real resource here (see kustomization.yaml's own comment) — created
  // out-of-band in the cluster, so it's allow-listed rather than flagged.
  const allowedExternalRefs = new Set(["havyn-api-secrets"]);
  for (const { file, doc } of resources) {
    if (doc.kind !== "Deployment") continue;
    for (const ref of doc.spec.template.spec.containers[0].envFrom ?? []) {
      const name = ref.configMapRef?.name ?? ref.secretRef?.name;
      const kind = ref.configMapRef ? "ConfigMap" : "Secret";
      if (!allowedExternalRefs.has(name) && !byKindName.has(`${kind}/${name}`)) {
        fail(`${file}: envFrom references ${kind}/${name}, which doesn't exist in base/`);
      }
    }
  }

  // HPA scaleTargetRef -> Deployment name.
  for (const { file, doc } of resources) {
    if (doc.kind !== "HorizontalPodAutoscaler") continue;
    const target = `${doc.spec.scaleTargetRef.kind}/${doc.spec.scaleTargetRef.name}`;
    if (!byKindName.has(target)) {
      fail(`${file}: scaleTargetRef points at ${target}, which doesn't exist in base/`);
    }
  }

  // Ingress backend -> Service name.
  for (const { file, doc } of resources) {
    if (doc.kind !== "Ingress") continue;
    for (const rule of doc.spec.rules) {
      for (const path of rule.http.paths) {
        const svcName = path.backend.service.name;
        if (!byKindName.has(`Service/${svcName}`)) {
          fail(`${file}: Ingress rule references Service/${svcName}, which doesn't exist in base/`);
        }
      }
    }
  }

  // kustomization.yaml resources: list -> files that actually exist.
  const baseKustomization = loadYaml(join(BASE_DIR, "kustomization.yaml"));
  for (const rel of baseKustomization.resources ?? []) {
    if (!resources.some(({ file }) => file === rel)) {
      fail(`base/kustomization.yaml: lists "${rel}" which isn't a file validate.mjs found in base/`);
    }
  }

  // Overlays: well-formed, and every patch target matches a real kind+name (from
  // base, since none of these overlays define their own net-new named resources).
  const baseImageNames = new Set(
    resources
      .filter((r) => r.doc.kind === "Deployment")
      .flatMap((r) => r.doc.spec.template.spec.containers.map((c) => c.image.split(":")[0])),
  );
  for (const overlayDir of OVERLAY_DIRS) {
    const overlay = loadYaml(join(overlayDir, "kustomization.yaml"));
    const label = overlayDir.split(/[\\/]/).slice(-1)[0];
    if (!overlay.namespace) fail(`overlays/${label}: kustomization.yaml has no namespace set`);

    for (const img of overlay.images ?? []) {
      if (!baseImageNames.has(img.name)) {
        fail(`overlays/${label}: images[] references "${img.name}", which doesn't match any base/ Deployment image`);
      }
    }
    for (const patch of overlay.patches ?? []) {
      const target = `${patch.target.kind}/${patch.target.name}`;
      if (!byKindName.has(target)) {
        fail(`overlays/${label}: patch targets ${target}, which doesn't exist in base/`);
      }
    }
    console.log(`overlays/${label}/kustomization.yaml: OK (namespace=${overlay.namespace}, ${(overlay.patches ?? []).length} patches)`);
  }

  if (process.exitCode !== 1) {
    console.log("All k8s manifest cross-references check out.");
  }
}

main();
