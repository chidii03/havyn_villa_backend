"use client";

import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * prompt 25's "Web CWV optimization... code-splitting": `MapView` pulls in
 * `@vis.gl/react-google-maps`, a real third-party bundle, into whatever route
 * imports it — even on the mobile `/search` layout, where the map panel is hidden
 * (`hidden lg:block`, see `SearchResultsView`) until the user explicitly taps "Map".
 * `MapView` is already a client-only component (real `AdvancedMarker`/browser Maps
 * API usage), so `ssr: false` costs nothing it wasn't already paying — but Next only
 * allows `ssr: false` inside a Client Component, hence this separate "use client"
 * wrapper rather than calling `dynamic()` directly from the (server) room-detail page.
 *
 * `map-view.tsx` itself is untouched and still directly unit-tested
 * (`map-view.test.tsx`) — this wrapper has no logic of its own worth testing.
 */
export const MapView = dynamic(() => import("./map-view").then((mod) => mod.MapView), {
  ssr: false,
  loading: () => <Skeleton className="h-full w-full rounded-xl" />,
});
