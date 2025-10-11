"use client";

import {
  Book,
  Menu,
  Boxes,
  CloudCheck,
  Zap,
  Scale,
  MessageCircleQuestionMark,
  Calendar,
  Smile,
  Calculator,
  User,
  CreditCard,
  Settings,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { dmSerifDisplay } from "../fonts";

import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Button } from "@/components/ui/button";
import {
  NavigationMenu,
  NavigationMenuContent,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
  NavigationMenuTrigger,
} from "@/components/ui/navigation-menu";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";

import {
  Command,
  CommandInput,
  CommandList,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandSeparator,
  CommandShortcut,
  CommandDialog,
} from "@/components/ui/command";
import { Kbd, KbdGroup } from "@/components/ui/kbd";

interface MenuItem {
  title: string;
  url: string;
  description?: string;
  icon?: React.ReactNode;
  items?: MenuItem[];
}

interface Navbar1Props {
  logo?: {
    url: string;
    src: string;
    alt: string;
    title: string;
  };
  menu?: MenuItem[];
  auth?: {
    login: {
      title: string;
      url: string;
    };
    signup: {
      title: string;
      url: string;
    };
  };
}

const Header = ({
  logo = {
    url: "#",
    src: "",
    alt: "logo",
    title: "MOONEVUE",
  },
  menu = [
    { title: "Início", url: "#" },
    {
      title: "Produtos",
      url: "#",
      items: [
        {
          title: "Blog",
          description:
            "Acompanhe as últimas notícias, atualizações e informações",
          icon: <Book className="size-5 shrink-0" />,
          url: "#",
        },
        {
          title: "Sandbox",
          description: "Teste nossas APIs em um ambiente seguro",
          icon: <Boxes className="size-5 shrink-0" />,
          url: "#",
        },
        {
          title: "Suporte",
          description:
            "Entre em contato com nosso time de suporte ou consulte nossa documentação",
          icon: <MessageCircleQuestionMark className="size-5 shrink-0" />,
          url: "#",
        },
      ],
    },
    {
      title: "Recursos",
      url: "#",
      items: [
        {
          title: "Documentação",
          description: "Encontre todas as respostas que você precisa aqui",
          icon: <Book className="size-5 shrink-0" />,
          url: "#",
        },
        {
          title: "Status",
          description: "Verifique o status atual de nossos serviços e APIs",
          icon: <CloudCheck className="size-5 shrink-0" />,
          url: "#",
        },
        {
          title: "Termos de Serviço",
          description: "Nossos termos e condições para o uso dos serviços",
          icon: <Scale className="size-5 shrink-0" />,
          url: "#",
        },
      ],
    },
    { title: "Preços", url: "#" },
    { title: "Blog", url: "#" },
  ],
  auth = {
    login: { title: "Entrar", url: "#" },
    signup: { title: "Cadastrar", url: "#" },
  },
}: Navbar1Props) => {
  const [isCommandOpen, setIsCommandOpen] = useState(false);

  useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setIsCommandOpen((prev) => !prev);
      }
    };
    document.addEventListener("keydown", down);
    return () => document.removeEventListener("keydown", down);
  }, []);

  return (
    <header className="py-4 relative z-50 bg-background">
      <div className="max-w-7xl mx-auto px-4 flex flex-col">
        {/* Desktop Menu */}
        <nav className="hidden justify-between lg:flex items-center">
          <div className="flex items-center gap-6">
            <Link href={logo.url} className="flex items-center gap-2">
              <span
                className={`${dmSerifDisplay.className} text-3xl md:text-4xl font-bold`}
              >
                {logo.title}
              </span>
            </Link>

            <NavigationMenu>
              <NavigationMenuList>
                {menu.map((item) => renderMenuItem(item))}
              </NavigationMenuList>
            </NavigationMenu>
          </div>

          <div className="flex gap-2 items-center">
            {/* Botão de procurar documentação agora fica junto aos auth */}
            <Button
              size="sm"
              variant="outline"
              className="hidden lg:flex items-center gap-2 cursor-pointer"
              onClick={() => setIsCommandOpen(true)}
            >
              Procurar documentação...
              <KbdGroup>
                <Kbd>Ctrl</Kbd>
                <span>+</span>
                <Kbd>K</Kbd>
              </KbdGroup>
            </Button>

            {/* Botões de login/cadastro */}
            <Button asChild size="sm" variant="outline">
              <Link href={auth.login.url}>{auth.login.title}</Link>
            </Button>
            <Button asChild size="sm">
              <Link href={auth.signup.url}>{auth.signup.title}</Link>
            </Button>
          </div>
        </nav>

        {/* Mobile Menu */}
        <div className="flex justify-between items-center lg:hidden">
          <Link href={logo.url} className="flex items-center gap-2">
            <span
              className={`${dmSerifDisplay.className} text-5xl md:text-6xl font-bold`}
            >
              {logo.title}
            </span>
          </Link>

          <Sheet>
            <SheetTrigger asChild>
              <Button variant="outline" size="icon">
                <Menu className="size-4" />
              </Button>
            </SheetTrigger>
            <SheetContent side="right" className="overflow-y-auto">
              <div className="flex flex-col gap-6 p-4">
                <Accordion
                  type="single"
                  collapsible
                  className="flex flex-col gap-4"
                >
                  {menu.map((item) => renderMobileMenuItem(item))}
                </Accordion>

                <div className="flex flex-col gap-3">
                  <Button asChild variant="outline">
                    <Link href={auth.login.url}>{auth.login.title}</Link>
                  </Button>
                  <Button asChild>
                    <Link href={auth.signup.url}>{auth.signup.title}</Link>
                  </Button>
                </div>
              </div>
            </SheetContent>
          </Sheet>
        </div>
      </div>

      {/* Componente CommandDemo como modal */}
      <CommandDialogDemo open={isCommandOpen} setOpen={setIsCommandOpen} />
    </header>
  );
};

