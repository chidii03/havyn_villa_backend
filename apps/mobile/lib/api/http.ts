import { apiErrorResponseSchema } from "@havyn/shared";

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details?: { field: string; message: string }[];
  readonly traceId?: string;

  constructor(status: number, code: string, message: string, details?: { field: string; message: string }[], traceId?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
    this.traceId = traceId;
  }
}

interface ApiFetchOptions extends Omit<RequestInit, "body"> {
  accessToken?: string;
  body?: unknown;
}

/**
 * Thin fetch wrapper — mirrors apps/web/src/lib/api/http.ts's contract (JSON in/out,
 * the same `{ error: { code, message, details, traceId } }` envelope), deliberately
 * without that file's `credentials`/`cookie` handling: mobile has no browser cookie
 * jar, and project-docs/prompts/30-expo-mobile-app.md's own constraint is "Auth works
 * without browser cookies (bearer refresh + secure storage)" — the refresh token
 * travels as a request body field instead (see lib/api/auth.ts#refresh), which
 * AuthController already accepts as a fallback to the cookie (`RefreshRequest`), so
 * no backend change was needed for this.
 */
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { accessToken, body, headers, ...rest } = options;

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: {
      "Content-Type": "application/json",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  // A failure response isn't guaranteed to be our JSON envelope — a reverse proxy
  // returning an HTML error page for a 502/504 is common and must not crash this
  // with a raw SyntaxError.
  let data: unknown;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    data = undefined;
  }

  if (!response.ok) {
    const parsed = apiErrorResponseSchema.safeParse(data);
    if (parsed.success) {
      const { code, message, details, traceId } = parsed.data.error;
      throw new ApiError(response.status, code, message, details, traceId);
    }
    throw new ApiError(response.status, "UNKNOWN_ERROR", `Request failed with status ${response.status}`);
  }

  return data as T;
}
