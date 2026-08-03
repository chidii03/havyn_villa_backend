import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import SignupPage from "./page";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/lib/auth/auth-provider", () => ({ useAuth: () => ({ register: vi.fn() }) }));

describe("SignupPage", () => {
  it("renders the signup form with a link to log in", () => {
    render(<SignupPage />);

    expect(screen.getByRole("heading", { name: "Sign up" })).toBeInTheDocument();
    expect(screen.getByLabelText("Full name")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Log in" })).toHaveAttribute("href", "/login");
  });
});
