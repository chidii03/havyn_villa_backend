"use client";

import { motion, useReducedMotion } from "framer-motion";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

// Standard Unicode emoji, not custom-drawn artwork — a shared character set, not
// anyone's proprietary asset, so this doesn't run into CLAUDE.md's "never copy
// competitor icons/assets" rule the way reproducing bespoke icon art would.
const TABS: { href: string; label: string; emoji: string }[] = [
  { href: "/", label: "All", emoji: "🌐" },
  { href: "/", label: "Homes", emoji: "🏡" },
  { href: "/experiences", label: "Experiences", emoji: "🎈" },
  { href: "/services", label: "Services", emoji: "🛎️" },
];

/**
 * Center header nav — active tab underlines + turns brand blue
 * (frontend/03-ui-and-navigation-spec.md#1.1). Header mounts two responsive copies of
 * this (desktop row + mobile row, CSS-toggled) — `variant` gives each its own
 * accessible name (axe's landmark-unique rule flags two <nav>s with the same name)
 * and its own Framer Motion layoutId (a shared id across two simultaneously-mounted
 * instances would fight over the same underline animation).
 */
export function CategoryTabs({ variant = "desktop" }: { variant?: "desktop" | "mobile" }) {
  const pathname = usePathname();
  const reduceMotion = useReducedMotion();

  // "All" and "Homes" both point at "/" (this app has no distinct "all listings"
  // view separate from the homes feed yet) — only the first tab matching the current
  // path lights up, so they don't both render active at once.
  const firstMatchIndex = TABS.findIndex((tab) => tab.href === pathname);

  return (
    <nav aria-label={variant === "desktop" ? "Primary" : "Primary (mobile)"} className="flex items-center gap-1">
      {TABS.map(({ href, label, emoji }, index) => {
        const isActive = index === firstMatchIndex;
        return (
          <Link
            key={label}
            href={href}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "relative flex min-h-11 flex-col items-center justify-center gap-0.5 px-4 text-xs font-medium transition-colors",
              isActive ? "text-brand" : "text-ink-muted hover:text-ink",
            )}
          >
            <span aria-hidden className="text-lg leading-none">
              {emoji}
            </span>
            <span>{label}</span>
            {isActive && (
              <motion.span
                layoutId={`category-tab-underline-${variant}`}
                className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-brand"
                transition={reduceMotion ? { duration: 0 } : { type: "spring", stiffness: 400, damping: 32 }}
              />
            )}
          </Link>
        );
      })}
    </nav>
  );
}
