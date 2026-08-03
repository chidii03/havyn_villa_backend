import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/http";
import VerifyEmailPage from "./page";

const { verifyEmail } = vi.hoisted(() => ({ verifyEmail: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ verifyEmail }));

describe("VerifyEmailPage", () => {
  it("shows a missing-token message when the link has no token", async () => {
    render(await VerifyEmailPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByRole("alert")).toHaveTextContent("This verification link is missing its token.");
    expect(verifyEmail).not.toHaveBeenCalled();
  });

  it("verifies and shows success when the token is valid", async () => {
    verifyEmail.mockResolvedValue(undefined);

    render(await VerifyEmailPage({ searchParams: Promise.resolve({ token: "good-token" }) }));

    expect(verifyEmail).toHaveBeenCalledWith("good-token");
    expect(screen.getByText("Your email is verified.")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows the real server error message when the token is invalid/expired", async () => {
    verifyEmail.mockRejectedValue(new ApiError(400, "INVALID_TOKEN", "This link has expired"));

    render(await VerifyEmailPage({ searchParams: Promise.resolve({ token: "bad-token" }) }));

    expect(screen.getByRole("alert")).toHaveTextContent("This link has expired");
  });

  it("falls back to a generic message for a non-ApiError failure", async () => {
    verifyEmail.mockRejectedValue(new Error("network down"));

    render(await VerifyEmailPage({ searchParams: Promise.resolve({ token: "bad-token" }) }));

    expect(screen.getByRole("alert")).toHaveTextContent("This verification link is invalid or has expired.");
  });
});
