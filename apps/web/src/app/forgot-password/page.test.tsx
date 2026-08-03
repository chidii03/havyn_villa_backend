import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ForgotPasswordPage from "./page";

vi.mock("@/lib/api/auth", () => ({ requestPasswordReset: vi.fn() }));

describe("ForgotPasswordPage", () => {
  it("renders the forgot-password form with a link back to log in", () => {
    render(<ForgotPasswordPage />);

    expect(screen.getByRole("heading", { name: "Reset your password" })).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to log in" })).toHaveAttribute("href", "/login");
  });
});
