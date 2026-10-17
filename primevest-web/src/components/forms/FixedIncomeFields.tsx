import { Indexer, RateType } from '@/types';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { PercentInput } from '@/components/ui/PercentInput';
import { LabelWithInfo } from '@/components/ui/InfoTooltip';
import { IssuerPicker } from '@/components/forms/IssuerPicker';
import { IssuerPick } from '@/components/forms/issuerPick';

export interface FixedIncomeValues {
  assetCategoryId: number | null;
  /** Emissora (fixed_income_issuers): obrigatória no título de renda fixa; no Tesouro é o Tesouro Nacional. */
  issuer: IssuerPick | null;
  /** Distribuidora: onde o título foi comprado (RF e Tesouro). */
  distributor: IssuerPick | null;
  /** Código do papel na distribuidora (opcional; só letras e números). */
  paperCode: string;
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
   * Nada se digita: o título já existe e a operação é sobre ele.
   *
   * <p>É o caso de Comprar e Vender abertos pela carteira, onde o título vem
   * da linha clicada. Mostrar os dados continua valendo — eles são o que
   * distingue uma aplicação da outra —, mas editá-los ali mudaria o título por
   * tabela.</p>
   */
  readOnly?: boolean;
  /**
   * Emissor não se pergunta ao Tesouro Direto: é sempre o Tesouro Nacional.
   * Num CDB é a informação que distingue duas aplicações do mesmo produto.
   */
  showIssuer?: boolean;
  /**
   * A taxa é do dia da COMPRA. Numa venda de Tesouro Direto ela não existe: a
   * venda é do ativo e pode consumir títulos de taxas diferentes, escolhidos
   * pelo FIFO.
   */
  showRate?: boolean;
  /** Produto do título: a emissora é filtrada pelos tipos que ele aceita (CDB só bancos). */
  ticker?: string | null;
  /** A distribuidora se escolhe mesmo com o resto travado: é o caso da venda do Tesouro. */
  distributorReadOnly?: boolean;
  /** Venda do Tesouro: só as distribuidoras com título aberto do ativo (D7). */
  distributorOptions?: IssuerPick[];
  /** Aviso abaixo da distribuidora (a regra de saída muda, na edição de RF). */
  distributorWarning?: string | null;
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
      return { label: 'Taxa ao ano', hint: 'Ex: 12,500 para 12,5% a.a.' };
    case 'POS_FIXADO':
      return { label: `Percentual do ${indexer ?? 'índice'}`, hint: 'Ex: 110,000 para 110% do CDI.' };
    case 'HIBRIDO':
      return { label: 'Spread ao ano', hint: `Ex: 6,000 para ${indexer ?? 'IPCA'} + 6% a.a.` };
    default:
      return { label: 'Taxa', hint: 'Escolha antes como o título remunera.' };
  }
}

/**
 * Dados do PAPEL, não do ativo.
 *
 * O bloco preenche uma nota do usuário (fixed_income_notes), não um ativo no
 * catálogo. Dois CDBs de bancos diferentes são duas notas — duas linhas na
 * carteira, cada uma com sua taxa — em vez de dois ativos globais que
 * apareciam na busca de todo mundo.
 */
