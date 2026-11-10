import { ReactNode, useMemo, useState } from 'react';
import { BonusCostBadge } from '@/components/notifications/BonusCostBadge';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { clsx } from 'clsx';
import { ArrowDownLeft, ArrowUpRight, ChevronDown, ChevronRight, List } from 'lucide-react';
import { portfolioApi } from '@/api/portfolio';
import { assetsApi } from '@/api/assets';
import { PositionLogo } from '@/components/ui/PositionLogo';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { Switch } from '@/components/ui/Switch';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { Modal } from '@/components/ui/Modal';
import { useToast } from '@/context/ToastContext';
import { TransactionFormModal, TransactionPreset } from '@/components/transactions/TransactionFormModal';
import { presetFromPosition } from '@/components/transactions/transactionPreset';
import { TABLE_ACTION_CLASS } from '@/components/ui/tableAction';
import { sortRows, useSortable } from '@/hooks/useSortable';
import { AssetType, PortfolioAssetDetail, RateType, TransactionType } from '@/types';
import { formatCurrency, formatDate, formatFxRate, formatMoney, formatPercentage, formatQuantity, formatUnitPrice, getReturnColor } from '@/utils/format';
import { RENDA_FIXA, TESOURO_DIRETO, isFixedIncomeNote } from '@/utils/assetTypeRules';
import { apiMessage } from '@/utils/apiError';
import { TickerLink } from '@/components/issuer/TickerLink';
import { performanceApi } from '@/api/performance';
import { ASSETS_TWR_KEY, POSITIONS_TWR_KEY, classicReturn } from '@/utils/returns';
import { PortfolioRow, assetKey, commonNoteInfo, groupTreasuryTitles } from '@/utils/portfolioGroups';
import { foreignTotals, lotTotals, portfolioTotals } from '@/utils/portfolioTotals';
import { assetText, useAssetLabel } from '@/utils/assetLabel';

/** "17,5%" — alíquota (0,175) em percentual. */
const pct = (v: number) => `${(v * 100).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`;

/**
 * Segunda linha do VALOR ATUAL na renda fixa (docs/planos/PLANO_VENDA_RENDA_FIXA.md, D5): o líquido estimado de um
 * resgate hoje. O clique abre o título aporte a aporte, com o IOF e o IR de cada um (seção 7, detalhe da posição).
 */
