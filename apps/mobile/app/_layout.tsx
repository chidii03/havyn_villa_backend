import "../global.css";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { AuthProvider } from "@/lib/auth/auth-provider";

const queryClient = new QueryClient();

/**
 * Root layout — mirrors apps/web/src/app/layout.tsx's provider stack (TanStack Query
 * + auth context wrapping everything). `GestureHandlerRootView`/`SafeAreaProvider`
 * are React Native-specific requirements Expo Router itself needs at the root, with
 * no web equivalent.
 */
export default function RootLayout() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <Stack screenOptions={{ headerTintColor: "#0B5FD0" }}>
              <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
              <Stack.Screen name="login" options={{ presentation: "modal", title: "Log in" }} />
              <Stack.Screen name="signup" options={{ presentation: "modal", title: "Sign up" }} />
              <Stack.Screen name="property/[id]" options={{ title: "" }} />
              <Stack.Screen name="conversation/[id]" options={{ title: "Conversation" }} />
            </Stack>
            <StatusBar style="dark" />
          </AuthProvider>
        </QueryClientProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
