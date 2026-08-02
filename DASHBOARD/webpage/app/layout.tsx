import type { Metadata } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import { Header } from "@/components/Header";
import { Nav } from "@/components/Nav";
import { Footer } from "@/components/Footer";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
});

const jetbrainsMono = JetBrains_Mono({
  subsets: ["latin"],
  variable: "--font-jetbrains-mono",
  display: "swap",
});

export const metadata: Metadata = {
  title: "ANI-KUTA · Visual Documentation",
  description:
    "A visual documentation dashboard for the ANI-KUTA project — modules, decisions, progress, and architecture.",
};

/**
 * Inline theme-init script (DESIGN.md §5.9 — "no flash of wrong theme").
 * Runs before paint to set the `dark` class on <html> based on
 * localStorage preference or prefers-color-scheme.
 *
 * Must be a plain string (no React template logic) — kept minimal.
 */
const THEME_INIT_SCRIPT = `(function(){try{var s=localStorage.getItem('theme');var d=s? s==='dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;if(d){document.documentElement.classList.add('dark');}}catch(e){}})();`;

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className={`${inter.variable} ${jetbrainsMono.variable}`} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body className="antialiased">
        <div className="min-h-screen flex flex-col max-w-5xl mx-auto px-6 md:px-8 pt-10 pb-16">
          <Header />
          <Nav />
          <main className="flex-1 mt-8 animate-fade-in">{children}</main>
          <Footer />
        </div>
      </body>
    </html>
  );
}
