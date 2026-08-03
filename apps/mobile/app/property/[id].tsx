import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery } from "@tanstack/react-query";
import DateTimePicker from "@react-native-community/datetimepicker";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useState } from "react";
import { ActivityIndicator, Pressable, ScrollView, Text, View } from "react-native";
import { createBooking, quoteProperty } from "@/lib/api/bookings";
import { startConversation } from "@/lib/api/messaging";
import { getProperty } from "@/lib/api/properties";
import { ApiError } from "@/lib/api/http";
import { generateIdempotencyKey } from "@/lib/id";
import { useAuth } from "@/lib/auth/auth-provider";
import { formatPrice } from "@/lib/format/currency";
import { ErrorState } from "@/components/error-state";

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function defaultCheckIn(): Date {
  const date = new Date();
  date.setMonth(date.getMonth() + 1);
  date.setDate(10);
  return date;
}

function defaultCheckOut(checkIn: Date): Date {
  const date = new Date(checkIn);
  date.setDate(date.getDate() + 3);
  return date;
}

/**
 * Reserving creates a real, server-verified PENDING hold — mirrors apps/web's
 * BookingWidget exactly, including its own honest stopping point: there's no live
 * payment provider anywhere this project has run, so this never claims a booking is
 * "confirmed." See booking-widget.tsx's own doc comment (apps/web) for the same
 * reasoning.
 */
