import React, { useCallback, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowDownLeft, ArrowUpRight, Receipt, RotateCcw, Save, X } from 'lucide-react';
import { transactionsApi } from '@/api/transactions';
import { assetsApi } from '@/api/assets';
import { portfolioApi } from '@/api/portfolio';
import { currenciesApi } from '@/api/currencies';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { CurrencyInput } from '@/components/ui/CurrencyInput';
import { MaskedNumberInput } from '@/components/ui/MaskedNumberInput';
import { Modal } from '@/components/ui/Modal';
import { Popover } from '@/components/ui/Popover';
import { InfoTooltip, LabelWithInfo } from '@/components/ui/InfoTooltip';
import { useToast } from '@/context/ToastContext';
import { apiMessage } from '@/utils/apiError';
import { AssetTypeSelect } from '@/components/forms/AssetTypeSelect';
import { TickerAutocomplete } from '@/components/forms/TickerAutocomplete';
import { FundAutocomplete } from '@/components/forms/FundAutocomplete';
import { fundsApi } from '@/api/funds';
import { assetLabel } from '@/utils/assetLabel';
import { FixedIncomeFields, FixedIncomeValues } from '@/components/forms/FixedIncomeFields';
import { IssuerPick, distributorPickOf, issuerPickOf } from '@/components/forms/issuerPick';
import { DistributorChangeModal } from '@/components/transactions/DistributorChangeModal';
import { formatCurrency, formatDate, formatMoney, toLocalISODate } from '@/utils/format';
import { fullRedemptionAmount, fundShares as fundSharesOf, transactionTotal } from '@/utils/transactionAmounts';
import { CRYPTO_DECIMALS, isCryptoType, isFundType } from '@/utils/assetTypeRules';
import {
  Asset, AssetType, DistributorChangePreview, Indexer, NoteInfo, RateType, SalePreview, TransactionCreate,
  TransactionResponse, TransactionType
} from '@/types';

/**
 * Tesouro e Renda Fixa são comparados por nome aqui (e cripto em
 * utils/assetTypeRules). No Tesouro Direto a família
 * do papel já está no próprio nome do título, e o saldo é do ativo — não do
 * título —, o que muda o formulário inteiro. Se surgir outro tipo com a mesma
 * característica, o lugar certo é uma coluna em asset_types, como
 * price_source e fixed_income.
 */
const TESOURO_DIRETO = 'TESOURO_DIRETO';

/** Renda Fixa é lançada por valor total, sem quantidade nem preço unitário. */
const RENDA_FIXA = 'RENDA_FIXA';

/** Liga os botões do rodapé do Modal ao <form>, que fica na área que rola. */
const FORM_ID = 'transaction-form';

const EMPTY_FIXED_INCOME: FixedIncomeValues = {
  assetCategoryId: null, issuer: null, distributor: null, paperCode: '', maturityDate: '',
  rateType: null, indexer: null, indexerRate: null
};

/**
 * O que a tela já sabe quando abre o modal por um dos ícones de ação (Comprar
 * e Vender, na Carteira e em Transações).
 *
 * Operação, tipo e ativo vêm travados: a linha clicada já respondeu essas três
 * perguntas, e deixá-las editáveis criaria um lançamento em outra posição sem
 * o usuário perceber.
 */
export interface TransactionPreset {
  operation: TransactionType;
  assetType: AssetType;
  ticker: string;
  /**
   * Emissora, distribuidora e código do título da linha clicada. Na Renda Fixa preenchem o quadro do título; no
   * Tesouro a distribuidora da linha já vem escolhida na compra e na venda.
   */
  noteInfo?: NoteInfo | null;
  /**
   * O título da posição. Vai no payload como noteId, e é isso que faz a compra
   * cair na MESMA aplicação em vez de criar outra linha na carteira.
   *
   * Ausente no Tesouro Direto: lá cada compra é um título novo e a venda é do
   * ativo, com os títulos escolhidos por FIFO.
   */
  note?: {
    noteId: string;
    issuer: string | null;
    maturityDate: string | null;
    rateType: RateType | null;
    indexer: Indexer | null;
    indexerRate: number | null;
    /** Saldo atual do título (curva), para o "resgatar tudo". */
    currentValue?: number | null;
    /** Líquido estimado do título hoje (bruto − IR − IOF): o valor do "resgatar tudo" (PLANO_VENDA_RENDA_FIXA, R3). */
    estimatedNet?: number | null;
  } | null;
  /**
   * Valores iniciais do formulário. O link "Lançar liquidação" da mensagem de título vencido (D6) abre a venda com a
   * data do vencimento, a liquidação total marcada e o líquido estimado (RF) ou as cotas e o PU oficial (Tesouro).
   */
  defaults?: {
    transactionDate?: string;
    totalAmount?: number;
    shares?: number;
    pricePerShare?: number;
    fullSettlement?: boolean;
  } | null;
}

/** "17,5%" — alíquota (0,175) em percentual. */
const pct = (v: number | null | undefined) =>
  v == null ? '—' : `${(v * 100).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`;

const SALE_RULE_LABEL: Record<string, string> = {
  PROPORCIONAL: 'proporcional entre os aportes',
  UEPS: 'último aporte primeiro (UEPS)',
  PEPS: 'primeiro aporte primeiro (PEPS)',
};

/**
 * Prévia da venda: bruto, IOF, IR e líquido, o líquido disponível (Renda Fixa) e os lotes — os aportes do título na
 * data (RF) ou as partes da venda por título (Tesouro). Desde 17/10/2026 abre num popover pelo ícone ao lado do
 * Líquido (Total bruto, no Tesouro); o erro do cálculo e o aviso de "passa do disponível" continuam no formulário.
 */
