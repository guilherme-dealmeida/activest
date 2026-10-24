import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import { ArrowLeft, Calculator, FunctionSquare, Pencil, RefreshCw, RotateCcw, Save } from 'lucide-react';
import { clsx } from 'clsx';
import { ceilingPricesApi } from '@/api/ceilingPrices';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { AssetLogo } from '@/components/ui/AssetLogo';
import { TickerLink } from '@/components/issuer/TickerLink';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { SignedNumberInput } from '@/components/ui/SignedNumberInput';
import { IDEAL_PRICE_INFO, bigMoney, pct, signedPct } from '@/components/ceilingprice/ceilingFormat';
import { useToast } from '@/context/ToastContext';
import { formatCurrency, formatDate } from '@/utils/format';
import {
  CeilingDefaultValue, CeilingDefaults, CeilingIssue, CeilingPriceDetail, CeilingPriceInput, CeilingQuarterInput,
  CeilingRejection, CeilingResult, CeilingUnit,
} from '@/types';

type Field = 'shares' | 'roePct' | 'payoutPct' | 'discountRatePct' | 'perpetualGrowthPct' | 'safetyMarginPct' | 'price';

const FIELDS: { key: Field; label: string; info: string; decimals: number; adornment?: { text: string; side: 'left' | 'right' }; negative: boolean }[] = [
  { key: 'roePct', label: 'ROE', info: 'Retorno sobre o patrimônio. Padrão: média dos 3 últimos exercícios fechados.', decimals: 2, adornment: { text: '%', side: 'right' }, negative: true },
  { key: 'payoutPct', label: 'Payout', info: 'Dividendos + JCP pagos ÷ lucro. Padrão: média dos 3 últimos exercícios fechados.', decimals: 2, adornment: { text: '%', side: 'right' }, negative: true },
  { key: 'discountRatePct', label: 'Taxa de desconto (d)', info: 'Padrão: Selic meta vigente.', decimals: 2, adornment: { text: '%', side: 'right' }, negative: true },
  { key: 'safetyMarginPct', label: 'Margem de segurança (MS)', info: 'Desconto sobre o teto para o Preço Ideal. Padrão: 15%.', decimals: 2, adornment: { text: '%', side: 'right' }, negative: false },
  { key: 'shares', label: 'Quantidade de ações (N)', info: 'Padrão: ações em circulação sem tesouraria do último balanço, ajustadas por desdobramentos e grupamentos posteriores. Na unit, em units.', decimals: 0, negative: false },
  { key: 'price', label: 'Preço da ação', info: 'Preço para comparar com o teto e o ideal. Padrão: último fechamento.', decimals: 2, adornment: { text: 'R$', side: 'left' }, negative: false },
];

const UNITS: { value: CeilingUnit; label: string; factor: number }[] = [
  { value: 'UNIDADE', label: 'R$', factor: 1 },
  { value: 'MIL', label: 'mil', factor: 1e3 },
  { value: 'MILHAO', label: 'milhões', factor: 1e6 },
  { value: 'BILHAO', label: 'bilhões', factor: 1e9 },
];

const same = (a: number | null | undefined, b: number | null | undefined) =>
  a != null && b != null ? Math.abs(a - b) < 1e-9 * Math.max(1, Math.abs(b)) : (a ?? null) === (b ?? null);

const fromDefaults = (d: CeilingDefaults): CeilingPriceInput => ({
  ticker: d.ticker,
  quarters: d.quarters.map(q => ({ periodEnd: q.periodEnd, originalValue: q.value, unit: 'UNIDADE', currency: 'BRL', fxRate: null })),
  shares: d.shares.value, roePct: d.roePct.value, payoutPct: d.payoutPct.value, discountRatePct: d.discountRatePct.value,
  perpetualGrowthPct: d.perpetualGrowthPct.value, safetyMarginPct: d.safetyMarginPct.value, price: d.price.value,
  notes: null, confirmWarnings: false,
});

