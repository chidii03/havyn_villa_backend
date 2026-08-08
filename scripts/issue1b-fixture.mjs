import http from "node:http";
import { Buffer } from "node:buffer";
import { Client } from "pg";

const stamp = Date.now();
const password = "correct-horse-battery-staple";

function post(path, body) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const req = http.request(
      {
        hostname: "localhost",
        port: 8080,
        path,
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(data),
        },
      },
      (res) => {
        let chunks = "";
        res.on("data", (chunk) => {
          chunks += chunk;
        });
        res.on("end", () => {
          const parsed = chunks ? JSON.parse(chunks) : null;
          if (res.statusCode >= 400) {
            reject(Object.assign(new Error(`HTTP ${res.statusCode}`), { status: res.statusCode, body: parsed }));
            return;
          }
          resolve({ status: res.statusCode, body: parsed, headers: res.headers });
        });
      },
    );
    req.on("error", reject);
    req.write(data);
    req.end();
  });
}

const emails = {
  customer: `issue1b-customer-${stamp}@example.com`,
  host: `issue1b-host-${stamp}@example.com`,
  admin: `issue1b-admin-${stamp}@example.com`,
};

for (const [kind, email] of Object.entries(emails)) {
  await post("/api/v1/auth/register", {
    email,
    password,
    fullName: `Issue 1B ${kind}`,
  });
}

const client = new Client({
  host: process.env.POSTGRES_HOST ?? "localhost",
  port: Number(process.env.POSTGRES_PORT ?? 5432),
  database: process.env.POSTGRES_DB ?? "havyn_villa",
  user: process.env.POSTGRES_USER ?? "postgres",
  password: process.env.POSTGRES_PASSWORD ?? "chidi03",
});

await client.connect();
await client.query("update app_user set status='ACTIVE', email_verified_at=now() where email = any($1::text[])", [
  Object.values(emails),
]);
await client.query(
  `insert into user_role (user_id, role_id)
   select u.id, r.id
   from app_user u
   cross join role r
   where (u.email = $1 and r.code = 'HOST')
      or (u.email = $2 and r.code = 'ADMIN')
   on conflict do nothing`,
  [emails.host, emails.admin],
);
await client.end();

const sessions = {};
for (const [kind, email] of Object.entries(emails)) {
  sessions[kind] = (await post("/api/v1/auth/login", { email, password })).body;
}

const claims = Object.fromEntries(
  Object.entries(sessions).map(([kind, session]) => [
    kind,
    JSON.parse(Buffer.from(session.accessToken.split(".")[1], "base64url").toString("utf8")),
  ]),
);

console.log(
  JSON.stringify(
    {
      password,
      emails,
      roles: Object.fromEntries(Object.entries(sessions).map(([kind, session]) => [kind, session.user.roles])),
      claims,
    },
    null,
    2,
  ),
);
