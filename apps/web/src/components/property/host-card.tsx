import { Icon } from "@/components/ui/icon";

/**
 * `PropertyDetail` only exposes `hostId` today, not a display name/avatar — there's no
 * public host-profile lookup endpoint, and adding one is outside this prompt's
 * `apps/api/**` consume-only file scope. This shows a real, honest placeholder rather
 * than fabricating a host name; see backend/02-domain-modules.md's session 4 notes for
 * the "become a host" gap this is downstream of.
 */
export function HostCard({ hostId }: { hostId: string }) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-line p-4" data-host-id={hostId}>
      <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-muted">
        <Icon name="user" size={20} className="text-ink-muted" />
      </span>
      <div>
        <p className="font-medium text-ink">Hosted by a Havyn Villa host</p>
        <p className="text-sm text-ink-muted">Host details will appear here once host profiles are available.</p>
      </div>
    </div>
  );
}
