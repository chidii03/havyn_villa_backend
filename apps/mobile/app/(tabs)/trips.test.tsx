import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import type { BookingDetail } from "@havyn/shared";
import { cancelBooking, listBookings } from "@/lib/api/bookings";
import { useAuth } from "@/lib/auth/auth-provider";
import TripsScreen from "./trips";

jest.mock("expo-router", () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock("@/lib/auth/auth-provider", () => ({ useAuth: jest.fn() }));
jest.mock("@/lib/api/bookings", () => ({ listBookings: jest.fn(), cancelBooking: jest.fn() }));

async function renderScreen() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return await render(
    <QueryClientProvider client={queryClient}>
      <TripsScreen />
    </QueryClientProvider>,
  );
}

const booking: BookingDetail = {
  id: "b1",
  property: { id: "p1", title: "Sunset Villa", city: "Lagos", state: "Lagos", country: "Nigeria" },
  checkIn: "2026-09-01",
  checkOut: "2026-09-04",
  nights: 3,
  guests: 2,
  baseTotal: 30000,
  cleaningFee: 2000,
  serviceFee: 3000,
  discountTotal: 0,
  taxTotal: 0,
  grandTotal: 35000,
  currency: "NGN",
  status: "PENDING",
  holdExpiresAt: null,
  createdAt: "2026-07-01T00:00:00Z",
};

describe("TripsScreen", () => {
  it("prompts a logged-out visitor to log in instead of calling the API", async () => {
    (useAuth as jest.Mock).mockReturnValue({ status: "unauthenticated", accessToken: null });

    await renderScreen();

    expect(screen.getByText("Log in to see your trips")).toBeTruthy();
    expect(listBookings).not.toHaveBeenCalled();
  });

  it("shows real bookings from the API with an honest status label", async () => {
    (useAuth as jest.Mock).mockReturnValue({ status: "authenticated", accessToken: "token-123" });
    (listBookings as jest.Mock).mockResolvedValue({ data: [booking], page: 0, size: 20, total: 1, nextCursor: null });

    await renderScreen();

    expect(await screen.findByText("Sunset Villa")).toBeTruthy();
    expect(screen.getByText("Awaiting payment")).toBeTruthy();
    expect(screen.getByText("₦35,000")).toBeTruthy();
    expect(listBookings).toHaveBeenCalledWith("token-123");
  });

  it("cancels a booking and refetches the list", async () => {
    (useAuth as jest.Mock).mockReturnValue({ status: "authenticated", accessToken: "token-123" });
    (listBookings as jest.Mock).mockResolvedValue({ data: [booking], page: 0, size: 20, total: 1, nextCursor: null });
    (cancelBooking as jest.Mock).mockResolvedValue({ booking: { ...booking, status: "CANCELLED" }, refundPercentage: 0, refundAmount: 0 });

    await renderScreen();
    await screen.findByText("Sunset Villa");

    fireEvent.press(screen.getByText("Cancel"));

    await waitFor(() => expect(cancelBooking).toHaveBeenCalledWith("token-123", "b1"));
  });
});
