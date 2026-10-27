import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { LineChart as LineChartIcon, Pencil, Plus } from 'lucide-react';
import { clsx } from 'clsx';
import { currenciesApi, currencyAdminApi, CurrencyAdminInput } from '@/api/currencies';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Switch } from '@/components/ui/Switch';
import { Modal } from '@/components/ui/Modal';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { TABLE_ACTION_CLASS } from '@/components/ui/tableAction';
import { useToast } from '@/context/ToastContext';
import { useTheme } from '@/context/ThemeContext';
import { apiMessage } from '@/utils/apiError';
import { formatDate, formatFxRate, toLocalISODate } from '@/utils/format';
import { CurrencyAdminRow, ForeignTaxRule } from '@/types';

const GOLD = '#c9a84c';
const RANGES = [
  { key: '1A', label: '1A', years: 1 },
  { key: '5A', label: '5A', years: 5 },
  { key: 'MAX', label: 'MAX', years: null },
] as const;
type RangeKey = typeof RANGES[number]['key'];

const TAX_LABEL: Record<ForeignTaxRule['tax'], string> = {
  IOF_FX: 'IOF de câmbio',
  IR_FOREIGN: 'IR no Brasil',
  WITHHOLDING_FOREIGN: 'Retenção no país de origem',
};
const EVENT_LABEL: Record<ForeignTaxRule['event'], string> = {
  REMITTANCE_INVESTMENT: 'Remessa para investimento',
  REMITTANCE_OWN_ACCOUNT: 'Remessa para conta própria',
  INFLOW: 'Ingresso de recursos',
  DIVIDEND: 'Provento',
  SALE: 'Venda',
};

const pct = (v: number) => `${(v * 100).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`;

const EMPTY: CurrencyAdminInput = {
  code: '', name: '', symbol: '', decimals: 2, countryCode: '', rateSource: 'BCB_PTAX', active: true,
};

/**
 * Moedas (docs/planos/PLANO_CADASTRO_MOEDAS.md, D13): tela só de administrador. O cadastro (moeda nova = uma linha
 * mais a série de câmbio, carregada pela tarefa de câmbio), o gráfico da série (compra e venda da PTAX) e, em
 * consulta, as regras de tributo do exterior por data (mudam por migration, D18).
 */
