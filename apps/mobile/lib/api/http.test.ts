import { ApiError, apiFetch } from "./http";

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("apiFetch", () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it("returns the parsed JSON body on success", async () => {
    jest.spyOn(global, "fetch").mockResolvedValue(jsonResponse(200, { id: "123", name: "Havyn Villa" }));

    const result = await apiFetch<{ id: string; name: string }>("/api/v1/ping");

    expect(result).toEqual({ id: "123", name: "Havyn Villa" });
  });

  it("returns undefined for a 204 No Content response", async () => {
    jest.spyOn(global, "fetch").mockResolvedValue(new Response(null, { status: 204 }));

    const result = await apiFetch<void>("/api/v1/auth/logout", { method: "POST" });

    expect(result).toBeUndefined();
  });

  it("parses the backend's { error: {...} } envelope into a typed ApiError", async () => {
    jest.spyOn(global, "fetch").mockResolvedValue(
      jsonResponse(401, { error: { code: "INVALID_CREDENTIALS", message: "Invalid email or password", traceId: "trace-1" } }),
    );

    await expect(apiFetch("/api/v1/auth/login", { method: "POST", body: {} })).rejects.toMatchObject({
      name: "ApiError",
      status: 401,
      code: "INVALID_CREDENTIALS",
      message: "Invalid email or password",
      traceId: "trace-1",
    });
  });

  it("falls back to a generic ApiError when the failure body doesn't match the envelope", async () => {
    jest.spyOn(global, "fetch").mockResolvedValue(new Response("<html>502</html>", { status: 502 }));

    const error = await apiFetch("/api/v1/anything").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(502);
    expect((error as ApiError).code).toBe("UNKNOWN_ERROR");
  });

  it("sends the access token as a Bearer header and serializes the body as JSON — no cookie handling, unlike web", async () => {
    const fetchMock = jest.spyOn(global, "fetch").mockResolvedValue(jsonResponse(200, { ok: true }));

    await apiFetch("/api/v1/me", { method: "PATCH", accessToken: "token-abc", body: { fullName: "New Name" } });

    const [, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(options.headers).toMatchObject({ Authorization: "Bearer token-abc" });
    expect(options.body).toBe(JSON.stringify({ fullName: "New Name" }));
    expect(options.credentials).toBeUndefined();
  });
});
