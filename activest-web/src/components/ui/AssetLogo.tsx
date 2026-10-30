import { useState } from 'react';
import { clsx } from 'clsx';
import { logoUrl } from '@/utils/logoUrl';

interface AssetLogoProps {
  ticker: string;
  /** Há logo guardado para o ativo. false (ou erro ao carregar) mostra as iniciais. */
  hasLogo: boolean;
  /** Lado em px. Padrão 24. */
  size?: number;
  /** Formato do ícone. Padrão 'circle'. */
  shape?: 'circle' | 'square';
  className?: string;
  /** Imagem do tipo em vez da do ativo (Tesouro Direto — utils/assetTypeRules, typeLogo). */
  src?: string;
  /** Texto do quadro sem logo em vez das iniciais do ticker ("RF" na Renda Fixa). */
  label?: string;
  /**
   * Nome técnico do tipo (STOCK, CRIPTO...). Com ele o logo vem pelo par (tipo, ticker): o ticker se repete entre
   * tipos (META é cripto e ação do exterior) e os do exterior ficam na pasta do tipo.
   */
  assetType?: string | null;
}

/**
 * Logo do ativo, redondo, à esquerda do ticker nas listagens da carteira
 * (docs/planos/PLANO_BRAPI.md, F7 e B25).
 *
 * A imagem vem da própria API (GET /api/v1/assets/{tipo}/{ticker}/logo, ou só {ticker} sem o tipo), que serve o
 * arquivo guardado pela rotina de logos — nunca do site de terceiros, onde
 * metade dos ícones não existe e o endereço muda.
 *
 * Sem logo, as iniciais do ticker nas cores do Activest (dourado sobre
 * #111111). O mesmo vale se a imagem falhar ao carregar.
 */
export const AssetLogo = ({ ticker, hasLogo, size = 24, shape = 'circle', className, src, label, assetType }: AssetLogoProps) => {
  const [failed, setFailed] = useState(false);
  const style = { width: size, height: size };
  const rounded = shape === 'square' ? 'rounded-none' : 'rounded-full';

  if ((hasLogo || src) && !failed) {
    return (
      <img
        src={src ?? logoUrl(ticker, assetType)}
        alt=""
        aria-hidden="true"
        loading="lazy"
        onError={() => setFailed(true)}
        style={style}
        className={clsx('shrink-0', src ? 'object-contain' : 'object-cover', rounded, className)}
      />
    );
  }

  return (
    <span
      aria-hidden="true"
      style={{ ...style, fontSize: Math.max(8, Math.round(size * 0.36)) }}
      className={clsx(
        'shrink-0 inline-flex items-center justify-center',
        rounded,
        'bg-[#111111] text-[#c9a84c] border border-[#c9a84c]/40 font-semibold leading-none',
        className
      )}
    >
      {label ?? initials(ticker)}
    </span>
  );
};

/** PETR4 → PE; 5PRE11 → 5P: as duas primeiras letras ou dígitos do ticker. */
function initials(ticker: string): string {
  return ticker.replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase();
}
