import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "export",
  basePath: "/ANI-KUTA",
  trailingSlash: true,
  images: {
    unoptimized: true,
  },
  // Strict mode off for static demo
  reactStrictMode: true,
};

export default nextConfig;
