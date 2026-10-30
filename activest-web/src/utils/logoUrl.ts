/**
 * O endereço do logo de um ativo (servido pela API, nunca por terceiros). Com o tipo, pelo par (tipo, ticker): desde a
 * V13 o ticker é único só por tipo — META é cripto e ação do exterior — e os logos do exterior ficam na pasta do tipo
 * (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, P4). Sem o tipo, a rota pelo ticker.
 */
export function logoUrl(ticker: string, assetType?: string | null): string {
  return assetType
    ? `/api/v1/assets/${encodeURIComponent(assetType)}/${encodeURIComponent(ticker)}/logo`
    : `/api/v1/assets/${encodeURIComponent(ticker)}/logo`;
}
