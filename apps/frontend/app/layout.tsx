import "@/app/ui/globals.css";
import { inter } from "@/app/ui/fonts";
import { AntdRegistry } from "@ant-design/nextjs-registry";
import AntdProvider from "./antd-provider";
import { AuthProvider } from "@/app/providers";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="pt-BR">
      <body className={`${inter.className} antialiased`}>
        <AntdRegistry>
          <AntdProvider>
            <AuthProvider>{children}</AuthProvider>
          </AntdProvider>
        </AntdRegistry>
      </body>
    </html>
  );
}
