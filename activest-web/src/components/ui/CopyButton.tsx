import { MouseEvent, useEffect, useRef, useState } from 'react';
import { Check, Copy } from 'lucide-react';
import { clsx } from 'clsx';
import { copyToClipboard } from '@/utils/clipboard';

/**
 * Ícone que copia um valor (docs/planos/PLANO_USABILIDADE_PRECO_TETO.md, S3, D1): sempre visível e discreto, dourado
 * no hover; vira ✓ por 1,5 s depois de copiar.
 */
export const CopyButton = ({ text, title = 'Copiar valor', className }: { text: string; title?: string; className?: string }) => {
  const [copied, setCopied] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); }, []);

  const copy = async (e: MouseEvent) => {
    e.stopPropagation();
    if (!(await copyToClipboard(text))) return;
    setCopied(true);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setCopied(false), 1500);
  };

  return (
    <button type="button" onClick={copy} title={copied ? 'Copiado' : `${title}: ${text}`} aria-label={title}
            className={clsx('shrink-0 rounded p-0.5 transition-colors', copied ? 'text-success' : 'text-gray-600 hover:text-accent', className)}>
      {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
    </button>
  );
};
