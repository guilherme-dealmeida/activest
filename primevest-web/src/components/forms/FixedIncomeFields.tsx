import { useQuery } from '@tanstack/react-query';
import { assetsApi } from '@/api/assets';
import { AssetCategory, Indexer, RateType } from '@/types';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { PercentInput } from '@/components/ui/PercentInput';

export interface FixedIncomeValues {
  assetName: string;
  assetCategoryId: number | null;
  maturityDate: string;
  rateType: RateType | null;
  indexer: Indexer | null;
  indexerRate: number | null;
}

interface Props {
  typeId: number;
  values: FixedIncomeValues;
  /** true quando o ticker casou com um ativo já cadastrado: campos viram leitura. */
  readOnly: boolean;
  /**
   * Nem todo tipo de renda fixa usa categoria. No Tesouro Direto a família já
   * está no nome do título ("Tesouro Selic 2031"), então o campo seria uma
   * segunda fonte para o mesmo fato — e duas fontes divergem.
   */
  showCategory?: boolean;
  onChange: (patch: Partial<FixedIncomeValues>) => void;
}

const INDEXERS: Indexer[] = ['CDI', 'SELIC', 'IPCA', 'IGPM', 'INPC', 'TR'];

const RATE_TYPES: { value: RateType; label: string }[] = [
  { value: 'PREFIXADO',  label: 'Prefixado — taxa fixa' },
  { value: 'POS_FIXADO', label: 'Pós-fixado — percentual de um índice' },
  { value: 'HIBRIDO',    label: 'Híbrido — índice mais spread' },
];

/**
 * O rótulo e o sufixo do campo de taxa mudam conforme rate_type, porque o
 * número significa coisas diferentes: 12,5% a.a. num prefixado, 110% do índice
 * num pós-fixado, 6% de spread num híbrido.
 */
function rateLabel(rateType: RateType | null, indexer: Indexer | null): { label: string; hint: string } {
  switch (rateType) {
    case 'PREFIXADO':
      return { label: 'Taxa ao ano', hint: 'Ex: 12,50 para 12,5% a.a.' };
    case 'POS_FIXADO':
      return { label: `Percentual do ${indexer ?? 'índice'}`, hint: 'Ex: 110,00 para 110% do CDI' };
    case 'HIBRIDO':
      return { label: 'Spread ao ano', hint: `Ex: 6,00 para ${indexer ?? 'IPCA'} + 6% a.a.` };
    default:
      return { label: 'Taxa', hint: 'Escolha antes como o título remunera' };
  }
}

export const FixedIncomeFields = ({ typeId, values, readOnly, showCategory = true, onChange }: Props) => {
  const { data: categories } = useQuery<AssetCategory[]>({
    queryKey: ['assetCategories', typeId],
    queryFn: () => assetsApi.getCategories(typeId) as any,
    enabled: !!typeId && showCategory
  });

  const precisaIndice = values.rateType === 'POS_FIXADO' || values.rateType === 'HIBRIDO';
  const { label, hint } = rateLabel(values.rateType, values.indexer);

  return (
    <div className="rounded-md border border-accent/20 bg-accent/5 p-4 space-y-4">
      <p className="text-xs uppercase tracking-wider text-accent/80 font-medium">
        Dados do título {readOnly && <span className="text-gray-500 normal-case tracking-normal">· já cadastrado</span>}
      </p>

      <Input
        label="Nome do título"
        value={values.assetName}
        disabled={readOnly}
        onChange={(e) => onChange({ assetName: e.target.value })}
        placeholder="Ex: CDB BTG Pactual 2027"
      />

      <div className={showCategory ? 'grid grid-cols-1 sm:grid-cols-2 gap-4' : ''}>
        {showCategory && (
          <Select
            label="Categoria"
            value={values.assetCategoryId ?? ''}
            disabled={readOnly}
            onChange={(e) => onChange({ assetCategoryId: e.target.value ? Number(e.target.value) : null })}
          >
            <option value="">Selecione</option>
            {(categories || []).map(c => (
              <option key={c.id} value={c.id}>{c.description || c.name}</option>
            ))}
          </Select>
        )}

        <Input
          label="Vencimento"
          type="date"
          value={values.maturityDate}
          disabled={readOnly}
          onChange={(e) => onChange({ maturityDate: e.target.value })}
        />
      </div>

      <Select
        label="Como remunera"
        value={values.rateType ?? ''}
        disabled={readOnly}
        onChange={(e) => {
          const rt = (e.target.value || null) as RateType | null;
          // Prefixado não tem índice: limpa para não enviar combinação que o
          // banco rejeita (ck_assets_rate_coherence).
          onChange({ rateType: rt, indexer: rt === 'PREFIXADO' ? null : values.indexer });
        }}
      >
        <option value="">Selecione</option>
        {RATE_TYPES.map(rt => <option key={rt.value} value={rt.value}>{rt.label}</option>)}
      </Select>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {precisaIndice && (
          <Select
            label="Índice"
            value={values.indexer ?? ''}
            disabled={readOnly}
            onChange={(e) => onChange({ indexer: (e.target.value || null) as Indexer | null })}
          >
            <option value="">Selecione</option>
            {INDEXERS.map(i => <option key={i} value={i}>{i}</option>)}
          </Select>
        )}

        <PercentInput
          label={label}
          hint={hint}
          value={values.indexerRate}
          disabled={readOnly || !values.rateType}
          onChange={(v) => onChange({ indexerRate: v || null })}
        />
      </div>
    </div>
  );
};
