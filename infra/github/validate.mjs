import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import yaml from "js-yaml";

/**
 * prompt 28's "pipeline dry-run" — there's no `act`(+Docker) here to actually run
 * GitHub Actions locally (no Docker in this dev sandbox — same gap as everywhere
 * else), so this is the structural proxy: every workflow is well-formed YAML,
 * every `needs:` reference points at a job that actually exists in the same
 * workflow (a real, easy-to-typo class of bug — GitHub only catches it by failing
 * the whole workflow run), and every `secrets.*`/`vars.*` reference matches
 * `required-secrets-and-vars.md` in both directions. Run via `node validate.mjs`
 * (after `npm install`); wired into .github/workflows/ci.yml.
 */

const WORKFLOWS_DIR = join(import.meta.dirname, "..", "..", ".github", "workflows");

function fail(message) {
  console.error(`FAIL: ${message}`);
  process.exitCode = 1;
}

function extractRefs(text, prefix) {
  const names = new Set();
  const pattern = new RegExp(`\\b${prefix}\\.([A-Z_][A-Z0-9_]*)\\b`, "g");
  for (const match of text.matchAll(pattern)) {
    if (prefix === "secrets" && match[1] === "GITHUB_TOKEN") continue; // built-in, not configured by anyone
    names.add(match[1]);
  }
  return names;
}

function checkNeedsReferences(file, doc) {
  const jobNames = new Set(Object.keys(doc.jobs ?? {}));
  for (const [jobId, job] of Object.entries(doc.jobs ?? {})) {
    const needs = job.needs ? (Array.isArray(job.needs) ? job.needs : [job.needs]) : [];
    for (const dep of needs) {
      if (!jobNames.has(dep)) {
        fail(`${file}: job "${jobId}" needs "${dep}", which isn't defined in this workflow`);
      }
    }
  }
}

function parseDocTable(markdown, sectionHeading) {
  const names = new Set();
  const section = markdown.split(sectionHeading)[1]?.split(/\n## /)[0] ?? "";
  for (const match of section.matchAll(/^\|\s*`([A-Z_][A-Z0-9_]*)`\s*\|/gm)) {
    names.add(match[1]);
  }
  return names;
}

function main() {
  const files = readdirSync(WORKFLOWS_DIR).filter((f) => f.endsWith(".yml") || f.endsWith(".yaml"));
  const allSecrets = new Set();
  const allVars = new Set();

  for (const file of files) {
    const text = readFileSync(join(WORKFLOWS_DIR, file), "utf8");
    const doc = yaml.load(text);
    if (!doc?.jobs) {
      fail(`${file}: no top-level "jobs" key`);
      continue;
    }
    checkNeedsReferences(file, doc);
    for (const name of extractRefs(text, "secrets")) allSecrets.add(name);
    for (const name of extractRefs(text, "vars")) allVars.add(name);
  }
  console.log(`${files.length} workflow files parsed, "needs:" references OK.`);

  const docsPath = join(import.meta.dirname, "required-secrets-and-vars.md");
  const docs = readFileSync(docsPath, "utf8");
  const documentedSecrets = parseDocTable(docs, "## Secrets");
  const documentedVars = parseDocTable(docs, "## Variables");

  for (const name of allSecrets) {
    if (!documentedSecrets.has(name)) {
      fail(`secrets.${name} is used in a workflow but not documented in required-secrets-and-vars.md`);
    }
  }
  for (const name of documentedSecrets) {
    if (!allSecrets.has(name)) {
      fail(`required-secrets-and-vars.md documents secrets.${name}, but no workflow references it`);
    }
  }
  for (const name of allVars) {
    if (!documentedVars.has(name)) {
      fail(`vars.${name} is used in a workflow but not documented in required-secrets-and-vars.md`);
    }
  }
  for (const name of documentedVars) {
    if (!allVars.has(name)) {
      fail(`required-secrets-and-vars.md documents vars.${name}, but no workflow references it`);
    }
  }

  if (process.exitCode !== 1) {
    console.log(`All ${allSecrets.size} secrets and ${allVars.size} vars match required-secrets-and-vars.md exactly.`);
  }
}

main();
