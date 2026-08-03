import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { RequireAuth } from "./require-auth";

const { useAuth, replace } = vi.hoisted(() => ({ useAuth: vi.fn(), replace: vi.fn() }));
vi.mock("@/lib/auth/auth-provider", () => ({ useAuth }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

describe("RequireAuth", () => {
  it("shows a loading skeleton (not children) while auth status is still resolving", () => {
    useAuth.mockReturnValue({ status: "loading", user: null });
    render(
      <RequireAuth>
        <p>Protected content</p>
      </RequireAuth>,
    );

    expect(screen.queryByText("Protected content")).not.toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it("renders children once authenticated", () => {
    useAuth.mockReturnValue({ status: "authenticated", user: { roles: ["CUSTOMER"] } });
    render(
      <RequireAuth>
        <p>Protected content</p>
      </RequireAuth>,
    );

    expect(screen.getByText("Protected content")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it("redirects to /login (by default) when unauthenticated", () => {
    useAuth.mockReturnValue({ status: "unauthenticated", user: null });
    render(
      <RequireAuth>
        <p>Protected content</p>
      </RequireAuth>,
    );

    expect(screen.queryByText("Protected content")).not.toBeInTheDocument();
    expect(replace).toHaveBeenCalledWith("/login");
  });

  it("redirects to the given roleRedirectTo when authenticated but missing the required role", () => {
    useAuth.mockReturnValue({ status: "authenticated", user: { roles: ["CUSTOMER"] } });
    render(
      <RequireAuth role="ADMIN" roleRedirectTo="/">
        <p>Admin only</p>
      </RequireAuth>,
    );

    expect(screen.queryByText("Admin only")).not.toBeInTheDocument();
    expect(replace).toHaveBeenCalledWith("/");
  });

  it("renders children when authenticated and holding the required role", () => {
    useAuth.mockReturnValue({ status: "authenticated", user: { roles: ["CUSTOMER", "ADMIN"] } });
    render(
      <RequireAuth role="ADMIN">
        <p>Admin only</p>
      </RequireAuth>,
    );

    expect(screen.getByText("Admin only")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
