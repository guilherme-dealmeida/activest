import { useQuery } from '@tanstack/react-query';
import { assetsApi } from '@/api/assets';
import { AssetCategory, Indexer, RateType } from '@/types';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { PercentInput } from '@/components/ui/PercentInput';

export interface FixedIncomeValues {
  assetCategoryId: number | null;
  issuer: string;
  maturityDate: string;
  rateType: RateType | null;
  indexer: Indexer | null;
  indexerRate: number | null;
}

interface Props {
  typeId: number;
  values: FixedIncomeValues;
  /**
   * true quando o ativo escolhido traz parâmetros públicos (asset_terms).
   * Hoje é o Tesouro Direto: vencimento e forma de remuneração de "Tesouro
   * IPCA+ 2029" são do papel e valem para todo mundo, então não se digita — só
   * a taxa, que é do dia da compra, continua editável.
   */
  lockedTerms: boolean;
  /**
   * Emissor não se pergunta ao Tesouro Direto: é sempre o Tesouro Nacional.
   * Num CDB é a informação que distingue duas aplicações do mesmo produto.
   */
  showIssuer?: boolean;
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

/**
 * Dados do PAPEL, não do ativo.
 *
 * O bloco voltou a ser editável, mas o que ele preenche mudou de lugar: não
 * cria mais ativo no catálogo, e sim uma nota do usuário (fixed_income_notes).
 * Dois CDBs de bancos diferentes são duas notas — duas linhas na carteira, cada
 * uma com sua taxa — em vez de dois ativos globais que apareciam na busca de
 * todo mundo.
 */
export const FixedIncomeFields = ({
  typeId, values, lockedTerms, showIssuer = true, onChange
}: Props) => {
  const { data: categories } = useQuery<AssetCategory[]>({
    queryKey: ['assetCategories', typeId],
    queryFn: () => assetsApi.getCategories(typeId) as any,
    enabled: !!typeId
  });

  const precisaIndice = values.rateType === 'POS_FIXADO' || values.rateType === 'HIBRIDO';
  const { label, hint } = rateLabel(values.rateType, values.indexer);

  return (
    <div className="rounded-md border border-accent/20 bg-accent/5 p-4 space-y-4">
      <p className="text-xs uppercase tracking-wider text-accent/80 font-medium">
        Dados do título
        {lockedTerms && (
          <span className="text-gray-500 normal-case tracking-normal"> · prazo e remuneração são do papel</span>
        )}
      </p>

      {showIssuer && (
        <Input
          label="Emissor"
          placeholder="Ex: Banco BTG"
          value={values.issuer}
          onChange={(e) => onChange({ issuer: e.target.value })}
        />
      )}

      <Input
        label="Vencimento"
        type="date"
        value={values.maturityDate}
        disabled={lockedTerms}
        onChange={(e) => onChange({ maturityDate: e.target.value })}
      />

      <Select
        label="Como remunera"
        value={values.rateType ?? ''}
        disabled={lockedTerms}
        onChange={(e) => {
          const rt = (e.target.value || null) as RateType | null;
          // Prefixado não tem índice: limpa para não enviar combinação que o
          // banco rejeita (ck_fixed_income_notes_rate_coherence).
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
            disabled={lockedTerms}
            onChange={(e) => onChange({ indexer: (e.target.value || null) as Indexer | null })}
          >
            <option value="">Selecione</option>
            {INDEXERS.map(i => <option key={i} value={i}>{i}</option>)}
          </Select>
        )}

        {/* A taxa nunca trava, nem no Tesouro: ela é do dia da compra, não do
            título. Dois investidores levam o mesmo papel a IPCA + 6,2% e
            IPCA + 5,8% conforme quando compraram. */}
        <PercentInput
          label={label}
          hint={hint}
          value={values.indexerRate}
          disabled={!values.rateType}
          onChange={(v) => onChange({ indexerRate: v || null })}
        />
      </div>
    </div>
  );
};
