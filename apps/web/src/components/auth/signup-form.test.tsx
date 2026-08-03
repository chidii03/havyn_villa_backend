import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "jest-axe";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/http";
import { SignupForm } from "./signup-form";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const { register } = vi.hoisted(() => ({ register: vi.fn() }));
vi.mock("@/lib/auth/auth-provider", () => ({ useAuth: () => ({ register }) }));

const { toastSuccess } = vi.hoisted(() => ({ toastSuccess: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: toastSuccess } }));

describe("SignupForm", () => {
  it("rejects a password under 8 characters, without calling the API", async () => {
    const user = userEvent.setup();
    render(<SignupForm />);

    await user.type(screen.getByLabelText("Full name"), "Ada Lovelace");
    await user.type(screen.getByLabelText("Email"), "ada@example.com");
    await user.type(screen.getByLabelText("Password"), "short");
    await user.click(screen.getByRole("button", { name: "Sign up" }));

    expect(await screen.findByText("Use at least 8 characters")).toBeInTheDocument();
    expect(register).not.toHaveBeenCalled();
  });

  it("registers and redirects to /account on success", async () => {
    register.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<SignupForm />);

    await user.type(screen.getByLabelText("Full name"), "Ada Lovelace");
    await user.type(screen.getByLabelText("Email"), "ada@example.com");
    await user.type(screen.getByLabelText("Password"), "a-strong-password");
    await user.click(screen.getByRole("button", { name: "Sign up" }));

    expect(register).toHaveBeenCalledWith("ada@example.com", "a-strong-password", "Ada Lovelace");
    expect(toastSuccess).toHaveBeenCalledWith(expect.stringContaining("Check your email to verify"));
    expect(push).toHaveBeenCalledWith("/account");
  });

  it("surfaces a real server error (e.g. duplicate email) without redirecting", async () => {
    register.mockRejectedValue(new ApiError(409, "EMAIL_ALREADY_REGISTERED", "That email is already registered"));
    const user = userEvent.setup();
    render(<SignupForm />);

    await user.type(screen.getByLabelText("Full name"), "Ada Lovelace");
    await user.type(screen.getByLabelText("Email"), "ada@example.com");
    await user.type(screen.getByLabelText("Password"), "a-strong-password");
    await user.click(screen.getByRole("button", { name: "Sign up" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("That email is already registered");
    expect(push).not.toHaveBeenCalled();
  });

  it("has no detectable accessibility violations", async () => {
    const { container } = render(<SignupForm />);

    expect((await axe(container)).violations).toHaveLength(0);
  });
});
