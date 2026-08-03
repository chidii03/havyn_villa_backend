import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { axe } from "jest-axe";
import { describe, expect, it, vi } from "vitest";
import type { AdminAnalyticsSummary } from "@havyn/shared";
import AdminDashboardPage from "./page";

const { useAuth } = vi.hoisted(() => ({ useAuth: vi.fn(() => ({ accessToken: "token-123" })) }));
vi.mock("@/lib/auth/auth-provider", () => ({ useAuth }));

const { getAnalyticsSummary } = vi.hoisted(() => ({ getAnalyticsSummary: vi.fn() }));
vi.mock("@/lib/api/admin", () => ({ getAnalyticsSummary }));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdminDashboardPage />
    </QueryClientProvider>,
  );
}

const summary: AdminAnalyticsSummary = {
  totalUsers: 42,
  totalHosts: 7,
  totalProperties: 15,
  activeProperties: 10,
  totalBookings: 30,
  confirmedOrCompletedBookings: 20,
  grossRevenue: 500000,
  commissionCollected: 60000,
  pendingVerificationRequests: 2,
  openDisputes: 1,
};

describe("AdminDashboardPage", () => {
  it("shows real platform-wide figures from the backend", async () => {
    getAnalyticsSummary.mockResolvedValue(summary);

    renderPage();

    expect(await screen.findByText("42")).toBeInTheDocument();
    expect(screen.getByText("7 hosts")).toBeInTheDocument();
    expect(screen.getByText("15")).toBeInTheDocument();
    expect(screen.getByText("10 active")).toBeInTheDocument();
    expect(screen.getByText("₦500,000")).toBeInTheDocument();
    expect(screen.getByText("₦60,000")).toBeInTheDocument();
    expect(getAnalyticsSummary).toHaveBeenCalledWith("token-123");
  });

  it("shows an error state with a working retry", async () => {
    getAnalyticsSummary.mockRejectedValueOnce(new Error("network error")).mockResolvedValueOnce(summary);

    renderPage();

    await screen.findByText("Couldn't load platform analytics");
    const user = (await import("@testing-library/user-event")).default.setup();
    await user.click(screen.getByRole("button", { name: "Try again" }));

    await waitFor(() => expect(screen.getByText("42")).toBeInTheDocument());
  });

  it("has no detectable accessibility violations", async () => {
    getAnalyticsSummary.mockResolvedValue(summary);

    const { container } = renderPage();
    await screen.findByText("₦500,000");

    const results = await axe(container);
    expect(results.violations).toHaveLength(0);
  });
});
