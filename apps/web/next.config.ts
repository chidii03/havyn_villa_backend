import type { NextConfig } from "next";
import path from "node:path";

const nextConfig: NextConfig = {
  // Pin the workspace root explicitly — this machine has an unrelated package-lock.json
  // in the Windows user profile root that Next.js's auto-detection otherwise picks up.
  turbopack: {
    root: path.join(__dirname, "..", ".."),
  },
  // @havyn/shared ships as raw TS (no build step — see packages/shared/README.md).
  transpilePackages: ["@havyn/shared"],
  images: {
    remotePatterns: [
      // RayProp-imported listing images (project-docs decision: RayProp listings are
      // synced into our own DB — property_media.secure_url points straight at RayProp's
      // own Supabase storage rather than a re-upload through our Cloudinary pipeline).
      // Every synced image observed so far is tytsncfkohapygnkilvh.supabase.co, but
      // scoping to *.supabase.co (not that one hostname) since RayProp could add
      // inventory from a different Supabase project reference later.
      { protocol: "https", hostname: "*.supabase.co", pathname: "/storage/v1/object/public/**" },
      // Our own Cloudinary-hosted media (host-uploaded listing photos).
      { protocol: "https", hostname: "res.cloudinary.com" },
    ],
  },
  // prompt 27: a minimal, self-contained server bundle (only the node_modules
  // actually reachable from the traced dependency graph, not the whole workspace)
  // for the production Docker image — see apps/web/Dockerfile.
  output: "standalone",
};

export default nextConfig;
