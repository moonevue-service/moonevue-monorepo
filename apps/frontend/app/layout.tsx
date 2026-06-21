import "@/app/ui/globals.css";
import { geistSans, dmSerifDisplay } from "@/app/ui/fonts";
import { AntdRegistry } from "@ant-design/nextjs-registry";
import AntdProvider from "./antd-provider";
import { AuthProvider } from "@/app/providers";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="pt-BR" className={`${geistSans.variable} ${dmSerifDisplay.variable}`}>
      <body className={`${geistSans.className} antialiased`}>
        <AntdRegistry>
          <AntdProvider>
            <AuthProvider>{children}</AuthProvider>
          </AntdProvider>
        </AntdRegistry>
      </body>
    </html>
  );
}
