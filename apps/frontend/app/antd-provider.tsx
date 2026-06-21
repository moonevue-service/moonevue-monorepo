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
          colorPrimary: '#0a0a0a',
          colorLink: '#0a0a0a',
          colorLinkHover: '#404040',
          // Tons claros para os fundos derivados do primário (evita cinza escuro
          // ilegível em opções selecionadas de Select, Menu, etc.)
          colorPrimaryBg: '#f5f5f5',
          colorPrimaryBgHover: '#e5e5e5',
          controlItemBgActive: '#f5f5f5',
          controlItemBgActiveHover: '#e5e5e5',
          borderRadius: 8,
          fontFamily:
            "var(--font-geist-sans), -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
        },
        components: {
          Layout: {
            siderBg: '#ffffff',
            headerBg: '#ffffff',
            bodyBg: '#fafafa',
          },
          Menu: {
            itemBorderRadius: 8,
            itemSelectedBg: '#f5f5f5',
            itemSelectedColor: '#0a0a0a',
            itemHoverBg: '#f5f5f5',
          },
          Select: {
            optionSelectedBg: '#f5f5f5',
            optionSelectedColor: '#0a0a0a',
            optionActiveBg: '#f5f5f5',
          },
        },
      }}
    >
      <App>{children}</App>
    </ConfigProvider>
  );
}
