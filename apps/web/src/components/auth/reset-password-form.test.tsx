import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "jest-axe";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/http";
import { ResetPasswordForm } from "./reset-password-form";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const { confirmPasswordReset } = vi.hoisted(() => ({ confirmPasswordReset: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ confirmPasswordReset }));

const { toastSuccess } = vi.hoisted(() => ({ toastSuccess: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: toastSuccess } }));

describe("ResetPasswordForm", () => {
  it("confirms the reset with the token from the URL, not a form field, and redirects to /login", async () => {
    confirmPasswordReset.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<ResetPasswordForm token="reset-token-abc" />);

    await user.type(screen.getByLabelText("New password"), "a-new-strong-password");
    await user.click(screen.getByRole("button", { name: "Update password" }));

    expect(confirmPasswordReset).toHaveBeenCalledWith({ token: "reset-token-abc", newPassword: "a-new-strong-password" });
    expect(toastSuccess).toHaveBeenCalledWith("Password updated — please log in again.");
    expect(push).toHaveBeenCalledWith("/login");
  });

  it("rejects a password under 8 characters without calling the API", async () => {
    const user = userEvent.setup();
    render(<ResetPasswordForm token="reset-token-abc" />);

    await user.type(screen.getByLabelText("New password"), "short");
    await user.click(screen.getByRole("button", { name: "Update password" }));

    expect(await screen.findByText("Use at least 8 characters")).toBeInTheDocument();
    expect(confirmPasswordReset).not.toHaveBeenCalled();
  });

  it("surfaces a real server error (e.g. an expired token) without redirecting", async () => {
    confirmPasswordReset.mockRejectedValue(new ApiError(400, "INVALID_TOKEN", "This reset link has expired"));
    const user = userEvent.setup();
    render(<ResetPasswordForm token="expired-token" />);

    await user.type(screen.getByLabelText("New password"), "a-new-strong-password");
    await user.click(screen.getByRole("button", { name: "Update password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("This reset link has expired");
    expect(push).not.toHaveBeenCalled();
  });

  it("has no detectable accessibility violations", async () => {
    const { container } = render(<ResetPasswordForm token="reset-token-abc" />);

    expect((await axe(container)).violations).toHaveLength(0);
  });
});
