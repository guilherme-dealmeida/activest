import { ReactNode, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { clsx } from 'clsx';
import { ArrowDownLeft, ArrowUpRight, List } from 'lucide-react';
import { portfolioApi } from '@/api/portfolio';
import { assetsApi } from '@/api/assets';
import { AssetLogo } from '@/components/ui/AssetLogo';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Switch } from '@/components/ui/Switch';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { useToast } from '@/context/ToastContext';
import { TransactionFormModal, TransactionPreset } from '@/components/transactions/TransactionFormModal';
import { useSortable } from '@/hooks/useSortable';
import { AssetType, PortfolioAssetDetail, RateType, TransactionType } from '@/types';
import { formatCurrency, formatDate, formatPercentage, formatQuantity, formatUnitPrice, getReturnColor } from '@/utils/format';
import { RENDA_FIXA, TESOURO_DIRETO, isFixedIncomeNote } from '@/utils/assetTypeRules';
import { apiMessage } from '@/utils/apiError';

/**
 * Colunas que só fazem sentido para alguns tipos de ativo.
 *
 * A regra olha a coluna fixed_income do tipo, e não uma lista de nomes, pelo
 * mesmo motivo do scheduler: tipo novo de renda fixa já nasce com vencimento.
 * A única exceção nominal é o emissor do Tesouro Direto — é sempre o Tesouro
 * Nacional, então a coluna repetiria o mesmo valor em todas as linhas.
 *
 * isFixedIncomeNote e RENDA_FIXA vivem em utils/assetTypeRules porque a tela de
 * transações aplica a mesma distinção nos filtros.
 */
const showIssuer = isFixedIncomeNote;
const showMaturity = (type: AssetType) => type.fixedIncome;
// Só o Tesouro: cada compra é um título novo, e a data da aplicação é o que
// separa duas linhas do mesmo papel (e a ordem em que a venda os consome).
const showPurchaseDate = (type: AssetType) => type.name === TESOURO_DIRETO;
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
  /** Explicação no (i) do cabeçalho. Função quando o significado muda com o tipo. */
  info?: string | ((type: AssetType) => string);
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
      // Nome do ativo só no hover (tooltip nativo), não mais abaixo do ticker.
      <div className="flex items-center gap-3" title={a.assetName || undefined}>
        {/* Logo à esquerda do ticker (B25). null = tipo sem logo (renda fixa, Tesouro). */}
        {a.hasLogo !== null && a.hasLogo !== undefined && <AssetLogo ticker={a.ticker} hasLogo={a.hasLogo} size={38} shape="square" />}
        <span className="font-medium">{a.ticker}</span>
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
    key: 'purchaseDate',
    label: 'APLICAÇÃO',
    info: 'Data de compra do título. A venda consome os títulos da aplicação mais antiga para a mais nova.',
    align: 'left',
    visible: showPurchaseDate,
    render: (a) => <span className="text-white/70">{a.purchaseDate ? formatDate(a.purchaseDate) : '—'}</span>,
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
    render: (a) => formatQuantity(a.totalShares, a.isCrypto),
  },
  {
    key: 'averagePrice',
    label: 'PREÇO MÉDIO',
    info: 'Custo médio por cota, considerando todas as compras da posição.',
    align: 'right',
    visible: showAveragePrice,
    render: (a) => formatUnitPrice(a.averagePrice, a.isCrypto),
  },
  {
    key: 'currentPrice',
    label: 'PREÇO ATUAL',
    align: 'right',
    visible: showCurrentPrice,
    render: (a) => formatUnitPrice(a.currentPrice, a.isCrypto),
  },
  {
    key: 'totalInvested',
    label: 'VALOR INICIAL',
    labelFor: (type) => (type.name === RENDA_FIXA ? 'VALOR APLICADO' : 'VALOR INICIAL'),
    // Mesmo motivo do labelFor: nos tipos por cota a venda já reduziu o custo,
    // então "a soma das compras" só é verdade na Renda Fixa.
    info: (type) => (type.name === RENDA_FIXA
      ? 'Quanto foi investido na posição: a soma das compras.'
      : 'Custo que continua na posição: o que foi investido, já descontadas as vendas pelo preço médio.'),
    align: 'right',
    render: (a) => formatCurrency(a.totalInvested),
  },
  {
    key: 'totalSold',
    label: 'RESGATADO',
    info: 'Quanto já foi resgatado desta aplicação.',
    align: 'right',
    visible: showSold,
    render: (a) => formatCurrency(a.totalSold),
  },
  {
    key: 'currentValue',
    label: 'VALOR ATUAL',
    info: 'Valor aplicado + valorização − resgatado.',
    align: 'right',
    render: (a) => formatCurrency(a.currentValue),
  },
  {
    key: 'totalDividends',
    label: 'PROVENTOS',
    info: 'Total de proventos recebidos desde o início da posição, incluindo dividendos, JCP e outras distribuições.',
    align: 'right',
    visible: showDividends,
    render: (a) => formatCurrency(a.totalDividends),
  },
  {
    key: 'valorization',
    label: 'VALORIZAÇÃO',
    info: 'Quanto a posição ganhou ou perdeu em relação ao valor aplicado.',
    align: 'right',
    render: (a) => <span className={getReturnColor(a.valorization)}>{formatCurrency(a.valorization)}</span>,
  },
  {
    key: 'returnPercentage',
    label: 'RENT. %',
    info: 'Valorização dividida pelo valor aplicado.',
    align: 'right',
    render: (a) => <span className={getReturnColor(a.returnPercentage)}>{formatPercentage(a.returnPercentage)}</span>,
  },
];

