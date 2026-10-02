const BASE_URL = process.env.SYNTHETIC_BASE_URL;
if (!BASE_URL) {
  console.log("SYNTHETIC_BASE_URL not set — nothing to check against yet (no staging/prod deployed, see prompts 27/29). Skipping.");
  process.exit(0);
}

const TIMEOUT_MS = 10_000;

async function check(name, fn) {
  const start = Date.now();
  try {
    await fn();
    console.log(`OK   ${name} (${Date.now() - start}ms)`);
    return true;
  } catch (error) {
    console.error(`FAIL ${name} (${Date.now() - start}ms): ${error.message}`);
    return false;
  }
}

async function fetchJson(path, options) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(`${BASE_URL}${path}`, { ...options, signal: controller.signal });
    const body = await res.json().catch(() => null);
    return { status: res.status, body };
  } finally {
    clearTimeout(timeout);
  }
}

async function main() {
  const results = [];

  results.push(
    await check("GET /actuator/health is UP", async () => {
      const { status, body } = await fetchJson("/actuator/health");
      if (status !== 200) throw new Error(`status ${status}`);
      if (body?.status !== "UP") throw new Error(`status field was "${body?.status}"`);
    }),
  );

  let firstPropertyId = null;
  results.push(
    await check("GET /api/v1/search returns a page of real listings", async () => {
      const { status, body } = await fetchJson("/api/v1/search?sort=newest&size=1");
      if (status !== 200) throw new Error(`status ${status}`);
      if (!Array.isArray(body?.data)) throw new Error("response has no data array");
      firstPropertyId = body.data[0]?.id ?? null;
    }),
  );

  results.push(
    await check("POST /properties/{id}/quote returns a real price breakdown", async () => {
      if (!firstPropertyId) {
        throw new Error("no property id from the search check above — nothing to quote (search may be returning zero listings)");
      }
      const checkIn = new Date(Date.now() + 30 * 86_400_000).toISOString().slice(0, 10);
      const checkOut = new Date(Date.now() + 33 * 86_400_000).toISOString().slice(0, 10);
      const { status, body } = await fetchJson(`/api/v1/properties/${firstPropertyId}/quote`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ checkIn, checkOut, guests: 1 }),
      });
      if (status !== 200) throw new Error(`status ${status}`);
      if (typeof body?.grandTotal !== "number") throw new Error("response has no numeric grandTotal");
    }),
  );

  const failures = results.filter((ok) => !ok).length;
  if (failures > 0) {
    console.error(`${failures}/${results.length} synthetic checks failed.`);
    process.exit(1);
  }
  console.log(`All ${results.length} synthetic checks passed.`);
}

main();