const fromDetail = (c: CeilingPriceDetail): CeilingPriceInput => ({
  ticker: c.ticker,
  quarters: c.quarters.map(q => ({ periodEnd: q.periodEnd, originalValue: q.originalValue, unit: q.unit, currency: q.currency,
    fxRate: q.currency === 'USD' ? q.fxRate : null })),
  shares: c.shares, roePct: c.roePct, payoutPct: c.payoutPct, discountRatePct: c.discountRatePct,
  perpetualGrowthPct: c.perpetualGrowthPct, safetyMarginPct: c.safetyMarginPct, price: c.price,
  notes: c.notes, confirmWarnings: false,
});

const quarterLabel = (iso: string) => {
  const [y, m] = iso.split('-').map(Number);
  return `${Math.floor((m - 1) / 3) + 1}T${String(y % 100).padStart(2, '0')}`;
};

/** Conteúdo do (i) de uma premissa: o padrão de hoje (referência e observação) e o que o campo significa. */
const defaultInfo = (dv: CeilingDefaultValue | undefined, description: string) => (
  <div className="space-y-1">
    {dv?.ref && <div>Padrão: {dv.ref}</div>}
    {dv?.note && <div>{dv.ref ? dv.note : `Sem padrão: ${dv.note}`}</div>}
    <div className="text-gray-400">{description}</div>
  </div>
);

const LEVEL_STYLE: Record<CeilingIssue['level'], string> = {
  BLOQUEIO: 'border-danger/40 bg-danger/10 text-danger',
  ATENCAO: 'border-yellow-500/40 bg-yellow-500/10 text-yellow-500',
  INFORMACAO: 'border-white/10 bg-white/5 text-gray-300',
};
const LEVEL_LABEL: Record<CeilingIssue['level'], string> = { BLOQUEIO: 'Bloqueio', ATENCAO: 'Atenção', INFORMACAO: 'Informação' };

/** Origem de um campo: padrão (importado), informado ou calculado — passo 9.6 do pedido. */
const OriginTag = ({ kind, title }: { kind: 'PADRAO' | 'INFORMADO' | 'CALCULADO'; title?: string }) => (
  <span title={title} className={clsx('inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide',
    kind === 'PADRAO' && 'bg-white/5 text-gray-300 border border-white/10',
    kind === 'INFORMADO' && 'bg-accent/15 text-accent border border-accent/40',
    kind === 'CALCULADO' && 'bg-transparent text-yellow-500 border border-dashed border-yellow-600')}>
    {kind === 'INFORMADO' && <Pencil className="w-3 h-3" />}
    {kind === 'CALCULADO' && <FunctionSquare className="w-3 h-3" />}
    {kind === 'PADRAO' ? 'Padrão' : kind === 'INFORMADO' ? 'Informado' : 'Calculado'}
  </span>
);

/**
 * Preço Teto de uma ação (docs/planos/PLANO_PRECO_TETO.md, 7.2): formulário com os padrões do catálogo (↻ por campo e
 * "Restaurar todos os padrões", nada persiste até "Salvar"), resultado recalculado enquanto digita e alertas.
 * Revisão de 11/10/2026 (Guilherme): sem o cartão de memória de cálculo (a API ainda devolve os passos em
 * {@code result.steps}), sem fonte do lucro trimestral, padrões no (i) de cada premissa, G só leitura.
 */
