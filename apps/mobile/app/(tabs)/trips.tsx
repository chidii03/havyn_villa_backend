import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import { ActivityIndicator, FlatList, Pressable, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import type { BookingDetail } from "@havyn/shared";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { cancelBooking, listBookings } from "@/lib/api/bookings";
import { useAuth } from "@/lib/auth/auth-provider";
import { formatPrice } from "@/lib/format/currency";

const STATUS_LABELS: Record<string, string> = {
  PENDING: "Awaiting payment",
  CONFIRMED: "Confirmed",
  CANCELLED: "Cancelled",
  COMPLETED: "Completed",
  REFUNDED: "Refunded",
};
const CANCELLABLE_STATUSES = new Set(["PENDING", "CONFIRMED"]);

/** Real GET /bookings data — no "upcoming/past" framing, mirrors apps/web's TripsPage: nothing can be CONFIRMED yet without a live payment provider. */
export default function TripsScreen() {
  const { status, accessToken } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();

  const bookingsQuery = useQuery({
    queryKey: ["bookings", "mine"],
    queryFn: () => listBookings(accessToken!),
    enabled: status === "authenticated",
  });

  const cancelMutation = useMutation({
    mutationFn: (bookingId: string) => cancelBooking(accessToken!, bookingId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["bookings", "mine"] }),
  });

  if (status !== "authenticated") {
    return (
      <SafeAreaView className="flex-1 justify-center bg-bg px-6">
        <EmptyState icon="briefcase-outline" title="Log in to see your trips" actionLabel="Log in" onAction={() => router.push("/login")} />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 bg-bg" edges={["top"]}>
      <View className="px-4 pb-2 pt-4">
        <Text className="font-bold text-2xl text-ink">Trips</Text>
      </View>

      {bookingsQuery.isLoading && (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color="#0B5FD0" />
        </View>
      )}

      {bookingsQuery.isError && (
        <View className="px-4">
          <ErrorState title="Couldn't load your trips" onRetry={() => bookingsQuery.refetch()} />
        </View>
      )}

      {bookingsQuery.data && bookingsQuery.data.data.length === 0 && (
        <View className="px-4">
          <EmptyState icon="briefcase-outline" title="No trips booked yet" description="Reserve a stay and it'll show up here." />
        </View>
      )}

      {bookingsQuery.data && bookingsQuery.data.data.length > 0 && (
        <FlatList
          data={bookingsQuery.data.data}
          keyExtractor={(item) => item.id}
          contentContainerClassName="gap-3 px-4 pb-6"
          renderItem={({ item }) => (
            <TripCard
              booking={item}
              onCancel={() => cancelMutation.mutate(item.id)}
              cancelling={cancelMutation.isPending && cancelMutation.variables === item.id}
            />
          )}
        />
      )}
    </SafeAreaView>
  );
}

function TripCard({ booking, onCancel, cancelling }: { booking: BookingDetail; onCancel: () => void; cancelling: boolean }) {
  const canCancel = CANCELLABLE_STATUSES.has(booking.status);

  return (
    <View className="rounded-xl border border-line bg-surface p-4">
      <View className="flex-row items-start justify-between gap-2">
        <View className="flex-1">
          <Text className="font-medium text-ink">{booking.property.title}</Text>
          <Text className="text-ink-muted text-sm">
            {booking.property.city}, {booking.property.state}
          </Text>
        </View>
        <View className="rounded-full bg-bg px-2.5 py-1">
          <Text className="text-ink-muted text-xs">{STATUS_LABELS[booking.status] ?? booking.status}</Text>
        </View>
      </View>

      <View className="mt-3 flex-row items-center gap-1.5">
        <Ionicons name="calendar-outline" size={14} color="#5B6B7F" />
        <Text className="text-ink-muted text-sm">
          {new Date(booking.checkIn).toLocaleDateString("en-NG", { month: "short", day: "numeric" })} –{" "}
          {new Date(booking.checkOut).toLocaleDateString("en-NG", { month: "short", day: "numeric", year: "numeric" })} · {booking.guests}{" "}
          {booking.guests === 1 ? "guest" : "guests"}
        </Text>
      </View>

      <View className="mt-3 flex-row items-center justify-between">
        <Text className="text-ink">
          <Text className="font-semibold">{formatPrice(booking.grandTotal, booking.currency)}</Text>
          <Text className="text-ink-muted"> total</Text>
        </Text>
        {canCancel && (
          <Pressable onPress={onCancel} disabled={cancelling} className="rounded-full border border-line px-4 py-2 active:opacity-70">
            <Text className="text-ink text-sm">{cancelling ? "Cancelling…" : "Cancel"}</Text>
          </Pressable>
        )}
      </View>
    </View>
  );
}
