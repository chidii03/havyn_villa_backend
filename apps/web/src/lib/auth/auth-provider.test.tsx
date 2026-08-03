import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "./auth-provider";

const { refresh, login, register, logout, getMe } = vi.hoisted(() => ({
  refresh: vi.fn(),
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  getMe: vi.fn(),
}));
vi.mock("@/lib/api/auth", () => ({ refresh, login, register, logout, getMe }));

const USER = { id: "u1", email: "ada@example.com", fullName: "Ada Lovelace", emailVerified: true, roles: ["CUSTOMER"], phone: null, avatarUrl: null };
const SESSION = { accessToken: "token-1", expiresIn: 900, user: USER };

function Consumer() {
  const { status, user, accessToken, login: doLogin, register: doRegister, logout: doLogout, refreshUser } = useAuth();
  return (
    <div>
      <p data-testid="status">{status}</p>
      <p data-testid="user">{user?.email ?? "none"}</p>
      <p data-testid="token">{accessToken ?? "none"}</p>
      <button onClick={() => void doLogin("ada@example.com", "password")}>Login</button>
      <button onClick={() => void doRegister("ada@example.com", "password", "Ada Lovelace")}>Register</button>
      <button onClick={() => void doLogout()}>Logout</button>
      <button onClick={() => void refreshUser()}>Refresh user</button>
    </div>
  );
}

describe("AuthProvider", () => {
  it("throws when useAuth is called outside a provider", () => {
    const ConsumerOutsideProvider = () => {
      useAuth();
      return null;
    };
    // Suppress React's expected error-boundary console noise for this one assertion.
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<ConsumerOutsideProvider />)).toThrow("useAuth must be used within an AuthProvider");
    spy.mockRestore();
  });

  it("silently authenticates on mount when a valid refresh cookie exists", async () => {
    refresh.mockResolvedValue(SESSION);
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    expect(screen.getByTestId("status")).toHaveTextContent("loading");
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    expect(screen.getByTestId("user")).toHaveTextContent("ada@example.com");
    expect(screen.getByTestId("token")).toHaveTextContent("token-1");
  });

  it("falls back to unauthenticated on mount when there's no valid session", async () => {
    refresh.mockRejectedValue(new Error("no session"));
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));
    expect(screen.getByTestId("user")).toHaveTextContent("none");
  });

  it("login sets the session state from the real API response", async () => {
    refresh.mockRejectedValue(new Error("no session"));
    login.mockResolvedValue(SESSION);
    const user = userEvent.setup();
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));

    await user.click(screen.getByRole("button", { name: "Login" }));

    expect(login).toHaveBeenCalledWith({ email: "ada@example.com", password: "password" });
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
  });

  it("register sets the session state from the real API response", async () => {
    refresh.mockRejectedValue(new Error("no session"));
    register.mockResolvedValue(SESSION);
    const user = userEvent.setup();
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));

    await user.click(screen.getByRole("button", { name: "Register" }));

    expect(register).toHaveBeenCalledWith({ email: "ada@example.com", password: "password", fullName: "Ada Lovelace" });
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
  });

  it("logout clears local session state even if the API call fails (finally block)", async () => {
    refresh.mockResolvedValue(SESSION);
    logout.mockRejectedValue(new Error("network error"));
    const user = userEvent.setup();
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));

    await user.click(screen.getByRole("button", { name: "Logout" }));

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));
    expect(screen.getByTestId("token")).toHaveTextContent("none");
  });

  it("refreshUser re-fetches /me and updates the user (e.g. after verifying email)", async () => {
    refresh.mockResolvedValue(SESSION);
    getMe.mockResolvedValue({ ...USER, emailVerified: true, fullName: "Ada, Countess of Lovelace" });
    const user = userEvent.setup();
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));

    await user.click(screen.getByRole("button", { name: "Refresh user" }));

    expect(getMe).toHaveBeenCalledWith("token-1");
  });
});
