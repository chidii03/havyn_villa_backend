import { chromium } from "../apps/web/node_modules/@playwright/test/index.mjs";

const baseUrl = process.env.WEB_BASE_URL ?? "http://localhost:3000";
const password = process.env.ISSUE1B_PASSWORD ?? "correct-horse-battery-staple";
const accounts = {
  customer: process.env.ISSUE1B_CUSTOMER_EMAIL,
  host: process.env.ISSUE1B_HOST_EMAIL,
  admin: process.env.ISSUE1B_ADMIN_EMAIL,
};
const concise = process.env.ISSUE1B_CONCISE === "1";
const requestedCases = process.env.ISSUE1B_CASES?.split(",").map((item) => {
  const [kind, path] = item.split(":");
  return { kind, path };
});

async function run(accountKind, email, path) {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ?? "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  });
  const context = await browser.newContext();
  const page = await context.newPage();
  page.setDefaultTimeout(45_000);
  page.setDefaultNavigationTimeout(90_000);
  const events = [];

  page.on("console", (message) => {
    if (message.type() === "error") {
      events.push({ type: "console-error", text: message.text() });
    }
  });
  page.on("pageerror", (error) => {
    events.push({ type: "page-error", text: error.message });
  });
  page.on("response", async (response) => {
    const url = response.url();
    if (!url.includes("/api/v1/")) {
      return;
    }
    let body = "";
    try {
      body = await response.text();
    } catch {
      body = "<unreadable>";
    }
    events.push({ type: "network", method: response.request().method(), url, status: response.status(), body: body.slice(0, 500) });
  });

  await page.goto(`${baseUrl}/login`);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Log in" }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 15000 });

  let navigationError = null;
  try {
    await page.goto(`${baseUrl}${path}`, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(3500);
  } catch (error) {
    navigationError = error instanceof Error ? error.message : String(error);
  }

  const snapshot = {
    accountKind,
    path,
    navigationError,
    finalUrl: page.url(),
    heading: await page.locator("h1").first().textContent().catch(() => null),
    bodyText: (await page.locator("body").innerText().catch(() => "")).replace(/\s+/g, " ").slice(0, 800),
    events: concise
      ? events
          .filter((event) => event.type === "network" && event.url?.includes("/api/v1/auth/refresh"))
          .map((event) => ({ method: event.method, url: event.url, status: event.status }))
      : events,
  };

  await browser.close();
  return snapshot;
}

const results = [];
const cases = requestedCases ?? Object.keys(accounts).flatMap((kind) => ["/admin", "/host"].map((path) => ({ kind, path })));

for (const { kind, path } of cases) {
  const email = accounts[kind];
  if (!email || !path) {
    continue;
  }
  try {
    results.push(await run(kind, email, path));
  } catch (error) {
    results.push({ accountKind: kind, path, setupError: error instanceof Error ? error.message : String(error) });
  }
}

console.log(JSON.stringify(results, null, 2));
