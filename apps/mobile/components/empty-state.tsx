import { Ionicons } from "@expo/vector-icons";
import { Pressable, Text, View } from "react-native";

/** Mirrors apps/web/src/components/patterns/empty-state.tsx's rule: never just gray text — an icon, a headline, and (when actionable) a primary action. */
export function EmptyState({
  icon,
  title,
  description,
  actionLabel,
  onAction,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <View className="items-center gap-3 rounded-xl border border-dashed border-line px-6 py-16">
      <View className="size-12 items-center justify-center rounded-full bg-bg">
        <Ionicons name={icon} size={24} color="#5B6B7F" />
      </View>
      <Text className="text-center font-semibold text-lg text-ink">{title}</Text>
      {description && <Text className="max-w-xs text-center text-sm text-ink-muted">{description}</Text>}
      {actionLabel && onAction && (
        <Pressable onPress={onAction} className="mt-2 rounded-full bg-brand px-5 py-3 active:opacity-80">
          <Text className="font-medium text-white">{actionLabel}</Text>
        </Pressable>
      )}
    </View>
  );
}