export const FixedIncomeFields = ({
  values, lockedTerms, readOnly = false, showIssuer = true, showRate = true, ticker,
  distributorReadOnly, distributorOptions, distributorWarning, onChange
}: Props) => {
  const precisaIndice = values.rateType === 'POS_FIXADO' || values.rateType === 'HIBRIDO';
  const { label, hint } = rateLabel(values.rateType, values.indexer);
  const travado = readOnly || lockedTerms;

  return (
    <div className="rounded-md border border-accent/20 bg-accent/5 p-4 space-y-4">
      <p className="text-xs uppercase tracking-wider text-accent/80 font-medium">
        <LabelWithInfo
          // Papel com parâmetros públicos (Tesouro, o único tipo sem emissor)
          // vem antes: na venda dele o quadro também é somente leitura, mas
          // não há "o título selecionado" — a venda é do ativo, por FIFO.
          info={lockedTerms && !showIssuer
            ? 'Vencimento e forma de remuneração são do papel e valem para todos os investidores. A taxa é a do dia da sua compra.'
            : readOnly
              ? 'Dados do título selecionado na carteira. A operação é sobre ele, por isso não podem ser alterados aqui.'
              : lockedTerms
                ? 'Vencimento e forma de remuneração são do papel e valem para todos os investidores. A taxa é a do dia da sua compra.'
                : undefined}
        >
          Dados do título
        </LabelWithInfo>
      </p>

      {/* Duas colunas de campos: com emissora, distribuidora e código, o quadro numa coluna só ficava mais alto que
          a transação inteira. Os pares saem na ordem de leitura: emissora | distribuidora, código | vencimento,
          remuneração | índice e a taxa. Em tela estreita volta a uma coluna. */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-start">
      {showIssuer && (
        <IssuerPicker
          label="Emissora"
          info="Quem emitiu o título e deve o dinheiro. A lista mostra só as empresas que emitem este produto."
          role="ISSUER"
          ticker={ticker}
          value={values.issuer}
          disabled={readOnly}
          onChange={(issuer) => onChange({ issuer })}
        />
      )}

      <div>
        <IssuerPicker
          label="Distribuidora"
          info={distributorOptions
            ? 'Onde o título foi comprado. Na venda aparecem só as distribuidoras com título aberto deste ativo.'
            : 'Onde o título foi comprado (corretora, banco ou cooperativa). É ela que define a regra de saída dos resgates.'}
          role="DISTRIBUTOR"
          value={values.distributor}
          options={distributorOptions}
          disabled={distributorReadOnly ?? readOnly}
          onChange={(distributor) => onChange({ distributor })}
        />
        {distributorWarning && <p className="mt-1 text-xs text-accent">{distributorWarning}</p>}
      </div>

      {showIssuer && (
        <Input
          label="Código do papel"
          info="Opcional. O código do título na distribuidora (ex: CDB0123ABCD). Com código, o aporte só entra no título de mesmo código."
          placeholder="Ex: CDB0123ABCD"
          maxLength={40}
          value={values.paperCode}
          disabled={readOnly}
          onChange={(e) => onChange({ paperCode: e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '') })}
        />
      )}

      <Input
        label="Vencimento"
        type="date"
        value={values.maturityDate}
        disabled={travado}
        onChange={(e) => onChange({ maturityDate: e.target.value })}
      />

      <Select
        label="Como remunera"
        value={values.rateType ?? ''}
        disabled={travado}
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

        {precisaIndice && (
          <Select
            label="Índice"
            value={values.indexer ?? ''}
            disabled={travado}
            onChange={(e) => onChange({ indexer: (e.target.value || null) as Indexer | null })}
          >
            <option value="">Selecione</option>
            {INDEXERS.map(i => <option key={i} value={i}>{i}</option>)}
          </Select>
        )}

        {/* A taxa não trava pelo papel, nem no Tesouro: ela é do dia da compra.
            Dois investidores levam o mesmo papel a IPCA + 6,2% e IPCA + 5,8%
            conforme quando compraram. Some na venda de Tesouro, onde a
            operação é do ativo e os títulos saem por FIFO.

            Três casas na máscara: indexer_rate é NUMERIC(10,4), e taxas de CDB
            e Tesouro chegam com fração de décimo ("IPCA + 6,125%"). */}
        {showRate && (
          <PercentInput
            label={label}
            info={hint}
            decimals={3}
            value={values.indexerRate}
            disabled={readOnly || !values.rateType}
            onChange={(v) => onChange({ indexerRate: v || null })}
          />
        )}
      </div>
    </div>
  );
};
