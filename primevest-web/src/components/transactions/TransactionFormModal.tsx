import React, { useCallback, useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowDownLeft, ArrowUpRight } from 'lucide-react';
import { transactionsApi } from '@/api/transactions';
import { assetsApi } from '@/api/assets';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { CurrencyInput } from '@/components/ui/CurrencyInput';
import { MaskedNumberInput } from '@/components/ui/MaskedNumberInput';
import { Modal } from '@/components/ui/Modal';
import { AssetTypeSelect } from '@/components/forms/AssetTypeSelect';
import { TickerAutocomplete } from '@/components/forms/TickerAutocomplete';
import { FixedIncomeFields, FixedIncomeValues } from '@/components/forms/FixedIncomeFields';
import { formatCurrency, toLocalISODate } from '@/utils/format';
import {
  Asset, AssetType, Indexer, RateType, TransactionCreate, TransactionResponse, TransactionType
} from '@/types';

/**
 * Único ponto do front que compara o tipo por nome. No Tesouro Direto a família
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
  assetCategoryId: null, issuer: '', maturityDate: '',
  rateType: null, indexer: null, indexerRate: null
};

/**
 * O que a carteira já sabe quando abre o modal por um dos ícones de ação.
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
  } | null;
}

interface Props {
  isOpen: boolean;
  onClose: () => void;
  /** Edição de uma transação existente. */
  editing?: TransactionResponse | null;
  /** Abertura pela carteira, com a posição já escolhida. */
  preset?: TransactionPreset | null;
  /** Lista de tipos, para reconstruir o tipo da transação editada. */
  assetTypes?: AssetType[];
}

/**
 * O formulário de transação, usado por três caminhos: Nova Transação, edição
 * pelo extrato e os ícones Comprar e Vender da carteira.
 *
 * <p>Os três são o mesmo formulário com graus diferentes de travamento, e é
 * por isso que ele virou componente: duplicar essa tela significaria manter
 * duas vezes as regras de Renda Fixa, de Tesouro e de título.</p>
 */
