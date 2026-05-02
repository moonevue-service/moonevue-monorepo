'use client';

import { useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { Avatar, Button, Drawer, Dropdown, Flex, Layout, Menu, theme, Typography } from 'antd';
import {
  AppstoreOutlined,
  BankOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SettingOutlined,
  SwapOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/app/providers';
import { ProtectedRoute } from '@/app/protected-route';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const navItems = [
  { key: '/dashboard', label: 'Visão Geral', icon: <AppstoreOutlined /> },
  { key: '/dashboard/bank-accounts', label: 'Contas Bancárias', icon: <BankOutlined /> },
  { key: '/dashboard/transactions', label: 'Transações', icon: <SwapOutlined /> },
  { key: '/dashboard/settings', label: 'Configurações', icon: <SettingOutlined /> },
];

function SidebarContent({
  pathname,
  onNavigate,
}: {
  pathname: string;
  onNavigate: (key: string) => void;
}) {
  const { token } = theme.useToken();
  return (
    <>
      <Flex
        align="center"
        style={{
          height: 64,
          paddingLeft: 24,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
          flexShrink: 0,
        }}
      >
        <Link
          href="/dashboard"
          style={{ color: token.colorPrimary, fontWeight: 700, fontSize: 16, textDecoration: 'none' }}
        >
          Moonevue
        </Link>
      </Flex>
      <Menu
        mode="inline"
        selectedKeys={[pathname]}
        items={navItems}
        style={{ border: 'none', marginTop: 8, flex: 1 }}
        onClick={({ key }) => onNavigate(key)}
      />
    </>
  );
}

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, logout } = useAuth();
  const { token } = theme.useToken();
  const [collapsed, setCollapsed] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const handleLogout = async () => {
    try {
      await logout();
    } finally {
      router.push('/login');
    }
  };

  const handleNavigate = (key: string) => {
    router.push(key);
    setDrawerOpen(false);
  };

  const userMenu = {
    items: [
      { key: 'email', label: <Text type="secondary" style={{ fontSize: 12 }}>{user?.email}</Text>, disabled: true },
      { type: 'divider' as const },
      { key: 'logout', label: 'Sair', icon: <LogoutOutlined />, danger: true },
    ],
    onClick: ({ key }: { key: string }) => key === 'logout' && handleLogout(),
  };

  const headerStyle: React.CSSProperties = {
    position: 'sticky',
    top: 0,
    zIndex: 10,
    background: '#fff',
    borderBottom: `1px solid ${token.colorBorderSecondary}`,
    display: 'flex',
    alignItems: 'center',
    padding: '0 24px',
    gap: 12,
    height: 64,
  };

  return (
    <ProtectedRoute>
      <Layout style={{ minHeight: '100vh' }}>
        {/* Desktop sidebar */}
        <Sider
          collapsible
          collapsed={collapsed}
          trigger={null}
          width={220}
          style={{
            background: '#fff',
            borderRight: `1px solid ${token.colorBorderSecondary}`,
            overflow: 'auto',
            height: '100vh',
            position: 'sticky',
            top: 0,
            display: 'flex',
            flexDirection: 'column',
          }}
          breakpoint="md"
          collapsedWidth={0}
          onBreakpoint={(broken) => setCollapsed(broken)}
          className="hidden md:block"
        >
          <SidebarContent pathname={pathname} onNavigate={handleNavigate} />
        </Sider>

        {/* Mobile drawer */}
        <Drawer
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          placement="left"
          width={220}
          styles={{ body: { padding: 0 }, header: { display: 'none' } }}
          className="md:hidden"
        >
          <Flex vertical style={{ height: '100%' }}>
            <SidebarContent pathname={pathname} onNavigate={handleNavigate} />
          </Flex>
        </Drawer>

        <Layout>
          <Header style={headerStyle}>
            {/* Desktop: collapse toggle */}
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              className="hidden md:flex"
            />
            {/* Mobile: drawer toggle */}
            <Button
              type="text"
              icon={<MenuUnfoldOutlined />}
              onClick={() => setDrawerOpen(true)}
              className="flex md:hidden"
            />

            <div style={{ flex: 1 }} />

            <Dropdown menu={userMenu} placement="bottomRight">
              <Flex align="center" gap={8} style={{ cursor: 'pointer' }}>
                <Avatar
                  size="small"
                  icon={<UserOutlined />}
                  style={{ backgroundColor: token.colorPrimary }}
                />
                <Text
                  style={{ fontSize: 14, maxWidth: 200 }}
                  ellipsis
                  className="hidden sm:block"
                >
                  {user?.email}
                </Text>
              </Flex>
            </Dropdown>
          </Header>

          <Content
            style={{
              padding: 24,
              background: token.colorBgLayout,
              minHeight: 'calc(100vh - 64px)',
            }}
          >
            <div style={{ maxWidth: 1200, margin: '0 auto' }}>{children}</div>
          </Content>
        </Layout>
      </Layout>
    </ProtectedRoute>
  );
}




