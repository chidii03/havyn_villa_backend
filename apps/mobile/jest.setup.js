// React 19's test renderer only flushes state updates synchronously inside act()
// when this flag is set — without it, effects/setState in AuthProvider etc. run
// outside act(), producing warnings and non-deterministic render timing in tests.
global.IS_REACT_ACT_ENVIRONMENT = true;

// expo-secure-store wraps native Keychain/Keystore APIs that don't exist in the Jest
// environment — mocked here so lib/auth/secure-store.ts (and anything that imports
// it transitively) doesn't crash on import during tests.
jest.mock("expo-secure-store", () => ({
  getItemAsync: jest.fn(() => Promise.resolve(null)),
  setItemAsync: jest.fn(() => Promise.resolve()),
  deleteItemAsync: jest.fn(() => Promise.resolve()),
}));
