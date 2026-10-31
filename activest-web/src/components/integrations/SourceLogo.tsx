import { useState } from 'react';
import { IntegrationSource } from '@/types';

/**
 * Logo da fonte de integração (D9: imagens em public/images, pelo caminho que o servidor informa). Sem o catálogo ou
 * se a imagem falhar, a inicial do nome, como antes.
 */
export const SourceLogo = ({ source, fallback }: { source?: IntegrationSource; fallback?: string }) => {
  const [failed, setFailed] = useState(false);
  const name = source?.name ?? fallback ?? '?';
  if (source?.logo && !failed) {
    return (
      <img src={source.logo} alt={name} onError={() => setFailed(true)}
        className="h-10 w-10 shrink-0 rounded-full bg-white/5 object-contain p-1" />
    );
  }
  return (
    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent/10 text-accent font-bold">
      {name.charAt(0)}
    </div>
  );
};
