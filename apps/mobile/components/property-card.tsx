import { Ionicons } from "@expo/vector-icons";
import { Link } from "expo-router";
import { Pressable, Text, View } from "react-native";
import type { PropertySummary } from "@havyn/shared";
import { formatPrice } from "@/lib/format/currency";

/**
 * `photos` is always empty at this list-item level, same as apps/web's
 * `PropertyCard`/`CardCarousel` — `PropertySummary`/`SearchResultItem` never include
 * media URLs (that's a separate, per-property `GET /properties/{id}/media` call);
 * real photos only render on the detail screen. Not a mobile-specific gap.
 */
export function PropertyCard({ property }: { property: PropertySummary }) {
  return (
    <Link href={`/property/${property.id}`} asChild>
      <Pressable testID={`property-card-${property.id}`} className="mb-4 overflow-hidden rounded-xl border border-line bg-surface active:opacity-90">
        <View className="h-40 items-center justify-center bg-bg">
          <Ionicons name="image-outline" size={32} color="#5B6B7F" />
        </View>
        <View className="gap-1 p-3">
          <View className="flex-row items-center justify-between">
            <Text className="flex-1 font-semibold text-ink" numberOfLines={1}>
              {property.title}
            </Text>
            {property.ratingCount > 0 && (
              <View className="ml-2 flex-row items-center gap-1">
                <Ionicons name="star" size={14} color="#F4B740" />
                <Text className="text-sm text-ink">{property.ratingAvg.toFixed(2)}</Text>
              </View>
            )}
          </View>
          <Text className="text-sm text-ink-muted">
            {property.city}, {property.state}
          </Text>
          <Text className="text-sm text-ink">
            <Text className="font-semibold">{formatPrice(property.basePrice, property.currency)}</Text>
            <Text className="text-ink-muted"> / night</Text>
          </Text>
        </View>
      </Pressable>
    </Link>
  );
}
