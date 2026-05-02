'use client';

import { App, ConfigProvider } from 'antd';
import ptBR from 'antd/locale/pt_BR';
import type { ReactNode } from 'react';

export default function AntdProvider({ children }: { children: ReactNode }) {
  return (
    <ConfigProvider
      locale={ptBR}
      theme={{
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 6,
          fontFamily:
            "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
        },
        components: {
          Layout: {
            siderBg: '#ffffff',
            headerBg: '#ffffff',
            bodyBg: '#f5f7fa',
          },
          Menu: {
            itemBorderRadius: 6,
          },
        },
      }}
    >
      <App>{children}</App>
    </ConfigProvider>
  );
}