/**
 * Chave da flag que liga e desliga um grupo de posições na tabela.
 *
 * É a categoria, menos nas abas de renda fixa com emissor (CDB, LCI, LCA...),
 * onde o próprio ativo já faz o papel dela — é o mesmo motivo pelo qual a
 * coluna CATEGORIA não aparece nessas abas (showCategory).
 */
const flagKeyOf = (type: AssetType, asset: PortfolioAssetDetail): string =>
  isFixedIncomeNote(type) ? asset.ticker : asset.categoryName || 'Sem categoria';

interface FlagsProps {
  keys: string[];
  offKeys: Set<string>;
  onToggle: (key: string) => void;
}

/** Flags em fila horizontal: ON soma as posições daquele grupo à tabela, OFF as tira. */
const PortfolioFlags = ({ keys, offKeys, onToggle }: FlagsProps) => (
  <div className="flex flex-wrap items-center gap-2 pb-4">
    {keys.map((key) => {
      const on = !offKeys.has(key);
      return (
        <button
          key={key}
          type="button"
          aria-pressed={on}
          onClick={() => onToggle(key)}
          title={on ? `Ocultar ${key}` : `Mostrar ${key}`}
          className={clsx(
            'inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium transition-colors',
            on
              ? 'border-accent/40 bg-accent/15 text-accent hover:bg-accent/25'
              : 'border-white/10 bg-surface-light/40 text-gray-500 hover:text-gray-300'
          )}
        >
          <span className={clsx('h-1.5 w-1.5 rounded-full', on ? 'bg-accent' : 'bg-gray-600')} />
          {key}
        </button>
      );
    })}
  </div>
);

/**
 * A coluna RESERVA e o que ela precisa para funcionar.
 *
 * Fica fora de COLUMNS porque um render de coluna recebe só a posição, e a
 * chave precisa avisar quem está de fora que o estado mudou. pendingId desliga
 * a chave da linha que está gravando — sem isso o segundo clique sai antes da
 * primeira resposta e a tela mostra um estado que o banco não tem.
 */
interface ReservationControl {
  pendingId: string | null;
  onToggle: (asset: PortfolioAssetDetail, next: boolean) => void;
}

interface TableProps {
  type: AssetType;
  assets: PortfolioAssetDetail[];
  reservation: ReservationControl;
  onList: (asset: PortfolioAssetDetail) => void;
  onBuy: (asset: PortfolioAssetDetail) => void;
  onSell: (asset: PortfolioAssetDetail) => void;
}

