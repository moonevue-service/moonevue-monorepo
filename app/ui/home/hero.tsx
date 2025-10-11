"use client";

import { ArrowRight, ArrowUpRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { motion } from "framer-motion";

interface HeroProps {
  badge?: string;
  heading?: string;
  description?: string;
  buttons?: {
    primary?: { text: string; url: string };
    secondary?: { text: string; url: string };
  };
}

const Hero = ({
  badge = "Gestão Financeira Inteligente",
  heading = "Centralize pagamentos e controle financeiro em um só lugar",
  description = "Automatize integrações bancárias, visualize métricas e tome decisões rápidas. Experimente o futuro da gestão financeira!",
  buttons = {
    primary: {
      text: "Ver documentação",
      url: "/docs",
    },
    secondary: {
      text: "Acessar Sandbox",
      url: "/sandbox",
    },
  },
}: HeroProps) => {
  // 3 imagens fixas
  const floatingImages = [
    { img: "/img (2).png", top: "10%", left: "5%", size: 200, duration: 8 },
    { img: "/img (10).png", top: "40%", left: "80%", size: 250, duration: 10 },
    { img: "/img (5).png", top: "70%", left: "20%", size: 220, duration: 9 },
  ];

  return (
    <section className="relative flex flex-col items-center justify-center text-center py-32 px-4 overflow-hidden">
      {badge && (
        <Badge variant="outline" className="mb-4 flex items-center gap-2 z-10">
          {badge}
          <ArrowUpRight className="size-4" />
        </Badge>
      )}

      <h1 className="my-6 text-pretty text-4xl font-bold lg:text-6xl max-w-4xl z-10">
        {heading}
      </h1>

      <p className="text-muted-foreground mb-8 max-w-2xl text-lg lg:text-xl z-10">
        {description}
      </p>

      <div className="flex flex-col sm:flex-row gap-3 justify-center z-10">
        {buttons.primary && (
          <Button asChild className="w-full sm:w-auto">
            <a href={buttons.primary.url}>{buttons.primary.text}</a>
          </Button>
        )}
        {buttons.secondary && (
          <Button asChild variant="outline" className="w-full sm:w-auto">
            <a href={buttons.secondary.url}>
              {buttons.secondary.text}
              <ArrowRight className="ml-2 size-4" />
            </a>
          </Button>
        )}
      </div>

      {/* Imagens flutuantes */}
      {floatingImages.map(({ img, top, left, size, duration }, idx) => (
        <motion.img
          key={idx}
          src={img}
          alt={`floating-${idx}`}
          className="absolute"
          style={{
            top,
            left,
            width: `${size}px`,
            height: "auto",
          }}
          animate={{
            y: [0, -30, 0], // flutuação vertical maior
            x: [0, 20, -20, 0], // leve movimento horizontal
          }}
          transition={{
            duration,
            repeat: Infinity,
            repeatType: "mirror",
            ease: "easeInOut",
          }}
        />
      ))}
    </section>
  );
};

export default Hero;
