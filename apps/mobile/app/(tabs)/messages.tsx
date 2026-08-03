import { useQuery } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import { ActivityIndicator, FlatList, Pressable, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import type { ConversationSummary } from "@havyn/shared";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { listConversations } from "@/lib/api/messaging";
import { useAuth } from "@/lib/auth/auth-provider";

/**
 * The first real frontend for the messaging backend built in prompt 16/session 17 —
 * apps/web never got one either (`(protected)/messages/page.tsx` there is still an
 * EmptyState stub). This screen and conversation/[id].tsx are genuinely new
 * capability, not a port of an existing web screen.
 */
export default function MessagesScreen() {
  const { status, accessToken, user } = useAuth();
  const router = useRouter();

  const conversationsQuery = useQuery({
    queryKey: ["conversations"],
    queryFn: () => listConversations(accessToken!),
    enabled: status === "authenticated",
  });

  if (status !== "authenticated") {
    return (
      <SafeAreaView className="flex-1 justify-center bg-bg px-6">
        <EmptyState icon="chatbubble-outline" title="Log in to see your messages" actionLabel="Log in" onAction={() => router.push("/login")} />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 bg-bg" edges={["top"]}>
      <View className="px-4 pb-2 pt-4">
        <Text className="font-bold text-2xl text-ink">Messages</Text>
      </View>

      {conversationsQuery.isLoading && (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color="#0B5FD0" />
        </View>
      )}

      {conversationsQuery.isError && (
        <View className="px-4">
          <ErrorState title="Couldn't load your messages" onRetry={() => conversationsQuery.refetch()} />
        </View>
      )}

      {conversationsQuery.data && conversationsQuery.data.data.length === 0 && (
        <View className="px-4">
          <EmptyState icon="chatbubble-outline" title="No conversations yet" description="Message a host from a listing to start one." />
        </View>
      )}

      {conversationsQuery.data && conversationsQuery.data.data.length > 0 && (
        <FlatList
          data={conversationsQuery.data.data}
          keyExtractor={(item) => item.id}
          contentContainerClassName="px-4 pb-6"
          renderItem={({ item }) => <ConversationRow conversation={item} myId={user?.id} />}
        />
      )}
    </SafeAreaView>
  );
}

function ConversationRow({ conversation, myId }: { conversation: ConversationSummary; myId?: string }) {
  const router = useRouter();
  const iAmHost = conversation.hostId === myId;

  return (
    <Pressable
      onPress={() => router.push(`/conversation/${conversation.id}`)}
      className="border-line border-b py-3 active:opacity-70"
    >
      <Text className="font-medium text-ink">{conversation.propertyTitle}</Text>
      <Text className="text-ink-muted text-sm">{iAmHost ? "Guest inquiry" : "Conversation with host"}</Text>
      {conversation.lastMessageAt && (
        <Text className="text-ink-muted text-xs mt-1">
          {new Date(conversation.lastMessageAt).toLocaleDateString("en-NG", { month: "short", day: "numeric" })}
        </Text>
      )}
    </Pressable>
  );
}
