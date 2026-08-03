import { render, screen } from "@testing-library/react-native";
import type { PropertySummary } from "@havyn/shared";
import { PropertyCard } from "./property-card";

jest.mock("expo-router", () => ({
  Link: ({ children }: { children: React.ReactNode }) => children,
}));

const property: PropertySummary = {
  id: "p1",
  title: "Sunset Villa",
  city: "Lagos",
  state: "Lagos",
  country: "Nigeria",
  lat: null,
  lng: null,
  propertyType: "VILLA",
  currency: "NGN",
  basePrice: 45000,
  capacity: 4,
  bedrooms: 2,
  ratingAvg: 4.5,
  ratingCount: 3,
  status: "ACTIVE",
};

describe("PropertyCard", () => {
  it("renders the title, location, price, and rating", async () => {
    await render(<PropertyCard property={property} />);

    expect(screen.getByText("Sunset Villa")).toBeTruthy();
    expect(screen.getByText("Lagos, Lagos")).toBeTruthy();
    expect(screen.getByText("₦45,000")).toBeTruthy();
    expect(screen.getByText("4.50")).toBeTruthy();
  });

  it("omits the rating when the listing has no reviews yet", async () => {
    await render(<PropertyCard property={{ ...property, ratingCount: 0, ratingAvg: 0 }} />);

    expect(screen.queryByText("0.00")).toBeNull();
  });
});
