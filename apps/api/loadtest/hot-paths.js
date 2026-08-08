import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";
import { SharedArray } from "k6/data";


const BASE_URL = __ENV.API_BASE_URL || "http://localhost:8080";

const properties = new SharedArray("properties", function () {
  return JSON.parse(open("./properties.json"));
});

const bookingErrors = new Rate("booking_errors");

export const options = {
  scenarios: {
    search: {
      executor: "constant-vus",
      exec: "search",
      vus: 20,
      duration: "30s",
    },
    property_detail: {
      executor: "constant-vus",
      exec: "propertyDetail",
      vus: 20,
      duration: "30s",
    },
    quote: {
      executor: "constant-vus",
      exec: "quote",
      vus: 10,
      duration: "30s",
    },
    booking: {
      executor: "constant-vus",
      exec: "booking",
      vus: 5,
      duration: "30s",
    },
  },
  thresholds: {
    "http_req_duration{scenario:search}": ["p(95)<300"],
    "http_req_duration{scenario:property_detail}": ["p(95)<150"], // the cache is the point — should be fast
    "http_req_duration{scenario:quote}": ["p(95)<200"],
    "http_req_duration{scenario:booking}": ["p(95)<400"],
    booking_errors: ["rate<0.05"], // a handful of legitimate DATES_UNAVAILABLE races is fine; a lot isn't
  },
};

function randomProperty() {
  return properties[Math.floor(Math.random() * properties.length)];
}

/** Spread across a wide future range so concurrent VUs mostly land on different dates. */
function randomDateRange() {
  const startOffset = 30 + Math.floor(Math.random() * 300);
  const checkIn = new Date(Date.now() + startOffset * 86_400_000);
  const checkOut = new Date(checkIn.getTime() + 3 * 86_400_000);
  const iso = (d) => d.toISOString().slice(0, 10);
  return { checkIn: iso(checkIn), checkOut: iso(checkOut) };
}

export function search() {
  const cities = ["Lagos", "Abuja", "Port Harcourt", "Ibadan"];
  const city = cities[Math.floor(Math.random() * cities.length)];
  const res = http.get(`${BASE_URL}/api/v1/search?destination=${city}`, { tags: { scenario: "search" } });
  check(res, { "search: 200": (r) => r.status === 200 });
  sleep(1);
}

export function propertyDetail() {
  // Deliberately hammers the SAME small set of ids repeatedly — this is what "popular
  // properties" caching is for; a uniformly random id per request would defeat the
  // point of measuring cache-hit latency.
  const id = properties[0];
  const res = http.get(`${BASE_URL}/api/v1/properties/${id}`, { tags: { scenario: "property_detail" } });
  check(res, { "property detail: 200": (r) => r.status === 200 });
  sleep(1);
}

export function quote() {
  const id = randomProperty();
  const { checkIn, checkOut } = randomDateRange();
  const res = http.post(
    `${BASE_URL}/api/v1/properties/${id}/quote`,
    JSON.stringify({ checkIn, checkOut, guests: 2 }),
    { headers: { "Content-Type": "application/json" }, tags: { scenario: "quote" } },
  );
  check(res, { "quote: 200": (r) => r.status === 200 });
  sleep(1);
}

export function setup() {
  // A small pool of real, pre-authenticated users — reused across the whole booking
  // scenario rather than registering fresh per-iteration, which would immediately hit
  // the /api/v1/auth rate limit (20/60s) added in prompt 24. Run this job with
  // HAVYN_RATE_LIMIT_ENABLED=false (see the CI job) so the booking scenario's own
  // sustained throughput doesn't trip the /api/v1/bookings limit (30/60s) either —
  // that limiter has its own dedicated test (RateLimitFilterIT), this load test isn't
  // trying to re-prove it.
  const tokens = [];
  for (let i = 0; i < 5; i++) {
    const email = `loadtest-guest-${Date.now()}-${i}@example.com`;
    const res = http.post(
      `${BASE_URL}/api/v1/auth/register`,
      JSON.stringify({ email, password: "a-strong-password-1", fullName: "Load Test Guest" }),
      { headers: { "Content-Type": "application/json" } },
    );
    if (res.status === 201) {
      tokens.push(res.json("accessToken"));
    }
  }
  return { tokens };
}

export function booking(data) {
  const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
  if (!token) return; // setup() couldn't register a user (e.g. rate-limited) — skip rather than fail noisily

  const id = randomProperty();
  const { checkIn, checkOut } = randomDateRange();
  const headers = { "Content-Type": "application/json", Authorization: `Bearer ${token}` };

  const quoteRes = http.post(
    `${BASE_URL}/api/v1/properties/${id}/quote`,
    JSON.stringify({ checkIn, checkOut, guests: 2 }),
    { headers, tags: { scenario: "booking" } },
  );
  if (quoteRes.status !== 200) {
    bookingErrors.add(1);
    return;
  }

  const bookRes = http.post(
    `${BASE_URL}/api/v1/bookings`,
    JSON.stringify({ propertyId: id, checkIn, checkOut, guests: 2, expectedTotal: quoteRes.json("grandTotal") }),
    { headers: { ...headers, "Idempotency-Key": `${__VU}-${__ITER}-${Date.now()}` }, tags: { scenario: "booking" } },
  );
  const ok = check(bookRes, { "booking: 200/201": (r) => r.status === 200 || r.status === 201 });
  // DATES_UNAVAILABLE from concurrent VUs landing on the same date is a real,
  // expected outcome under load (that's the double-booking guard working), not
  // counted as an error here — only genuine failures are.
  if (!ok && bookRes.status !== 409) {
    bookingErrors.add(1);
  } else {
    bookingErrors.add(0);
  }

  // Self-cleaning: cancel what we just held so repeated CI runs don't pile up
  // permanent PENDING holds against these fixture properties' availability.
  if (bookRes.status === 200 || bookRes.status === 201) {
    http.post(`${BASE_URL}/api/v1/bookings/${bookRes.json("id")}/cancel`, null, { headers });
  }

  sleep(1);
}
