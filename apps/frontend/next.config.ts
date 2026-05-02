import type { NextConfig } from "next";

const backendBaseUrl = process.env.INTERNAL_API_BASE_URL || "http://gateway:8080";
const authBaseUrl = process.env.AUTH_INTERNAL_API_BASE_URL || "http://auth:8081";
const financeBaseUrl = process.env.FINANCE_INTERNAL_API_BASE_URL || "http://finance:8082";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/auth/:path*",
        destination: `${authBaseUrl}/auth/:path*`,
      },
      {
        source: "/payments/:path*",
        destination: `${backendBaseUrl}/payments/:path*`,
      },
      {
        source: "/api/:path*",
        destination: `${financeBaseUrl}/api/:path*`,
      },
      {
        source: "/webhooks/:path*",
        destination: `${backendBaseUrl}/webhooks/:path*`,
      },
    ];
  },
};

export default nextConfig;