export const CurrenciesPage = () => {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const { theme } = useTheme();
  const [editing, setEditing] = useState<CurrencyAdminRow | null>(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<CurrencyAdminInput>(EMPTY);
  const [chartCode, setChartCode] = useState<string | null>(null);
  const [range, setRange] = useState<RangeKey>('1A');

  const { data: rows, isLoading } = useQuery({ queryKey: ['admin', 'currencies'], queryFn: currencyAdminApi.list });
  const { data: rules } = useQuery({ queryKey: ['foreign-tax-rules'], queryFn: currenciesApi.taxRules });

  const withSeries = (rows ?? []).filter(r => !r.base);
  const selected = chartCode ?? withSeries[0]?.code ?? null;
  const from = useMemo(() => {
    const years = RANGES.find(r => r.key === range)?.years;
    if (years == null) return '2010-01-01';
    const d = new Date();
    d.setFullYear(d.getFullYear() - years);
    return toLocalISODate(d);
  }, [range]);
  const { data: points, isFetching: loadingPoints } = useQuery({
    queryKey: ['currencies', selected, 'rates', from],
    queryFn: () => currenciesApi.rates(selected!, from, toLocalISODate()),
    enabled: !!selected,
  });

  const save = useMutation({
    mutationFn: (input: CurrencyAdminInput) => editing
      ? currencyAdminApi.update(editing.code, input)
      : currencyAdminApi.create(input),
    onSuccess: (row) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'currencies'] });
      queryClient.invalidateQueries({ queryKey: ['currencies'] });
      showToast(editing ? `Moeda ${row.code} atualizada.` : `Moeda ${row.code} cadastrada. A série de câmbio entra na próxima rodada da tarefa de câmbio.`, 'success');
      close();
    },
    onError: (e: unknown) => showToast(apiMessage(e) ?? 'Não foi possível salvar a moeda.', 'error'),
  });

  const open = (row: CurrencyAdminRow | null) => {
    setEditing(row);
    setCreating(!row);
    setForm(row ? {
      name: row.name, symbol: row.symbol, decimals: row.decimals, countryCode: row.countryCode ?? '',
      rateSource: row.rateSource, active: row.active,
    } : EMPTY);
  };
  const close = () => { setEditing(null); setCreating(false); };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    save.mutate({ ...form, countryCode: form.countryCode?.trim() ? form.countryCode.trim().toUpperCase() : null });
  };

  const axisColor = theme === 'light' ? '#6b7280' : '#9ca3af';
  const gridColor = theme === 'light' ? '#e5e7eb' : '#1f1f1f';

  return (
    <div className="space-y-6">
      <Card
        title="Moedas"
        titleInfo="A moeda vem do ativo: o usuário nunca escolhe. O real é a moeda base (a carteira soma tudo nele). Moeda nova = uma linha aqui; a série de câmbio entra pela tarefa de câmbio (Tarefas)."
        action={<Button size="sm" onClick={() => open(null)}><Plus className="w-4 h-4 mr-1" />Nova moeda</Button>}
      >
        {isLoading ? <LoadingSpinner /> : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left text-gray-400">
              <thead className="text-xs uppercase bg-surface-light text-gray-300">
                <tr>
                  <th className="px-4 py-3">CÓDIGO</th>
                  <th className="px-4 py-3">NOME</th>
                  <th className="px-4 py-3">SÍMBOLO</th>
                  <th className="px-4 py-3 text-center">SITUAÇÃO</th>
                  <th className="px-4 py-3">FONTE</th>
                  <th className="px-4 py-3 text-right">ÚLTIMO CÂMBIO</th>
                  <th className="px-4 py-3 text-right">SÉRIE</th>
                  <th className="px-4 py-3 text-center">AÇÃO</th>
                </tr>
              </thead>
              <tbody>
                {(rows ?? []).map(r => (
                  <tr key={r.code} className="border-b border-surface-light hover:bg-surface-light/40">
                    <td className="px-4 py-3 font-medium text-white">{r.code}</td>
                    <td className="px-4 py-3">{r.name}{r.countryCode ? <span className="text-gray-500"> · {r.countryCode}</span> : null}</td>
                    <td className="px-4 py-3">{r.symbol}</td>
                    <td className="px-4 py-3 text-center">
                      {r.base ? <Badge variant="info">Base</Badge>
                        : r.active ? <Badge variant="success">Ativa</Badge> : <Badge>Inativa</Badge>}
                    </td>
                    <td className="px-4 py-3">{r.rateSource === 'BCB_PTAX' ? 'PTAX (Banco Central)' : '—'}</td>
                    <td className="px-4 py-3 text-right tabular-nums">
                      {r.lastSell != null ? (
                        <span title={`Compra ${formatFxRate(r.lastBuy ?? 0)} · venda ${formatFxRate(r.lastSell)}`}>
                          R$ {formatFxRate(r.lastSell)} <span className="text-gray-500">em {formatDate(r.lastDate ?? '')}</span>
                        </span>
                      ) : '—'}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums">
                      {r.rates > 0 ? `${r.rates.toLocaleString('pt-BR')} dias desde ${formatDate(r.firstDate ?? '')}` : r.base ? '—' : 'sem série'}
                    </td>
                    <td className="px-4 py-3 text-center">
                      <div className="flex items-center justify-center gap-1">
                        <button type="button" className={TABLE_ACTION_CLASS} title="Editar" onClick={() => open(r)}>
                          <Pencil className="w-4 h-4" />
                        </button>
                        {!r.base && (
                          <button type="button" className={TABLE_ACTION_CLASS} title="Ver o gráfico do câmbio"
                            onClick={() => setChartCode(r.code)}>
                            <LineChartIcon className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {selected && (
        <Card
          title={`Câmbio ${selected} (PTAX)`}
          titleInfo="Boletim de fechamento da PTAX do Banco Central, em reais por unidade da moeda. A venda converte compras e posições; a compra converte proventos."
          action={
            <div className="flex space-x-1 bg-surface p-1 rounded-lg border border-surface-light">
              {RANGES.map(r => (
                <button key={r.key} type="button" onClick={() => setRange(r.key)}
                  className={clsx('px-3 py-1 text-xs rounded-md', range === r.key ? 'bg-accent text-black font-semibold' : 'text-gray-400 hover:text-white')}>
                  {r.label}
                </button>
              ))}
            </div>
          }
        >
          {loadingPoints && !points ? <LoadingSpinner /> : !points?.length ? (
            <p className="text-sm text-gray-500">Sem câmbio carregado no período. Rode a tarefa de câmbio em Tarefas.</p>
          ) : (
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={points} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                  <CartesianGrid stroke={gridColor} vertical={false} />
                  <XAxis dataKey="date" tick={{ fill: axisColor, fontSize: 11 }} minTickGap={40}
                    tickFormatter={(d: string) => { const [y, m, dd] = d.split('-'); return range === '1A' ? `${dd}/${m}` : `${m}/${y}`; }} />
                  <YAxis domain={['auto', 'auto']} tick={{ fill: axisColor, fontSize: 11 }} width={56}
                    tickFormatter={(v: number) => v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} />
                  <Tooltip
                    contentStyle={{ background: theme === 'light' ? '#fff' : '#111111', border: '1px solid #333' }}
                    labelFormatter={(d: string) => formatDate(d)}
                    formatter={(v: number, name: string) => [`R$ ${formatFxRate(v)}`, name === 'sell' ? 'Venda' : 'Compra']}
                  />
                  <Line type="monotone" dataKey="sell" stroke={GOLD} dot={false} strokeWidth={2} name="sell" />
                  <Line type="monotone" dataKey="buy" stroke={axisColor} dot={false} strokeWidth={1} name="buy" />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </Card>
      )}

      <Card
        title="Tributos do exterior"
        titleInfo="Alíquotas por data, com a base legal. Usadas no líquido de vendas e proventos de ativos do exterior e no IOF das operações de câmbio. Mudança de alíquota entra por atualização do sistema, com a nova base legal."
      >
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left text-gray-400">
            <thead className="text-xs uppercase bg-surface-light text-gray-300">
              <tr>
                <th className="px-4 py-3">TRIBUTO</th>
                <th className="px-4 py-3">SOBRE</th>
                <th className="px-4 py-3">PAÍS</th>
                <th className="px-4 py-3 text-right">ALÍQUOTA</th>
                <th className="px-4 py-3">VIGÊNCIA</th>
                <th className="px-4 py-3">BASE LEGAL</th>
              </tr>
            </thead>
            <tbody>
              {(rules ?? []).map(r => (
                <tr key={r.id} className="border-b border-surface-light">
                  <td className="px-4 py-3 text-white">{TAX_LABEL[r.tax]}</td>
                  <td className="px-4 py-3">{EVENT_LABEL[r.event]}</td>
                  <td className="px-4 py-3">{r.countryCode ?? 'Qualquer'}</td>
                  <td className="px-4 py-3 text-right tabular-nums">
                    {r.brackets ? <span title={r.brackets}>{pct(r.rate)} (faixas)</span> : pct(r.rate)}
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap">
                    {r.validFrom ? formatDate(r.validFrom) : 'desde sempre'} – {r.validTo ? formatDate(r.validTo) : 'vigente'}
                  </td>
                  <td className="px-4 py-3 text-xs">{r.legalBasis}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Modal
        isOpen={creating || !!editing}
        onClose={close}
        title={editing ? `Editar moeda ${editing.code}` : 'Nova moeda'}
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={close}>Cancelar</Button>
            <Button type="submit" form="currency-form" isLoading={save.isPending}>Salvar</Button>
          </div>
        }
      >
        <form id="currency-form" onSubmit={submit} className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {!editing && (
            <Input label="Código (ISO 4217)" value={form.code ?? ''} maxLength={3} required
              onChange={e => setForm({ ...form, code: e.target.value.toUpperCase() })} hint="Ex.: EUR" />
          )}
          <Input label="Nome" value={form.name} maxLength={60} required
            onChange={e => setForm({ ...form, name: e.target.value })} />
          <Input label="Símbolo" value={form.symbol} maxLength={6} required
            onChange={e => setForm({ ...form, symbol: e.target.value })} hint="Ex.: €" />
          <Input label="Casas decimais" type="number" min={0} max={8} value={form.decimals} required
            onChange={e => setForm({ ...form, decimals: Number(e.target.value) })} />
          <Input label="País (ISO 3166)" value={form.countryCode ?? ''} maxLength={2}
            onChange={e => setForm({ ...form, countryCode: e.target.value.toUpperCase() })}
            hint="Para a retenção do país de origem; vazio em moeda de vários países" />
          <Select label="Fonte do câmbio" value={form.rateSource} disabled={editing?.base}
            onChange={e => setForm({ ...form, rateSource: e.target.value as CurrencyAdminInput['rateSource'] })}>
            {editing?.base && <option value="NONE">Nenhuma (moeda base)</option>}
            <option value="BCB_PTAX">PTAX (Banco Central)</option>
          </Select>
          <div className="sm:col-span-2">
            <Switch label="Ativa" checked={form.active} disabled={editing?.base}
              onChange={v => setForm({ ...form, active: v })}
              title={editing?.base ? 'A moeda base fica sempre ativa' : undefined} />
          </div>
        </form>
      </Modal>
    </div>
  );
};
