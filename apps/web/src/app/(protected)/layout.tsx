import { RequireAuth } from "@/components/auth/require-auth";
import { requireSessionCookie } from "@/lib/auth/require-session-cookie";

/**
 * Shared guard for /wishlists, /trips, /messages, /account, /host — see
 * require-session-cookie.ts and components/auth/require-auth.tsx for why this is a
 * two-layer (fast server redirect + authoritative client check) guard rather than a
 * single server-side check. /admin adds its own nested role check on top of this.
 */
export default async function ProtectedLayout({ children }: { children: React.ReactNode }) {
  await requireSessionCookie();

  return <RequireAuth>{children}</RequireAuth>;
}
