"use client";

import React from "react";
import { dmSerifDisplay } from "@/app/ui/fonts";
import {
  NavigationMenu,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
  navigationMenuTriggerStyle,
} from "@/components/ui/navigation-menu";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Kbd, KbdGroup } from "@/components/ui/kbd";

import { Eclipse } from "lucide-react";
import { useTheme } from "next-themes";

export default function Header() {
  const { theme, setTheme } = useTheme();
  const handleToggleTheme = () => {
    setTheme(theme === "dark" ? "light" : "dark");
  };
  return (
    <header className="w-full border-b bg-background">
      <div className="max-w-7xl mx-auto h-14 px-4 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <h1
            className={`${dmSerifDisplay.className} text-2xl md:text-3xl font-bold`}
          >
            MOOVENUE
          </h1>
          <NavigationMenu>
            <NavigationMenuList>
              <NavigationMenuItem>
                <NavigationMenuLink
                  asChild
                  className={navigationMenuTriggerStyle()}
                >
                  <Link href="/docs">Documentação</Link>
                </NavigationMenuLink>
              </NavigationMenuItem>
              <NavigationMenuItem>
                <NavigationMenuLink
                  asChild
                  className={navigationMenuTriggerStyle()}
                >
                  <Link href="/pricing">Planos</Link>
                </NavigationMenuLink>
              </NavigationMenuItem>
              <NavigationMenuItem>
                <NavigationMenuLink
                  asChild
                  className={navigationMenuTriggerStyle()}
                >
                  <Link href="/sandbox">Sandbox</Link>
                </NavigationMenuLink>
              </NavigationMenuItem>
            </NavigationMenuList>
          </NavigationMenu>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline">
            Procurar documentação...
            <KbdGroup>
              <Kbd>Ctrl</Kbd>
              <span>+</span>
              <Kbd>K</Kbd>
            </KbdGroup>
          </Button>
          <Button>Entrar</Button>
          <Button variant="outline">Registrar</Button>
          <Button
            variant="outline"
            size="icon"
            onClick={handleToggleTheme}
            aria-label="Alternar tema"
          >
            <Eclipse />
          </Button>
        </div>
      </div>
    </header>
  );
}
