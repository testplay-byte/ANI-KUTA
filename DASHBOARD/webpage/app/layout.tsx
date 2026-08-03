import type { Metadata } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import { Sidebar } from "@/components/Sidebar";
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
  title: "ANI-KUTA · Project Dashboard",
  description:
    "A living dashboard for the ANI-KUTA project — modules, decisions, progress, architecture, analytics, and planning.",
};

/**
 * Inline theme-init script (DESIGN.md §5.10 — "no flash of wrong theme").
 * Runs before paint to set the `dark` class on <html> based on
 * localStorage preference or prefers-color-scheme.
 *
 * Also reads the sidebar-shrink preference so the sidebar renders at the
 * correct width on first paint (no layout shift).
 */
const THEME_INIT_SCRIPT = `(function(){try{var s=localStorage.getItem('theme');var d=s?s==='dark':window.matchMedia('(prefers-color-scheme: dark)').matches;if(d){document.documentElement.classList.add('dark');}var sh=localStorage.getItem('sidebar-shrink');if(sh==='1'){document.documentElement.setAttribute('data-sidebar-shrink','1');}}catch(e){}})();`;

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html
      lang="en"
      className={`${inter.variable} ${jetbrainsMono.variable}`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body className="antialiased">
        <div className="min-h-screen flex flex-col lg:flex-row lg:gap-3 lg:p-3">
          <Sidebar />
          <div className="flex-1 min-w-0 flex flex-col">
            <main className="flex-1 px-4 sm:px-6 lg:px-10 py-6 lg:py-8 animate-fade-in">
              <div className="max-w-[1280px] mx-auto">{children}</div>
            </main>
            <Footer />
          </div>
        </div>
      </body>
    </html>
  );
}