const PortfolioTypeTable = ({ type, assets, reservation, onList, onBuy, onSell }: TableProps) => {
  // Regra (A): só renda fixa compõe reserva de emergência. A condição é a
  // coluna fixed_income do tipo, e não uma lista de nomes — Renda Fixa e
  // Tesouro Direto hoje, e qualquer tipo novo marcado assim amanhã.
  const showReservation = type.fixedIncome;

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
            {/* Primeira coluna, e só nas abas de renda fixa. Não ordena: o
                estado muda com um clique ali mesmo. */}
            {showReservation && (
              <th className="py-3 px-3 font-medium text-xs text-center">
                <LabelWithInfo info="Marque os títulos que fazem parte da sua reserva de emergência. Eles saem do rebalanceamento por tipo e passam a contar no bloco da reserva.">
                  RESERVA
                </LabelWithInfo>
              </th>
            )}
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
                {/* A seta de ordenação vai junto do rótulo, antes do (i): fora
                    dele ela quebrava sozinha para a linha de baixo. */}
                <LabelWithInfo info={typeof col.info === 'function' ? col.info(type) : col.info}>
                  <span>
                    {col.labelFor ? col.labelFor(type) : col.label}
                    {getSortIndicator(col.key)}
                  </span>
                </LabelWithInfo>
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
              {showReservation && (
                <td className="py-3 px-3">
                  <div className="flex items-center justify-center">
                    <Switch
                      checked={asset.isReservation}
                      disabled={reservation.pendingId === asset.id}
                      onChange={(next) => reservation.onToggle(asset, next)}
                      label={`Reserva de emergência — ${asset.ticker}`}
                      title={
                        asset.isReservation
                          ? 'Retirar da reserva de emergência'
                          : 'Incluir na reserva de emergência'
                      }
                    />
                  </div>
                </td>
              )}
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
            <td className="py-3 px-3" colSpan={labelSpan + (showReservation ? 1 : 0)}>
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

/**
 * A aba inteira: as flags e a tabela que elas alimentam.
 *
 * O estado das flags mora aqui, e não na página, porque a aba é remontada a
 * cada troca de tipo (key pelo type.id) — é o mesmo mecanismo que devolve a
 * ordenação ao padrão, e faz com que toda aba comece com tudo ligado.
 */
const PortfolioTypeSection = ({ type, assets, reservation, onList, onBuy, onSell }: TableProps) => {
  const [offKeys, setOffKeys] = useState<Set<string>>(new Set());

  // Só entram na fila as chaves que têm posição nesta aba.
  const keys = useMemo(
    () =>
      [...new Set(assets.map((a) => flagKeyOf(type, a)))].sort((a, b) => a.localeCompare(b, 'pt-BR')),
    [type, assets]
  );

  const toggle = (key: string) =>
    setOffKeys((prev) => {
      const next = new Set(prev);
      if (!next.delete(key)) next.add(key);
      return next;
    });

  // Uma categoria que saiu da carteira não pode continuar desligando nada.
  const visible = assets.filter((a) => !offKeys.has(flagKeyOf(type, a)));

  return (
    <>
      {keys.length > 0 && <PortfolioFlags keys={keys} offKeys={offKeys} onToggle={toggle} />}
      {visible.length > 0 ? (
        <PortfolioTypeTable
          type={type}
          assets={visible}
          reservation={reservation}
          onList={onList}
          onBuy={onBuy}
          onSell={onSell}
        />
      ) : (
        <p className="text-sm text-gray-400">
          {isFixedIncomeNote(type)
            ? 'Nenhum ativo selecionado. Ligue ao menos uma flag para ver as posições.'
            : 'Nenhuma categoria selecionada. Ligue ao menos uma flag para ver as posições.'}
        </p>
      )}
    </>
  );
};

export const PortfolioPage = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [activeTypeId, setActiveTypeId] = useState<number | null>(null);
  const [preset, setPreset] = useState<TransactionPreset | null>(null);
  const [pendingReservationId, setPendingReservationId] = useState<string | null>(null);

  const { data: allAssets = [], isLoading: loadingAssets } = useQuery({
    queryKey: ['portfolio', 'assets'],
    queryFn: portfolioApi.getAssets,
  });

  const { data: assetTypes = [], isLoading: loadingTypes } = useQuery<AssetType[]>({
    queryKey: ['assets', 'types'],
    queryFn: assetsApi.getTypes,
  });

  /**
   * Liga e desliga a reserva de emergência da posição.
   *
   * Manda o estado desejado — o switch já sabe para onde vai —, e não um
   * "inverta", que dependeria da ordem em que as respostas voltam.
   *
   * A estratégia também é invalidada: a reserva de emergência é um bloco
   * próprio que concorre com o resto da carteira, então marcar um CDB aqui muda
   * a aba Estratégia Reserva e as duas tabelas do rebalanceamento. Sem isso a
   * outra tela mostraria o cálculo anterior até alguém recarregar.
   */
  const reservationMutation = useMutation({
    mutationFn: ({ id, isReservation }: { id: string; isReservation: boolean }) =>
      portfolioApi.setReservation(id, isReservation),
    onMutate: ({ id }) => {
      setPendingReservationId(id);
    },
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      queryClient.invalidateQueries({ queryKey: ['strategy'] });
      showToast(
        updated.isReservation
          ? `O ${updated.ticker} selecionado agora faz parte da sua reserva de emergência`
          : `O ${updated.ticker} selecionado NÃO faz mais parte da sua reserva de emergência`,
        'success'
      );
    },
    onError: (error: unknown) => {
      showToast(apiMessage(error) ?? 'Não foi possível atualizar a reserva de emergência.', 'error');
    },
    onSettled: () => {
      setPendingReservationId(null);
    },
  });

  const reservation: ReservationControl = {
    pendingId: pendingReservationId,
    onToggle: (asset, next) => reservationMutation.mutate({ id: asset.id, isReservation: next }),
  };

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
          // key pelo tipo: trocar de aba volta a ordenação e as flags ao padrão.
          <PortfolioTypeSection
            key={activeTab.type.id}
            type={activeTab.type}
            assets={activeTab.assets}
            reservation={reservation}
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
