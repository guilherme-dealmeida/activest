import { assetTypeIcon } from '@/utils/assetTypeIcons';
import { useTheme } from '@/context/ThemeContext';

/**
 * Nome do tipo de ativo com o ícone da cor dele à esquerda, como o logo do ativo na Carteira (quadrado). Usado nas
 * tabelas por tipo do Rebalanceamento e da Estratégia. No tema claro, a versão de fundo transparente. Sem ícone para
 * o tipo, só o texto.
 */
export const AssetTypeName = ({ name, label, size = 24 }: {
  /** Nome interno do tipo (ACAO, ETF_INTERNACIONAL...), não a descrição. */
  name: string | null | undefined;
  /** Texto exibido: a descrição do tipo. */
  label: string;
  size?: number;
}) => {
  const { isDark } = useTheme();
  const src = assetTypeIcon(name, isDark);
  return (
    <span className="inline-flex items-center gap-2.5">
      {src && <img src={src} alt="" aria-hidden="true" style={{ width: size, height: size }} className="shrink-0 object-contain" />}
      <span>{label}</span>
    </span>
  );
};