const EstimatedNet = ({ asset }: { asset: PortfolioAssetDetail }) => {
  const [open, setOpen] = useState(false);
  const lots = asset.lots ?? [];
  if (lots.length === 0) {
    // fundo de investimento (PLANO_FUNDOS_INVESTIMENTO.md, C10): o líquido de um resgate total hoje, sem a tabela de
    // aportes; na Previdência, o bruto (IR do plano não calculado — D21)
    return (
      <span className="text-xs text-gray-400 tabular-nums"
        title={asset.valuationNote ?? 'Líquido estimado de um resgate total hoje: bruto − IOF − IR, aporte a aporte (PEPS), já descontado o come-cotas.'}>
        {formatCurrency(asset.estimatedNet ?? 0)}
      </span>
    );
  }
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="text-xs text-gray-400 hover:text-accent hover:underline tabular-nums"
        title="Líquido estimado de um resgate hoje (sem IR e IOF). Clique para ver os aportes."
      >
        {formatCurrency(asset.estimatedNet ?? 0)}
      </button>
      <Modal
        isOpen={open}
        onClose={() => setOpen(false)}
        title={`${assetText(asset.ticker)}${asset.issuer ? ` ${asset.issuer}` : ''} — aportes`}
        size="xl"
      >
        <p className="mb-4 text-sm text-gray-400">
          Cada aporte rende e é tributado pela própria idade: o IOF até o 29º dia, o IR pela tabela regressiva
          ({asset.valuationDate ? `resgate estimado em ${formatDate(asset.valuationDate)}` : 'resgate estimado hoje'}).
        </p>
        <div className="overflow-x-auto">
          <table className="w-full text-sm tabular-nums">
            <thead className="text-xs text-gray-500">
              <tr className="border-b border-white/10">
                <th className="py-2 px-2 text-left font-medium">{asset.lots?.length === 1 && !asset.noteId ? 'Título de' : 'Aporte'}</th>
                <th className="py-2 px-2 text-right font-medium">Dias</th>
                <th className="py-2 px-2 text-right font-medium">Aplicado</th>
                <th className="py-2 px-2 text-right font-medium">Rendimento</th>
                <th className="py-2 px-2 text-right font-medium">% IOF</th>
                <th className="py-2 px-2 text-right font-medium">Alíq. IR</th>
                <th className="py-2 px-2 text-right font-medium">IR + IOF</th>
                <th className="py-2 px-2 text-right font-medium">Líquido</th>
              </tr>
            </thead>
            <tbody className="text-gray-200">
              {lots.map((l, i) => (
                <tr key={i} className="border-b border-white/5">
                  <td className="py-2 px-2">{formatDate(l.date)}</td>
                  <td className="py-2 px-2 text-right">{l.days}</td>
                  <td className="py-2 px-2 text-right">{l.applied == null ? '—' : formatCurrency(l.applied)}</td>
                  <td className="py-2 px-2 text-right">{formatCurrency(l.income)}</td>
                  <td className="py-2 px-2 text-right">{pct(l.iofRate)}</td>
                  <td className="py-2 px-2 text-right">{pct(l.irRate)}</td>
                  <td className="py-2 px-2 text-right">{formatCurrency(l.iof + l.ir)}</td>
                  <td className="py-2 px-2 text-right">{formatCurrency(l.net)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="font-semibold">
                <td className="py-2 px-2" colSpan={3}>Total (bruto {formatCurrency(asset.currentValue)})</td>
                <td className="py-2 px-2 text-right">{formatCurrency(lotTotals(lots).income)}</td>
                <td className="py-2 px-2" colSpan={2} />
                <td className="py-2 px-2 text-right">{formatCurrency(lotTotals(lots).taxes)}</td>
                <td className="py-2 px-2 text-right">{formatCurrency(asset.estimatedNet ?? 0)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </Modal>
    </>
  );
};

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
// Distribuidora: onde o título foi comprado — renda fixa e Tesouro (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, seção 7)
const showDistributor = (type: AssetType) => type.fixedIncome;
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
  /** type: a aba em que a linha está — o logo do Tesouro e da Renda Fixa é do tipo. */
  render: (asset: PortfolioAssetDetail, type: AssetType) => ReactNode;
  visible?: (type: AssetType) => boolean;
}

/** Segunda linha da RENTABILIDADE: "TWR: x%", ou "TWR: —" quando o motor não tem o número (falta de preço). */
const TwrLine = ({ value, title }: { value: number | null | undefined; title?: string }) => (
  value == null
    ? <span className="text-xs font-normal text-gray-400" title={title ?? 'TWR indisponível: falta preço em algum dia do período (detalhes na tela Rentabilidade).'}>TWR: —</span>
    : <span className={clsx('text-xs font-normal tabular-nums', getReturnColor(value))} title={title}>TWR: {formatPercentage(value)}</span>
);

/**
 * Linha de baixo em outra moeda (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md, R2 e R4): exterior na moeda do ativo,
 * cripto em dólar. O valor de cima continua em reais.
 */
const ForeignLine = ({ asset, value, maxDigits = 2, cost = false }: {
  asset: PortfolioAssetDetail; value: number | null | undefined; maxDigits?: number; cost?: boolean;
}) => {
  const fv = asset.foreignValues;
  if (!fv || value == null) return null;
  const title = cost
    ? (fv.costFxRate != null ? `Câmbio médio das compras ${formatFxRate(fv.costFxRate)}` : undefined)
    : `Câmbio ${formatFxRate(fv.fxRate)} (PTAX venda de ${formatDate(fv.rateDate ?? '')})`;
  return (
    <span className="text-xs tabular-nums text-gray-400" title={title}>
      {formatMoney(value, fv.symbol, 2, maxDigits)}
    </span>
  );
};

/**
 * Valor com sinal (VALORIZAÇÃO em dinheiro, VARIAÇÃO em %) e, embaixo, o mesmo na outra moeda quando houver
 * (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md, R2/R4; coluna VARIAÇÃO separada em 01/11/2026). Cada linha na cor do seu
 * sinal.
 */
const SignedLines = ({ main, below, title, footer = false }: {
  main: { value: number; text: string } | null;
  below?: { value: number; text: string } | null;
  title?: string;
  footer?: boolean;
}) => (
  <span className="inline-flex flex-col items-end">
    {main ? <span className={clsx('tabular-nums', getReturnColor(main.value))}>{main.text}</span> : <span className="text-gray-400">—</span>}
    {below && (
      <span className={clsx('text-xs tabular-nums', footer && 'font-normal', getReturnColor(below.value))} title={title}>
        {below.text}
      </span>
    )}
  </span>
);

/** Dica da linha na moeda: como a valorização na moeda é medida. */
const foreignTitle = (a: PortfolioAssetDetail) => a.foreignValues
  ? `Na moeda: valor atual − valor inicial (câmbio médio das compras ${a.foreignValues.costFxRate != null ? formatFxRate(a.foreignValues.costFxRate) : '—'}, hoje ${formatFxRate(a.foreignValues.fxRate)})`
  : undefined;

const COLUMNS: Column[] = [
  {
    key: 'ticker',
    label: 'ATIVO',
    align: 'left',
    render: (a, type) => (
      // Nome do ativo só no hover (tooltip nativo), não mais abaixo do ticker.
      <div className="flex items-center gap-3" title={a.assetName || undefined}>
        {/* Logo à esquerda do ticker (B25): regras em PositionLogo (tipo, emissora da RF ou o do ativo). */}
        <PositionLogo typeName={type.name} asset={a} />
        {/* Linha-mãe do Tesouro: "N títulos" embaixo do nome, alinhado com o texto (review R2) */}
        {a.groupSize
          ? (
            <div className="flex flex-col">
              <TickerLink ticker={a.ticker} issuerId={a.issuerId} />
              <span className="text-xs font-normal text-gray-400">{a.groupSize} títulos</span>
            </div>
          )
          : <TickerLink ticker={a.ticker} issuerId={a.issuerId} />}
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
    label: 'EMISSORA',
    info: 'Quem emitiu o título: o nome do grupo. A razão social aparece ao passar o mouse.',
    align: 'left',
    visible: showIssuer,
    render: (a) => (
      <span className="text-white/70" title={a.noteInfo?.issuerLegalName ?? undefined}>{a.issuer || '—'}</span>
    ),
  },
  {
    key: 'distributorName',
    label: 'DISTRIBUIDORA',
    info: 'Onde o título foi comprado. A razão social aparece ao passar o mouse.',
    align: 'left',
    visible: showDistributor,
    render: (a) => (
      <span className="text-white/70" title={a.noteInfo?.distributorLegalName ?? undefined}>
        {a.noteInfo?.distributorName || '—'}
      </span>
    ),
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
    render: (a) => (
      <span className="inline-flex items-center gap-1.5 text-white/70">
        {a.maturityDate ? formatDate(a.maturityDate) : '—'}
        {a.matured && (
          <span
            className="rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase bg-danger/15 text-danger"
            title={a.valuationBasis === 'CURVA'
              ? 'O título venceu e parou de render. Lance a liquidação com o valor que caiu na sua conta (veja as Notificações, ou o ícone Vender).'
              : 'O título venceu. Lance a liquidação (veja as Notificações, ou o ícone Vender).'}
          >
            Vencido
          </span>
        )}
      </span>
    ),
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
    // linha-mãe do Tesouro: a média dos títulos ponderada pelo valor inicial (review R1)
    render: (a) => a.groupSize
      ? <span title="Média das taxas dos títulos, ponderada pelo valor inicial de cada um.">{formatRate(a)}</span>
      : formatRate(a),
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
    render: (a) => (
      <span className="inline-flex flex-col items-end">
        <span className="inline-flex items-center justify-end gap-1">
          {formatUnitPrice(a.averagePrice, a.isCrypto)}
          <BonusCostBadge asset={a} />
        </span>
        <ForeignLine asset={a} value={a.foreignValues?.averagePrice} maxDigits={a.isCrypto ? 8 : 2} cost />
      </span>
    ),
  },
  {
    key: 'currentPrice',
    label: 'PREÇO ATUAL',
    align: 'right',
    visible: showCurrentPrice,
    // Ativo em outra moeda (PLANO_CADASTRO_MOEDAS, C4): o preço em reais e, embaixo, a cotação na moeda com o câmbio
    render: (a) => a.foreignQuote ? (
      <span className="inline-flex flex-col items-end">
        <span>{formatUnitPrice(a.currentPrice, a.isCrypto)}</span>
        <span className="text-xs text-gray-400 tabular-nums"
          title={a.foreignQuote.fxRate != null
            ? `Câmbio ${formatFxRate(a.foreignQuote.fxRate)} (PTAX venda de ${formatDate(a.foreignQuote.rateDate ?? '')})`
            : 'Sem câmbio carregado'}>
          {formatMoney(a.foreignQuote.price, a.foreignQuote.symbol, 2, 8)}
        </span>
      </span>
    ) : a.foreignValues ? (
      // Cripto (R4): o preço em reais e, embaixo, em dólar pela PTAX de hoje
      <span className="inline-flex flex-col items-end">
        <span>{formatUnitPrice(a.currentPrice, a.isCrypto)}</span>
        <ForeignLine asset={a} value={a.foreignValues.price} maxDigits={a.isCrypto ? 8 : 2} />
      </span>
    ) : formatUnitPrice(a.currentPrice, a.isCrypto),
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
    render: (a) => (
      <span className="inline-flex flex-col items-end">
        <span>{formatCurrency(a.totalInvested)}</span>
        <ForeignLine asset={a} value={a.foreignValues?.totalInvested} cost />
      </span>
    ),
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
    info: 'Valor aplicado + valorização − resgatado. Na renda fixa, valor bruto; embaixo, o líquido estimado de um '
      + 'resgate hoje (sem IR e IOF), com os aportes ao clicar.',
    align: 'right',
    render: (a) => (
      // Sem o (i) da base do valor na renda fixa (retirado a pedido em 15/10/2026): bruto e, embaixo, o líquido.
      <span className="inline-flex flex-col items-end">
        <span>{formatCurrency(a.currentValue)}</span>
        {a.estimatedNet != null && <EstimatedNet asset={a} />}
        <ForeignLine asset={a} value={a.foreignValues?.currentValue} />
      </span>
    ),
  },
  {
    key: 'totalDividends',
    label: 'PROVENTOS',
    info: 'Total de proventos recebidos desde o início da posição, incluindo dividendos, JCP e outras distribuições.',
    align: 'right',
    visible: showDividends,
    render: (a) => (
      <span className="inline-flex flex-col items-end">
        <span>{formatCurrency(a.totalDividends)}</span>
        <ForeignLine asset={a} value={a.foreignValues?.totalDividends} />
      </span>
    ),
  },
  {
    // 17/10/2026: em reais; desde 01/11/2026 a % foi para a coluna VARIAÇÃO. Embaixo, na moeda (exterior e cripto).
    key: 'valorization',
    label: 'VALORIZAÇÃO',
    info: 'Quanto a posição ganhou ou perdeu em relação ao valor aplicado, sem os proventos. No exterior e na cripto, '
      + 'embaixo, o mesmo na moeda (dólar).',
    align: 'right',
    render: (a) => (
      <SignedLines main={{ value: a.valorization, text: formatCurrency(a.valorization) }}
        below={a.foreignValues?.valorization != null
          ? { value: a.foreignValues.valorization, text: formatMoney(a.foreignValues.valorization, a.foreignValues.symbol) }
          : null}
        title={foreignTitle(a)} />
    ),
  },
  {
    // 01/11/2026: a % que ficava embaixo da VALORIZAÇÃO (a "Variação" do Investidor10). Ordena pela % em reais.
    key: 'returnPercentage',
    label: 'VARIAÇÃO',
    info: 'Valorização em percentual, sem os proventos: valorização ÷ valor aplicado (em ações, preço atual ÷ preço '
      + 'médio − 1). No exterior e na cripto, embaixo, a variação na moeda (dólar) — a diferença para a de cima é o câmbio.',
    align: 'right',
    render: (a) => a.valuationBasis === 'SEM_CURVA'
      ? <span className="text-gray-400" title={a.valuationNote ?? undefined}>—</span>
      : (
        <SignedLines main={{ value: a.returnPercentage, text: formatPercentage(a.returnPercentage) }}
          below={a.foreignValues?.returnPercentage != null
            ? { value: a.foreignValues.returnPercentage, text: formatPercentage(a.foreignValues.returnPercentage) }
            : null}
          title={foreignTitle(a)} />
      ),
  },
  {
    // 17/10/2026, antes "RESULTADO S/ INVESTIDO": a rentabilidade clássica e, embaixo, o TWR. Ordena pela clássica.
    key: 'classicReturn',
    label: 'RENTABILIDADE',
    info: 'Rentabilidade com os proventos: (valorização + proventos) ÷ valor aplicado. Depois de uma venda parcial, os '
      + 'proventos de toda a vida da posição ficam sobre o custo que restou, e a % pode parecer maior. '
      + 'Embaixo, o TWR bruto desde a primeira compra: a rentabilidade no tempo, que não depende de quando nem de '
      + 'quanto você aportou — a que se compara com CDI e Ibovespa (tela Rentabilidade).',
    align: 'right',
    render: (a) => (
      <span className="inline-flex flex-col items-end">
        {a.valuationBasis === 'SEM_CURVA' || a.classicReturn == null
          ? <span className="text-gray-400" title={a.valuationNote ?? undefined}>—</span>
          : <span className={getReturnColor(a.classicReturn)}>{formatPercentage(a.classicReturn)}</span>}
        <TwrLine value={a.twr} />
      </span>
    ),
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
  /** TWR bruto desde o início por posição (id → %); ausente = ainda carregando ou sem número. */
  twrByPosition: Map<string, number | null>;
  /** TWR bruto desde o início por ativo (assetKey → %), para a linha-mãe do Tesouro (PLANO_AGRUPAMENTO_TITULOS, G6). */
  twrByAsset: Map<string, number | null>;
  /** TWR bruto desde o início do tipo inteiro (a aba), para a linha de Totais. */
  typeTwr: number | null | undefined;
  /** Alguma flag desligada: o TWR do tipo não vale para o subconjunto (TWR não se soma). */
  partial?: boolean;
  onList: (asset: PortfolioAssetDetail) => void;
  /** Listar da linha-mãe: as transações do ativo inteiro (todos os títulos). */
  onListAsset: (ticker: string) => void;
  onBuy: (asset: PortfolioAssetDetail) => void;
  onSell: (asset: PortfolioAssetDetail) => void;
}

/** Linha da tabela já com o registro exibido (a posição, ou a soma do grupo) e a referência ao agrupamento. */
type DisplayRow = PortfolioAssetDetail & { __row: PortfolioRow<PortfolioAssetDetail> };

const PortfolioTypeTable = ({ type, assets, reservation, twrByPosition, twrByAsset, typeTwr, partial, onList, onListAsset, onBuy, onSell }: TableProps) => {
  // Regra (A): só renda fixa compõe reserva de emergência. A condição é a
  // coluna fixed_income do tipo, e não uma lista de nomes — Renda Fixa e
  // Tesouro Direto hoje, e qualquer tipo novo marcado assim amanhã.
  const showReservation = type.fixedIncome;

  // Começa ordenada por ativo (A→Z); o clique no cabeçalho continua trocando.
  // A distribuidora vem dentro de noteInfo; o campo plano é o que a ordenação pelo cabeçalho lê
  const rowList = useMemo(
    () => assets.map(a => ({
      ...a,
      distributorName: a.noteInfo?.distributorName ?? null,
      classicReturn: classicReturn(a.valorization, a.totalDividends, a.totalInvested),
      twr: twrByPosition.get(a.id) ?? null,
    })),
    [assets, twrByPosition]
  );
  // Tesouro: títulos do mesmo ativo numa linha-mãe (docs/planos/PLANO_AGRUPAMENTO_TITULOS.md); a mãe ordena pelos
  // valores somados junto com as linhas simples, e os títulos dentro dela pela mesma coluna (D2).
  const displayRows = useMemo<DisplayRow[]>(
    () => groupTreasuryTitles(type.name === TESOURO_DIRETO, rowList).map((row) => row.kind === 'group'
      ? { ...row.group, twr: twrByAsset.get(assetKey(type.description, row.group.ticker)) ?? null, __row: row }
      : { ...row.position, __row: row }),
    [type, rowList, twrByAsset]
  );
  const { sortedData, requestSort, getSortIndicator, getSortClass, sortConfig } = useSortable(displayRows, {
    key: 'ticker',
    direction: 'asc',
  });
  // Coluna do chevron só quando a aba tem linha-mãe: o logo e o nome da mãe ficam alinhados com as demais (review R2).
  const hasGroups = displayRows.some((d) => d.__row.kind === 'group');
  // D1: as mães começam recolhidas e o estado não é lembrado (a aba remonta na troca de tipo).
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const toggleGroup = (key: string) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (!next.delete(key)) next.add(key);
      return next;
    });

  const columns = COLUMNS.filter((c) => !c.visible || c.visible(type));

  // Totais (17/10/2026): as mesmas contas das linhas, sobre as somas; líquido estimado da aba (o da posição quando
  // há, senão o próprio valor). Contas em utils/portfolioTotals.ts (testadas).
  const {
    invested: totalInvested, sold: totalSold, current: totalCurrent, dividends: totalDividends, valorization: totalValorization,
    valorizationPct: totalValorizationPct, classic: totalClassic, hasNet, net: totalNet,
  } = portfolioTotals(assets);
  // R2/R4 (01/11/2026): exterior e cripto ganham a segunda linha na moeda também nos Totais
  const foreign = foreignTotals(assets);
  const foreignLine = (value: number) => foreign && (
    <span className="text-xs font-normal text-gray-400 tabular-nums">{formatMoney(value, foreign.symbol)}</span>
  );
  const withForeign = (brl: ReactNode, value: number | undefined) => foreign && value != null
    ? <span className="inline-flex flex-col items-end"><span>{brl}</span>{foreignLine(value)}</span>
    : brl;

  const footer: Partial<Record<keyof PortfolioAssetDetail, ReactNode>> = {
    totalInvested: withForeign(formatCurrency(totalInvested), foreign?.invested),
    totalSold: formatCurrency(totalSold),
    currentValue: hasNet
      ? (
        <span className="inline-flex flex-col items-end">
          <span>{formatCurrency(totalCurrent)}</span>
          <span className="text-xs font-normal text-gray-400 tabular-nums">{formatCurrency(totalNet)}</span>
        </span>
      )
      : withForeign(formatCurrency(totalCurrent), foreign?.current),
    totalDividends: withForeign(formatCurrency(totalDividends), foreign?.dividends),
    valorization: (
      <SignedLines footer main={{ value: totalValorization, text: formatCurrency(totalValorization) }}
        below={foreign ? { value: foreign.valorization, text: formatMoney(foreign.valorization, foreign.symbol) } : null} />
    ),
    returnPercentage: (
      <SignedLines footer main={{ value: totalValorizationPct, text: formatPercentage(totalValorizationPct) }}
        below={foreign ? { value: foreign.valorizationPct, text: formatPercentage(foreign.valorizationPct) } : null} />
    ),
    classicReturn: (
      <span className="inline-flex flex-col items-end">
        <span className={getReturnColor(totalClassic)}>{formatPercentage(totalClassic)}</span>
        {partial
          ? <TwrLine value={null} title="TWR do tipo inteiro: com flags desligadas ele não vale para as posições mostradas (TWR não se soma). Ligue todas as flags para vê-lo." />
          : <TwrLine value={typeTwr} title={`TWR bruto desde o início de ${type.description}, o mesmo da tela Rentabilidade.`} />}
      </span>
    ),
  };
  // "Totais" ocupa as colunas até a primeira que tem total.
  const labelSpan = columns.findIndex((c) => c.key in footer);

  /** Ícones Listar, Comprar e Vender (G3: na linha-mãe e nos títulos). */
  const actions = (list: () => void, buy: () => void, sell: () => void, listTitle: string) => (
    <td className="py-3 px-3">
      <div className="flex items-center justify-center gap-1">
        <button onClick={list} title={listTitle} className={TABLE_ACTION_CLASS}>
          <List className="w-4 h-4" />
        </button>
        <button onClick={buy} title="Comprar" className={TABLE_ACTION_CLASS}>
          <ArrowDownLeft className="w-4 h-4" />
        </button>
        <button onClick={sell} title="Vender" className={TABLE_ACTION_CLASS}>
          <ArrowUpRight className="w-4 h-4" />
        </button>
      </div>
    </td>
  );

  const reservationSwitch = (asset: PortfolioAssetDetail, target: PortfolioAssetDetail, pending: boolean, label: string) => (
    <td className="py-3 px-3">
      <div className="flex items-center justify-center">
        <Switch
          checked={asset.isReservation}
          disabled={pending}
          onChange={(next) => reservation.onToggle(target, next)}
          label={`Reserva de emergência — ${label}`}
          title={asset.isReservation ? 'Retirar da reserva de emergência' : 'Incluir na reserva de emergência'}
        />
      </div>
    </td>
  );

  /**
   * Linha de uma posição: simples, ou título dentro de uma linha-mãe do Tesouro. O título agrupado não tem interruptor
   * de reserva — a reserva do Tesouro é do ativo e fica na mãe (G7).
   */
  const renderPosition = (asset: PortfolioAssetDetail, kind: 'single' | 'child') => (
    // key pelo id da posição: o ticker não é único na carteira.
    <tr key={asset.id} className={clsx('border-b border-white/5 hover:bg-white/5 transition-colors', kind === 'child' && 'bg-white/[0.02]')}>
      {showReservation && (kind === 'single'
        ? reservationSwitch(asset, asset, reservation.pendingId === asset.id, assetText(asset.ticker))
        : <td className="py-3 px-3" />)}
      {hasGroups && <td className="py-3 px-1" />}
      {/* título agrupado sem recuo: alinhado com as outras linhas, só o fundo o distingue (review R2) */}
      {columns.map((col) => (
        <td key={col.key} className={clsx('py-3 px-3', col.align === 'right' && 'text-right')}>
          {col.render(asset, type)}
        </td>
      ))}
      {actions(() => onList(asset), () => onBuy(asset), () => onSell(asset), 'Ver as transações desta posição')}
    </tr>
  );

  /**
   * Linha-mãe (G1–G6, review R1/R2): chevron na coluna própria, "N títulos" embaixo do nome; valores somados; dados
   * comuns aos títulos e a taxa média vêm de aggregatePositions; reserva do ativo inteiro; Listar pelo ticker e
   * Comprar/Vender com a distribuidora só quando todos os títulos têm a mesma (D4).
   */
  const renderGroup = (group: PortfolioAssetDetail, key: string, children: PortfolioAssetDetail[], open: boolean) => {
    const presetBase = { ...group, noteInfo: commonNoteInfo(children) };
    const Chevron = open ? ChevronDown : ChevronRight;
    return (
      <tr key={key} className="border-b border-white/5 hover:bg-white/5 transition-colors font-medium">
        {showReservation && reservationSwitch(group, children[0],
          children.some((c) => c.id === reservation.pendingId), `${assetText(group.ticker)} (todos os títulos)`)}
        <td className="py-3 px-1">
          <button
            type="button"
            onClick={() => toggleGroup(key)}
            aria-expanded={open}
            title={open ? 'Recolher os títulos' : 'Mostrar os títulos'}
            className="rounded p-1 text-gray-400 hover:bg-white/10 hover:text-white"
          >
            <Chevron className="w-4 h-4" />
          </button>
        </td>
        {columns.map((col) => (
          <td key={col.key} className={clsx('py-3 px-3', col.align === 'right' && 'text-right')}>
            {col.render(group, type)}
          </td>
        ))}
        {actions(() => onListAsset(group.ticker), () => onBuy(presetBase), () => onSell(presetBase),
          'Ver as transações de todos os títulos deste ativo')}
      </tr>
    );
  };

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-white/10">
            {/* Primeira coluna, e só nas abas de renda fixa. Não ordena: o
                estado muda com um clique ali mesmo. */}
            {showReservation && (
              <th className="py-3 px-3 font-medium text-xs text-center">
                <LabelWithInfo info={type.name === TESOURO_DIRETO
                  ? 'Marque os ativos que fazem parte da sua reserva de emergência. No Tesouro a reserva é do ativo: vale para todos os títulos dele, inclusive as próximas compras. Eles saem do rebalanceamento por tipo e passam a contar no bloco da reserva.'
                  : 'Marque os títulos que fazem parte da sua reserva de emergência. Eles saem do rebalanceamento por tipo e passam a contar no bloco da reserva.'}>
                  RESERVA
                </LabelWithInfo>
              </th>
            )}
            {hasGroups && <th className="w-8 py-3 px-1" aria-label="Agrupamento" />}
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
          {sortedData.flatMap((display) => {
            const row = display.__row;
            if (row.kind === 'position') {
              return [renderPosition(display, 'single')];
            }
            const open = expanded.has(row.key);
            const children = sortRows(row.children, sortConfig);
            return [renderGroup(display, row.key, row.children, open), ...(open ? children.map((c) => renderPosition(c, 'child')) : [])];
          })}
        </tbody>
        <tfoot>
          <tr className="border-t border-white/10 font-semibold">
            <td className="py-3 px-3" colSpan={labelSpan + (showReservation ? 1 : 0) + (hasGroups ? 1 : 0)}>
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
const PortfolioTypeSection = ({ type, assets, reservation, twrByPosition, twrByAsset, typeTwr, onList, onListAsset, onBuy, onSell }: TableProps) => {
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
          twrByPosition={twrByPosition}
          twrByAsset={twrByAsset}
          typeTwr={typeTwr}
          partial={visible.length < assets.length}
          onList={onList}
          onListAsset={onListAsset}
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
  // nomes curtos dos fundos: redesenha quando chegam (o ticker do fundo é o CNPJ — PLANO_FUNDOS_INVESTIMENTO.md, D3)
  useAssetLabel();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  // A aba fica na URL (`?tab=<nome do tipo em minúsculas>`, ex.: `?tab=etf`; 09/11/2026): a linha do tipo no
  // Rebalanceamento abre direto a aba dele. Sem parâmetro, ou tipo sem posição, abre a primeira aba.
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab')?.toLowerCase() ?? null;
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

  // TWR bruto desde o início (2ª linha da RENTABILIDADE): por posição — mesma consulta do Top 5 do Dashboard — e por
  // tipo, para os Totais da aba. Não segura a tela: enquanto carrega, a linha mostra "TWR: —".
  const { data: positionsTwr } = useQuery({
    queryKey: POSITIONS_TWR_KEY,
    queryFn: () => performanceApi.breakdown({ range: 'MAX', basis: 'GROSS' }, 'POSITION'),
  });
  const { data: typesTwr } = useQuery({
    queryKey: ['performance', 'types', 'MAX', 'bruto'],
    queryFn: () => performanceApi.breakdown({ range: 'MAX', basis: 'GROSS' }, 'TYPE'),
  });
  const twrByPosition = useMemo(
    () => new Map((positionsTwr?.rows ?? []).filter(r => r.positionId).map(r => [r.positionId as string, r.individual])),
    [positionsTwr]
  );
  // TWR por ativo: a linha-mãe dos títulos do Tesouro (PLANO_AGRUPAMENTO_TITULOS, G6) — mesma consulta do Top 5.
  const { data: assetsTwr } = useQuery({
    queryKey: ASSETS_TWR_KEY,
    queryFn: () => performanceApi.breakdown({ range: 'MAX', basis: 'GROSS' }, 'ASSET'),
  });
  const twrByAsset = useMemo(
    () => new Map((assetsTwr?.rows ?? []).filter(r => r.ticker).map(r => [assetKey(r.typeDescription, r.ticker), r.individual])),
    [assetsTwr]
  );

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
          ? `O ${assetText(updated.ticker)} selecionado agora faz parte da sua reserva de emergência`
          : `O ${assetText(updated.ticker)} selecionado NÃO faz mais parte da sua reserva de emergência`,
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

  if (loadingAssets || loadingTypes) {
    return <LoadingSpinner />;
  }

  const activeTab = tabs.find((t) => t.type.name.toLowerCase() === tabParam) ?? tabs[0];


  return (
    <div className="space-y-6">
      {tabs.length > 0 && (
        <div className="flex flex-wrap gap-1 bg-surface p-1 rounded-lg w-fit border border-surface-light">
          {tabs.map(({ type }) => (
            <button
              key={type.id}
              onClick={() => setSearchParams({ tab: type.name.toLowerCase() }, { replace: true })}
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
            twrByPosition={twrByPosition}
            twrByAsset={twrByAsset}
            typeTwr={typesTwr?.rows.find(r => r.typeName === activeTab.type.name)?.individual}
            onList={(asset) => navigate(`/transactions?position=${asset.id}`)}
            onListAsset={(ticker) => navigate(`/transactions?ticker=${encodeURIComponent(ticker)}`)}
            onBuy={(asset) => setPreset(presetFromPosition(TransactionType.BUY, activeTab.type, asset))}
            onSell={(asset) => setPreset(presetFromPosition(TransactionType.SELL, activeTab.type, asset))}
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
