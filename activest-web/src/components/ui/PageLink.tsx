import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';

/**
 * Link de uma tela para outra, no canto superior direito do conteúdo (11/10/2026): Estratégia de Investimentos e
 * Rebalanceamento eram abas da mesma tela e viraram telas separadas, uma levando à outra.
 */
export const PageLink = ({ to, label }: { to: string; label: string }) => (
  <Link
    to={to}
    className="inline-flex items-center h-9 px-3 rounded-md border border-accent/40 text-sm font-medium text-accent whitespace-nowrap transition-colors hover:bg-accent/10 focus:outline-none focus:ring-2 focus:ring-accent"
  >
    {label}
    <ArrowRight className="w-4 h-4 ml-2" />
  </Link>
);