export default function PropertyDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { status, accessToken } = useAuth();
  const router = useRouter();

  const propertyQuery = useQuery({ queryKey: ["property", id], queryFn: () => getProperty(id) });

  const [checkIn, setCheckIn] = useState(defaultCheckIn());
  const [checkOut, setCheckOut] = useState(defaultCheckOut(defaultCheckIn()));
  const [guests, setGuests] = useState(2);
  const [pickerOpenFor, setPickerOpenFor] = useState<"checkIn" | "checkOut" | null>(null);

  const quoteMutation = useMutation({
    mutationFn: () => quoteProperty(id, { checkIn: toIsoDate(checkIn), checkOut: toIsoDate(checkOut), guests }),
  });

  const reserveMutation = useMutation({
    mutationFn: () =>
      createBooking(
        accessToken!,
        { propertyId: id, checkIn: toIsoDate(checkIn), checkOut: toIsoDate(checkOut), guests, expectedTotal: quoteMutation.data!.grandTotal },
        generateIdempotencyKey(),
      ),
  });

  const messageMutation = useMutation({
    mutationFn: () => startConversation(accessToken!, id, { body: `Hi, I'm interested in ${propertyQuery.data?.title ?? "this listing"}.` }),
    onSuccess: (conversation) => router.push(`/conversation/${conversation.id}`),
  });

  function requireAuth(action: () => void) {
    if (status !== "authenticated") {
      router.push("/login");
      return;
    }
    action();
  }

  if (propertyQuery.isLoading) {
    return (
      <View className="flex-1 items-center justify-center bg-bg">
        <ActivityIndicator color="#0B5FD0" />
      </View>
    );
  }

  if (propertyQuery.isError || !propertyQuery.data) {
    return (
      <View className="flex-1 bg-bg px-4 pt-6">
        <ErrorState title="Couldn't load this listing" onRetry={() => propertyQuery.refetch()} />
      </View>
    );
  }

  const property = propertyQuery.data;

  return (
    <ScrollView className="flex-1 bg-bg" contentContainerClassName="pb-10">
      <View className="h-56 items-center justify-center bg-line">
        <Ionicons name="image-outline" size={40} color="#5B6B7F" />
      </View>

      <View className="gap-4 p-4">
        <View>
          <Text className="font-bold text-2xl text-ink">{property.title}</Text>
          <Text className="mt-1 text-ink-muted">
            {property.city}, {property.state}, {property.country}
          </Text>
          {property.ratingCount > 0 && (
            <View className="mt-1 flex-row items-center gap-1">
              <Ionicons name="star" size={14} color="#F4B740" />
              <Text className="text-ink">
                {property.ratingAvg.toFixed(2)} · {property.ratingCount} review{property.ratingCount === 1 ? "" : "s"}
              </Text>
            </View>
          )}
        </View>

        <Text className="text-ink">{property.description}</Text>

        <View className="flex-row flex-wrap gap-3">
          <Text className="text-ink-muted text-sm">{property.capacity} guests</Text>
          <Text className="text-ink-muted text-sm">{property.bedrooms} bedrooms</Text>
          <Text className="text-ink-muted text-sm">{property.beds} beds</Text>
          <Text className="text-ink-muted text-sm">{property.bathrooms} baths</Text>
        </View>

        {property.amenities.length > 0 && (
          <View className="gap-2">
            <Text className="font-semibold text-ink">Amenities</Text>
            <View className="flex-row flex-wrap gap-2">
              {property.amenities.map((amenity) => (
                <View key={amenity.code} className="rounded-full border border-line px-3 py-1.5">
                  <Text className="text-ink text-sm">{amenity.name}</Text>
                </View>
              ))}
            </View>
          </View>
        )}

        <Pressable
          onPress={() => requireAuth(() => messageMutation.mutate())}
          disabled={messageMutation.isPending}
          className="flex-row items-center justify-center gap-2 rounded-full border border-line py-3 active:opacity-70"
        >
          <Ionicons name="chatbubble-outline" size={16} color="#0F1B2D" />
          <Text className="font-medium text-ink">{messageMutation.isPending ? "Starting…" : "Message host"}</Text>
        </Pressable>

        <View className="gap-3 rounded-xl border border-line bg-surface p-4">
          <Text className="font-semibold text-ink text-lg">
            {formatPrice(property.basePrice, property.currency)} <Text className="font-normal text-ink-muted">/ night</Text>
          </Text>

          <View className="flex-row gap-3">
            <DateField label="Check-in" date={checkIn} onPress={() => setPickerOpenFor("checkIn")} />
            <DateField label="Check-out" date={checkOut} onPress={() => setPickerOpenFor("checkOut")} />
          </View>

          <View className="flex-row items-center justify-between">
            <Text className="text-ink">Guests</Text>
            <View className="flex-row items-center gap-3">
              <Pressable
                onPress={() => setGuests((n) => Math.max(1, n - 1))}
                className="size-9 items-center justify-center rounded-full border border-line active:opacity-70"
              >
                <Ionicons name="remove" size={16} color="#0F1B2D" />
              </Pressable>
              <Text className="w-6 text-center text-ink">{guests}</Text>
              <Pressable
                onPress={() => setGuests((n) => Math.min(property.capacity, n + 1))}
                className="size-9 items-center justify-center rounded-full border border-line active:opacity-70"
              >
                <Ionicons name="add" size={16} color="#0F1B2D" />
              </Pressable>
            </View>
          </View>

          {pickerOpenFor && (
            <DateTimePicker
              value={pickerOpenFor === "checkIn" ? checkIn : checkOut}
              mode="date"
              minimumDate={pickerOpenFor === "checkIn" ? new Date() : checkIn}
              onChange={(_event, selected) => {
                setPickerOpenFor(null);
                if (!selected) return;
                if (pickerOpenFor === "checkIn") {
                  setCheckIn(selected);
                  if (selected >= checkOut) setCheckOut(defaultCheckOut(selected));
                } else {
                  setCheckOut(selected);
                }
              }}
            />
          )}

          {!reserveMutation.data && (
            <Pressable
              onPress={() => quoteMutation.mutate()}
              disabled={quoteMutation.isPending}
              className="items-center rounded-full bg-brand py-3.5 active:opacity-80 disabled:opacity-50"
            >
              <Text className="font-semibold text-white">{quoteMutation.isPending ? "Getting price…" : "Check price"}</Text>
            </Pressable>
          )}

          {quoteMutation.isError && <Text className="text-danger text-sm">Couldn&apos;t get a quote. Please try again.</Text>}

          {quoteMutation.data && !reserveMutation.data && (
            <View className="gap-1 border-line border-t pt-3">
              <PriceRow label={`${formatPrice(property.basePrice, property.currency)} × ${quoteMutation.data.nights} nights`} amount={quoteMutation.data.baseTotal} currency={quoteMutation.data.currency} />
              {quoteMutation.data.cleaningFee > 0 && <PriceRow label="Cleaning fee" amount={quoteMutation.data.cleaningFee} currency={quoteMutation.data.currency} />}
              {quoteMutation.data.serviceFee > 0 && <PriceRow label="Service fee" amount={quoteMutation.data.serviceFee} currency={quoteMutation.data.currency} />}
              <View className="mt-1 flex-row justify-between border-line border-t pt-2">
                <Text className="font-semibold text-ink">Total</Text>
                <Text className="font-semibold text-ink">{formatPrice(quoteMutation.data.grandTotal, quoteMutation.data.currency)}</Text>
              </View>

              <Pressable
                onPress={() => requireAuth(() => reserveMutation.mutate())}
                disabled={reserveMutation.isPending}
                className="mt-3 items-center rounded-full bg-brand py-3.5 active:opacity-80 disabled:opacity-50"
              >
                <Text className="font-semibold text-white">{reserveMutation.isPending ? "Reserving…" : "Reserve"}</Text>
              </Pressable>
              {reserveMutation.isError && (
                <Text className="text-danger text-sm">
                  {reserveMutation.error instanceof ApiError ? reserveMutation.error.message : "Couldn't reserve these dates. Please try again."}
                </Text>
              )}
            </View>
          )}

          {reserveMutation.data && (
            <View className="gap-2 border-line border-t pt-3">
              <View className="flex-row items-center gap-2">
                <Ionicons name="time-outline" size={18} color="#0B5FD0" />
                <Text className="font-semibold text-ink">Dates held</Text>
              </View>
              <Text className="text-ink-muted text-sm">
                Payment isn&apos;t available yet — checkout is coming in a future update. If the hold expires before then, you can reserve again.
              </Text>
              <Pressable onPress={() => router.push("/(tabs)/trips")} className="mt-1 items-center rounded-full border border-line py-3 active:opacity-70">
                <Text className="font-medium text-ink">View in Trips</Text>
              </Pressable>
            </View>
          )}
        </View>
      </View>
    </ScrollView>
  );
}

function DateField({ label, date, onPress }: { label: string; date: Date; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} className="flex-1 gap-1 rounded-lg border border-line px-3 py-2 active:opacity-70">
      <Text className="text-ink-muted text-xs">{label}</Text>
      <Text className="text-ink">{date.toLocaleDateString("en-NG", { month: "short", day: "numeric" })}</Text>
    </Pressable>
  );
}

function PriceRow({ label, amount, currency }: { label: string; amount: number; currency: string }) {
  return (
    <View className="flex-row justify-between">
      <Text className="text-ink-muted text-sm">{label}</Text>
      <Text className="text-ink text-sm">{formatPrice(amount, currency)}</Text>
    </View>
  );
}
