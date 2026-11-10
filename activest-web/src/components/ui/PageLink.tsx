import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';

/**
 * Link de uma tela para outra, no canto superior direito do conteúdo (11/10/2026): Estratégia de Investimentos e
 * Rebalanceamento eram abas da mesma tela e viraram telas separadas, uma levando à outra. Desde 09/11/2026 com as
 * cores do botão primário (o mesmo de "Nova Transação"); antes, contorno dourado.
 */
export const PageLink = ({ to, label }: { to: string; label: string }) => (
  <Link
    to={to}
    className="inline-flex items-center h-9 px-3 rounded-md bg-accent text-sm font-medium text-primary whitespace-nowrap transition-colors hover:bg-accent/90 focus:outline-none focus:ring-2 focus:ring-accent"
  >
    {label}
    <ArrowRight className="w-4 h-4 ml-2" />
  </Link>
);
