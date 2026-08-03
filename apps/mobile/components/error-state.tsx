import { Ionicons } from "@expo/vector-icons";
import { Pressable, Text, View } from "react-native";

export function ErrorState({
  title = "Something went wrong",
  description = "Please try again in a moment.",
  onRetry,
}: {
  title?: string;
  description?: string;
  onRetry?: () => void;
}) {
  return (
    <View accessibilityRole="alert" className="items-center gap-3 rounded-xl border border-dashed border-line px-6 py-16">
      <View className="size-12 items-center justify-center rounded-full bg-danger/10">
        <Ionicons name="warning-outline" size={24} color="#D24545" />
      </View>
      <Text className="text-center font-semibold text-lg text-ink">{title}</Text>
      <Text className="max-w-xs text-center text-sm text-ink-muted">{description}</Text>
      {onRetry && (
        <Pressable onPress={onRetry} className="mt-2 rounded-full border border-line px-5 py-3 active:opacity-70">
          <Text className="font-medium text-ink">Try again</Text>
        </Pressable>
      )}
    </View>
  );
}