export const TransactionFormModal = ({ isOpen, onClose, editing, preset, assetTypes }: Props) => {
  const queryClient = useQueryClient();

  const [selectedType, setSelectedType] = useState<AssetType | null>(null);
  const [matchedAsset, setMatchedAsset] = useState<Asset | null>(null);
  const [tickerInput, setTickerInput] = useState('');
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null);
  const [fixedIncome, setFixedIncome] = useState<FixedIncomeValues>(EMPTY_FIXED_INCOME);
  const [fullSettlement, setFullSettlement] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
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
    setErro(null);
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
      setErro(null);
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
        pricePerShare: editing.pricePerShare ?? 0,
        totalAmount: editing.totalAmount ?? 0,
        brokerageFee: editing.groupBrokerageFee ?? editing.brokerageFee ?? 0,
        transactionDate: editing.transactionDate
      });
      // O que está no quadro é o que foi CONTRATADO, e isso mora no título —
      // não no catálogo. Um ajuste posterior em asset_terms não deve reescrever
      // uma posição já registrada.
      setFixedIncome(editing.noteId ? {
        assetCategoryId: null,
        issuer: editing.issuer ?? '',
        maturityDate: editing.maturityDate ?? '',
        rateType: editing.rateType ?? null,
        indexer: editing.indexer ?? null,
        indexerRate: editing.indexerRate ?? null
      } : EMPTY_FIXED_INCOME);
      return;
    }

    if (preset) {
      setErro(null);
      setSelectedType(preset.assetType);
      setTickerInput(preset.ticker);
      setMatchedAsset(null);
      setEditingNoteId(preset.note?.noteId ?? null);
      setFullSettlement(false);
      setFormData({
        ticker: preset.ticker,
        type: preset.operation,
        shares: 0, pricePerShare: 0, totalAmount: 0, brokerageFee: 0,
        transactionDate: toLocalISODate()
      });
      setFixedIncome(preset.note ? {
        assetCategoryId: null,
        issuer: preset.note.issuer ?? '',
        maturityDate: preset.note.maturityDate ?? '',
        rateType: preset.note.rateType ?? null,
        indexer: preset.note.indexer ?? null,
        indexerRate: preset.note.indexerRate ?? null
      } : EMPTY_FIXED_INCOME);
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
    let cancelado = false;
    assetsApi.getByTicker(ticker)
      .then((asset: Asset) => {
        if (cancelado) return;
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
    return () => { cancelado = true; };
  }, [isOpen, editing, preset]);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['transactions'] });
    queryClient.invalidateQueries({ queryKey: ['portfolio'] });
  };

  const createMutation = useMutation({
    mutationFn: (txData: TransactionCreate) => transactionsApi.create(txData),
    onSuccess: () => { invalidate(); onClose(); },
    onError: (e: any) => setErro(mensagemDeErro(e))
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, txData }: { id: string; txData: TransactionCreate }) => transactionsApi.update(id, txData),
    onSuccess: () => { invalidate(); onClose(); },
    onError: (e: any) => setErro(mensagemDeErro(e))
  });

  const travado = !!editing || !!preset;
  const isTesouro = selectedType?.name === TESOURO_DIRETO;
  const isValorTotal = selectedType?.name === RENDA_FIXA;
  const isVenda = formData.type === TransactionType.SELL;

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
    // Venda de Renda Fixa ainda não se lança por aqui: falta dizer de qual
    // título ela sai. Pela carteira, onde o título é a própria linha, ela
    // funciona.
    setFormData(prev => ({
      ...prev,
      ticker: '', shares: 0, pricePerShare: 0, totalAmount: 0,
      type: type?.name === RENDA_FIXA ? TransactionType.BUY : prev.type
    }));
  };

  /**
   * asset != null -> ticker casou com um ativo do catalogo. Os parametros
   * publicos dele (Tesouro) preenchem o titulo; emissor e taxa ficam com o
   * usuario. asset == null -> nada escolhido ainda.
   */
  const handleTickerChange = (ticker: string, asset: Asset | null) => {
    setTickerInput(ticker);
    setFormData(prev => ({ ...prev, ticker }));
    setMatchedAsset(asset);
    if (asset) {
      setFixedIncome({
        assetCategoryId: asset.categoryId ?? null,
        issuer: '',
        maturityDate: asset.maturityDate ?? '',
        rateType: asset.rateType ?? null,
        indexer: asset.indexer ?? null,
        indexerRate: null
      });
    } else {
      setFixedIncome(EMPTY_FIXED_INCOME);
    }
  };

  const computedTotal = isValorTotal
    ? (formData.totalAmount || 0)
    : (formData.shares || 0) * (formData.pricePerShare || 0);

  /**
   * O quadro do título aparece quando há título para mostrar.
   *
   * Na Renda Fixa ele é sempre preenchido pelo usuário. No Tesouro ele mostra
   * os parâmetros públicos do papel: na compra para conferência (a taxa é do
   * dia e se digita), na venda só como identificação — ali a operação é do
   * ativo e os títulos saem por FIFO, então não há taxa a informar.
   */
  const mostraDadosTitulo = !!selectedType?.fixedIncome
    && (travado ? (!!editingNoteId || !!preset || isTesouro) : !!matchedAsset);

  /**
   * O título já existe: os dados são dele, e editá-los aqui mudaria a aplicação
   * por tabela. Na venda de Tesouro nada é editável porque não há um título só.
   */
  const tituloSomenteLeitura = (!!preset && !!preset.note) || (isTesouro && isVenda);

  /**
   * O ativo já traz vencimento e forma de remuneração? Então são do papel, não
   * da aplicação — é o caso do Tesouro Direto — e não se digitam. A taxa segue
   * editável em qualquer caso: ela é do dia da compra.
   *
   * Na edição o ativo chega depois do modal abrir. Até chegar, os campos ficam
   * travados: destravar primeiro e travar depois descartaria o que o usuário
   * tivesse digitado nesse intervalo.
   */
  const lockedTerms = travado && !matchedAsset
    ? true
    : (!!matchedAsset?.maturityDate || !!matchedAsset?.rateType);

  /** Renda Fixa não tem venda pelo formulário avulso: falta o título. */
  const vendaBloqueada = !travado && isValorTotal;

  const grupo = editing?.groupParts && editing.groupParts > 1 ? editing.groupParts : null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErro(null);

    // Renda Fixa manda o valor total e anula quantidade e preço; os demais
    // tipos mandam quantidade e preço e deixam o total para a API calcular.
    const valores: TransactionCreate = isValorTotal
      ? { ...formData, shares: null, pricePerShare: null, totalAmount: formData.totalAmount || 0 }
      : { ...formData, totalAmount: null };

    // O título vai junto em dois casos, por motivos opostos: quando o usuário o
    // preencheu (Renda Fixa avulsa, que cria a nota) e quando ele veio da
    // carteira (noteId, que lança na nota que já existe). Mandar os campos na
    // edição não é opcional: a API só toca no título quando eles vêm, e é assim
    // que editar a quantidade de um CDB deixa de apagar o emissor e a taxa.
    const dadosTitulo = mostraDadosTitulo && !(isTesouro && isVenda)
      ? {
          issuer: fixedIncome.issuer || null,
          maturityDate: fixedIncome.maturityDate || null,
          rateType: fixedIncome.rateType,
          indexer: fixedIncome.indexer,
          indexerRate: fixedIncome.indexerRate
        }
      : {};

    const payload: TransactionCreate = {
      ...valores,
      ...dadosTitulo,
      // O Tesouro nunca manda título: cada compra é um novo e a venda é do
      // ativo.
      noteId: !isTesouro && !editing ? (preset?.note?.noteId ?? null) : null,
      isFullSettlement: isValorTotal && isVenda ? fullSettlement : null
    };

    if (editing) {
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

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={modalTitle}
      size="xl"
      footer={
        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" onClick={onClose}>Cancelar</Button>
          <Button type="submit" form={FORM_ID} isLoading={isSaving}>Salvar</Button>
        </div>
      }
    >
      {/*
        Duas colunas de alturas independentes: a transação inteira à esquerda,
        o quadro do título à direita. A proporção 3fr/2fr existe porque a
        esquerda ainda se divide em dois campos por linha — repartida meio a
        meio, cada campo ficava com uns 165px e Quantidade/Preço espremiam.
        Sem o quadro, a esquerda ocupa a largura toda.
      */}
      <form
        id={FORM_ID}
        onSubmit={handleSubmit}
        className={`grid grid-cols-1 gap-6 ${mostraDadosTitulo ? 'lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)] lg:items-start' : ''}`}
      >
        {/* Coluna da transação */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 content-start">
          {erro && (
            <div className="sm:col-span-2 rounded-md border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
              {erro}
            </div>
          )}

          {grupo && (
            <div className="sm:col-span-2 rounded-md border border-white/10 bg-surface-light/40 px-4 py-3 text-xs text-gray-400">
              Esta venda foi dividida em {grupo} títulos pelo sistema, do mais antigo para o mais novo.
              O que você edita aqui é a venda inteira.
            </div>
          )}

          {/* Toggle Compra/Venda */}
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-1.5">Operação</label>
            <div className={`flex bg-surface rounded-md border border-white/10 p-1 ${travado ? 'opacity-60 pointer-events-none' : ''}`}>
              <button
                type="button"
                disabled={travado}
                onClick={() => setFormData({ ...formData, type: TransactionType.BUY })}
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
                disabled={travado || vendaBloqueada}
                onClick={() => setFormData({ ...formData, type: TransactionType.SELL })}
                title={vendaBloqueada ? 'A venda de Renda Fixa sai da carteira, pelo ícone da posição' : undefined}
                className={`flex-1 flex items-center justify-center gap-2 py-1.5 rounded text-sm font-medium transition-all ${
                  formData.type === TransactionType.SELL
                    ? 'bg-danger/15 text-danger border border-danger/30 shadow-sm'
                    : 'text-gray-400 hover:text-gray-300 hover:bg-white/5'
                } ${vendaBloqueada ? 'opacity-40 cursor-not-allowed' : ''}`}
              >
                <ArrowUpRight className="w-4 h-4" />
                Venda
              </button>
            </div>
            {vendaBloqueada && (
              <p className="mt-1 text-xs text-gray-500">
                Renda Fixa: venda pela carteira, onde o título é a própria linha.
              </p>
            )}
          </div>

          {/* Tipo de Ativo — comanda o resto do formulário */}
          <AssetTypeSelect
            value={selectedType?.id ?? null}
            disabled={travado}
            onChange={handleTypeChange}
          />

          {/* Ticker — autocomplete restrito ao tipo escolhido */}
          <div className="sm:col-span-2">
            {travado ? (
              <Input label="Ticker (Ativo)" value={tickerInput} readOnly disabled />
            ) : (
              <TickerAutocomplete
                value={tickerInput}
                typeId={selectedType?.id ?? null}
                onChange={handleTickerChange}
              />
            )}
          </div>

          {isValorTotal ? (
            <div className="sm:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-4 items-end">
              <CurrencyInput
                label="Valor"
                value={formData.totalAmount ?? 0}
                onChange={(v) => setFormData({ ...formData, totalAmount: v })}
                required
              />
              {/* A liquidação total só existe na venda: ela diz que este resgate
                  encerra a aplicação, e é o que autoriza a posição a zerar. */}
              {isVenda && (
                <label className="flex items-start gap-2 rounded-md border border-white/10 bg-surface-light/40 px-3 py-2 cursor-pointer">
                  <input
                    type="checkbox"
                    className="mt-0.5 accent-accent"
                    checked={fullSettlement}
                    onChange={(e) => setFullSettlement(e.target.checked)}
                  />
                  <span className="text-sm text-gray-300">
                    Liquidação total
                    <span className="block text-xs text-gray-500">
                      Encerra a aplicação e ajusta a valorização para o valor resgatado.
                    </span>
                  </span>
                </label>
              )}
            </div>
          ) : (
            <>
              {/* Quantidade usa a mesma máscara dos valores: duas casas fixas,
                  digitadas da direita para a esquerda. Sem ela, "1.5" digitado
                  por quem está acostumado à vírgula virava quinze. */}
              <MaskedNumberInput
                label="Quantidade"
                decimals={2}
                value={formData.shares ?? 0}
                onChange={(v) => setFormData({ ...formData, shares: v })}
                required
              />
              <CurrencyInput
                label="Preço Unitário"
                value={formData.pricePerShare ?? 0}
                onChange={(v) => setFormData({ ...formData, pricePerShare: v })}
                required
              />
            </>
          )}

          <Input
            label="Data da Transação"
            type="date"
            value={formData.transactionDate}
            onChange={(e) => setFormData({ ...formData, transactionDate: e.target.value })}
            required
          />
          <CurrencyInput
            label="Taxas"
            value={formData.brokerageFee ?? 0}
            onChange={(v) => setFormData({ ...formData, brokerageFee: v })}
          />

          {/* Total é resultado, não campo: uma linha de leitura ocupa metade
              da altura de um input com rótulo e texto de apoio. */}
          <div className="sm:col-span-2 flex items-baseline justify-between gap-4 rounded-md border border-surface-light bg-surface-light/40 px-4 py-3">
            <span className="text-sm text-gray-400">
              Total
              {selectedType && !isValorTotal && (
                <span className="text-gray-500"> — quantidade x preço unitário, sem as taxas</span>
              )}
            </span>
            <span className="text-lg font-semibold tabular-nums text-white">
              {formatCurrency(computedTotal)}
            </span>
          </div>

          {isTesouro && isVenda && (
            <p className="sm:col-span-2 text-xs text-gray-500">
              A venda é do ativo: as cotas saem dos títulos mais antigos primeiro, e o sistema
              divide a operação entre eles.
            </p>
          )}
        </div>

        {/* Coluna do título — só para tipos com remuneração contratada */}
        {mostraDadosTitulo && selectedType && (
          <FixedIncomeFields
            typeId={selectedType.id}
            values={fixedIncome}
            lockedTerms={lockedTerms}
            readOnly={tituloSomenteLeitura}
            showIssuer={!isTesouro}
            showRate={!(isTesouro && isVenda)}
            onChange={(patch) => setFixedIncome(prev => ({ ...prev, ...patch }))}
          />
        )}
      </form>
    </Modal>
  );
};

/** A API devolve a regra violada em texto; é ela que o usuário precisa ler. */
function mensagemDeErro(e: any): string {
  return e?.response?.data?.message
    || e?.response?.data?.error
    || 'Não foi possível salvar a transação.';
}
