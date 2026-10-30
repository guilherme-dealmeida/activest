import { useId } from 'react';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Switch } from '@/components/ui/Switch';
import { CurrencyInput } from '@/components/ui/CurrencyInput';
import { PercentInput } from '@/components/ui/PercentInput';
import { MaskedNumberInput } from '@/components/ui/MaskedNumberInput';
import { GoalField, Option } from '@/types/goals';
import { FieldLabel } from './FieldLabel';
import { assetText } from '@/utils/assetLabel';
import { GoalAssetPicker } from './GoalAssetPicker';

interface Props {
  field: GoalField;
  value: unknown;
  onChange: (value: unknown) => void;
  /** Obrigatório agora (o campo pode ser obrigatório só com outra escolha — requiredIf). */
  required: boolean;
  /** Opções vindas da carteira (item da dimensão). */
  options?: Option[];
  /** Ativo da meta (CATALOG_ASSET): o ticker e o tipo gravados com ela, para mostrar o já escolhido. */
  assetLabel?: { ticker?: string | null; assetType?: string | null };
}

const num = (v: unknown): number | null => (v === null || v === undefined || v === '' ? null : Number(v));

/**
 * Um campo do catálogo de tipos (docs/planos/PLANO_METAS.md, Anexo A 12): o formulário é montado a partir de
 * GET /goals/types — tipo novo não pede tela nova. Obrigatório com asterisco vermelho; opcional vazio é vazio (nunca
 * zero).
 */
export const GoalFieldInput = ({ field, value, onChange, required, options, assetLabel }: Props) => {
  const id = useId();
  const info = field.help ?? undefined;
  const clear = () => onChange(null);
  const label = <FieldLabel htmlFor={id} required={required} info={info}>{field.label}</FieldLabel>;

  if (field.kind === 'BOOLEAN') {
    return (
      <div className="flex h-10 items-center justify-between gap-3 sm:mt-7">
        <span className="text-sm text-gray-300">{field.label}</span>
        <Switch checked={Boolean(value)} onChange={onChange} label={field.label} />
      </div>
    );
  }

  let input;
  switch (field.kind) {
    case 'MONEY':
      input = <CurrencyInput id={id} value={num(value)} onChange={onChange} allowEmpty onClear={clear} />;
      break;
    case 'PERCENT':
      input = <PercentInput id={id} value={num(value)} onChange={onChange} allowEmpty onClear={clear} />;
      break;
    case 'NUMBER':
      input = <MaskedNumberInput id={id} value={num(value)} onChange={onChange} decimals={2} allowEmpty onClear={clear} />;
      break;
    case 'INTEGER':
      input = (
        <Input id={id} type="number" min={field.min ?? undefined} max={field.max ?? undefined} step={1}
          value={value === null || value === undefined ? '' : String(value)}
          onChange={e => onChange(e.target.value === '' ? null : Math.trunc(Number(e.target.value)))} />
      );
      break;
    case 'DATE':
      input = <Input id={id} type="date" value={(value as string) ?? ''} onChange={e => onChange(e.target.value || null)} />;
      break;
    case 'CHOICE':
      input = (
        <Select id={id} value={(value as string) ?? ''} onChange={e => onChange(e.target.value || null)}>
          {!required && <option value="">—</option>}
          {(field.options ?? []).map(o => <option key={o.value} value={o.value}>{assetText(o.label)}</option>)}
        </Select>
      );
      break;
    case 'CATALOG_ASSET':
      input = <GoalAssetPicker value={value} ticker={assetLabel?.ticker} assetTypeLabel={assetLabel?.assetType}
        onChange={onChange} />;
      break;
    case 'MULTI_CHOICE': {
      const list = Array.isArray(value) ? (value as string[]) : [];
      input = (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1.5">
          {(field.options ?? []).map(o => (
            <label key={o.value} className="flex items-center gap-2 text-sm text-gray-300">
              <input type="checkbox" className="accent-[#c9a84c]" checked={list.includes(o.value)}
                onChange={e => onChange(e.target.checked ? [...list, o.value] : list.filter(x => x !== o.value))} />
              {o.label}
            </label>
          ))}
        </div>
      );
      break;
    }
    default: {
      if (options && options.length > 0 && field.kind === 'TEXT') {
        input = (
          <Select id={id} value={(value as string) ?? ''} onChange={e => onChange(e.target.value || null)}>
            <option value="">Escolha…</option>
            {options.map(o => <option key={o.value} value={o.value}>{assetText(o.label)}</option>)}
          </Select>
        );
      } else {
        const listId = `${id}-options`;
        input = (
          <>
            <Input id={id} list={options ? listId : undefined} value={(value as string) ?? ''}
              onChange={e => onChange(e.target.value)} />
            {options && <datalist id={listId}>{options.map(o => <option key={o.value} value={o.value}>{assetText(o.label)}</option>)}</datalist>}
          </>
        );
      }
    }
  }
  return <div>{label}{input}</div>;
};
