import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { axe } from "jest-axe";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/http";
import RoomDetailPage from "./page";

const { getProperty } = vi.hoisted(() => ({ getProperty: vi.fn() }));
vi.mock("@/lib/api/properties", () => ({ getProperty }));

const { notFound, useRouter } = vi.hoisted(() => ({
  notFound: vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
  useRouter: vi.fn(() => ({ push: vi.fn() })),
}));
vi.mock("next/navigation", () => ({ notFound, useRouter }));

const { useAuth } = vi.hoisted(() => ({ useAuth: vi.fn(() => ({ status: "unauthenticated", accessToken: null })) }));
vi.mock("@/lib/auth/auth-provider", () => ({ useAuth }));

// BookingWidget (booking-widget.test.tsx covers its own behavior in depth) uses
// TanStack Query — every render here needs a provider in the tree.
function renderPage(jsx: React.ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{jsx}</QueryClientProvider>);
}

const PROPERTY = {
  id: "prop-1",
  hostId: "host-1",
  type: { code: "VILLA", name: "Villa" },
  title: "Sunset Villa",
  description: "A lovely villa by the water.",
  address: "1 Beach Rd",
  city: "Lagos",
  state: "Lagos",
  country: "Nigeria",
  lat: 6.5,
  lng: 3.3,
  currency: "NGN",
  basePrice: 50000,
  capacity: 4,
  bedrooms: 2,
  beds: 2,
  bathrooms: 2,
  cleaningFee: 5000,
  serviceFeePct: 10,
  houseRules: "No smoking.",
  cancellationPolicy: "FLEXIBLE",
  status: "ACTIVE",
  ratingAvg: 0,
  ratingCount: 0,
  amenities: [{ code: "WIFI", name: "Wifi", category: "internet_and_office" }],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  photoUrls: [],
};

describe("RoomDetailPage", () => {
  it("renders the listing's real details", async () => {
    getProperty.mockResolvedValue(PROPERTY);

    renderPage(await RoomDetailPage({ params: Promise.resolve({ id: "prop-1" }) }));

    expect(screen.getByRole("heading", { level: 1, name: "Sunset Villa" })).toBeInTheDocument();
    expect(screen.getByText("Lagos, Lagos, Nigeria")).toBeInTheDocument();
    expect(screen.getByText("A lovely villa by the water.")).toBeInTheDocument();
    expect(screen.getByText("Wifi")).toBeInTheDocument();
    expect(screen.getByText("No reviews yet")).toBeInTheDocument();
    // The real BookingWidget (prompt 21) — see booking-widget.test.tsx for its behavior.
    expect(screen.getByText("Add your dates to see the total price.")).toBeInTheDocument();
  });

  it("calls notFound() (not a generic error page) when the property is 404", async () => {
    getProperty.mockRejectedValue(new ApiError(404, "NOT_FOUND", "Property x not found"));

    await expect(RoomDetailPage({ params: Promise.resolve({ id: "missing" }) })).rejects.toThrow();

    expect(notFound).toHaveBeenCalledTimes(1);
  });

  it("re-throws non-404 errors instead of swallowing them", async () => {
    getProperty.mockRejectedValue(new ApiError(500, "INTERNAL_ERROR", "boom"));

    await expect(RoomDetailPage({ params: Promise.resolve({ id: "prop-1" }) })).rejects.toThrow("boom");

    expect(notFound).not.toHaveBeenCalled();
  });

  it("has no detectable accessibility violations", async () => {
    getProperty.mockResolvedValue(PROPERTY);

    const { container } = renderPage(await RoomDetailPage({ params: Promise.resolve({ id: "prop-1" }) }));

    const results = await axe(container);

    expect(results.violations).toHaveLength(0);
  });
});
