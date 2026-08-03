import type { AuthResponse, UserSummary } from "@havyn/shared";
import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import * as authApi from "@/lib/api/auth";
import { clearStoredRefreshToken, getStoredRefreshToken, setStoredRefreshToken } from "./secure-store";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthContextValue {
  status: AuthStatus;
  user: UserSummary | null;
  accessToken: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, fullName: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  applySession: (session: AuthResponse) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const REFRESH_MARGIN_MS = 60_000; // rotate ~1 minute before the access token actually expires

/**
 * Mirrors apps/web/src/lib/auth/auth-provider.tsx's exact session-lifecycle shape
 * (same state machine, same "access token in memory only, silent-refresh on mount,
 * re-arm before every expiry" design) — the one substantive difference is *where*
 * the refresh token lives: SecureStore here instead of an httpOnly cookie there, per
 * this prompt's own "Auth works without browser cookies (bearer refresh + secure
 * storage)" constraint. `AuthController#refresh` already accepts a refresh token in
 * the request body as a fallback to the cookie, so no backend change was needed.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<UserSummary | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const silentRefreshRef = useRef<() => Promise<void>>(() => Promise.resolve());

  const scheduleRefresh = useCallback((expiresInSeconds: number) => {
    if (refreshTimer.current) {
      clearTimeout(refreshTimer.current);
    }
    const delay = Math.max(expiresInSeconds * 1000 - REFRESH_MARGIN_MS, 5_000);
    refreshTimer.current = setTimeout(() => {
      void silentRefreshRef.current();
    }, delay);
  }, []);

  const applySession = useCallback(
    async (session: AuthResponse) => {
      await setStoredRefreshToken(session.refreshToken);
      setAccessToken(session.accessToken);
      setUser(session.user);
      setStatus("authenticated");
      scheduleRefresh(session.expiresIn);
    },
    [scheduleRefresh],
  );

  const silentRefresh = useCallback(async () => {
    try {
      const storedToken = await getStoredRefreshToken();
      if (!storedToken) {
        setStatus("unauthenticated");
        return;
      }
      const session = await authApi.refresh(storedToken);
      await applySession(session);
    } catch {
      await clearStoredRefreshToken();
      setAccessToken(null);
      setUser(null);
      setStatus("unauthenticated");
    }
  }, [applySession]);

  useEffect(() => {
    silentRefreshRef.current = silentRefresh;
  }, [silentRefresh]);

  useEffect(() => {
    void silentRefreshRef.current();
    return () => {
      if (refreshTimer.current) {
        clearTimeout(refreshTimer.current);
      }
    };
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const session = await authApi.login({ email, password });
      await applySession(session);
    },
    [applySession],
  );

  const register = useCallback(
    async (email: string, password: string, fullName: string) => {
      const session = await authApi.register({ email, password, fullName });
      await applySession(session);
    },
    [applySession],
  );

  const logout = useCallback(async () => {
    try {
      const storedToken = await getStoredRefreshToken();
      if (storedToken) {
        await authApi.logout(storedToken);
      }
    } catch {
      // Best-effort server-side revoke — client-side state clears below regardless.
    } finally {
      if (refreshTimer.current) {
        clearTimeout(refreshTimer.current);
      }
      await clearStoredRefreshToken();
      setAccessToken(null);
      setUser(null);
      setStatus("unauthenticated");
    }
  }, []);

  const refreshUser = useCallback(async () => {
    if (!accessToken) {
      return;
    }
    setUser(await authApi.getMe(accessToken));
  }, [accessToken]);

  return (
    <AuthContext.Provider value={{ status, user, accessToken, login, register, logout, refreshUser, applySession }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
