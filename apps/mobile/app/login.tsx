import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "expo-router";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { loginSchema, type LoginInput } from "@havyn/shared";
import { ApiError } from "@/lib/api/http";
import { useAuth } from "@/lib/auth/auth-provider";

export default function LoginScreen() {
  const { login } = useAuth();
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);
  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginInput>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" } });

  async function onSubmit(values: LoginInput) {
    setServerError(null);
    try {
      await login(values.email, values.password);
      router.replace("/(tabs)/account");
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : "Something went wrong. Please try again.");
    }
  }

  return (
    <KeyboardAvoidingView className="flex-1 bg-bg" behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <ScrollView contentContainerClassName="gap-4 p-6" keyboardShouldPersistTaps="handled">
        {serverError && (
          <View accessibilityRole="alert" className="rounded-md bg-danger/10 px-3 py-2">
            <Text className="text-sm text-danger">{serverError}</Text>
          </View>
        )}

        <View className="gap-1.5">
          <Text className="font-medium text-sm text-ink">Email</Text>
          <Controller
            name="email"
            control={control}
            render={({ field: { onChange, onBlur, value } }) => (
              <TextInput
                testID="email-input"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                keyboardType="email-address"
                autoCapitalize="none"
                autoComplete="email"
                placeholderTextColor="#5B6B7F"
                className={`rounded-lg border px-4 py-3 text-ink ${errors.email ? "border-danger" : "border-line"}`}
              />
            )}
          />
          {errors.email && <Text className="text-danger text-xs">{errors.email.message}</Text>}
        </View>

        <View className="gap-1.5">
          <Text className="font-medium text-sm text-ink">Password</Text>
          <Controller
            name="password"
            control={control}
            render={({ field: { onChange, onBlur, value } }) => (
              <TextInput
                testID="password-input"
                value={value}
                onChangeText={onChange}
                onBlur={onBlur}
                secureTextEntry
                autoComplete="password"
                placeholderTextColor="#5B6B7F"
                className={`rounded-lg border px-4 py-3 text-ink ${errors.password ? "border-danger" : "border-line"}`}
              />
            )}
          />
          {errors.password && <Text className="text-danger text-xs">{errors.password.message}</Text>}
        </View>

        <Pressable
          onPress={handleSubmit(onSubmit)}
          disabled={isSubmitting}
          className="mt-2 items-center rounded-full bg-brand py-3.5 active:opacity-80 disabled:opacity-50"
        >
          <Text className="font-semibold text-white">{isSubmitting ? "Logging in…" : "Log in"}</Text>
        </Pressable>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
