import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "export",
  basePath: "/ANI-KUTA",
  trailingSlash: true,
  images: {
    unoptimized: true,
  },
  reactStrictMode: true,
  // Silence the "multiple lockfiles" warning — the workspace root
  // is this project directory, not the parent monorepo root.
  turbopack: {
    root: __dirname,
  },
};

export default nextConfig;
