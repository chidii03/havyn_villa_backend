import { Ionicons } from "@expo/vector-icons";
import { Link } from "expo-router";
import { ActivityIndicator, Pressable, Text } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useAuth } from "@/lib/auth/auth-provider";

export default function AccountScreen() {
  const { status, user, logout } = useAuth();

  if (status === "loading") {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-bg">
        <ActivityIndicator color="#0B5FD0" />
      </SafeAreaView>
    );
  }

  if (status === "unauthenticated") {
    return (
      <SafeAreaView className="flex-1 items-center justify-center gap-4 bg-bg px-6">
        <Ionicons name="person-circle-outline" size={64} color="#5B6B7F" />
        <Text className="text-center font-semibold text-lg text-ink">Log in to see your account</Text>
        <Link href="/login" asChild>
          <Pressable className="rounded-full bg-brand px-6 py-3 active:opacity-80">
            <Text className="font-medium text-white">Log in</Text>
          </Pressable>
        </Link>
        <Link href="/signup" asChild>
          <Pressable className="rounded-full border border-line px-6 py-3 active:opacity-70">
            <Text className="font-medium text-ink">Sign up</Text>
          </Pressable>
        </Link>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 bg-bg px-6 pt-6" edges={["top"]}>
      <Text className="font-bold text-2xl text-ink">{user?.fullName ?? "Your account"}</Text>
      <Text className="mt-1 text-ink-muted">{user?.email}</Text>
      {!user?.emailVerified && <Text className="mt-3 text-sm text-warning">Verify your email to unlock hosting and reviews.</Text>}

      <Pressable onPress={() => logout()} className="mt-8 self-start rounded-full border border-line px-5 py-3 active:opacity-70">
        <Text className="font-medium text-ink">Log out</Text>
      </Pressable>
    </SafeAreaView>
  );
}
