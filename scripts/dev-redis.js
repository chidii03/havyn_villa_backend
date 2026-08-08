const net = require("node:net");

const host = process.env.DEV_REDIS_HOST || "127.0.0.1";
const port = Number(process.env.DEV_REDIS_PORT || 6379);
const values = new Map();
const sets = new Map();
const expiries = new Map();

function now() {
  return Date.now();
}

function expireAt(key, ttlMs) {
  if (ttlMs > 0) {
    expiries.set(key, now() + ttlMs);
  } else {
    expiries.delete(key);
  }
}

function isExpired(key) {
  const deadline = expiries.get(key);
  if (deadline === undefined || deadline > now()) {
    return false;
  }
  values.delete(key);
  sets.delete(key);
  expiries.delete(key);
  return true;
}

function getValue(key) {
  return isExpired(key) ? undefined : values.get(key);
}

function getSet(key) {
  if (isExpired(key)) {
    return undefined;
  }
  return sets.get(key);
}

function simple(value) {
  return `+${value}\r\n`;
}

function integer(value) {
  return `:${value}\r\n`;
}

function bulk(value) {
  if (value === undefined || value === null) {
    return "$-1\r\n";
  }
  const text = String(value);
  return `$${Buffer.byteLength(text)}\r\n${text}\r\n`;
}

function array(values) {
  return `*${values.length}\r\n${values.map(bulk).join("")}`;
}

function error(message) {
  return `-ERR ${message}\r\n`;
}

function parseCommand(buffer) {
  const text = buffer.toString("utf8");
  if (!text.startsWith("*")) {
    const line = text.trim();
    return line ? line.split(/\s+/) : null;
  }

  let offset = 1;
  const endCount = text.indexOf("\r\n", offset);
  const count = Number(text.slice(offset, endCount));
  offset = endCount + 2;
  const parts = [];

  for (let i = 0; i < count; i += 1) {
    if (text[offset] !== "$") {
      return null;
    }
    offset += 1;
    const endLength = text.indexOf("\r\n", offset);
    const length = Number(text.slice(offset, endLength));
    offset = endLength + 2;
    parts.push(text.slice(offset, offset + length));
    offset += length + 2;
  }
  return parts;
}

function handle(parts) {
  if (!parts || parts.length === 0) {
    return error("empty command");
  }

  const command = parts[0].toUpperCase();
  const key = parts[1];

  switch (command) {
    case "PING":
      return parts[1] ? bulk(parts[1]) : simple("PONG");
    case "HELLO":
      return array(["server", "redis", "version", "7.0.0", "proto", "2", "mode", "standalone", "role", "master"]);
    case "CLIENT":
    case "SELECT":
      return simple("OK");
    case "INFO":
      return bulk("redis_version:7.0.0\r\nrole:master\r\n");
    case "SET": {
      values.set(key, parts[2] ?? "");
      sets.delete(key);
      expiries.delete(key);
      for (let i = 3; i < parts.length - 1; i += 1) {
        const option = parts[i].toUpperCase();
        const amount = Number(parts[i + 1]);
        if (option === "EX") expireAt(key, amount * 1000);
        if (option === "PX") expireAt(key, amount);
      }
      return simple("OK");
    }
    case "GET":
      return bulk(getValue(key));
    case "GETDEL": {
      const value = getValue(key);
      values.delete(key);
      expiries.delete(key);
      return bulk(value);
    }
    case "DEL": {
      let deleted = 0;
      for (const item of parts.slice(1)) {
        if (!isExpired(item) && (values.delete(item) || sets.delete(item))) {
          deleted += 1;
        }
        expiries.delete(item);
      }
      return integer(deleted);
    }
    case "INCR": {
      const next = Number(getValue(key) || "0") + 1;
      values.set(key, String(next));
      return integer(next);
    }
    case "EXPIRE":
      if (getValue(key) === undefined && getSet(key) === undefined) {
        return integer(0);
      }
      expireAt(key, Number(parts[2]) * 1000);
      return integer(1);
    case "SADD": {
      const existing = getSet(key) || new Set();
      let added = 0;
      for (const member of parts.slice(2)) {
        if (!existing.has(member)) {
          added += 1;
          existing.add(member);
        }
      }
      sets.set(key, existing);
      values.delete(key);
      return integer(added);
    }
    case "SMEMBERS": {
      const existing = getSet(key);
      return array(existing ? Array.from(existing) : []);
    }
    case "COMMAND":
      return array([]);
    case "QUIT":
      return simple("OK");
    default:
      return error(`unsupported command ${command}`);
  }
}

const server = net.createServer((socket) => {
  socket.on("data", (buffer) => {
    try {
      socket.write(handle(parseCommand(buffer)));
    } catch (err) {
      socket.write(error(err.message));
    }
  });
});

setInterval(() => {
  for (const key of expiries.keys()) {
    isExpired(key);
  }
}, 1000).unref();

server.listen(port, host, () => {
  console.log(`dev-redis listening on ${host}:${port}`);
});
