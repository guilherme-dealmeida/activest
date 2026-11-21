import { Suspense, useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { TermsGate } from '@/components/auth/TermsGate';

export const Layout = () => {
  // Menu lateral em tela estreita: fora do fluxo, como gaveta sobre o conteúdo.
  // Abaixo de `lg` o menu fixo de 256 px comia a tela inteira (o conteúdo
  // ficava com 130 px e era cortado); a gaveta só existe nessa faixa.
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();

  // Trocar de tela fecha a gaveta.
  useEffect(() => { setMenuOpen(false); }, [location.pathname]);

  return (
    <div className="flex h-screen bg-bg">
      {/* Termos atualizados: cobre a tela até aceitar ou sair (D19). */}
      <TermsGate />
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        <Header onToggleMenu={() => setMenuOpen(o => !o)} />
        <main className="flex-1 overflow-x-hidden overflow-y-auto p-4 sm:p-5 xl:p-6">
          {/* Página sob demanda (PLANO_PERFORMANCE_TELAS.md, D3): menu e cabeçalho ficam enquanto o pedaço da tela chega. */}
          <Suspense fallback={
            <div className="flex items-center justify-center py-20 text-content-3">
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
