'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Button } from 'antd';
import { CloseOutlined, MenuOutlined } from '@ant-design/icons';
import { dmSerifDisplay } from '@/app/ui/fonts';

const links = [
  { href: '#produto', label: 'Produto' },
  { href: '#beneficios', label: 'Benefícios' },
  { href: '#integracoes', label: 'Integrações' },
  { href: '/client-area', label: 'Área do cliente' },
];

export default function Header() {
  const [mobileOpen, setMobileOpen] = useState(false);

  const navStyle: React.CSSProperties = { color: '#595959', fontSize: 14, textDecoration: 'none' };

  return (
    <header
      style={{
        position: 'sticky',
        top: 0,
        zIndex: 50,
        borderBottom: '1px solid #f0f0f0',
        background: 'rgba(255,255,255,0.92)',
        backdropFilter: 'blur(8px)',
      }}
    >
      <div
        style={{
          maxWidth: 1280,
          margin: '0 auto',
          padding: '0 24px',
          height: 64,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Link
          href="/"
          className={dmSerifDisplay.className}
          style={{ fontSize: 22, color: '#0a0a0a', textDecoration: 'none' }}
        >
          MOONEVUE
        </Link>

        {/* Desktop nav */}
        <nav
          className="hidden md:flex"
          style={{ display: 'flex', gap: 32, alignItems: 'center' }}
        >
          {links.map((item) =>
            item.href.startsWith('#') ? (
              <a key={item.href} href={item.href} style={navStyle}>
                {item.label}
              </a>
            ) : (
              <Link key={item.href} href={item.href} style={navStyle}>
                {item.label}
              </Link>
            )
          )}
        </nav>

        {/* Desktop auth buttons */}
        <div className="hidden md:flex" style={{ display: 'flex', gap: 8 }}>
          <Button href="/client-area">Sou cliente</Button>
          <Button href="/login">Entrar</Button>
          <Button type="primary" href="/register">
            Criar conta
          </Button>
        </div>

        {/* Mobile toggle */}
        <Button
          type="text"
          className="flex md:hidden"
          icon={mobileOpen ? <CloseOutlined /> : <MenuOutlined />}
          onClick={() => setMobileOpen((prev) => !prev)}
          aria-label="Abrir menu"
        />
      </div>

      {/* Mobile menu */}
      {mobileOpen && (
        <div style={{ borderTop: '1px solid #f0f0f0', background: '#fff', padding: '16px 24px' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {links.map((item) =>
              item.href.startsWith('#') ? (
                <a
                  key={item.href}
                  href={item.href}
                  style={{ ...navStyle, padding: '8px 0', display: 'block' }}
                  onClick={() => setMobileOpen(false)}
                >
                  {item.label}
                </a>
              ) : (
                <Link
                  key={item.href}
                  href={item.href}
                  style={{ ...navStyle, padding: '8px 0', display: 'block' }}
                  onClick={() => setMobileOpen(false)}
                >
                  {item.label}
                </Link>
              )
            )}
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            <Button href="/client-area" block>
              Sou cliente
            </Button>
            <Button href="/login" block>
              Entrar
            </Button>
            <Button type="primary" href="/register" block>
              Criar conta
            </Button>
          </div>
        </div>
      )}
    </header>
  );
}
