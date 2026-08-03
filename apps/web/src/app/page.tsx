import { EmptyState } from "@/components/patterns/empty-state";
import { CarouselRow } from "@/components/property/carousel-row";
import { CategoryChips } from "@/components/property/category-chips";
import { FiltersSheet } from "@/components/property/filters-sheet";
import { listAmenities, listPropertyTypes } from "@/lib/api/properties";
import { search } from "@/lib/api/search";
import type { SearchResultItem } from "@havyn/shared";

const ROW_SIZE = 12;
const FEATURED_TITLE = "New on Havyn Villa";

export default async function Home({ searchParams }: { searchParams: Promise<{ type?: string }> }) {
  const { type } = await searchParams;
  const [types, amenities, results] = await Promise.all([
    listPropertyTypes(),
    listAmenities(),
    search({ type, sort: "newest", size: 96 }),
  ]);

  // Homepage carousels are image-led. A listing without a usable remote image is
  // still searchable, but it must not create an empty/broken card on this page.
  const properties = results.data.filter(hasUsablePhoto);

  if (properties.length === 0) {
    return (
      <div className="flex flex-1 flex-col">
        <div className="mx-auto w-full max-w-6xl px-6 pt-6 pb-24">
          <div className="flex items-center gap-3">
            <div className="min-w-0 flex-1">
              <CategoryChips types={types} basePath="/" />
            </div>
            <FiltersSheet types={types} amenities={amenities} basePath="/" />
          </div>
          <div className="mt-8">
            <EmptyState
              icon="sparkle"
              title="No stays yet"
              description="Havyn Villa is just getting started — new listings will appear here as hosts publish them."
            />
          </div>
        </div>
      </div>
    );
  }

  const { featured, rows } = buildHomeRows(properties);

  return (
    <div className="flex flex-1 flex-col">
      <div className="mx-auto w-full max-w-6xl px-6 pt-6 pb-24">
        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <CategoryChips types={types} basePath="/" />
          </div>
          <FiltersSheet types={types} amenities={amenities} basePath="/" />
        </div>

        <div className="mt-8 space-y-10">
          <CarouselRow title={FEATURED_TITLE} properties={featured} />
          {rows.map((row) => (
            <CarouselRow key={row.title} title={row.title} properties={row.properties} />
          ))}
        </div>
      </div>
    </div>
  );
}

function buildHomeRows(properties: SearchResultItem[]) {
  const used = new Set<string>();

  const featured = properties.slice(0, ROW_SIZE);
  featured.forEach((p) => used.add(p.id));

  const remaining = properties.filter((p) => !used.has(p.id));

  const byCity = new Map<string, SearchResultItem[]>();
  for (const property of remaining) {
    const key = property.city?.trim() || "Other";
    const list = byCity.get(key) ?? [];
    list.push(property);
    byCity.set(key, list);
  }

  // Only build a row for a city if there's enough content to make scrolling
  // worthwhile — avoids a "row" that's just 1-2 cards.
  const MIN_ROW_SIZE = 4;

  const rows = Array.from(byCity.entries())
    .filter(([, list]) => list.length >= MIN_ROW_SIZE)
    .sort((a, b) => b[1].length - a[1].length) // biggest city sections first
    .map(([city, list]) => {
      const cityProperties = list.slice(0, ROW_SIZE);
      cityProperties.forEach((p) => used.add(p.id));
      const state = cityProperties[0]?.state;
      return {
        title: state && state !== city ? `Stays in ${city}, ${state}` : `Stays in ${city}`,
        properties: cityProperties,
      };
    });

  return { featured, rows };
}

function hasUsablePhoto(property: SearchResultItem) {
  return property.photoUrls.some((url) => {
    try {
      const parsed = new URL(url);
      return parsed.protocol === "https:" || parsed.protocol === "http:";
    } catch {
      return false;
    }
  });
}
