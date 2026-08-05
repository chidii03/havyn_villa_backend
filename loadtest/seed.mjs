import { writeFileSync } from "node:fs";
import { Client } from "pg";

/**
 * Seeds fixture data for k6's hot-paths.js — 20 ACTIVE properties spread across a
 * few cities/price points, so GET /search has a real (if modest) dataset to filter/
 * paginate/index-scan over rather than a single row. Ephemeral CI/load-test Postgres
 * only — same "not fake production data" reasoning as apps/web/e2e/seed.ts (see that
 * file's own comment); this is disposable fixture data for a throwaway database.
 *
 * Writes the seeded property ids to properties.json so hot-paths.js can pick real
 * ids at init time (k6 has no direct Postgres driver).
 */
const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) {
  throw new Error("loadtest/seed.mjs needs DATABASE_URL, e.g. postgres://havyn:change_me@localhost:5432/havyn_villa");
}

const CITIES = ["Lagos", "Abuja", "Port Harcourt", "Ibadan"];
const PROPERTY_COUNT = 20;

const client = new Client({ connectionString: databaseUrl });
await client.connect();

const hostResult = await client.query(
  `INSERT INTO app_user (email, password_hash, status, email_verified_at)
   VALUES ('loadtest-host@havynvilla.test', '$argon2id$v=19$m=16,t=2,p=1$bG9hZHRlc3Q$notarealhash', 'ACTIVE', now())
   ON CONFLICT (email) DO UPDATE SET email = EXCLUDED.email
   RETURNING id`,
);
const hostId = hostResult.rows[0].id;

const hostRole = await client.query("SELECT id FROM role WHERE code = 'HOST'");
await client.query("INSERT INTO user_role (user_id, role_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [
  hostId,
  hostRole.rows[0].id,
]);

const propertyType = await client.query("SELECT id FROM property_type WHERE code = 'VILLA'");
const wifi = await client.query("SELECT id FROM amenity WHERE code = 'WIFI'");

const propertyIds = [];
for (let i = 0; i < PROPERTY_COUNT; i++) {
  const city = CITIES[i % CITIES.length];
  const basePrice = 20000 + i * 5000;
  const title = `Loadtest Property ${i}`;

  const existing = await client.query("SELECT id FROM property WHERE title = $1", [title]);
  if (existing.rows.length > 0) {
    propertyIds.push(existing.rows[0].id);
    continue;
  }

  const propertyResult = await client.query(
    `INSERT INTO property (
       host_id, type_id, title, description, address, city, state, country,
       lat, lng, currency, base_price, capacity, bedrooms, beds, bathrooms,
       cleaning_fee, service_fee_pct, cancellation_policy, status
     ) VALUES ($1, $2, $3, $4, $5, $6, $6, 'Nigeria', 6.5, 3.3, 'NGN', $7, 4, 2, 2, 2, 5000, 10, 'FLEXIBLE', 'ACTIVE')
     RETURNING id`,
    [hostId, propertyType.rows[0].id, title, "Load-test fixture — not a real listing.", "1 Fixture Rd", city, basePrice],
  );
  const propertyId = propertyResult.rows[0].id;
  await client.query("INSERT INTO property_amenity (property_id, amenity_id) VALUES ($1, $2)", [
    propertyId,
    wifi.rows[0].id,
  ]);
  propertyIds.push(propertyId);
}

await client.end();

writeFileSync(new URL("./properties.json", import.meta.url), JSON.stringify(propertyIds, null, 2));
console.log(`Seeded ${propertyIds.length} load-test properties.`);
