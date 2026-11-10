import { AssetLogo } from '@/components/ui/AssetLogo';
import { PortfolioAssetDetail } from '@/types';
import { RENDA_FIXA, typeLogo } from '@/utils/assetTypeRules';

/**
 * Logo de uma posição da carteira, com as regras da Carteira (B25): Renda Fixa pelo grupo da emissora quando há (D5),
 * senão o "RF" do tipo; Tesouro e fundos pelo logo do tipo; os demais pelo logo do ativo (null = tipo sem logo).
 * Usado na Carteira e no Top 5 do Dashboard (docs/planos/PLANO_AGRUPAMENTO_TITULOS.md, review R3).
 */
export const PositionLogo = ({ typeName, asset, size = 32 }: {
  /** Nome interno do tipo (TESOURO_DIRETO, RENDA_FIXA...), não a descrição. */
  typeName: string | null | undefined;
  asset: Pick<PortfolioAssetDetail, 'ticker' | 'hasLogo' | 'noteInfo'>;
  size?: number;
}) => {
  const logoOfType = typeLogo(typeName);
  if (typeName === RENDA_FIXA && asset.noteInfo?.issuerLogoUrl) {
    return <AssetLogo ticker={asset.ticker} hasLogo src={asset.noteInfo.issuerLogoUrl} label="RF" size={size} shape="square" />;
  }
  if (logoOfType) {
    return <AssetLogo ticker={asset.ticker} hasLogo={!!logoOfType.src} src={logoOfType.src} label={logoOfType.label} size={size} shape="square" />;
  }
  if (asset.hasLogo === null || asset.hasLogo === undefined) return null;
  return <AssetLogo ticker={asset.ticker} hasLogo={asset.hasLogo} assetType={typeName ?? undefined} size={size} shape="square" />;
};