export const CeilingPriceDetailPage = () => {
  const { ticker = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { showToast } = useToast();

  const defaultsQuery = useQuery({ queryKey: ['ceilingDefaults', ticker], queryFn: () => ceilingPricesApi.defaults(ticker), retry: false });
  const defaults = defaultsQuery.data;
  const existingId = defaults?.existingId ?? null;
  const detailQuery = useQuery({ queryKey: ['ceilingPrice', existingId], queryFn: () => ceilingPricesApi.get(existingId!), enabled: !!existingId });
  const saved = detailQuery.data;

  const [form, setForm] = useState<CeilingPriceInput | null>(null);
  const [dirty, setDirty] = useState(false);
  const [result, setResult] = useState<CeilingResult | null>(null);
  const [calculating, setCalculating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [rejection, setRejection] = useState<CeilingRejection | null>(null);

  // estado inicial: o gravado (edição) ou os padrões (novo)
  useEffect(() => {
    if (!defaults || form) return;
    if (existingId) {
      if (saved) { setForm(fromDetail(saved)); setResult(saved.result); }
    } else {
      setForm(fromDefaults(defaults));
    }
  }, [defaults, saved, existingId, form]);

  // recálculo ao vivo (sem gravar)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (!form) return;
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(async () => {
      setCalculating(true);
      try { setResult(await ceilingPricesApi.calculate(form)); } catch { /* mantém o último */ } finally { setCalculating(false); }
    }, 350);
    return () => { if (timer.current) clearTimeout(timer.current); };
  }, [form]);

  // sair com alterações não salvas
  useEffect(() => {
    const h = (e: BeforeUnloadEvent) => { if (dirty) { e.preventDefault(); e.returnValue = ''; } };
    window.addEventListener('beforeunload', h);
    return () => window.removeEventListener('beforeunload', h);
  }, [dirty]);

  const update = (patch: Partial<CeilingPriceInput>) => { setForm(f => (f ? { ...f, ...patch } : f)); setDirty(true); };
  const updateQuarter = (i: number, patch: Partial<CeilingQuarterInput>) =>
    setForm(f => { if (!f) return f; const qs = f.quarters.map((q, k) => (k === i ? { ...q, ...patch } : q)); setDirty(true); return { ...f, quarters: qs }; });

  const defaultOf = (k: Field): CeilingDefaultValue | undefined => defaults?.[k];
  const fieldIssues = (prefix: string) => (result?.issues ?? []).filter(i => i.field === prefix || i.field?.startsWith(prefix + '['));

  const newerQuarters = useMemo(() => {
    if (!defaults?.latestQuarterEnd || !form?.quarters.length) return false;
    return defaults.latestQuarterEnd > form.quarters[form.quarters.length - 1].periodEnd;
  }, [defaults, form]);

  const restoreAll = () => {
    if (!defaults || !form) return;
    setForm({ ...fromDefaults(defaults), notes: form.notes });
    setDirty(true);
  };

  const save = async (confirmWarnings = false) => {
    if (!form) return;
    setSaving(true);
    try {
      const body = { ...form, confirmWarnings };
      const r = existingId ? await ceilingPricesApi.update(existingId, body) : await ceilingPricesApi.create(body);
      setRejection(null);
      setDirty(false);
      queryClient.invalidateQueries({ queryKey: ['ceilingPrices'] });
      queryClient.setQueryData(['ceilingPrice', r.id], r);
      queryClient.invalidateQueries({ queryKey: ['ceilingDefaults', ticker] });
      setForm(fromDetail(r));
      setResult(r.result);
      showToast('Preço teto salvo.', 'success');
    } catch (e) {
      const err = e as AxiosError<CeilingRejection>;
      if (err.response?.status === 422 && err.response.data?.issues) {
        setRejection(err.response.data);
      } else {
        showToast((err.response?.data as { message?: string } | undefined)?.message ?? 'Não foi possível salvar.', 'error');
      }
    } finally {
      setSaving(false);
    }
  };

  const back = () => {
    if (dirty && !window.confirm('Há alterações não salvas. Sair mesmo assim?')) return;
    navigate('/ceiling-prices');
  };

  if (defaultsQuery.isError) {
    const msg = ((defaultsQuery.error as AxiosError<{ message?: string }>).response?.data?.message) ?? 'Ativo não encontrado.';
    return (
      <div className="space-y-4">
        <Button variant="ghost" onClick={() => navigate('/ceiling-prices')}><ArrowLeft className="w-4 h-4 mr-2" />Voltar</Button>
        <Card><p className="text-gray-300">{msg}</p></Card>
      </div>
    );
  }
  if (!defaults || !form) {
    return <div className="flex h-full items-center justify-center"><LoadingSpinner className="h-8 w-8 text-accent" /></div>;
  }

  const changed = saved?.changedDefaults ?? [];
  const r = result;
  /** Complemento da margem: contra qual preço ela foi medida (o do formulário). */
  const priceRef = form?.price == null ? '' : ` de ${formatCurrency(form.price)}`;
  const onlyWarnings = rejection && rejection.issues.every(i => i.level !== 'BLOQUEIO');

  return (
    <div className="space-y-6">
      {/* cabeçalho */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="sm" onClick={back}><ArrowLeft className="w-4 h-4" /></Button>
          <AssetLogo ticker={defaults.ticker} hasLogo={!!defaults.hasLogo} size={36} />
          <div>
            <div className="text-lg font-semibold text-white"><TickerLink ticker={defaults.ticker} issuerId={defaults.issuerId} className="font-semibold" /></div>
            <div className="text-sm text-gray-400">{defaults.assetName}</div>
          </div>
          {saved && <span className="text-xs text-gray-500 ml-2">calculado em {formatDate(saved.calculatedAt.slice(0, 10))}</span>}
          {!existingId && <span className="text-xs text-accent ml-2">cálculo novo — não salvo</span>}
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={restoreAll} title="Aplica os padrões de hoje em todos os campos (só vale ao salvar)">
            <RotateCcw className="w-4 h-4 mr-2" />Restaurar todos os padrões
          </Button>
          <Button size="sm" onClick={() => save(false)} isLoading={saving}>
            {!saving && <Save className="w-4 h-4 mr-2" />}Salvar
          </Button>
        </div>
      </div>

      {/* legenda */}
      <div className="flex flex-wrap items-center gap-3 text-xs text-gray-400">
        <span>Legenda:</span>
        <span className="flex items-center gap-1"><OriginTag kind="PADRAO" /> veio do catálogo (CVM, B3, BCB)</span>
        <span className="flex items-center gap-1"><OriginTag kind="INFORMADO" /> alterado por você</span>
        <span className="flex items-center gap-1"><OriginTag kind="CALCULADO" /> calculado pelo modelo</span>
        <span className="flex items-center gap-1">
          <span className="inline-flex items-center rounded px-1.5 py-0.5 bg-accent/15 text-accent border border-accent/40">
            <RefreshCw className="w-3 h-3" />
          </span>
          traz o padrão de hoje para o campo
        </span>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-5 gap-6">
        {/* ------------------------------------------------ formulário */}
        <div className="xl:col-span-3 space-y-6">
        <Card title="Premissas" titleInfo="Percentuais em pontos (13,75 = 13,75%). Nenhum campo pode ficar vazio.">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {FIELDS.map(f => {
                const dv = defaultOf(f.key);
                const value = form[f.key] as number | null;
                const isDefault = dv?.value != null && same(value, dv.value);
                const highlight = (dv?.value != null && !isDefault) || changed.includes(f.key);
                const issues = fieldIssues(f.key);
                return (
                  <div key={f.key}>
                    <div className="flex items-center justify-between mb-1">
                      <label className="text-sm text-gray-300">
                        <LabelWithInfo info={defaultInfo(dv, f.info)}>{f.label}</LabelWithInfo>
                      </label>
                      <OriginTag kind={isDefault ? 'PADRAO' : 'INFORMADO'} />
                    </div>
                    <div className="flex items-center gap-2">
                      <SignedNumberInput className={clsx('flex-1', !isDefault && '[&_input]:border-accent/60')} value={value}
                        decimals={f.decimals} adornment={f.adornment} allowNegative={f.negative}
                        onChange={v => update({ [f.key]: v } as Partial<CeilingPriceInput>)} ariaLabel={f.label}
                        error={issues.some(x => x.level === 'BLOQUEIO')} />
                      <button type="button" disabled={dv?.value == null}
                              title={dv?.value != null ? `Padrão de hoje: ${f.decimals === 0 ? dv.value.toLocaleString('pt-BR') : dv.value.toLocaleString('pt-BR', { maximumFractionDigits: 4 })}` : 'Sem padrão'}
                              onClick={() => dv?.value != null && update({ [f.key]: dv.value } as Partial<CeilingPriceInput>)}
                              className={clsx('p-2 rounded-md border disabled:opacity-30', highlight ? 'border-accent/60 text-accent' : 'border-surface-light text-gray-500')}>
                        <RefreshCw className="w-4 h-4" />
                      </button>
                    </div>
                    {issues.map((x, k) => <p key={k} className={clsx('text-xs', x.level === 'BLOQUEIO' ? 'text-danger' : 'text-yellow-500')}>{x.message}</p>)}
                  </div>
                );
              })}
              <div>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-sm text-gray-300">Crescimento sustentável (g)</span>
                  <OriginTag kind="CALCULADO" />
                </div>
                <div className="h-10 flex items-center justify-end rounded-md border border-dashed border-gray-600 px-3 text-sm text-gray-300 tabular-nums">
                  {pct(r?.growthPct)}
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-sm text-gray-300">
                    <LabelWithInfo info={defaultInfo(defaults.perpetualGrowthPct, 'Crescimento do lucro depois do 3º período. Precisa ser menor que a taxa de desconto.')}>
                      Crescimento na perpetuidade (G)
                    </LabelWithInfo>
                  </span>
                  <OriginTag kind={same(form.perpetualGrowthPct, defaults.perpetualGrowthPct.value) ? 'PADRAO' : 'INFORMADO'} />
                </div>
                <div className="h-10 flex items-center justify-end rounded-md border border-dashed border-gray-600 px-3 text-sm text-gray-300 tabular-nums">
                  {pct(form.perpetualGrowthPct)}
                </div>
                {fieldIssues('perpetualGrowthPct').map((x, k) => <p key={k} className={clsx('text-xs', x.level === 'BLOQUEIO' ? 'text-danger' : 'text-yellow-500')}>{x.message}</p>)}
              </div>
            </div>
          </Card>

          {/* D8 revisada (docs/planos/PLANO_USABILIDADE_PRECO_TETO.md): mesmo nome da linha dos Demonstrativos */}
          <Card title="Lucro líquido da controladora — 4 últimos trimestres" titleInfo="Lucro líquido atribuível aos acionistas">
            {newerQuarters && (
              <div className="mb-3 flex items-center justify-between rounded-md border border-accent/40 bg-accent/10 px-3 py-2 text-sm text-accent">
                <span>Há balanço mais recente ({quarterLabel(defaults.latestQuarterEnd!)}).</span>
                <Button size="sm" variant="outline" onClick={() => { update({ quarters: fromDefaults(defaults).quarters }); }}>
                  Atualizar trimestres
                </Button>
              </div>
            )}
            <div className="space-y-3">
              {form.quarters.map((q, i) => {
                const dq = defaults.quarters.find(x => x.periodEnd === q.periodEnd);
                const factor = UNITS.find(u => u.value === q.unit)?.factor ?? 1;
                const brl = q.originalValue == null ? null : q.currency === 'USD' ? (q.fxRate ? q.originalValue * factor * q.fxRate : null) : q.originalValue * factor;
                const isDefault = dq?.value != null && q.currency === 'BRL' && brl != null && same(brl, dq.value);
                const issues = fieldIssues(`quarters[${i}]`);
                return (
                  <div key={i}>
                    <div className="flex flex-wrap items-center gap-2 mb-2">
                      <span className="font-semibold text-white w-12">{quarterLabel(q.periodEnd)}</span>
                      <OriginTag kind={isDefault ? 'PADRAO' : 'INFORMADO'} />
                      {dq?.value == null && dq?.note && <span className="text-xs text-gray-500 truncate">{dq.note}</span>}
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      <SignedNumberInput className={clsx('flex-1 min-w-[200px]', !isDefault && '[&_input]:border-accent/60')}
                        value={q.originalValue} onChange={v => updateQuarter(i, { originalValue: v })} ariaLabel={`Lucro ${quarterLabel(q.periodEnd)}`}
                        error={issues.some(x => x.level === 'BLOQUEIO')} />
                      <select value={q.unit} onChange={e => updateQuarter(i, { unit: e.target.value as CeilingUnit })}
                              className="h-10 bg-surface border border-surface-light text-white text-sm rounded-md px-2">
                        {UNITS.map(u => <option key={u.value} value={u.value}>{u.label}</option>)}
                      </select>
                      <select value={q.currency} onChange={e => updateQuarter(i, { currency: e.target.value as 'BRL' | 'USD', fxRate: null })}
                              className="h-10 bg-surface border border-surface-light text-white text-sm rounded-md px-2">
                        <option value="BRL">BRL</option>
                        <option value="USD">USD</option>
                      </select>
                      {q.currency === 'USD' && (
                        <div className="w-36" title="Câmbio para reais. Vazio = PTAX de venda do fim do trimestre.">
                          <SignedNumberInput value={q.fxRate} decimals={4} allowNegative={false} adornment={{ text: 'R$/US$', side: 'left' }}
                                             onChange={v => updateQuarter(i, { fxRate: v || null })} ariaLabel="Câmbio" />
                        </div>
                      )}
                      <button type="button" title={`Padrão: ${dq?.value != null ? bigMoney(dq.value) : '—'}`}
                              onClick={() => dq && updateQuarter(i, { originalValue: dq.value, unit: 'UNIDADE', currency: 'BRL', fxRate: null })}
                              className={clsx('p-2 rounded-md border', !isDefault && dq?.value != null ? 'border-accent/60 text-accent' : 'border-surface-light text-gray-500')}>
                        <RefreshCw className="w-4 h-4" />
                      </button>
                    </div>
                    {issues.map((x, k) => <p key={k} className={clsx('mt-1 text-xs', x.level === 'BLOQUEIO' ? 'text-danger' : x.level === 'ATENCAO' ? 'text-yellow-500' : 'text-gray-400')}>{x.message}</p>)}
                  </div>
                );
              })}
              <div className="flex items-center justify-between rounded-lg bg-white/5 px-3 py-2">
                <span className="text-sm text-gray-300 flex items-center gap-2">Lucro líquido últimos 12 meses (LL TTM) <OriginTag kind="CALCULADO" /></span>
                <span className="font-semibold text-white tabular-nums">{r?.netIncomeTtm == null ? '—' : formatCurrency(r.netIncomeTtm)}</span>
              </div>
            </div>
          </Card>

        </div>

        {/* ------------------------------------------------ resultado */}
        <div className="xl:col-span-2 space-y-6">
          <Card title="Resultado" titleInfo="Calculado das entradas ao lado. Só é gravado ao salvar." icon={calculating ? <LoadingSpinner className="h-4 w-4" /> : <Calculator className="w-4 h-4" />}>
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-lg border border-accent/40 bg-accent/5 p-3">
                <div className="text-xs text-gray-400">Preço Teto</div>
                <div className="text-2xl font-semibold text-accent tabular-nums">{r?.ceilingPrice == null ? '—' : formatCurrency(r.ceilingPrice)}</div>
                <div className={clsx('text-xs tabular-nums', (r?.ceilingMarginPct ?? 0) >= 0 ? 'text-success' : 'text-danger')}>margem {signedPct(r?.ceilingMarginPct)}{priceRef}</div>
              </div>
              <div className="rounded-lg border border-surface-light p-3">
                <div className="text-xs text-gray-400"><LabelWithInfo info={IDEAL_PRICE_INFO}>Preço Ideal</LabelWithInfo></div>
                <div className="text-2xl font-semibold text-white tabular-nums">{r?.idealPrice == null ? '—' : formatCurrency(r.idealPrice)}</div>
                <div className={clsx('text-xs tabular-nums', (r?.idealMarginPct ?? 0) >= 0 ? 'text-success' : 'text-danger')}>margem {signedPct(r?.idealMarginPct)}{priceRef}</div>
              </div>
            </div>
          </Card>

          <Card title="Alertas e dados faltantes">
            {(r?.issues ?? []).length === 0 ? <p className="text-sm text-gray-500">Nenhum alerta.</p> : (
              <ul className="space-y-2">
                {r!.issues.map((x, k) => (
                  <li key={k} className={clsx('rounded-md border px-3 py-2 text-sm', LEVEL_STYLE[x.level])}>
                    <span className="font-semibold mr-1">{LEVEL_LABEL[x.level]}:</span>{x.message}
                  </li>
                ))}
              </ul>
            )}
          </Card>

        </div>
      </div>

      <Modal isOpen={!!rejection} onClose={() => setRejection(null)} title={onlyWarnings ? 'Confirmar alertas' : 'Não foi possível salvar'}
             footer={
               <div className="flex justify-end gap-2">
                 <Button variant="ghost" onClick={() => setRejection(null)}>Voltar ao cálculo</Button>
                 {onlyWarnings && <Button onClick={() => { setRejection(null); save(true); }}>Salvar mesmo assim</Button>}
               </div>
             }>
        <p className="text-sm text-gray-300 mb-3">{rejection?.message}</p>
        <ul className="space-y-2">
          {rejection?.issues.map((x, k) => (
            <li key={k} className={clsx('rounded-md border px-3 py-2 text-sm', LEVEL_STYLE[x.level])}>
              <span className="font-semibold mr-1">{LEVEL_LABEL[x.level]}:</span>{x.message}
            </li>
          ))}
        </ul>
      </Modal>
    </div>
  );
};

