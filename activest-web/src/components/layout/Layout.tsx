import { Suspense } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { TermsGate } from '@/components/auth/TermsGate';
import { useTheme } from '@/context/ThemeContext';
import { clsx } from 'clsx';

export const Layout = () => {
  const { isDark } = useTheme();

  return (
    <div className={clsx(
      "flex h-screen transition-colors duration-300",
      isDark ? "bg-black" : "bg-[#f5f5f0]"
    )}>
      {/* Termos atualizados: cobre a tela até aceitar ou sair (D19). */}
      <TermsGate />
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <main className={clsx(
          "flex-1 overflow-x-hidden overflow-y-auto p-6 transition-colors duration-300",
          isDark ? "bg-black" : "bg-[#f5f5f0]"
        )}>
          {/* Página sob demanda (PLANO_PERFORMANCE_TELAS.md, D3): menu e cabeçalho ficam enquanto o pedaço da tela chega. */}
          <Suspense fallback={
            <div className={clsx("flex items-center justify-center py-20", isDark ? "text-white" : "text-gray-700")}>
              Carregando...
            </div>
          }>
            <Outlet />
          </Suspense>
        </main>
      </div>
    </div>
  );
};
