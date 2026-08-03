import { Ionicons } from "@expo/vector-icons";
import { Tabs } from "expo-router";

const BRAND = "#0B5FD0";
const INK_MUTED = "#5B6B7F";

export default function TabsLayout() {
  return (
    <Tabs screenOptions={{ tabBarActiveTintColor: BRAND, tabBarInactiveTintColor: INK_MUTED, headerTintColor: "#0F1B2D" }}>
      <Tabs.Screen
        name="index"
        options={{ title: "Explore", tabBarIcon: ({ color, size }) => <Ionicons name="search" size={size} color={color} /> }}
      />
      <Tabs.Screen
        name="trips"
        options={{
          title: "Trips",
          tabBarIcon: ({ color, size }) => <Ionicons name="briefcase-outline" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="messages"
        options={{
          title: "Messages",
          tabBarIcon: ({ color, size }) => <Ionicons name="chatbubble-outline" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="account"
        options={{ title: "Account", tabBarIcon: ({ color, size }) => <Ionicons name="person-outline" size={size} color={color} /> }}
      />
    </Tabs>
  );
}
