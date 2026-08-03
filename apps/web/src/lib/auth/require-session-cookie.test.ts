import { describe, expect, it, vi } from "vitest";
import { requireSessionCookie } from "./require-session-cookie";

const { cookies } = vi.hoisted(() => ({ cookies: vi.fn() }));
vi.mock("next/headers", () => ({ cookies }));

const { redirect } = vi.hoisted(() => ({
  redirect: vi.fn((to: string) => {
    throw new Error(`NEXT_REDIRECT:${to}`);
  }),
}));
vi.mock("next/navigation", () => ({ redirect }));

describe("requireSessionCookie", () => {
  it("does nothing when the refresh cookie is present (the common signed-in case)", async () => {
    cookies.mockResolvedValue({ has: (name: string) => name === "havyn_refresh" });

    await expect(requireSessionCookie()).resolves.toBeUndefined();
    expect(redirect).not.toHaveBeenCalled();
  });

  it("redirects to /login by default when there's no refresh cookie at all", async () => {
    cookies.mockResolvedValue({ has: () => false });

    await expect(requireSessionCookie()).rejects.toThrow("NEXT_REDIRECT:/login");
  });

  it("redirects to a custom destination when given one", async () => {
    cookies.mockResolvedValue({ has: () => false });

    await expect(requireSessionCookie("/login?redirect=/host")).rejects.toThrow("NEXT_REDIRECT:/login?redirect=/host");
  });
});
