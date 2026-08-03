import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "jest-axe";
import { describe, expect, it, vi } from "vitest";
import { ForgotPasswordForm } from "./forgot-password-form";

const { requestPasswordReset } = vi.hoisted(() => ({ requestPasswordReset: vi.fn() }));
vi.mock("@/lib/api/auth", () => ({ requestPasswordReset }));

describe("ForgotPasswordForm", () => {
  it("shows the same confirmation regardless of whether the account exists (no enumeration)", async () => {
    requestPasswordReset.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<ForgotPasswordForm />);

    await user.type(screen.getByLabelText("Email"), "ada@example.com");
    await user.click(screen.getByRole("button", { name: "Send reset link" }));

    expect(requestPasswordReset).toHaveBeenCalledWith({ email: "ada@example.com" });
    expect(await screen.findByRole("status")).toHaveTextContent(
      "If an account exists for that email, we've sent a link to reset your password.",
    );
  });

  it("rejects an invalid email without calling the API", async () => {
    const user = userEvent.setup();
    render(<ForgotPasswordForm />);

    await user.type(screen.getByLabelText("Email"), "not-an-email");
    await user.click(screen.getByRole("button", { name: "Send reset link" }));

    expect(await screen.findByText("Enter a valid email address")).toBeInTheDocument();
    expect(requestPasswordReset).not.toHaveBeenCalled();
  });

  it("has no detectable accessibility violations", async () => {
    const { container } = render(<ForgotPasswordForm />);

    expect((await axe(container)).violations).toHaveLength(0);
  });
});