const SalePreviewPanel = ({ preview, loading, treasury, fee, fund = false }: {
  preview: SalePreview | undefined; loading: boolean; treasury: boolean; fee: number; fund?: boolean;
}) => {
  if (!preview) {
    return <p className="text-gray-400">{loading ? 'Calculando IR e IOF…' : 'Preencha a venda para ver a prévia.'}</p>;
  }
  const row = (labelText: string, amount: number | null | undefined, highlight = false) => (
    <div className="flex items-baseline justify-between gap-4">
      <span className={highlight ? 'text-gray-200' : 'text-gray-400'}>{labelText}</span>
      <span className={`tabular-nums ${highlight ? 'font-semibold text-white' : 'text-gray-200'}`}>
        {amount == null ? '—' : formatCurrency(amount)}
      </span>
    </div>
  );
  return (
    <div className={loading ? 'opacity-60' : ''}>
      <p className="mb-3 text-xs text-gray-400">
        {fund
          ? `O valor digitado é o bruto. IR e IOF calculados aporte a aporte, do mais antigo para o mais novo (PEPS), sobre o rendimento (cota do resgate − cota da aplicação), já descontado o come-cotas retido.${preview.reason ? ` ${preview.reason}` : ''}`
          : treasury
          ? 'IR e IOF calculados por título, do mais antigo para o mais novo, sobre o rendimento (PU de venda − PU de compra). O líquido desconta também as taxas.'
          : `O valor digitado é o líquido. O Activest calcula o bruto pela curva, tirando dos aportes pela regra de saída (${SALE_RULE_LABEL[preview.saleRule] ?? preview.saleRule}), com o IOF e o IR de cada aporte.`}
      </p>
      <div className="grid gap-1">
        {row('Bruto', preview.gross)}
        {row('IOF', preview.iof)}
        {row('IR', preview.ir)}
        {treasury && fee > 0 && row('Taxas', fee)}
        {!!preview.adjustment && row('Ajuste sobre a curva (no bruto)', preview.adjustment)}
        {row('Líquido', preview.net, true)}
        {!treasury && !fund && row('Líquido disponível estimado na data', preview.availableNet)}
      </div>
      {preview.lots.length > 0 && (
        <table className="mt-3 w-full text-xs tabular-nums">
          <thead className="text-gray-500">
            <tr>
              <th className="text-left font-normal">{treasury ? 'Título de' : 'Aporte'}</th>
              <th className="text-right font-normal">Dias</th>
              <th className="text-right font-normal">{treasury ? 'Bruto' : 'Saldo bruto'}</th>
              <th className="text-right font-normal">IOF</th>
              <th className="text-right font-normal">IR</th>
            </tr>
          </thead>
          <tbody className="text-gray-300">
            {preview.lots.map((l, i) => (
              <tr key={i}>
                <td>{formatDate(l.date)}</td>
                <td className="text-right">{l.days}</td>
                <td className="text-right">{formatCurrency(l.balance)}</td>
                <td className="text-right" title={pct(l.iofRate)}>{formatCurrency(l.iof)}</td>
                <td className="text-right" title={pct(l.irRate)}>{formatCurrency(l.ir)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

/** "110% do CDI", "CDI + 2%", "12,5% a.a." — rótulo curto da remuneração na lista de títulos. */
const rateLabel = (rateType: RateType | null, indexer: Indexer | null, rate: number | null) => {
  if (rate == null || !rateType) return '';
  const n = rate.toLocaleString('pt-BR', { maximumFractionDigits: 3 });
  if (rateType === 'PREFIXADO') return `${n}% a.a.`;
  if (rateType === 'POS_FIXADO') return `${n}% do ${indexer ?? ''}`;
  return `${indexer ?? ''} + ${n}%`;
};

interface Props {
  isOpen: boolean;
  onClose: () => void;
  /** Edição de uma transação existente. */
  editing?: TransactionResponse | null;
  /** Abertura pelos ícones Comprar/Vender (Carteira ou Transações), com a posição já escolhida. */
  preset?: TransactionPreset | null;
  /** Lista de tipos, para reconstruir o tipo da transação editada. */
  assetTypes?: AssetType[];
  /**
   * Depois de salvar (criação ou edição). A tela de Transações usa para filtrar
   * o extrato na posição lançada; a Carteira não passa nada.
   */
  onSaved?: (saved: TransactionResponse | undefined, txData: TransactionCreate) => void;
}

/**
 * O quadro do título como o catálogo o deixa num título NOVO: categoria e, no
 * que for público do papel, vencimento e remuneração. Emissor e taxa são do
 * usuário. É o estado de "nenhum título escolhido" numa compra.
 */
const catalogTerms = (asset: Asset | null): FixedIncomeValues => asset ? {
  assetCategoryId: asset.categoryId ?? null,
  issuer: null,
  distributor: null,
  paperCode: '',
  maturityDate: asset.maturityDate ?? '',
  rateType: asset.rateType ?? null,
  indexer: asset.indexer ?? null,
  indexerRate: null
} : EMPTY_FIXED_INCOME;

/**
 * O formulário de transação, usado por três caminhos: Nova Transação, edição
 * pelo extrato e os ícones Comprar e Vender da carteira.
 *
 * <p>Os três são o mesmo formulário com graus diferentes de travamento, e é
 * por isso que ele virou componente: duplicar essa tela significaria manter
 * duas vezes as regras de Renda Fixa, de Tesouro e de título.</p>
 */
export const TransactionFormModal = ({ isOpen, onClose, editing, preset, assetTypes, onSaved }: Props) => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  const [selectedType, setSelectedType] = useState<AssetType | null>(null);
  const [matchedAsset, setMatchedAsset] = useState<Asset | null>(null);
  /** Fundo: a cota foi digitada (ou é a da edição) — a da CVM não a substitui mais. */
  const [fundQuoteTouched, setFundQuoteTouched] = useState(false);
  const [tickerInput, setTickerInput] = useState('');
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null);
  const [fixedIncome, setFixedIncome] = useState<FixedIncomeValues>(EMPTY_FIXED_INCOME);
  const [fullSettlement, setFullSettlement] = useState(false);
  /** Renda Fixa pela Nova Transação: o título escolhido na lista — na venda é obrigatório, na compra é opcional. */
  const [pickedNoteId, setPickedNoteId] = useState<string | null>(null);
  /**
   * Câmbio do ativo cotado em outra moeda (docs/planos/PLANO_CADASTRO_MOEDAS.md, D5/C2): a PTAX de venda da data
   * pré-preenchida; editado pelo usuário, fica o informado até ele voltar ao padrão (↻). Ativo em reais não tem câmbio.
   */
  const [fxRate, setFxRate] = useState<number | null>(null);
  const [fxInformed, setFxInformed] = useState(false);
  const [formData, setFormData] = useState<TransactionCreate>({
    ticker: '',
    type: TransactionType.BUY,
    shares: 0,
    pricePerShare: 0,
    totalAmount: 0,
    brokerageFee: 0,
    transactionDate: toLocalISODate()
  });

  const resetForm = useCallback(() => {
    setSelectedType(null);
    setMatchedAsset(null);
    setTickerInput('');
    setEditingNoteId(null);
    setFixedIncome(EMPTY_FIXED_INCOME);
    setFullSettlement(false);
    setPickedNoteId(null);
    setFxRate(null);
    setFxInformed(false);
    setFormData({
      ticker: '', type: TransactionType.BUY, shares: 0,
      pricePerShare: 0, totalAmount: 0, brokerageFee: 0,
      transactionDate: toLocalISODate()
    });
  }, []);

  /**
   * Cada abertura reconstrói o estado inicial.
   *
   * O tipo de ativo comanda o resto da tela — decide o que o autocomplete
   * busca, se o valor é total ou quantidade x preço e se o quadro do título
   * aparece —, então ele é a primeira coisa a entrar.
   */
  useEffect(() => {
    if (!isOpen) return;

    if (editing) {
      setTickerInput(editing.ticker);
      setSelectedType((assetTypes || []).find(t => t.id === editing.assetTypeId) ?? null);
      setMatchedAsset(null);
      setEditingNoteId(editing.noteId ?? null);
      setFullSettlement(editing.isFullSettlement);
      setFormData({
        ticker: editing.ticker,
        type: editing.type as TransactionType,
        // Numa venda repartida por título, o que se edita é a VENDA: a
        // quantidade e as taxas do grupo, não as da linha clicada.
        shares: editing.groupShares ?? editing.shares ?? 0,
        // Ativo em outra moeda: reabre com o que foi lançado na moeda dele (os campos da transação estão em reais)
        pricePerShare: editing.currency?.originalPricePerShare ?? editing.pricePerShare ?? 0,
        // Venda de Renda Fixa: o que se digita é o LÍQUIDO; o bruto é calculado (PLANO_VENDA_RENDA_FIXA, R3).
        // Fundo de investimento: o valor digitado é o bruto (D24); IR e IOF são calculados (PLANO_FUNDOS_INVESTIMENTO).
        totalAmount: (isFundType((assetTypes || []).find(t => t.id === editing.assetTypeId))
          ? editing.totalAmount : editing.netAmount) ?? editing.totalAmount ?? 0,
        brokerageFee: editing.currency ? (editing.currency.originalFee ?? 0)
          : (editing.groupBrokerageFee ?? editing.brokerageFee ?? 0),
        transactionDate: editing.transactionDate
      });
      setFundQuoteTouched(true); // edição: a cota gravada fica, não é trocada pela da CVM
      setFxRate(editing.currency?.fxRate ?? null);
      setFxInformed(editing.currency?.fxRateOrigin === 'INFORMED');
      // O que está no quadro é o que foi CONTRATADO, e isso mora no título —
      // não no catálogo. Um ajuste posterior em asset_terms não deve reescrever
      // uma posição já registrada.
      setFixedIncome(editing.noteId ? {
        assetCategoryId: null,
        issuer: issuerPickOf(editing.noteInfo),
        distributor: distributorPickOf(editing.noteInfo),
        paperCode: editing.noteInfo?.paperCode ?? '',
        maturityDate: editing.maturityDate ?? '',
        rateType: editing.rateType ?? null,
        indexer: editing.indexer ?? null,
        indexerRate: editing.indexerRate ?? null
      } : { ...EMPTY_FIXED_INCOME, distributor: distributorPickOf(editing.noteInfo) });
      return;
    }

    if (preset) {
      setSelectedType(preset.assetType);
      setTickerInput(preset.ticker);
      setMatchedAsset(null);
      setEditingNoteId(preset.note?.noteId ?? null);
      setFullSettlement(preset.defaults?.fullSettlement ?? false);
      setPickedNoteId(null);
      setFxRate(null);
      setFxInformed(false);
      setFormData({
        ticker: preset.ticker,
        type: preset.operation,
        shares: preset.defaults?.shares ?? 0,
        pricePerShare: preset.defaults?.pricePerShare ?? 0,
        totalAmount: preset.defaults?.totalAmount ?? 0,
        brokerageFee: 0,
        transactionDate: preset.defaults?.transactionDate ?? toLocalISODate()
      });
      setFixedIncome(preset.note ? {
        assetCategoryId: null,
        issuer: issuerPickOf(preset.noteInfo),
        distributor: distributorPickOf(preset.noteInfo),
        paperCode: preset.noteInfo?.paperCode ?? '',
        maturityDate: preset.note.maturityDate ?? '',
        rateType: preset.note.rateType ?? null,
        indexer: preset.note.indexer ?? null,
        indexerRate: preset.note.indexerRate ?? null
      } : { ...EMPTY_FIXED_INCOME, distributor: distributorPickOf(preset.noteInfo) });
      return;
    }

    resetForm();
  }, [isOpen, editing, preset, assetTypes, resetForm]);

  /**
   * O ativo diz o que a transação não sabe: a categoria e quais campos são
   * parâmetros públicos do papel (Tesouro) e portanto não se digitam. Falhar
   * aqui deixa o quadro editável, o que não impede o lançamento.
   */
  useEffect(() => {
    if (!isOpen) return;
    const ticker = editing?.ticker ?? preset?.ticker;
    if (!ticker) return;
    let cancelled = false;
    assetsApi.getByTicker(ticker)
      .then((asset: Asset) => {
        if (cancelled) return;
        setMatchedAsset(asset);
        setFixedIncome(prev => ({
          ...prev,
          assetCategoryId: asset?.categoryId ?? null,
          // No Tesouro, prazo e remuneração são públicos: uma COMPRA nova herda
          // do catálogo, porque ela é um título novo. A taxa continua do dia.
          maturityDate: prev.maturityDate || (asset?.maturityDate ?? ''),
          rateType: prev.rateType ?? (asset?.rateType ?? null),
          indexer: prev.indexer ?? (asset?.indexer ?? null)
        }));
      })
      .catch(() => { /* segue sem os dados do catálogo */ });
    return () => { cancelled = true; };
  }, [isOpen, editing, preset]);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['transactions'] });
    queryClient.invalidateQueries({ queryKey: ['portfolio'] });
  };

  /**
   * A venda de Tesouro que o sistema repartiu entre títulos merece um aviso a
   * mais: o usuário lançou uma venda e o extrato vai mostrar várias linhas.
   */
  const warnSplit = (saved: TransactionResponse | undefined) => {
    const parts = saved?.groupParts ?? 0;
    if (parts > 1) {
      showToast(
        `A venda de ${assetLabel(saved!.ticker)} foi dividida em ${parts} títulos, do mais antigo para o mais novo.`,
        'info'
      );
    }
  };

  /**
   * O erro sai no toast, e o modal continua aberto: o que foi digitado fica
   * lá para ser corrigido.
   */
  const createMutation = useMutation({
    mutationFn: (txData: TransactionCreate) => transactionsApi.create(txData),
    onSuccess: (saved: TransactionResponse | undefined, txData) => {
      invalidate();
      onClose();
      onSaved?.(saved, txData);
      const ticker = assetLabel(saved?.ticker ?? txData.ticker);
      if (txData.type === TransactionType.BUY) {
        showToast(`Compra de ${ticker} registrada.`, 'success');
      } else if (txData.isFullSettlement) {
        showToast(`Resgate total de ${ticker} registrado. A aplicação foi encerrada.`, 'success');
      } else {
        showToast(`Venda de ${ticker} registrada.`, 'success');
      }
      warnSplit(saved);
    },
    onError: (e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível salvar a transação.', 'error')
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, txData }: { id: string; txData: TransactionCreate }) => transactionsApi.update(id, txData),
    onSuccess: (saved: TransactionResponse | undefined, { txData }) => {
      invalidate();
      onClose();
      onSaved?.(saved, txData);
      showToast(`Transação de ${assetLabel(saved?.ticker ?? txData.ticker)} atualizada.`, 'success');
      warnSplit(saved);
    },
    onError: (e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível atualizar a transação.', 'error')
  });

  const locked = !!editing || !!preset;
  const isTreasury = selectedType?.name === TESOURO_DIRETO;
  const isTotalValue = selectedType?.name === RENDA_FIXA;
  // Fundo de investimento (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, D16): Valor e Cota informados, cotas calculadas.
  const isFund = isFundType(selectedType);
  const fundTicker = isFund ? (matchedAsset?.ticker ?? (locked ? tickerInput : '')) : '';
  const { data: fundQuote, isFetching: fundQuoteLoading } = useQuery({
    queryKey: ['funds', fundTicker, 'quote', formData.transactionDate],
    queryFn: () => fundsApi.quote(fundTicker, formData.transactionDate),
    enabled: isOpen && isFund && !!fundTicker && !!formData.transactionDate,
    retry: false,
  });
  useEffect(() => {
    if (isFund && fundQuote && !fundQuoteTouched) {
      setFormData(prev => ({ ...prev, pricePerShare: fundQuote.quote }));
    }
  }, [isFund, fundQuote, fundQuoteTouched]);
  // Cripto: quantidade e preço unitário com 8 casas, na mesma máscara dos
  // demais tipos (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.12).
  const isCrypto = isCryptoType(selectedType);
  const isSale = formData.type === TransactionType.SELL;

  /**
   * Moeda do ativo (docs/planos/PLANO_CADASTRO_MOEDAS.md, D2/D5): vem do ativo, nunca de uma escolha. Ativo nacional
   * fica exatamente como antes (R$, sem câmbio); ativo de outra moeda é lançado na moeda dele, com o câmbio da data.
   */
  const { data: currencyList } = useQuery({
    queryKey: ['currencies'],
    queryFn: currenciesApi.list,
    enabled: isOpen,
    staleTime: 5 * 60_000,
  });
  const baseCurrency = currencyList?.find(c => c.base)?.code ?? 'BRL';
  const assetCurrency = editing?.currency?.code ?? matchedAsset?.currencyCode ?? baseCurrency;
  const isForeign = assetCurrency !== baseCurrency;
  const currencySymbol = editing?.currency?.symbol
    ?? currencyList?.find(c => c.code === assetCurrency)?.symbol ?? assetCurrency;
  const { data: ptax, isFetching: ptaxLoading } = useQuery({
    queryKey: ['currencies', assetCurrency, 'rate', formData.transactionDate],
    queryFn: () => currenciesApi.rate(assetCurrency, formData.transactionDate),
    enabled: isOpen && isForeign && !!formData.transactionDate,
  });
  useEffect(() => {
    if (isForeign && !fxInformed) setFxRate(ptax?.sell ?? null);
  }, [isForeign, fxInformed, ptax]);

  /**
   * Renda Fixa pela Nova Transação: o título sai de uma lista dos títulos do ativo — o mesmo que os ícones da
   * Carteira fazem pela linha.
   *
   * Na VENDA ele é obrigatório (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, seção 5, D11): o resgate sai de uma
   * aplicação. Na COMPRA é opcional: escolhido, o aporte cai na mesma aplicação; "Novo Título" cria outro com o
   * que o usuário preencher no quadro. O Tesouro fica de fora — lá cada compra é um título novo e a venda é FIFO.
   */
  const choosesNote = !locked && isTotalValue;
  const standaloneFiSale = choosesNote && isSale;
  const currentTicker = (matchedAsset?.ticker ?? tickerInput).trim().toUpperCase();
  const { data: noteOptions } = useQuery({
    queryKey: ['transactions', 'notes', currentTicker, selectedType?.name],
    queryFn: () => transactionsApi.notes(currentTicker, selectedType?.name),
    enabled: isOpen && choosesNote && !!matchedAsset,
  });
  /** Só para o saldo do "Resgatar tudo": a carteira traz o valor na curva das posições abertas. */
  const { data: positions } = useQuery({
    queryKey: ['portfolio', 'assets'],
    queryFn: portfolioApi.getAssets,
    enabled: isOpen && standaloneFiSale,
  });
  /**
   * Todos os títulos do ativo — abertos, vencidos e liquidados —, na compra e na
   * venda: lançamento retroativo existe, e hoje o título pode estar vencido ou
   * encerrado mas não estar na data da transação. Quem decide é a data (aporte
   * no vencimento ou depois é recusado, aqui e na API).
   */
  const noteList = choosesNote && matchedAsset ? (noteOptions ?? []) : [];

  /**
   * Venda do Tesouro (D7): a distribuidora sai das que têm título aberto do ativo — vender onde não há título nunca
   * fecha. A da venda editada entra mesmo que os títulos dela já tenham sido liquidados por ela.
   */
  const treasuryTicker = isTreasury ? (matchedAsset?.ticker ?? editing?.ticker ?? preset?.ticker ?? '') : '';
  const { data: treasuryNotes } = useQuery({
    queryKey: ['transactions', 'notes', treasuryTicker, TESOURO_DIRETO],
    queryFn: () => transactionsApi.notes(treasuryTicker, TESOURO_DIRETO),
    enabled: isOpen && isTreasury && isSale && !!treasuryTicker,
  });
  const treasuryDistributors: IssuerPick[] | undefined = isTreasury && isSale
    ? (() => {
        const map_ = new Map<number, IssuerPick>();
        for (const n of treasuryNotes ?? []) {
          const d = distributorPickOf(n.noteInfo);
          if (d && !n.settled) map_.set(d.id, d);
        }
        const current = fixedIncome.distributor;
        if (current && !map_.has(current.id)) map_.set(current.id, current);
        return [...map_.values()].sort((a, b) => a.label.localeCompare(b.label, 'pt-BR'));
      })()
    : undefined;
  // Uma distribuidora só: já vem escolhida
  const singleDistributor = treasuryDistributors?.length === 1 ? treasuryDistributors[0] : null;
  useEffect(() => {
    if (singleDistributor && !fixedIncome.distributor) {
      setFixedIncome(prev => ({ ...prev, distributor: singleDistributor }));
    }
  }, [singleDistributor, fixedIncome.distributor]);
  const picked = noteList.find(p => p.noteId === pickedNoteId) ?? null;
  /**
   * O "resgatar tudo" preenche o LÍQUIDO estimado do título hoje (o valor digitado na venda é o que cai na conta): o da
   * linha da Carteira ou o do título escolhido. Sem estimativa (título sem curva), não aparece.
   */
  const noteNet = preset?.note?.estimatedNet
    ?? (picked ? positions?.find(p => p.noteId === picked.noteId)?.estimatedNet ?? null : null);

  /**
   * Prévia da venda (PLANO_VENDA_RENDA_FIXA, seção 5): Renda Fixa pelo líquido digitado, com o título; Tesouro por
   * cotas × PU. Espera o usuário parar de digitar; erro da API aparece no painel, sem toast.
   */
  const previewNoteId = isTotalValue
    ? (editingNoteId ?? preset?.note?.noteId ?? picked?.noteId ?? null)
    : null;
  const previewPayload: TransactionCreate | null = !isOpen || !isSale || !formData.ticker || !formData.transactionDate
    ? null
    : isTotalValue
      ? (previewNoteId && (formData.totalAmount || 0) > 0
          ? {
              ticker: formData.ticker, assetType: selectedType?.name ?? null, type: TransactionType.SELL, shares: null, pricePerShare: null,
              totalAmount: formData.totalAmount, brokerageFee: formData.brokerageFee ?? 0,
              transactionDate: formData.transactionDate, noteId: previewNoteId, isFullSettlement: fullSettlement
            }
          : null)
      : isFund
        // Fundo (D24): bruto e cota; a prévia devolve IR, IOF e líquido pelo PEPS (PLANO_FUNDOS_INVESTIMENTO.md, C7)
        ? ((formData.totalAmount || 0) > 0 && (formData.pricePerShare || 0) > 0
            ? {
                ticker: formData.ticker, assetType: selectedType?.name ?? null, type: TransactionType.SELL, shares: null,
                pricePerShare: formData.pricePerShare, totalAmount: formData.totalAmount,
                brokerageFee: formData.brokerageFee ?? 0, transactionDate: formData.transactionDate
              }
            : null)
      : isTreasury && (formData.shares || 0) > 0 && (formData.pricePerShare || 0) > 0
        ? {
            ticker: formData.ticker, assetType: selectedType?.name ?? null, type: TransactionType.SELL, shares: formData.shares,
            pricePerShare: formData.pricePerShare, totalAmount: null, brokerageFee: formData.brokerageFee ?? 0,
            transactionDate: formData.transactionDate, distributorId: fixedIncome.distributor?.id ?? null
          }
        : null;
  const previewKey = previewPayload ? JSON.stringify(previewPayload) : null;
  const [debouncedKey, setDebouncedKey] = useState<string | null>(null);
  useEffect(() => {
    const t = setTimeout(() => setDebouncedKey(previewKey), 400);
    return () => clearTimeout(t);
  }, [previewKey]);
  const {
    data: preview, error: previewError, isFetching: previewLoading
  } = useQuery<SalePreview>({
    queryKey: ['transactions', 'sale-preview', debouncedKey, editing?.id ?? null],
    queryFn: () => transactionsApi.salePreview(JSON.parse(debouncedKey!) as TransactionCreate, editing?.id),
    enabled: !!debouncedKey && debouncedKey === previewKey,
    retry: false,
  });
  /** R9: o líquido passa do disponível e a venda não está marcada como liquidação total — pergunta antes de gravar. */
  const [askTotal, setAskTotal] = useState(false);
  /** Troca de distribuidora do Tesouro com venda ligada: o modal da cascata (seção 5.1 do plano de emissoras). */
  const [cascade, setCascade] = useState<{ preview: DistributorChangePreview; payload: TransactionCreate } | null>(null);
  useEffect(() => { if (!isOpen) setCascade(null); }, [isOpen]);
  useEffect(() => { setAskTotal(false); }, [previewKey, isOpen]);

  /**
   * "Novo Título" desfaz a escolha por inteiro. Na compra o quadro volta ao estado de
   * título novo, com o que o catálogo sabe do ativo — zerar tudo perderia a
   * categoria e, no papel que os tem, o vencimento e a remuneração travados.
   */
  const chooseNote = (noteId: string, typeValue: string = formData.type) => {
    const p = noteId ? noteList.find(x => x.noteId === noteId) : undefined;
    setPickedNoteId(p ? noteId : null);
    setFixedIncome(p ? {
      assetCategoryId: matchedAsset?.categoryId ?? null,
      issuer: issuerPickOf(p.noteInfo),
      distributor: distributorPickOf(p.noteInfo),
      paperCode: p.noteInfo?.paperCode ?? '',
      maturityDate: p.maturityDate ?? '',
      rateType: p.rateType,
      indexer: p.indexer,
      indexerRate: p.indexerRate,
    } : (typeValue === TransactionType.BUY ? catalogTerms(matchedAsset) : EMPTY_FIXED_INCOME));
  };

  /**
   * O título escolhido continua na troca de operação (a lista é a mesma). Sem
   * escolha, o quadro volta ao estado certo para a operação nova: título novo
   * com os dados do catálogo na compra, vazio na venda.
   */
  const switchOperation = (typeValue: TransactionType) => {
    setFormData(prev => ({ ...prev, type: typeValue }));
    if (typeValue === TransactionType.BUY) setFullSettlement(false);
    if (choosesNote && typeValue !== formData.type) chooseNote(pickedNoteId ?? '', typeValue);
  };

  /**
   * Trocar o tipo invalida o ticker: a lista do autocomplete é outra. Invalida
   * também os valores: entrar ou sair de Renda Fixa troca quantidade e preço
   * por valor total, e o que foi digitado num modo não tem sentido no outro.
   */
  const handleTypeChange = (type: AssetType | null) => {
    setSelectedType(type);
    setMatchedAsset(null);
    setFixedIncome(EMPTY_FIXED_INCOME);
    setTickerInput('');
    setFullSettlement(false);
    setPickedNoteId(null);
    // A operação escolhida fica: venda de Renda Fixa se lança por aqui, com o
    // título escolhido na lista (D11 do plano de valorização).
    setFormData(prev => ({
      ...prev,
      ticker: '', shares: 0, pricePerShare: 0, totalAmount: 0
    }));
  };

  /**
   * asset != null -> ticker casou com um ativo do catalogo. Os parametros
   * publicos dele (Tesouro) preenchem o titulo; emissor e taxa ficam com o
   * usuario. asset == null -> nada escolhido ainda.
   */
  const handleTickerChange = (ticker: string, asset: Asset | null) => {
    setPickedNoteId(null);
    setFundQuoteTouched(false);
    setTickerInput(ticker);
    setFormData(prev => ({ ...prev, ticker }));
    setMatchedAsset(asset);
    setFixedIncome(catalogTerms(asset));
  };

  // Contas em utils/transactionAmounts.ts (testadas): RF e fundo pelo valor, os demais cotas × preço; fundo: cotas =
  // valor ÷ cota com 8 casas (a API é quem grava).
  const computedTotal = transactionTotal(isTotalValue || isFund, formData.shares, formData.pricePerShare, formData.totalAmount);
  const fundShares = isFund ? fundSharesOf(formData.totalAmount, formData.pricePerShare) : 0;

  /**
   * O quadro do título aparece quando há título para mostrar.
   *
   * Na Renda Fixa ele é sempre preenchido pelo usuário. No Tesouro ele mostra
   * os parâmetros públicos do papel: na compra para conferência (a taxa é do
   * dia e se digita), na venda só como identificação — ali a operação é do
   * ativo e os títulos saem por FIFO, então não há taxa a informar.
   */
  const showsNoteData = !!selectedType?.fixedIncome
    && (locked ? (!!editingNoteId || !!preset || isTreasury) : !!matchedAsset);

  /**
   * O título já existe: os dados são dele, e editá-los aqui mudaria a aplicação
   * por tabela. Na venda de Tesouro nada é editável porque não há um título só.
   */
  const noteReadOnly = (!!preset && !!preset.note) || (isTreasury && isSale) || standaloneFiSale
    || (choosesNote && !!picked);

  /**
   * O ativo já traz vencimento e forma de remuneração? Então são do papel, não
   * da aplicação — é o caso do Tesouro Direto — e não se digitam. A taxa segue
   * editável em qualquer caso: ela é do dia da compra.
   *
   * Na edição o ativo chega depois do modal abrir. Até chegar, os campos ficam
   * travados: destravar primeiro e travar depois descartaria o que o usuário
   * tivesse digitado nesse intervalo.
   */
  const lockedTerms = locked && !matchedAsset
    ? true
    : (!!matchedAsset?.maturityDate || !!matchedAsset?.rateType);


  const group = editing?.groupParts && editing.groupParts > 1 ? editing.groupParts : null;

  /**
   * R16: na edição de um título de renda fixa, a distribuidora nova com outra regra de saída recalcula os resgates
   * do título (o líquido fica; bruto, IR e IOF são refeitos). O aviso aparece antes de salvar.
   */
  const ruleBefore = editing && isTotalValue ? (editing.noteInfo?.distributorSaleRule ?? null) : null;
  const ruleAfter = fixedIncome.distributor?.saleRule ?? null;
  const exitRuleNotice = ruleBefore && ruleAfter && ruleBefore !== ruleAfter
    && fixedIncome.distributor?.id !== editing?.noteInfo?.distributorId
    ? `A regra de saída passa de ${SALE_RULE_LABEL[ruleBefore] ?? ruleBefore} para ${SALE_RULE_LABEL[ruleAfter] ?? ruleAfter}. `
      + 'Os resgates deste título serão recalculados: o líquido recebido fica; bruto, IR e IOF são refeitos.'
    : null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    // R9: venda parcial de Renda Fixa acima do líquido disponível. Não bloqueia: pergunta se é o resgate total.
    if (isTotalValue && isSale && !fullSettlement && preview?.aboveAvailable && debouncedKey === previewKey) {
      setAskTotal(true);
      return;
    }
    send(fullSettlement);
  };

  const send = (settleInFull: boolean) => {
    setAskTotal(false);

    // Renda Fixa manda o valor total e anula quantidade e preço; os demais
    // tipos mandam quantidade e preço e deixam o total para a API calcular.
    // Aporte num título que já existe só até a véspera do vencimento (no dia
    // ele já está vencido, a regra do selo da carteira). O modal fica aberto.
    const noteMaturity = !isSale && !isTreasury && !editing
      ? (picked?.maturityDate ?? preset?.note?.maturityDate ?? null)
      : null;
    if (matchedAsset?.delistedOn && formData.transactionDate > matchedAsset.delistedOn) {
      showToast(`${matchedAsset.ticker} saiu da bolsa em ${formatDate(matchedAsset.delistedOn)}: a data não pode ser depois da saída.`, 'error');
      return;
    }
    if (noteMaturity && formData.transactionDate >= noteMaturity) {
      showToast('Título vencido para a data de transação escolhida', 'error');
      return;
    }

    const amounts: TransactionCreate = isTotalValue
      ? { ...formData, shares: null, pricePerShare: null, totalAmount: formData.totalAmount || 0 }
      : isFund
        ? { ...formData, shares: null, totalAmount: formData.totalAmount || 0 }
        : { ...formData, totalAmount: null };

    // O título vai junto em dois casos, por motivos opostos: quando o usuário o
    // preencheu (Renda Fixa avulsa, que cria a nota) e quando ele veio da
    // carteira (noteId, que lança na nota que já existe). Mandar os campos na
    // edição não é opcional: a API só toca no título quando eles vêm, e é assim
    // que editar a quantidade de um CDB deixa de apagar o emissor e a taxa.
    const noteData = showsNoteData && !(isTreasury && isSale)
      ? {
          // No Tesouro a emissora é sempre o Tesouro Nacional (a API ignora); o código do papel é da renda fixa
          issuerId: isTreasury ? null : (fixedIncome.issuer?.id ?? null),
          distributorId: fixedIncome.distributor?.id ?? null,
          paperCode: isTreasury ? null : (fixedIncome.paperCode || null),
          maturityDate: fixedIncome.maturityDate || null,
          rateType: fixedIncome.rateType,
          indexer: fixedIncome.indexer,
          indexerRate: fixedIncome.indexerRate
        }
      : {};

    const payload: TransactionCreate = {
      ...amounts,
      ...noteData,
      // O Tesouro nunca manda título: cada compra é um novo e a venda é do
      // ativo.
      noteId: !isTreasury && !editing ? (preset?.note?.noteId ?? (choosesNote ? (picked?.noteId ?? null) : null)) : null,
      isFullSettlement: isTotalValue && isSale ? settleInFull : null,
      // Ativo em outra moeda: o câmbio da tela (PTAX da data ou o informado); em reais nunca vai
      fxRate: isForeign ? fxRate : null,
      // O tipo vai junto: desde a V13 o ticker é único só por tipo (META é cripto e ação do exterior)
      assetType: selectedType?.name ?? null,
      // A venda do Tesouro informa a distribuidora: o FIFO consome só os títulos dela (R13)
      ...(isTreasury && isSale ? { distributorId: fixedIncome.distributor?.id ?? null } : {})
    };

    if (isForeign && !(fxRate && fxRate > 0)) {
      showToast('Informe o câmbio da data para converter a transação em reais.', 'error');
      return;
    }

    if (isTreasury && !fixedIncome.distributor) {
      showToast('Informe a distribuidora (onde o título foi comprado).', 'error');
      return;
    }

    if (editing) {
      // Compra de Tesouro com distribuidora nova: com venda ligada, a troca é pela cascata (seção 5.1 do plano)
      const newDistributor = fixedIncome.distributor?.id ?? null;
      if (isTreasury && !isSale && newDistributor != null
          && newDistributor !== (editing.noteInfo?.distributorId ?? null)) {
        transactionsApi.distributorChangePreview(editing.id, newDistributor)
          .then(fiSalePreview => {
            if (fiSalePreview.items.some(i => i.type === 'SELL')) {
              setCascade({ preview: fiSalePreview, payload });
            } else {
              updateMutation.mutate({ id: editing.id, txData: payload });
            }
          })
          .catch((e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível conferir a troca de distribuidora.', 'error'));
        return;
      }
      updateMutation.mutate({ id: editing.id, txData: payload });
      return;
    }
    createMutation.mutate(payload);
  };

  const isSaving = createMutation.isPending || updateMutation.isPending;
  const modalTitle = editing
    ? 'Editar Transação'
    : preset
      ? (preset.operation === TransactionType.SELL ? `Vender ${preset.ticker}` : `Comprar ${preset.ticker}`)
      : 'Nova Transação';

  // O (i) do título explica a venda de Tesouro, que é do ativo e não de um
  // título. Na edição de uma venda já repartida, o que importa é outra coisa:
  // que se edita a venda inteira.
  const modalTitleInfo = group
    ? `Esta venda foi dividida em ${group} títulos pelo sistema, do mais antigo para o mais novo. O que você edita aqui é a venda inteira.`
    : isTreasury && isSale
      ? 'A venda é do ativo, não de um título. As cotas saem dos títulos mais antigos primeiro, e o sistema divide a operação entre eles.'
      : undefined;

  // Prévia da venda (Renda Fixa e Tesouro): no popover do ícone ao lado do Líquido / Total bruto
  const hasPreview = isSale && (isTotalValue || isTreasury || isFund) && !!(previewPayload || preview);
  const currentPreview = debouncedKey === previewKey ? preview : undefined;

  return (
    <>
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={modalTitle}
      titleInfo={modalTitleInfo}
      // Com o quadro do título (agora em duas colunas de campos), o modal ganha largura; sem ele, fica como era
      size={showsNoteData ? '2xl' : 'xl'}
      footer={
        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" onClick={onClose}>
            <X className="h-4 w-4 mr-2" />
            Cancelar
          </Button>
          <Button type="submit" form={FORM_ID} isLoading={isSaving}>
            {!isSaving && <Save className="h-4 w-4 mr-2" />}
            Salvar
          </Button>
        </div>
      }
    >
      {/*
        Duas colunas de alturas independentes: a transação inteira à esquerda,
        o quadro do título à direita. Os dois lados se dividem em dois campos
        por linha (o quadro desde que ganhou emissora, distribuidora e código),
        por isso o modal fica mais largo com o quadro e a proporção é 5fr/6fr:
        a esquerda mantém a largura de antes e Quantidade/Preço não espremem.
        Sem o quadro, a esquerda ocupa a largura toda.
      */}
      <form
        id={FORM_ID}
        onSubmit={handleSubmit}
        className={`grid grid-cols-1 gap-6 ${showsNoteData ? 'lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] lg:items-start' : ''}`}
      >
        {/* Coluna da transação */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 content-start">
          {/* Toggle Compra/Venda */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-1.5">
              <LabelWithInfo
                info={standaloneFiSale
                  ? 'Na venda de Renda Fixa escolha de qual título sai o resgate.'
                  : choosesNote
                    ? 'Na compra de Renda Fixa escolha um título para aportar na mesma aplicação, ou deixe "Novo Título" para criar outro.'
                    : undefined}
              >
                Operação
              </LabelWithInfo>
            </label>
            <div className={`flex bg-surface rounded-md border border-white/10 p-1 ${locked ? 'opacity-60 pointer-events-none' : ''}`}>
              <button
                type="button"
                disabled={locked}
                onClick={() => switchOperation(TransactionType.BUY)}
                className={`flex-1 flex items-center justify-center gap-2 py-1.5 rounded text-sm font-medium transition-all ${
                  formData.type === TransactionType.BUY
                    ? 'bg-success/15 text-success border border-success/30 shadow-sm'
                    : 'text-gray-400 hover:text-gray-300 hover:bg-white/5'
                }`}
              >
                <ArrowDownLeft className="w-4 h-4" />
                Compra
              </button>
              <button
                type="button"
                disabled={locked}
                onClick={() => switchOperation(TransactionType.SELL)}
                className={`flex-1 flex items-center justify-center gap-2 py-1.5 rounded text-sm font-medium transition-all ${
                  formData.type === TransactionType.SELL
                    ? 'bg-danger/15 text-danger border border-danger/30 shadow-sm'
                    : 'text-gray-400 hover:text-gray-300 hover:bg-white/5'
                }`}
              >
                <ArrowUpRight className="w-4 h-4" />
                Venda
              </button>
            </div>
          </div>

          {/* Tipo de Ativo — comanda o resto do formulário */}
          <AssetTypeSelect
            value={selectedType?.id ?? null}
            disabled={locked}
            onChange={handleTypeChange}
          />

          {/* Ticker — autocomplete restrito ao tipo escolhido */}
          <div className="sm:col-span-2">
            {locked ? (
              <Input label={isFund ? 'Fundo' : 'Ticker (Ativo)'} value={isFund ? assetLabel(tickerInput) : tickerInput}
                readOnly disabled />
            ) : isFund ? (
              <FundAutocomplete
                value={tickerInput}
                typeId={selectedType?.id ?? null}
                onChange={handleTickerChange}
              />
            ) : (
              <TickerAutocomplete
                value={tickerInput}
                typeId={selectedType?.id ?? null}
                onChange={handleTickerChange}
                includeDelisted
              />
            )}
            {isFund && matchedAsset?.fund?.pension && (
              <p className="text-xs text-gray-400 mt-1">
                Fundo de previdência: vale o valor bruto. O IR é do plano (PGBL/VGBL) e não é calculado.
              </p>
            )}
          </div>

          {choosesNote && (
            <div className="sm:col-span-2">
              <label htmlFor="titulo-rf" className="block text-sm font-medium text-gray-300 mb-1.5">Título</label>
              <select
                id="titulo-rf"
                required={isSale}
                disabled={!isSale && !matchedAsset}
                value={picked?.noteId ?? ''}
                onChange={(e) => chooseNote(e.target.value)}
                className="flex h-10 w-full rounded-md border border-surface-light bg-surface px-3 py-2 text-sm text-white disabled:opacity-60 disabled:cursor-not-allowed"
              >
                <option value="">{!currentTicker
                  ? 'Escolha o ativo primeiro'
                  : isSale
                    ? (noteList.length ? 'Escolha a aplicação' : `Nenhum título de ${currentTicker}`)
                    : 'Novo Título'}</option>
                {noteList.map(p => (
                  <option key={p.noteId} value={p.noteId}>
                    {[p.issuer || 'Sem emissor',
                      p.noteInfo?.paperCode || null,
                      p.maturityDate ? `venc. ${formatDate(p.maturityDate)}` : 'sem vencimento',
                      rateLabel(p.rateType, p.indexer, p.indexerRate),
                      p.settled ? 'liquidado' : (p.matured ? 'vencido' : null)].filter(Boolean).join(' · ')}
                  </option>
                ))}
              </select>
            </div>
          )}

          {isFund ? (
            <>
              <CurrencyInput
                label={isSale ? 'Valor resgatado (bruto)' : 'Valor'}
                value={formData.totalAmount ?? 0}
                onChange={(v) => setFormData({ ...formData, totalAmount: v })}
                required
              />
              <MaskedNumberInput
                label="Cota"
                info="Valor da cota do fundo na data da transação, do informe diário da CVM. Se o seu extrato mostrar outra cota, informe-a."
                decimals={8}
                value={formData.pricePerShare ?? 0}
                onChange={(v) => { setFundQuoteTouched(true); setFormData({ ...formData, pricePerShare: v }); }}
                hint={fundQuoteLoading ? 'Buscando a cota da data…'
                  : fundQuoteTouched ? 'Cota informada por você.'
                  : fundQuote ? `Cota da CVM de ${formatDate(fundQuote.date)}.`
                  : (matchedAsset || locked) ? 'Sem cota publicada até a data: informe a cota.' : undefined}
                required
              />
            </>
          ) : isTotalValue ? (
            <div className="sm:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-4 items-end">
              <CurrencyInput
                label={isSale ? 'Valor recebido (líquido)' : 'Valor'}
                value={formData.totalAmount ?? 0}
                onChange={(v) => setFormData({ ...formData, totalAmount: v })}
                required
              />
              {/* A liquidação total só existe na venda: ela diz que este resgate
                  encerra a aplicação, e é o que autoriza a posição a zerar. */}
              {isSale && (
                <label className="flex h-10 items-center gap-2 rounded-md border border-white/10 bg-surface-light/40 px-3 cursor-pointer">
                  <input
                    type="checkbox"
                    className="accent-accent"
                    checked={fullSettlement}
                    onChange={(e) => setFullSettlement(e.target.checked)}
                  />
                  <span className="text-sm text-gray-300">Liquidação total</span>
                  <InfoTooltip content="Marque quando este resgate encerra a aplicação. A posição é zerada e a valorização é ajustada para o valor resgatado." />
                </label>
              )}
              {isSale && noteNet != null && noteNet > 0 && (
                <button
                  type="button"
                  onClick={() => {
                    setFormData({ ...formData, totalAmount: fullRedemptionAmount(noteNet) });
                    setFullSettlement(true);
                  }}
                  className="sm:col-span-2 justify-self-start text-xs text-accent hover:underline"
                >
                  Resgatar tudo — líquido estimado hoje {formatCurrency(noteNet)}
                </button>
              )}
            </div>
          ) : (
            <>
              {/* Quantidade usa a mesma máscara dos valores: duas casas fixas,
                  digitadas da direita para a esquerda. Sem ela, "1.5" digitado
                  por quem está acostumado à vírgula virava quinze.
                  Cripto usa a mesma máscara, com 8 casas na quantidade
                  (0,00197802 BTC) e no preço (R$ 525.297,05592977). */}
              <MaskedNumberInput
                label="Quantidade"
                decimals={isCrypto ? CRYPTO_DECIMALS : 2}
                value={formData.shares ?? 0}
                onChange={(v) => setFormData({ ...formData, shares: v })}
                required
              />
              <CurrencyInput
                label="Preço Unitário"
                symbol={isForeign ? currencySymbol : undefined}
                decimals={isCrypto ? CRYPTO_DECIMALS : 2}
                value={formData.pricePerShare ?? 0}
                onChange={(v) => setFormData({ ...formData, pricePerShare: v })}
                required
              />
            </>
          )}

          <div>
            <Input
              label="Data da Transação"
              type="date"
              value={formData.transactionDate}
              max={matchedAsset?.delistedOn ?? undefined}
              onChange={(e) => setFormData({ ...formData, transactionDate: e.target.value })}
              required
            />
            {/* D11: papel que saiu da bolsa só recebe lançamento até a saída */}
            {matchedAsset?.delistedOn && (
              <p className="text-xs text-gray-400 mt-1">
                {matchedAsset.ticker} saiu da bolsa em {formatDate(matchedAsset.delistedOn)}: a data vai até a saída.
              </p>
            )}
          </div>
          <CurrencyInput
            label="Taxas"
            symbol={isForeign ? currencySymbol : undefined}
            value={formData.brokerageFee ?? 0}
            onChange={(v) => setFormData({ ...formData, brokerageFee: v })}
          />

          {isForeign && (
            <div className="sm:col-span-2 flex items-end gap-2">
              <div className="flex-1">
                <MaskedNumberInput
                  label={`Câmbio (R$ por ${currencySymbol} 1)`}
                  info="Câmbio para reais: a PTAX de venda do Banco Central na data da transação. Se a sua corretora usou outro câmbio, informe-o."
                  decimals={4}
                  value={fxRate ?? 0}
                  onChange={(v) => { setFxRate(v); setFxInformed(true); }}
                  hint={ptaxLoading ? 'Buscando a PTAX da data…'
                    : fxInformed ? 'Câmbio informado por você.'
                    : ptax ? `PTAX venda de ${formatDate(ptax.rateDate)}${ptax.carried ? ' (último boletim antes da data)' : ''}.`
                    : 'Sem câmbio publicado até a data: informe a taxa.'}
                  required
                />
              </div>
              {fxInformed && ptax && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="mb-6"
                  title="Voltar à PTAX da data"
                  aria-label="Voltar à PTAX da data"
                  onClick={() => { setFxInformed(false); setFxRate(ptax.sell); }}
                >
                  <RotateCcw className="w-4 h-4" />
                </Button>
              )}
            </div>
          )}

          {/* Total é resultado, não campo: uma linha de leitura ocupa metade
              da altura de um input com rótulo e texto de apoio. */}
          <div className="sm:col-span-2 flex items-baseline justify-between gap-4 rounded-md border border-surface-light bg-surface-light/40 px-4 py-3">
            <span className="inline-flex items-center text-sm text-gray-400">
              <LabelWithInfo
                info={selectedType && isFund
                  ? (isSale
                      ? 'O valor bruto do resgate. IR e IOF estão na prévia (ícone ao lado).'
                      : 'O valor aplicado. As cotas saem da divisão pela cota e não são editáveis.')
                  : selectedType && !isTotalValue
                  ? (isTreasury && isSale
                      ? 'Quantidade × preço unitário: o valor bruto. IR e IOF de cada título estão na prévia da venda (ícone ao lado).'
                      : 'Quantidade × preço unitário. As taxas não entram no total.')
                  : undefined}
              >
                {isTotalValue && isSale ? 'Líquido' : (isTreasury || isFund) && isSale ? 'Total bruto' : 'Total'}
              </LabelWithInfo>
              {hasPreview && (
                <Popover
                  label="Prévia da venda"
                  title="Prévia da venda"
                  trigger={<Receipt className="h-4 w-4" />}
                  className="ml-1.5"
                >
                  <SalePreviewPanel
                    preview={currentPreview}
                    loading={previewLoading || debouncedKey !== previewKey}
                    treasury={isTreasury}
                    fund={isFund}
                    fee={formData.brokerageFee ?? 0}
                  />
                </Popover>
              )}
            </span>
            {isForeign ? (
              <span className="text-right">
                <span className="block text-lg font-semibold tabular-nums text-white">
                  {formatMoney(computedTotal, currencySymbol)}
                </span>
                <span className="block text-xs tabular-nums text-gray-400">
                  {fxRate ? `${formatCurrency(computedTotal * fxRate)} em reais` : 'em reais: informe o câmbio'}
                </span>
              </span>
            ) : isFund ? (
              // D16: as cotas são calculadas (valor ÷ cota) e só lidas — se não baterem com o extrato, corrige-se a cota
              <span className="text-right">
                <span className="block text-lg font-semibold tabular-nums text-white">{formatCurrency(computedTotal)}</span>
                <span className="block text-xs tabular-nums text-gray-400">
                  {fundShares > 0
                    ? `${fundShares.toLocaleString('pt-BR', { maximumFractionDigits: 8 })} cotas`
                    : 'cotas: informe valor e cota'}
                </span>
              </span>
            ) : (
              <span className="text-lg font-semibold tabular-nums text-white">
                {formatCurrency(computedTotal)}
              </span>
            )}
          </div>

          {hasPreview && previewError && (
            <div className="sm:col-span-2 rounded-md border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">
              {apiMessage(previewError) ?? 'Não foi possível calcular a prévia.'}
            </div>
          )}
          {hasPreview && currentPreview?.aboveAvailable && (
            <p className="sm:col-span-2 text-xs text-accent">
              O valor passa do líquido disponível estimado. Se é o resgate total, marque “Liquidação total”.
            </p>
          )}

          {askTotal && (
            <div className="sm:col-span-2 rounded-md border border-accent/40 bg-accent/10 px-4 py-3 text-sm">
              <p className="font-medium text-accent">É o resgate total?</p>
              <p className="mt-1 text-gray-300">
                O valor informado passa do líquido disponível estimado
                {preview?.availableNet != null ? ` (${formatCurrency(preview.availableNet)})` : ''}.
                Se este resgate encerra a aplicação, marque como liquidação total: a diferença entra no bruto como
                ajuste da liquidação.
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <Button type="button" size="sm" onClick={() => { setFullSettlement(true); send(true); }}>
                  Sim, resgate total
                </Button>
                <Button type="button" size="sm" variant="ghost" onClick={() => send(false)}>
                  Não, lançar mesmo assim
                </Button>
              </div>
            </div>
          )}
        </div>

        {/* Coluna do título — só para tipos com remuneração contratada */}
        {showsNoteData && selectedType && (
          <FixedIncomeFields
            typeId={selectedType.id}
            values={fixedIncome}
            lockedTerms={lockedTerms}
            readOnly={noteReadOnly}
            showIssuer={!isTreasury}
            showRate={!(isTreasury && isSale)}
            ticker={matchedAsset?.ticker ?? editing?.ticker ?? preset?.ticker ?? null}
            distributorReadOnly={isTreasury && isSale ? false : undefined}
            distributorOptions={treasuryDistributors}
            distributorWarning={exitRuleNotice}
            onChange={(patch) => setFixedIncome(prev => ({ ...prev, ...patch }))}
          />
        )}
      </form>
    </Modal>
    {cascade && editing && (
      <DistributorChangeModal
        transactionId={editing.id}
        preview={cascade.preview}
        purchase={cascade.payload}
        onCancel={() => setCascade(null)}
        onDone={(saved) => {
          setCascade(null);
          invalidate();
          onClose();
          onSaved?.(saved, cascade.payload);
          showToast(`Distribuidora de ${saved?.ticker ?? cascade.payload.ticker} trocada para ${cascade.preview.toName}.`, 'success');
        }}
      />
    )}
    </>
  );
};
