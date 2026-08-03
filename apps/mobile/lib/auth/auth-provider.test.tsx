import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { Pressable, Text } from "react-native";
import * as authApi from "@/lib/api/auth";
import { AuthProvider, useAuth } from "./auth-provider";
import * as secureStore from "./secure-store";

jest.mock("@/lib/api/auth", () => ({
  refresh: jest.fn(),
  login: jest.fn(),
  register: jest.fn(),
  logout: jest.fn(),
  getMe: jest.fn(),
}));
jest.mock("./secure-store", () => ({
  getStoredRefreshToken: jest.fn(),
  setStoredRefreshToken: jest.fn(() => Promise.resolve()),
  clearStoredRefreshToken: jest.fn(() => Promise.resolve()),
}));

const USER = { id: "u1", email: "ada@example.com", fullName: "Ada Lovelace", emailVerified: true, roles: ["CUSTOMER"], phone: null, avatarUrl: null };
const SESSION = { accessToken: "token-1", refreshToken: "refresh-1", expiresIn: 900, tokenType: "Bearer", user: USER };

function Consumer() {
  const { status, user, accessToken, login: doLogin, register: doRegister, logout: doLogout, refreshUser } = useAuth();
  return (
    <>
      <Text testID="status">{status}</Text>
      <Text testID="user">{user?.email ?? "none"}</Text>
      <Text testID="token">{accessToken ?? "none"}</Text>
      <Pressable testID="login" onPress={() => void doLogin("ada@example.com", "password")}>
        <Text>Login</Text>
      </Pressable>
      <Pressable testID="register" onPress={() => void doRegister("ada@example.com", "password", "Ada Lovelace")}>
        <Text>Register</Text>
      </Pressable>
      <Pressable testID="logout" onPress={() => void doLogout()}>
        <Text>Logout</Text>
      </Pressable>
      <Pressable testID="refresh-user" onPress={() => void refreshUser()}>
        <Text>Refresh user</Text>
      </Pressable>
    </>
  );
}

describe("AuthProvider", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("stays unauthenticated on mount when SecureStore has no stored refresh token", async () => {
    (secureStore.getStoredRefreshToken as jest.Mock).mockResolvedValue(null);
    await render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));
    expect(authApi.refresh).not.toHaveBeenCalled();
  });

  it("silently authenticates on mount using the stored refresh token", async () => {
    (secureStore.getStoredRefreshToken as jest.Mock).mockResolvedValue("refresh-1");
    (authApi.refresh as jest.Mock).mockResolvedValue(SESSION);
    await render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    expect(screen.getByTestId("user")).toHaveTextContent("ada@example.com");
    expect(authApi.refresh).toHaveBeenCalledWith("refresh-1");
    expect(secureStore.setStoredRefreshToken).toHaveBeenCalledWith("refresh-1");
  });

  it("falls back to unauthenticated and clears storage when the stored token is invalid", async () => {
    (secureStore.getStoredRefreshToken as jest.Mock).mockResolvedValue("stale-token");
    (authApi.refresh as jest.Mock).mockRejectedValue(new Error("expired"));
    await render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));
    expect(secureStore.clearStoredRefreshToken).toHaveBeenCalled();
  });

  it("login stores the refresh token and sets the session state from the real API response", async () => {
    (secureStore.getStoredRefreshToken as jest.Mock).mockResolvedValue(null);
    (authApi.login as jest.Mock).mockResolvedValue(SESSION);
    await render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));

    fireEvent.press(screen.getByTestId("login"));

    expect(authApi.login).toHaveBeenCalledWith({ email: "ada@example.com", password: "password" });
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    expect(secureStore.setStoredRefreshToken).toHaveBeenCalledWith("refresh-1");
  });

  it("logout clears SecureStore even if the API call fails (finally block)", async () => {
    (secureStore.getStoredRefreshToken as jest.Mock).mockResolvedValue("refresh-1");
    (authApi.refresh as jest.Mock).mockResolvedValue(SESSION);
    (authApi.logout as jest.Mock).mockRejectedValue(new Error("network error"));
    await render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));

    fireEvent.press(screen.getByTestId("logout"));

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));
    expect(screen.getByTestId("token")).toHaveTextContent("none");
    expect(secureStore.clearStoredRefreshToken).toHaveBeenCalled();
  });

  it("refreshUser re-fetches /me and updates the user", async () => {
    (secureStore.getStoredRefreshToken as jest.Mock).mockResolvedValue("refresh-1");
    (authApi.refresh as jest.Mock).mockResolvedValue(SESSION);
    (authApi.getMe as jest.Mock).mockResolvedValue({ ...USER, fullName: "Ada, Countess of Lovelace" });
    await render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));

    fireEvent.press(screen.getByTestId("refresh-user"));

    await waitFor(() => expect(authApi.getMe).toHaveBeenCalledWith("token-1"));
  });
});
