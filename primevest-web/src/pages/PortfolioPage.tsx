import { ReactNode, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { clsx } from 'clsx';
import { ArrowDownLeft, ArrowUpRight, List } from 'lucide-react';
import { portfolioApi } from '@/api/portfolio';
import { assetsApi } from '@/api/assets';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { TransactionFormModal, TransactionPreset } from '@/components/transactions/TransactionFormModal';
import { useSortable } from '@/hooks/useSortable';
import { AssetType, PortfolioAssetDetail, RateType, TransactionType } from '@/types';
import { formatCurrency, formatDate, formatPercentage, formatShares, getReturnColor } from '@/utils/format';

/**
 * Colunas que só fazem sentido para alguns tipos de ativo.
 *
 * A regra olha a coluna fixed_income do tipo, e não uma lista de nomes, pelo
 * mesmo motivo do scheduler: tipo novo de renda fixa já nasce com vencimento.
 * A única exceção nominal é o emissor do Tesouro Direto — é sempre o Tesouro
 * Nacional, então a coluna repetiria o mesmo valor em todas as linhas.
 */
const TESOURO_DIRETO = 'TESOURO_DIRETO';
const RENDA_FIXA = 'RENDA_FIXA';

const isFixedIncomeNote = (type: AssetType) => type.fixedIncome && type.name !== TESOURO_DIRETO;
const showIssuer = isFixedIncomeNote;
const showMaturity = (type: AssetType) => type.fixedIncome;
const showDividends = (type: AssetType) => !type.fixedIncome;
// Na renda fixa o ativo já é a categoria ("CDB — Certificado de Depósito
// Bancário"), e os dados do título entram no lugar dela.
const showCategory = (type: AssetType) => !isFixedIncomeNote(type);
// O Tesouro também guarda a remuneração na nota (copiada de asset_terms no
// lançamento), então a TAXA vale para toda renda fixa.
const showRate = (type: AssetType) => type.fixedIncome;
// REMUNERAÇÃO fica só nos títulos com emissor (CDB, LCI...). No Tesouro Direto
// o próprio papel já diz isso ("Tesouro IPCA+ 2029").
// Não há coluna de INDEXADOR em nenhuma aba: a TAXA já sai formatada com ele
// ("110,00% do CDI", "IPCA + 6,00% a.a."), e a coluna só repetiria.
const showRateType = isFixedIncomeNote;
// Preço médio só existe onde há cotas negociadas a um preço unitário. Renda
// Fixa é lançada por valor total (average_price nulo) e no Tesouro Direto o
// que importa é a taxa contratada, não o PU médio.
const showAveragePrice = (type: AssetType) => !type.fixedIncome;
// Cotas idem: Renda Fixa é lançada por valor total e não tem quantidade. O
// Tesouro Direto mantém a coluna — o título é negociado em frações.
const showShares = (type: AssetType) => !isFixedIncomeNote(type);
// Preço atual é a cotação de mercado (BrAPI, CoinGecko). Renda fixa e Tesouro
// não têm cotação: ali o número seria só custo + valorização dividido por cotas.
const showCurrentPrice = (type: AssetType) => !type.fixedIncome;
// Resgatado só existe onde a venda tira dinheiro sem tirar cotas: Renda Fixa.
const showSold = (type: AssetType) => type.name === RENDA_FIXA;

const RATE_TYPE_LABELS: Record<RateType, string> = {
  PREFIXADO: 'Prefixado',
  POS_FIXADO: 'Pós-fixado',
  HIBRIDO: 'Híbrido',
};

/** "12,50% a.a." no prefixado, "110,00% do CDI" no pós, "IPCA + 6,00% a.a." no híbrido. */
const formatRate = (a: PortfolioAssetDetail): string => {
  if (a.indexerRate == null || !a.rateType) return '—';
  const rate = formatPercentage(a.indexerRate);
  switch (a.rateType) {
    case 'PREFIXADO':
      return `${rate} a.a.`;
    case 'POS_FIXADO':
      return a.indexer ? `${rate} do ${a.indexer}` : rate;
    case 'HIBRIDO':
      return a.indexer ? `${a.indexer} + ${rate} a.a.` : `${rate} a.a.`;
  }
};

interface Column {
  key: keyof PortfolioAssetDetail;
  label: string;
  /**
   * Rótulo que depende do tipo. Existe por causa do VALOR INICIAL: nos tipos
   * por cota ele é o custo que ainda está na posição, já reduzido pelas vendas;
   * na Renda Fixa é o valor aplicado, a soma das compras, e o resgate aparece
   * em coluna própria. Mesmo número, significados diferentes — chamar os dois
   * de "valor inicial" convidaria a comparar um com o outro.
   */
  labelFor?: (type: AssetType) => string;
  align: 'left' | 'right';
  render: (asset: PortfolioAssetDetail) => ReactNode;
  visible?: (type: AssetType) => boolean;
}

const COLUMNS: Column[] = [
  {
    key: 'ticker',
    label: 'ATIVO',
    align: 'left',
    render: (a) => (
      <div className="flex flex-col">
        <span className="font-medium">{a.ticker}</span>
        <span className="text-xs text-white/50">{a.assetName}</span>
      </div>
    ),
  },
  {
    key: 'categoryName',
    label: 'CATEGORIA',
    align: 'left',
    visible: showCategory,
    render: (a) => a.categoryName,
  },
  // A posição é por título: três CDBs de emissores diferentes são três linhas,
  // todas "CDB". Emissor e vencimento são o que as distingue.
  {
    key: 'issuer',
    label: 'EMISSOR',
    align: 'left',
    visible: showIssuer,
    render: (a) => <span className="text-white/70">{a.issuer || '—'}</span>,
  },
  {
    key: 'maturityDate',
    label: 'VENCIMENTO',
    align: 'left',
    visible: showMaturity,
    render: (a) => <span className="text-white/70">{a.maturityDate ? formatDate(a.maturityDate) : '—'}</span>,
  },
  // Dados do título, logo depois do vencimento.
  {
    key: 'rateType',
    label: 'REMUNERAÇÃO',
    align: 'left',
    visible: showRateType,
    render: (a) => <span className="text-white/70">{a.rateType ? RATE_TYPE_LABELS[a.rateType] : '—'}</span>,
  },
  {
    key: 'indexerRate',
    label: 'TAXA',
    align: 'right',
    visible: showRate,
    render: (a) => formatRate(a),
  },
  {
    key: 'totalShares',
    label: 'COTAS',
    align: 'right',
    visible: showShares,
    render: (a) => formatShares(a.totalShares),
  },
  {
    key: 'averagePrice',
    label: 'PREÇO MÉDIO',
    align: 'right',
    visible: showAveragePrice,
    render: (a) => formatCurrency(a.averagePrice),
  },
  {
    key: 'currentPrice',
    label: 'PREÇO ATUAL',
    align: 'right',
    visible: showCurrentPrice,
    render: (a) => formatCurrency(a.currentPrice),
  },
  {
    key: 'totalInvested',
    label: 'VALOR INICIAL',
    labelFor: (type) => (type.name === RENDA_FIXA ? 'VALOR APLICADO' : 'VALOR INICIAL'),
    align: 'right',
    render: (a) => formatCurrency(a.totalInvested),
  },
  {
    key: 'totalSold',
    label: 'RESGATADO',
    align: 'right',
    visible: showSold,
    render: (a) => formatCurrency(a.totalSold),
  },
  { key: 'currentValue', label: 'VALOR ATUAL', align: 'right', render: (a) => formatCurrency(a.currentValue) },
  {
    key: 'totalDividends',
    label: 'PROVENTOS',
    align: 'right',
    visible: showDividends,
    render: (a) => formatCurrency(a.totalDividends),
  },
  {
    key: 'valorization',
    label: 'VALORIZAÇÃO',
    align: 'right',
    render: (a) => <span className={getReturnColor(a.valorization)}>{formatCurrency(a.valorization)}</span>,
  },
  {
    key: 'returnPercentage',
    label: 'RENT. %',
    align: 'right',
    render: (a) => <span className={getReturnColor(a.returnPercentage)}>{formatPercentage(a.returnPercentage)}</span>,
  },
];

interface TableProps {
  type: AssetType;
  assets: PortfolioAssetDetail[];
  onList: (asset: PortfolioAssetDetail) => void;
  onBuy: (asset: PortfolioAssetDetail) => void;
  onSell: (asset: PortfolioAssetDetail) => void;
}

const PortfolioTypeTable = ({ type, assets, onList, onBuy, onSell }: TableProps) => {
  // Começa ordenada por ativo (A→Z); o clique no cabeçalho continua trocando.
  const { sortedData, requestSort, getSortIndicator, getSortClass } = useSortable(assets, {
    key: 'ticker',
    direction: 'asc',
  });

  const columns = COLUMNS.filter((c) => !c.visible || c.visible(type));

  const totalInvested = assets.reduce((sum, a) => sum + a.totalInvested, 0);
  const totalSold = assets.reduce((sum, a) => sum + a.totalSold, 0);
  const totalCurrent = assets.reduce((sum, a) => sum + a.currentValue, 0);
  const totalDividends = assets.reduce((sum, a) => sum + a.totalDividends, 0);
  const totalValorization = assets.reduce((sum, a) => sum + a.valorization, 0);
  const totalReturn =
    totalInvested > 0 ? ((totalCurrent + totalDividends - totalInvested) / totalInvested) * 100 : 0;

  const footer: Partial<Record<keyof PortfolioAssetDetail, ReactNode>> = {
    totalInvested: formatCurrency(totalInvested),
    totalSold: formatCurrency(totalSold),
    currentValue: formatCurrency(totalCurrent),
    totalDividends: formatCurrency(totalDividends),
    valorization: <span className={getReturnColor(totalValorization)}>{formatCurrency(totalValorization)}</span>,
    returnPercentage: <span className={getReturnColor(totalReturn)}>{formatPercentage(totalReturn)}</span>,
  };
  // "Totais" ocupa as colunas até a primeira que tem total.
  const labelSpan = columns.findIndex((c) => c.key in footer);

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-white/10">
            {columns.map((col) => (
              <th
                key={col.key}
                className={clsx(
                  'py-3 px-3 font-medium text-xs cursor-pointer',
                  col.align === 'right' ? 'text-right' : 'text-left',
                  getSortClass(col.key)
                )}
                onClick={() => requestSort(col.key)}
              >
                {col.labelFor ? col.labelFor(type) : col.label}
                {getSortIndicator(col.key)}
              </th>
            ))}
            {/* Ações não ordena: não é dado da posição. */}
            <th className="py-3 px-3 font-medium text-xs text-center">AÇÕES</th>
          </tr>
        </thead>
        <tbody>
          {sortedData.map((asset) => (
            // key pelo id da posição: o ticker não é único na carteira.
            <tr key={asset.id} className="border-b border-white/5 hover:bg-white/5 transition-colors">
              {columns.map((col) => (
                <td key={col.key} className={clsx('py-3 px-3', col.align === 'right' && 'text-right')}>
                  {col.render(asset)}
                </td>
              ))}
              <td className="py-3 px-3">
                <div className="flex items-center justify-center gap-1">
                  <button
                    onClick={() => onList(asset)}
                    title="Ver as transações desta posição"
                    className="text-gray-500 hover:text-accent transition-colors p-1"
                  >
                    <List className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => onBuy(asset)}
                    title="Comprar"
                    className="text-gray-500 hover:text-success transition-colors p-1"
                  >
                    <ArrowDownLeft className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => onSell(asset)}
                    title="Vender"
                    className="text-gray-500 hover:text-danger transition-colors p-1"
                  >
                    <ArrowUpRight className="w-4 h-4" />
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr className="border-t border-white/10 font-semibold">
            <td className="py-3 px-3" colSpan={labelSpan}>
              Totais
            </td>
            {columns.slice(labelSpan).map((col) => (
              <td key={col.key} className="py-3 px-3 text-right">
                {footer[col.key]}
              </td>
            ))}
            <td className="py-3 px-3" />
          </tr>
        </tfoot>
      </table>
    </div>
  );
};

export const PortfolioPage = () => {
  const navigate = useNavigate();
  const [activeTypeId, setActiveTypeId] = useState<number | null>(null);
  const [preset, setPreset] = useState<TransactionPreset | null>(null);

  const { data: allAssets = [], isLoading: loadingAssets } = useQuery({
    queryKey: ['portfolio', 'assets'],
    queryFn: portfolioApi.getAssets,
  });

  const { data: assetTypes = [], isLoading: loadingTypes } = useQuery<AssetType[]>({
    queryKey: ['assets', 'types'],
    queryFn: assetsApi.getTypes,
  });

  // Uma aba por tipo que tem posição, na ordem de cadastro de asset_types.
  // O findAll do backend não garante ordem, por isso o sort por id aqui.
  // A posição chega com typeName = description do tipo (convenção dos DTOs).
  const tabs = useMemo(
    () =>
      [...assetTypes]
        .sort((a, b) => a.id - b.id)
        .map((type) => ({ type, assets: allAssets.filter((a) => a.typeName === type.description) }))
        .filter((tab) => tab.assets.length > 0),
    [assetTypes, allAssets]
  );

  useEffect(() => {
    if (tabs.length > 0 && !tabs.some((t) => t.type.id === activeTypeId)) {
      setActiveTypeId(tabs[0].type.id);
    }
  }, [tabs, activeTypeId]);

  if (loadingAssets || loadingTypes) {
    return <LoadingSpinner />;
  }

  const activeTab = tabs.find((t) => t.type.id === activeTypeId) ?? tabs[0];

  /**
   * O que o modal já sabe ao abrir pela carteira.
   *
   * O título só vai junto na Renda Fixa: lá a linha É uma aplicação, e comprar
   * mais significa aportar nela. No Tesouro cada compra é um título novo — a
   * taxa é a do dia — e a venda é do ativo, com os títulos escolhidos por FIFO.
   */
  const presetOf = (operation: TransactionType, type: AssetType, asset: PortfolioAssetDetail): TransactionPreset => ({
    operation,
    assetType: type,
    ticker: asset.ticker,
    note: isFixedIncomeNote(type) && asset.noteId
      ? {
          noteId: asset.noteId,
          issuer: asset.issuer,
          maturityDate: asset.maturityDate,
          rateType: asset.rateType,
          indexer: asset.indexer,
          indexerRate: asset.indexerRate,
        }
      : null,
  });

  return (
    <div className="space-y-6">
      {tabs.length > 0 && (
        <div className="flex flex-wrap gap-1 bg-surface p-1 rounded-lg w-fit border border-surface-light">
          {tabs.map(({ type }) => (
            <button
              key={type.id}
              onClick={() => setActiveTypeId(type.id)}
              className={clsx(
                'px-4 py-2 text-sm font-medium rounded-md transition-colors',
                activeTab?.type.id === type.id
                  ? 'bg-surface-light text-white'
                  : 'text-gray-400 hover:text-white hover:bg-surface-light/50'
              )}
            >
              {type.description}
            </button>
          ))}
        </div>
      )}

      <div className="bg-surface rounded-lg border border-white/10 p-6">
        {activeTab ? (
          // key pelo tipo: trocar de aba volta a ordenação para o padrão.
          <PortfolioTypeTable
            key={activeTab.type.id}
            type={activeTab.type}
            assets={activeTab.assets}
            onList={(asset) => navigate(`/transactions?position=${asset.id}`)}
            onBuy={(asset) => setPreset(presetOf(TransactionType.BUY, activeTab.type, asset))}
            onSell={(asset) => setPreset(presetOf(TransactionType.SELL, activeTab.type, asset))}
          />
        ) : (
          <p className="text-sm text-gray-400">Nenhum ativo na carteira.</p>
        )}
      </div>

      <TransactionFormModal
        isOpen={!!preset}
        onClose={() => setPreset(null)}
        preset={preset}
        assetTypes={assetTypes}
      />
    </div>
  );
};
