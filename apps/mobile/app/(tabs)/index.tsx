import { useQuery } from "@tanstack/react-query";
import { ActivityIndicator, FlatList, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { PropertyCard } from "@/components/property-card";
import { listProperties } from "@/lib/api/properties";

/** Explore/home tab — GET /api/v1/properties, public, no auth. Mirrors apps/web's home/search grid at a simpler, single-list scope. */
export default function ExploreScreen() {
  const propertiesQuery = useQuery({ queryKey: ["properties"], queryFn: () => listProperties() });

  return (
    <SafeAreaView className="flex-1 bg-bg" edges={["top"]}>
      <View className="px-4 pb-2 pt-4">
        <Text className="font-bold text-2xl text-ink">Explore stays</Text>
      </View>

      {propertiesQuery.isLoading && (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color="#0B5FD0" />
        </View>
      )}

      {propertiesQuery.isError && (
        <View className="px-4">
          <ErrorState title="Couldn't load listings" onRetry={() => propertiesQuery.refetch()} />
        </View>
      )}

      {propertiesQuery.data && propertiesQuery.data.data.length === 0 && (
        <View className="px-4">
          <EmptyState icon="home-outline" title="No listings yet" description="Check back soon for new stays." />
        </View>
      )}

      {propertiesQuery.data && propertiesQuery.data.data.length > 0 && (
        <FlatList
          data={propertiesQuery.data.data}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => <PropertyCard property={item} />}
          contentContainerClassName="px-4 pb-6"
        />
      )}
    </SafeAreaView>
  );
}
