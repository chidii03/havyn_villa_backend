import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import yaml from "js-yaml";

/**
 * prompt 26's "alert-rule tests" — no live Prometheus to load infra/observability/
 * alerts.yml into here (no Docker in this dev sandbox — see devops/
 * 02-observability.md's prompt 26 notes), so this is what's actually checkable
 * without one: alerts.yml/dashboard.json are well-formed, and every `havyn_*`
 * metric either file references by name is a real metric this app actually emits
 * — not a typo, not a renamed-and-forgotten-to-update-the-alert metric. Run via
 * `node validate.mjs` from this directory (after `npm install`); wired into
 * .github/workflows/ci.yml.
 */

const JAVA_SRC = join(import.meta.dirname, "..", "..", "apps", "api", "src", "main", "java");

function allJavaFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...allJavaFiles(full));
    } else if (entry.endsWith(".java")) {
      out.push(full);
    }
  }
  return out;
}

function toPrometheusCounterName(dotted) {
  return dotted.replaceAll(".", "_") + "_total";
}

function emittedMetricNames() {
  const names = new Set();
  const pattern = /meterRegistry\.counter\("(havyn\.[a-zA-Z0-9_.]+)"/g;
  for (const file of allJavaFiles(JAVA_SRC)) {
    const content = readFileSync(file, "utf8");
    for (const match of content.matchAll(pattern)) {
      names.add(toPrometheusCounterName(match[1]));
    }
  }
  return names;
}

function referencedMetricNames(text) {
  const names = new Set();
  for (const match of text.matchAll(/\bhavyn_[a-zA-Z0-9_]+\b/g)) {
    names.add(match[0]);
  }
  return names;
}

function main() {
  const alertsText = readFileSync(join(import.meta.dirname, "alerts.yml"), "utf8");
  const alertsDoc = yaml.load(alertsText);
  if (!alertsDoc?.groups?.length) {
    throw new Error("alerts.yml: no rule groups found");
  }
  let ruleCount = 0;
  for (const group of alertsDoc.groups) {
    for (const rule of group.rules ?? []) {
      ruleCount++;
      if (!rule.alert || !rule.expr || !rule.for || !rule.annotations?.summary) {
        throw new Error(`alerts.yml: rule "${rule.alert ?? "?"}" is missing alert/expr/for/annotations.summary`);
      }
    }
  }
  console.log(`alerts.yml: OK (${alertsDoc.groups.length} groups, ${ruleCount} rules)`);

  const dashboardText = readFileSync(join(import.meta.dirname, "dashboard.json"), "utf8");
  const dashboard = JSON.parse(dashboardText);
  if (!Array.isArray(dashboard.panels) || dashboard.panels.length === 0) {
    throw new Error("dashboard.json: no panels found");
  }
  console.log(`dashboard.json: OK (${dashboard.panels.length} panels)`);

  const emitted = emittedMetricNames();
  const referenced = new Set([...referencedMetricNames(alertsText), ...referencedMetricNames(dashboardText)]);
  const unknown = [...referenced].filter((name) => !emitted.has(name));

  if (unknown.length > 0) {
    console.error("Referenced havyn_* metrics with no matching meterRegistry.counter(...) call in apps/api/src/main/java:");
    for (const name of unknown) console.error(`  - ${name}`);
    console.error("Known emitted metrics:", [...emitted].sort());
    process.exit(1);
  }
  console.log(`All ${referenced.size} referenced havyn_* metrics match a real emitted counter:`, [...referenced].sort());
}

main();
