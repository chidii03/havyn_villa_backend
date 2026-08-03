import * as SecureStore from "expo-secure-store";

/**
 * The refresh token's home on-device — Keychain (iOS) / Keystore (Android) via
 * expo-secure-store, the native equivalent of the httpOnly cookie apps/web uses (see
 * project-docs/mobile/01-mobile-architecture.md: "Auth that works without browser
 * cookies... usable from secure device storage (SecureStore/Keychain)"). The access
 * token itself never touches storage — same as web, it lives only in memory
 * (AuthProvider's React state), cleared on app restart until silent refresh runs.
 */
const REFRESH_TOKEN_KEY = "havyn_refresh_token";

export function getStoredRefreshToken() {
  return SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
}

export function setStoredRefreshToken(token: string) {
  return SecureStore.setItemAsync(REFRESH_TOKEN_KEY, token);
}

export function clearStoredRefreshToken() {
  return SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
}
