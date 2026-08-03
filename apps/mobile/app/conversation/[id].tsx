import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useLocalSearchParams } from "expo-router";
import { useEffect, useState } from "react";
import { ActivityIndicator, FlatList, KeyboardAvoidingView, Platform, Pressable, Text, TextInput, View } from "react-native";
import type { MessageSummary } from "@havyn/shared";
import { ErrorState } from "@/components/error-state";
import { listMessages, markConversationRead, sendMessage } from "@/lib/api/messaging";
import { useAuth } from "@/lib/auth/auth-provider";

export default function ConversationScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { accessToken, user } = useAuth();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState("");

  const messagesQuery = useQuery({
    queryKey: ["conversation", id, "messages"],
    queryFn: () => listMessages(accessToken!, id),
  });

  useEffect(() => {
    if (accessToken) {
      markConversationRead(accessToken, id).catch(() => {
        // Best-effort — a failed read-receipt shouldn't block viewing the thread.
      });
    }
  }, [accessToken, id]);

  const sendMutation = useMutation({
    mutationFn: (body: string) => sendMessage(accessToken!, id, { body }),
    onSuccess: () => {
      setDraft("");
      queryClient.invalidateQueries({ queryKey: ["conversation", id, "messages"] });
    },
  });

  if (messagesQuery.isLoading) {
    return (
      <View className="flex-1 items-center justify-center bg-bg">
        <ActivityIndicator color="#0B5FD0" />
      </View>
    );
  }

  if (messagesQuery.isError) {
    return (
      <View className="flex-1 bg-bg px-4 pt-6">
        <ErrorState title="Couldn't load this conversation" onRetry={() => messagesQuery.refetch()} />
      </View>
    );
  }

  const messages = messagesQuery.data?.data ?? [];

  return (
    <KeyboardAvoidingView className="flex-1 bg-bg" behavior={Platform.OS === "ios" ? "padding" : undefined} keyboardVerticalOffset={90}>
      <FlatList
        data={messages}
        keyExtractor={(item) => item.id}
        contentContainerClassName="gap-2 p-4"
        renderItem={({ item }) => <MessageBubble message={item} mine={item.senderId === user?.id} />}
      />

      <View className="flex-row items-end gap-2 border-line border-t bg-surface p-3">
        <TextInput
          value={draft}
          onChangeText={setDraft}
          placeholder="Type a message…"
          placeholderTextColor="#5B6B7F"
          multiline
          className="max-h-24 flex-1 rounded-lg border border-line px-3 py-2 text-ink"
        />
        <Pressable
          onPress={() => draft.trim() && sendMutation.mutate(draft.trim())}
          disabled={sendMutation.isPending || draft.trim().length === 0}
          className="size-11 items-center justify-center rounded-full bg-brand active:opacity-80 disabled:opacity-40"
        >
          <Ionicons name="send" size={16} color="white" />
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

function MessageBubble({ message, mine }: { message: MessageSummary; mine: boolean }) {
  return (
    <View className={`max-w-[80%] rounded-2xl px-3.5 py-2.5 ${mine ? "self-end bg-brand" : "self-start bg-surface border border-line"}`}>
      <Text className={mine ? "text-white" : "text-ink"}>{message.body}</Text>
      <Text className={`mt-1 text-[11px] ${mine ? "text-white/70" : "text-ink-muted"}`}>
        {new Date(message.createdAt).toLocaleTimeString("en-NG", { hour: "numeric", minute: "2-digit" })}
      </Text>
    </View>
  );
}
