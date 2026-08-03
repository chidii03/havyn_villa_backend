import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "jest-axe";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/http";
import { LoginForm } from "./login-form";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const { login } = vi.hoisted(() => ({ login: vi.fn() }));
vi.mock("@/lib/auth/auth-provider", () => ({ useAuth: () => ({ login }) }));

const { toastSuccess } = vi.hoisted(() => ({ toastSuccess: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: toastSuccess } }));

describe("LoginForm", () => {
  it("rejects an empty submission with field-level errors, without calling the API", async () => {
    const user = userEvent.setup();
    render(<LoginForm />);

    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(await screen.findByText("Email is required")).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it("logs in and redirects to /account on success", async () => {
    login.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<LoginForm />);

    await user.type(screen.getByLabelText("Email"), "ada@example.com");
    await user.type(screen.getByLabelText("Password"), "correct-password");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(login).toHaveBeenCalledWith("ada@example.com", "correct-password");
    expect(await screen.findByRole("button")).toBeEnabled();
    expect(toastSuccess).toHaveBeenCalledWith("Welcome back!");
    expect(push).toHaveBeenCalledWith("/account");
  });

  it("surfaces the backend's deliberately generic INVALID_CREDENTIALS message, without redirecting", async () => {
    // security/01-security-plan.md: the same message for "no such user" and "wrong
    // password" — the form just displays it as-is, it doesn't reinterpret it.
    login.mockRejectedValue(new ApiError(401, "INVALID_CREDENTIALS", "Invalid email or password"));
    const user = userEvent.setup();
    render(<LoginForm />);

    await user.type(screen.getByLabelText("Email"), "ada@example.com");
    await user.type(screen.getByLabelText("Password"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password");
    expect(push).not.toHaveBeenCalled();
  });

  it("has a working Forgot password link", () => {
    render(<LoginForm />);

    expect(screen.getByRole("link", { name: "Forgot password?" })).toHaveAttribute("href", "/forgot-password");
  });

  it("has no detectable accessibility violations", async () => {
    const { container } = render(<LoginForm />);

    expect((await axe(container)).violations).toHaveLength(0);
  });
});