/* --- Funções auxiliares --- */
const renderMenuItem = (item: MenuItem) => {
  if (item.items) {
    return (
      <NavigationMenuItem key={item.title}>
        <NavigationMenuTrigger>{item.title}</NavigationMenuTrigger>
        <NavigationMenuContent className="z-50 bg-popover text-popover-foreground p-4 rounded-md shadow-md">
          <ul className="grid gap-2 w-[320px]">
            {item.items.map((subItem) => (
              <li key={subItem.title}>
                <NavigationMenuLink asChild>
                  <SubMenuLink item={subItem} />
                </NavigationMenuLink>
              </li>
            ))}
          </ul>
        </NavigationMenuContent>
      </NavigationMenuItem>
    );
  }

  return (
    <NavigationMenuItem key={item.title}>
      <NavigationMenuLink
        href={item.url}
        className="bg-background hover:bg-muted hover:text-accent-foreground inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium transition-colors"
      >
        {item.title}
      </NavigationMenuLink>
    </NavigationMenuItem>
  );
};

const renderMobileMenuItem = (item: MenuItem) => {
  if (item.items) {
    return (
      <AccordionItem key={item.title} value={item.title} className="border-b-0">
        <AccordionTrigger className="text-md py-0 font-semibold hover:no-underline">
          {item.title}
        </AccordionTrigger>
        <AccordionContent className="mt-2 flex flex-col gap-2">
          {item.items.map((subItem) => (
            <SubMenuLink key={subItem.title} item={subItem} />
          ))}
        </AccordionContent>
      </AccordionItem>
    );
  }

  return (
    <Link key={item.title} href={item.url} className="text-md font-semibold">
      {item.title}
    </Link>
  );
};

const SubMenuLink = ({ item }: { item: MenuItem }) => {
  return (
    <Link
      href={item.url}
      className="hover:bg-muted hover:text-accent-foreground flex min-w-80 select-none flex-row gap-4 rounded-md p-3 leading-none no-underline outline-none transition-colors"
    >
      <div className="text-foreground">{item.icon}</div>
      <div>
        <div className="text-sm font-semibold">{item.title}</div>
        {item.description && (
          <p className="text-muted-foreground text-sm leading-snug">
            {item.description}
          </p>
        )}
      </div>
    </Link>
  );
};

/* --- Componente CommandDemo --- */
export function CommandDialogDemo({
  open,
  setOpen,
}: {
  open: boolean;
  setOpen: (v: boolean) => void;
}) {
  return (
    <CommandDialog open={open} onOpenChange={setOpen}>
      <CommandInput placeholder="Digite um comando ou busque..." />
      <CommandList>
        <CommandEmpty>Nenhum resultado encontrado.</CommandEmpty>
        <CommandGroup heading="Sugestões">
          <CommandItem onSelect={() => setOpen(false)}>
            <Calendar />
            <span>Calendário</span>
          </CommandItem>
          <CommandItem onSelect={() => setOpen(false)}>
            <Smile />
            <span>Pesquisar Emoji</span>
          </CommandItem>
          <CommandItem disabled>
            <Calculator />
            <span>Calculadora</span>
          </CommandItem>
        </CommandGroup>
        <CommandSeparator />
        <CommandGroup heading="Configurações">
          <CommandItem onSelect={() => setOpen(false)}>
            <User />
            <span>Perfil</span>
            <CommandShortcut>⌘P</CommandShortcut>
          </CommandItem>
          <CommandItem onSelect={() => setOpen(false)}>
            <CreditCard />
            <span>Faturamento</span>
            <CommandShortcut>⌘B</CommandShortcut>
          </CommandItem>
          <CommandItem onSelect={() => setOpen(false)}>
            <Settings />
            <span>Configurações</span>
            <CommandShortcut>⌘S</CommandShortcut>
          </CommandItem>
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  );
}

export default Header;
