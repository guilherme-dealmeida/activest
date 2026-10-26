package com.activest.domain.goal.type;

import com.activest.domain.goal.GoalType;
import com.activest.domain.goal.engine.GoalScope;
import com.activest.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

import static com.activest.domain.goal.type.FieldKind.*;
import static com.activest.domain.goal.type.FieldSpec.TARGET_DATE;
import static com.activest.domain.goal.type.FieldSpec.TARGET_VALUE;
import static com.activest.domain.goal.type.ScopeKind.*;

/**
 * O catalogo de tipos de meta (docs/planos/PLANO_METAS.md, Anexo A 4 e 5): o que cada tipo pede ao usuario e a
 * validacao do cadastro. Regra pura — o ticker e conferido no catalogo de ativos pelo servico.
 *
 * <p><b>G1</b>: nenhum tipo declara campo de valor atual, progresso, falta, data estimada, renda atual, alocacao ou
 * concentracao; campo que o tipo nao declara e recusado com 400 (cenario 17).</p>
 */
public final class GoalTypeCatalog {

    /** Nomes que sao sempre calculados: aparecem na mensagem de recusa (cenario 17). */
    public static final Set<String> CALCULATED = Set.of("currentValue", "currentAmount", "progress", "progressPct",
            "remaining", "estimatedDate", "currentIncome", "allocation", "concentration", "indicator");

    private static final List<FieldSpec.Option> PERIODICITY = List.of(opt("MONTH", "Por mês"), opt("YEAR", "Por ano"));
    private static final List<FieldSpec.Option> INCOME_UNIT = List.of(opt("MONTH", "Por mês"), opt("YEAR", "Por ano"));
    private static final List<FieldSpec.Option> RETURN_SOURCE = List.of(
            opt("CDI", "CDI acumulado em 12 meses"),
            opt("PORTFOLIO", "Rentabilidade da sua carteira (precisa de 24 meses)"));
    private static final List<FieldSpec.Option> PERIOD = List.of(
            opt("SINCE_START", "Desde o início"), opt("YTD", "No ano"), opt("LAST_12M", "Últimos 12 meses"),
            opt("CUSTOM", "Entre datas"));
    private static final List<FieldSpec.Option> RETURN_BASIS = List.of(
            opt("ACCUMULATED", "Acumulada no período"), opt("ANNUALIZED", "Anualizada (período ≥ 12 meses)"));
    private static final List<FieldSpec.Option> BENCHMARKS = List.of(
            opt("CDI", "CDI"), opt("SELIC", "Selic"), opt("IPCA", "IPCA"), opt("IBOV", "Ibovespa"), opt("IFIX", "IFIX"));
    private static final List<FieldSpec.Option> BENCHMARK_FORM = List.of(
            opt("INDEX", "O próprio índice"), opt("PERCENT_OF", "% do índice (ex.: 110% do CDI)"),
            opt("PLUS_RATE", "Índice + taxa ao ano (só IPCA)"));
    private static final List<FieldSpec.Option> RULE = List.of(
            opt("MIN", "Mínimo"), opt("MAX", "Máximo"), opt("RANGE", "Faixa (de/até)"));
    private static final List<FieldSpec.Option> ALLOCATION_DIM = List.of(
            opt("TYPE", "Tipo de ativo"), opt("CATEGORY", "Categoria"), opt("INDEXER", "Indexador"),
            opt("ISSUER", "Emissor"), opt("ASSET", "Ativo"));
    private static final List<FieldSpec.Option> CONCENTRATION_DIM = List.of(
            opt("ASSET", "Ativo"), opt("ISSUER", "Emissor"), opt("CATEGORY", "Categoria / setor"),
            opt("TYPE", "Tipo de ativo"), opt("INDEXER", "Indexador"));
    private static final List<FieldSpec.Option> DRAWDOWN_WINDOW = List.of(
            opt("SINCE_START", "Desde o início"), opt("LAST_MONTHS", "Últimos N meses"));
    private static final List<FieldSpec.Option> VALUE_BASIS = List.of(
            opt("MARKET", "Valor de mercado"), opt("COST", "Valor aplicado (custo)"));
    public static final List<FieldSpec.Option> QUALITY_ISSUES = List.of(
            opt("STALE_PRICE", "Cotação defasada"), opt("NO_PRICE", "Sem cotação"),
            opt("NO_CURVE", "Título sem curva"), opt("BONUS_NO_COST", "Bonificação sem custo"),
            opt("DIVIDEND_NO_DATE", "Provento sem data de pagamento"),
            opt("IMPORT_PENDING", "Importação aguardando confirmação"));

    private static final List<ScopeKind> PORTFOLIO_SCOPES = List.of(ALL, TYPE, CATEGORY, INDEXER, ISSUER, ASSET, POSITIONS);
    private static final List<ScopeKind> INCOME_SCOPES = List.of(ALL, TYPE, CATEGORY, ISSUER, ASSET);

    private static final Map<GoalType, GoalTypeSpec> SPECS = new EnumMap<>(GoalType.class);

    static {
        add(GoalType.PAT_1, "Chegar a um valor de patrimônio, com ou sem data para isso. O valor considerado é o mesmo da sua Carteira.",
                List.of(money(TARGET_VALUE, "Valor-alvo", true), date(TARGET_DATE, "Prazo", false)),
                PORTFOLIO_SCOPES, ReservationRule.EXPLICIT,
                List.of("Quanto você já tem", "Quanto falta", "Quanto veio dos seus aportes e quanto da valorização",
                        "Quando deve chegar lá, ou quanto aportar por mês para chegar no prazo"),
                List.of("Posições e valorização", "Transações", "Proventos", "CDI e IPCA (premissas)"), List.of(),
                true);
        add(GoalType.APO_1, "Juntar um valor em aportes (o que você investiu menos o que resgatou) a partir de uma data.",
                List.of(money(TARGET_VALUE, "Valor-alvo dos aportes", true),
                        field("startDate", "Início da contagem", DATE, false, null, null,
                                "Padrão: o dia da criação da meta; pode ser retroativo", null, null, null),
                        date(TARGET_DATE, "Prazo", false)),
                PORTFOLIO_SCOPES, ReservationRule.DEFAULT_WITH,
                List.of("Quanto você já aportou", "Sua média por mês", "Quanto aportar por mês para chegar no prazo",
                        "Quando chega lá no seu ritmo atual"),
                List.of("Transações"), List.of("Reinvestir proventos conta como aporte"), false);
        add(GoalType.APO_2, "Aportar um valor todo mês (ou todo ano), podendo exigir uma sequência de períodos seguidos.",
                List.of(money(TARGET_VALUE, "Valor por período", true),
                        choice("periodicity", "Periodicidade", true, "MONTH", PERIODICITY),
                        field("startDate", "Início", DATE, false, null, null, "Padrão: o mês da criação", null, null, null),
                        field("endDate", "Fim", DATE, false, null, null, null, null, null, null),
                        field("streak", "Sequência desejada (períodos seguidos)", INTEGER, false, null, null, null, 1.0, 600.0, null),
                        field("tolerance", "Tolerância (R$)", MONEY, false, null, null, null, 0.0, null, null)),
                PORTFOLIO_SCOPES, ReservationRule.DEFAULT_WITH,
                List.of("Quanto você aportou em cada período", "Quantos períodos cumpriu", "Sua sequência atual e a maior",
                        "Quanto falta no mês (ou ano) corrente"),
                List.of("Transações"), List.of(), false);
        add(GoalType.REN_1, "Receber uma renda média em proventos (dividendos, JCP, rendimentos), já descontado o imposto retido.",
                List.of(money(TARGET_VALUE, "Renda-alvo", true), choice("unit", "Unidade", true, "MONTH", INCOME_UNIT),
                        field("window", "Janela da média (meses)", INTEGER, false, 12, null, null, 1.0, 60.0, null),
                        date(TARGET_DATE, "Prazo", false)),
                INCOME_SCOPES, ReservationRule.DEFAULT_WITH,
                List.of("Sua renda média dos últimos meses", "Proventos já anunciados a receber",
                        "Quando a renda deve chegar ao alvo, ou quanto será no prazo"),
                List.of("Proventos", "Posições"),
                List.of("Juros da renda fixa e cupons do Tesouro não entram: só viram dinheiro no resgate"),
                true);
        add(GoalType.REN_2, "Fazer a renda em proventos cobrir as suas despesas do mês (ou parte delas).",
                List.of(field("expense", "Despesa mensal de referência", MONEY, true, null, null, null, 0.01, null, null),
                        field("expenseDate", "Data da despesa", DATE, false, null, null, "Padrão: hoje", null, null, null),
                        field("ipcaCorrection", "Corrigir a despesa pelo IPCA", BOOLEAN, false, false, null, null, null, null, null),
                        field(TARGET_VALUE, "Cobertura desejada (%)", PERCENT, false, 100, null, null, 1.0, 1000.0, null),
                        field("window", "Janela da média (meses)", INTEGER, false, 12, null, null, 1.0, 60.0, null)),
                INCOME_SCOPES, ReservationRule.DEFAULT_WITH,
                List.of("Sua renda média", "Quanto da despesa ela cobre", "Quanto falta de renda por mês"),
                List.of("Proventos", "IPCA"), List.of("Você informa a despesa na própria meta"), true);
        add(GoalType.REN_3, "Fazer a renda em proventos passar do quanto você aporta por mês.",
                List.of(field("window", "Janela (meses)", INTEGER, false, 12, null,
                        "Quantos meses fechados entram nas duas médias, a da renda e a do aporte (12 = o último ano)", 1.0, 60.0, null)),
                INCOME_SCOPES, ReservationRule.DEFAULT_WITH,
                List.of("Sua renda média", "Seu aporte médio", "Quando a renda deve passar do aporte"),
                List.of("Proventos", "Transações"), List.of(), true);
        add(GoalType.RNT_1, "Alcançar uma rentabilidade num período, medida como na tela Rentabilidade (sem contar aportes e resgates como ganho).",
                List.of(field(TARGET_VALUE, "Rentabilidade-alvo (%)", PERCENT, true, null, null,
                                "Com 0% e a opção real, a meta é ganhar da inflação", -99.0, 10000.0, null),
                        choice("basis", "Base", true, "ACCUMULATED", RETURN_BASIS),
                        field("real", "Real (descontado o IPCA)", BOOLEAN, false, false, null, null, null, null, null),
                        choice("period", "Período", true, "LAST_12M", PERIOD),
                        field("from", "De", DATE, false, null, null, null, null, null, "period=CUSTOM"),
                        field("to", "Até", DATE, false, null, null, null, null, null, null)),
                PORTFOLIO_SCOPES, ReservationRule.DEFAULT_WITH,
                List.of("Sua rentabilidade no período", "Descontada a inflação, se quiser", "Ao ano, para períodos de 12 meses ou mais"),
                List.of("Série histórica da carteira", "IPCA"),
                List.of(), false);
        add(GoalType.RNT_2, "Render mais que um índice (CDI, IPCA, Ibovespa...) no mesmo período.",
                List.of(choice("index", "Índice", true, "CDI", BENCHMARKS),
                        choice("form", "Forma", true, "INDEX", BENCHMARK_FORM),
                        field("rate", "Percentual do índice ou taxa ao ano (%)", PERCENT, false, null, null, null, 0.0, 1000.0,
                                "form=PERCENT_OF,PLUS_RATE"),
                        field(TARGET_VALUE, "Quanto acima do índice (pontos percentuais)", PERCENT, false, 0, null,
                                "0 = só superar o índice", -100.0, 1000.0, null),
                        choice("period", "Período", true, "LAST_12M", PERIOD),
                        field("from", "De", DATE, false, null, null, null, null, null, "period=CUSTOM"),
                        field("to", "Até", DATE, false, null, null, null, null, null, null)),
                PORTFOLIO_SCOPES, ReservationRule.DEFAULT_WITH,
                List.of("Sua rentabilidade e a do índice no mesmo período", "A diferença entre as duas", "Quanto % do índice você rendeu"),
                List.of("Série histórica da carteira", "Índices"),
                List.of("Se o índice ainda não foi divulgado, a comparação vai até o último dado publicado"), false);
        add(GoalType.ALO_1, "Manter quanto um tipo de ativo, setor, indexador, emissor ou ativo representa na carteira: um mínimo, um máximo ou uma faixa.",
                List.of(choice("dimension", "Dimensão", true, "TYPE", ALLOCATION_DIM),
                        field("item", "Item", TEXT, true, null, null, null, null, null, null),
                        choice("rule", "Regra", true, "MIN", RULE),
                        field("min", "Mínimo (%)", PERCENT, false, null, null, null, 0.0, 100.0, "rule=MIN,RANGE"),
                        field("max", "Máximo (%)", PERCENT, false, null, null, null, 0.0, 100.0, "rule=MAX,RANGE"),
                        date(TARGET_DATE, "Prazo para chegar (opcional)", false)),
                List.of(ALL), ReservationRule.DEFAULT_WITHOUT,
                List.of("Quanto ele representa hoje", "Quanto está sobrando ou faltando", "Quanto aportar ou reduzir para voltar (só uma referência)"),
                List.of("Posições e valorização"), List.of(), false);
        add(GoalType.ALO_2, "Manter a carteira perto dos percentuais ideais da sua Estratégia.",
                List.of(field(TARGET_VALUE, "Tolerância (pontos percentuais)", PERCENT, false, 5, null, null, 0.0, 100.0, null)),
                List.of(), ReservationRule.NONE,
                List.of("Percentual atual e ideal de cada tipo", "O maior desvio", "Os tipos fora da tolerância"),
                List.of("Estratégia", "Posições e valorização"),
                List.of("Usa os mesmos números do Rebalanceamento, sem a reserva de emergência"), false);
        add(GoalType.RIS_1, "Não deixar nenhum ativo, emissor, setor ou tipo passar de um percentual da carteira.",
                List.of(choice("dimension", "Dimensão", true, "ASSET", CONCENTRATION_DIM),
                        field(TARGET_VALUE, "Limite (%)", PERCENT, true, null, null, null, 0.01, 100.0, null)),
                List.of(ALL), ReservationRule.DEFAULT_WITHOUT,
                List.of("Quanto cada um representa", "O maior deles", "Os que passaram do limite e quanto"),
                List.of("Posições e valorização"), List.of(), false);
        add(GoalType.RIS_2, "Limitar quanto a carteira pode cair desde o seu melhor momento.",
                List.of(field(TARGET_VALUE, "Queda máxima (%)", PERCENT, true, null, null, null, 0.01, 100.0, null),
                        choice("window", "Janela", true, "SINCE_START", DRAWDOWN_WINDOW),
                        field("months", "Meses", INTEGER, false, 12, null, null, 2.0, 600.0, "window=LAST_MONTHS")),
                PORTFOLIO_SCOPES, ReservationRule.DEFAULT_WITH,
                List.of("A queda atual desde o pico", "A maior queda no período, com as datas"),
                List.of("Série histórica da carteira"), List.of("Resgates não contam como queda"), false);
        add(GoalType.RIS_3, "Manter os dados da carteira em dia: cotações, custos de bonificação, datas de pagamento e importações.",
                List.of(field(TARGET_VALUE, "Máximo de pendências", INTEGER, false, 0, null, null, 0.0, 1000.0, null),
                        new FieldSpec("kinds", "Pendências que contam", MULTI_CHOICE, false,
                                QUALITY_ISSUES.stream().map(FieldSpec.Option::value).toList(), QUALITY_ISSUES, null,
                                null, null, null)),
                List.of(), ReservationRule.NONE,
                List.of("Quais ativos têm pendência, desde quando e quanto pesam na carteira"),
                List.of("Posições", "Cotações", "Proventos", "Integrações"), List.of(), false);
        add(GoalType.ATV_1, "Chegar a uma quantidade de cotas de um ativo.",
                List.of(field("ticker", "Ativo", TICKER, true, null, null, null, null, null, null),
                        field(TARGET_VALUE, "Quantidade-alvo", NUMBER, true, null, null, null, 0.00000001, null, null),
                        date(TARGET_DATE, "Prazo", false)),
                List.of(), ReservationRule.NONE,
                List.of("Quantas cotas você tem (com desdobramentos e bonificações)", "Quanto custaria completar hoje",
                        "Quando chega lá no seu ritmo de compra"),
                List.of("Transações", "Eventos do ativo", "Cotação"), List.of("Não vale para renda fixa (CDB, LCI...), que não tem cotas"), true);
        add(GoalType.ATV_2, "Chegar a um valor investido num ativo.",
                List.of(field("ticker", "Ativo", TICKER, true, null, null, null, null, null, null),
                        money(TARGET_VALUE, "Valor-alvo", true),
                        choice("basis", "Base", true, "MARKET", VALUE_BASIS),
                        date(TARGET_DATE, "Prazo", false)),
                List.of(), ReservationRule.NONE,
                List.of("Quanto você tem nele", "Quanto falta", "Quando deve chegar lá"),
                List.of("Posições e valorização"), List.of(), true);
        add(GoalType.ATV_3, "Fazer os proventos de um ativo comprarem novas cotas dele todo mês (o \"número mágico\").",
                List.of(field("ticker", "Ativo", TICKER, true, null, null, null, null, null, null),
                        field(TARGET_VALUE, "Cotas por mês", NUMBER, false, 1, null, null, 0.00000001, null, null)),
                List.of(), ReservationRule.NONE,
                List.of("O provento médio por cota", "Quantas cotas os proventos compram hoje", "Quantas cotas você precisa ter"),
                List.of("Proventos", "Cotação", "Transações"), List.of(), false);
        add(GoalType.LP_1, "Estimar quanto você terá e quanto poderá receber por mês na aposentadoria.",
                List.of(date(TARGET_DATE, "Início da aposentadoria", true),
                        field(TARGET_VALUE, "Renda mensal desejada (em valores de hoje)", MONEY, false, null, null, null, 0.01, null, null),
                        field("withdrawalRate", "Retirada do patrimônio (% ao ano)", PERCENT, false, null, null,
                                "Sem ela, a renda estimada é só a dos proventos", 0.0, 100.0, null)),
                PORTFOLIO_SCOPES, ReservationRule.DEFAULT_WITH,
                List.of("Patrimônio estimado na data", "Renda mensal estimada", "Quanto aportar por mês para a renda que você quer"),
                List.of("Posições", "Transações", "Proventos", "CDI e IPCA"), List.of("É uma estimativa: depende de premissas de rendimento, aporte e inflação"),
                true);
    }

    private GoalTypeCatalog() {
    }

    public static List<GoalTypeSpec> all() {
        return List.copyOf(SPECS.values());
    }

    public static GoalTypeSpec of(GoalType type) {
        return SPECS.get(type);
    }

    // ------------------------------------------------------------------ validacao

    /** O cadastro de uma meta depois da validacao: padroes aplicados, valores normalizados. */
    public record Validated(GoalType type, BigDecimal targetValue, LocalDate targetDate, GoalScope scope,
                            Map<String, Object> params) {
    }

    /**
     * Valida o cadastro contra o tipo: campo que o tipo nao declara (inclusive os calculaveis) → 400; campo
     * obrigatorio vazio, opcao invalida, numero fora do limite → 422.
     */
    public static Validated validate(GoalType type, BigDecimal targetValue, LocalDate targetDate,
                                     Map<String, Object> scopeIn, Map<String, Object> paramsIn, LocalDate today,
                                     LocalDate currentTargetDate) {
        GoalTypeSpec spec = SPECS.get(type);
        if (spec == null) {
            throw new BusinessException("Tipo de meta desconhecido");
        }
        Map<String, Object> in = paramsIn != null ? new LinkedHashMap<>(paramsIn) : new LinkedHashMap<>();
        for (String k : in.keySet()) {
            if (CALCULATED.contains(k)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O campo \"" + k + "\" é calculado pelo Activest e não pode ser informado");
            }
            FieldSpec f = spec.field(k);
            if (f == null || f.isColumn()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O tipo " + type.code() + " não aceita o campo \"" + k + "\"");
            }
        }
        FieldSpec tv = spec.field(TARGET_VALUE);
        if (tv == null && targetValue != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O tipo " + type.code() + " não tem valor-alvo");
        }
        FieldSpec td = spec.field(TARGET_DATE);
        if (td == null && targetDate != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O tipo " + type.code() + " não tem prazo");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        for (FieldSpec f : spec.fields()) {
            if (f.isColumn()) {
                continue;
            }
            Object v = in.get(f.key());
            if (isEmpty(v)) {
                v = f.defaultValue();
            }
            if (isEmpty(v)) {
                if (required(f, in, out)) {
                    throw new BusinessException("Informe " + lower(f.label()) + ".");
                }
                continue;
            }
            out.put(f.key(), normalize(f, v));
        }
        BigDecimal value = targetValue;
        if (tv != null) {
            if (value == null && tv.defaultValue() != null) {
                value = new BigDecimal(tv.defaultValue().toString());
            }
            if (value == null && required(tv, in, out)) {
                throw new BusinessException("Informe " + lower(tv.label()) + ".");
            }
            if (value != null) {
                checkRange(tv, value.doubleValue());
                if (tv.kind() == INTEGER && value.stripTrailingZeros().scale() > 0) {
                    throw new BusinessException(tv.label() + ": use um número inteiro.");
                }
            }
        }
        if (td != null && td.required() && targetDate == null) {
            throw new BusinessException("Informe " + lower(td.label()) + ".");
        }
        boolean kept = targetDate != null && targetDate.equals(currentTargetDate);
        if (targetDate != null && !kept && !targetDate.isAfter(today) && type != GoalType.LP_1) {
            throw new BusinessException("O prazo tem de ser uma data futura.");
        }
        if (type == GoalType.LP_1 && !kept && !targetDate.isAfter(today)) {
            throw new BusinessException("O início da aposentadoria tem de ser uma data futura.");
        }
        crossChecks(type, out);
        GoalScope scope = scope(spec, scopeIn);
        return new Validated(type, value, targetDate, scope, out);
    }

    private static void crossChecks(GoalType type, Map<String, Object> p) {
        if (type == GoalType.ALO_1 && "RANGE".equals(p.get("rule"))) {
            double min = ((Number) p.get("min")).doubleValue();
            double max = ((Number) p.get("max")).doubleValue();
            if (min > max) {
                throw new BusinessException("Na faixa, o mínimo não pode passar do máximo.");
            }
        }
        if ("CUSTOM".equals(p.get("period")) && p.get("from") != null && p.get("to") != null
                && LocalDate.parse((String) p.get("to")).isBefore(LocalDate.parse((String) p.get("from")))) {
            throw new BusinessException("Período inválido: a data final é anterior à inicial.");
        }
        if (type == GoalType.RNT_2 && "PLUS_RATE".equals(p.get("form")) && !"IPCA".equals(p.get("index"))) {
            throw new BusinessException("Índice + taxa só vale para o IPCA (IPCA + x% a.a.).");
        }
        if (type == GoalType.APO_2 && p.get("endDate") != null && p.get("startDate") != null
                && LocalDate.parse((String) p.get("endDate")).isBefore(LocalDate.parse((String) p.get("startDate")))) {
            throw new BusinessException("O fim não pode ser antes do início.");
        }
        Object ticker = p.get("ticker");
        if (ticker instanceof String s) {
            p.put("ticker", s.trim().toUpperCase());
        }
    }

    @SuppressWarnings("unchecked")
    private static GoalScope scope(GoalTypeSpec spec, Map<String, Object> in) {
        Map<String, Object> s = in != null ? in : Map.of();
        if (spec.reservation() == ReservationRule.NONE) {
            if (!s.isEmpty() && s.get("kind") != null && !"ALL".equals(s.get("kind"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O tipo " + spec.type().code() + " não tem escopo");
            }
            return GoalScope.all(null);
        }
        ScopeKind kind;
        try {
            kind = s.get("kind") == null ? ScopeKind.ALL : ScopeKind.valueOf(s.get("kind").toString());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Escopo inválido: " + s.get("kind"));
        }
        if (!spec.scopes().contains(kind)) {
            throw new BusinessException("O tipo " + spec.type().code() + " não aceita o escopo \"" + kind.label() + "\".");
        }
        List<String> values = strings(s.get("values"));
        if (kind != ScopeKind.ALL && values.isEmpty()) {
            throw new BusinessException("Escolha ao menos um item do escopo (" + kind.label().toLowerCase() + ").");
        }
        GoalScope.Reservation reservation = null;
        Object r = s.get("reservation");
        if (r != null && !r.toString().isBlank()) {
            try {
                reservation = GoalScope.Reservation.valueOf(r.toString());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Reserva de emergência inválida: " + r);
            }
        }
        if (reservation == null) {
            reservation = switch (spec.reservation()) {
                case EXPLICIT -> throw new BusinessException(
                        "Escolha se a meta conta a reserva de emergência (com ou sem ela).");
                case DEFAULT_WITHOUT -> GoalScope.Reservation.WITHOUT;
                default -> GoalScope.Reservation.WITH;
            };
        }
        return new GoalScope(kind, values, reservation, strings(s.get("excludeTypes")),
                strings(s.get("excludeCategories")));
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object o) {
        if (o == null) {
            return List.of();
        }
        if (o instanceof Collection<?> c) {
            return c.stream().filter(Objects::nonNull).map(Object::toString).map(String::trim)
                    .filter(x -> !x.isEmpty()).toList();
        }
        throw new BusinessException("Lista inválida no escopo.");
    }

    private static boolean required(FieldSpec f, Map<String, Object> in, Map<String, Object> out) {
        if (f.requiredIf() == null) {
            return f.required();
        }
        String[] parts = f.requiredIf().split("=");
        Object other = out.containsKey(parts[0]) ? out.get(parts[0]) : in.get(parts[0]);
        return other != null && Arrays.asList(parts[1].split(",")).contains(other.toString());
    }

    private static Object normalize(FieldSpec f, Object v) {
        try {
            switch (f.kind()) {
                case MONEY, PERCENT, NUMBER -> {
                    double d = new BigDecimal(v.toString()).doubleValue();
                    checkRange(f, d);
                    return new BigDecimal(v.toString());
                }
                case INTEGER -> {
                    BigDecimal b = new BigDecimal(v.toString());
                    if (b.stripTrailingZeros().scale() > 0) {
                        throw new BusinessException(f.label() + ": use um número inteiro.");
                    }
                    checkRange(f, b.doubleValue());
                    return b.intValueExact();
                }
                case DATE -> {
                    return LocalDate.parse(v.toString()).toString();
                }
                case BOOLEAN -> {
                    if (v instanceof Boolean b) {
                        return b;
                    }
                    String s = v.toString();
                    if (!s.equals("true") && !s.equals("false")) {
                        throw new BusinessException(f.label() + ": use sim ou não.");
                    }
                    return Boolean.parseBoolean(s);
                }
                case CHOICE -> {
                    String s = v.toString();
                    if (f.options().stream().noneMatch(o -> o.value().equals(s))) {
                        throw new BusinessException(f.label() + ": opção inválida (" + s + ").");
                    }
                    return s;
                }
                case MULTI_CHOICE -> {
                    List<String> list = strings(v);
                    for (String s : list) {
                        if (f.options().stream().noneMatch(o -> o.value().equals(s))) {
                            throw new BusinessException(f.label() + ": opção inválida (" + s + ").");
                        }
                    }
                    return list;
                }
                case TICKER, TEXT -> {
                    String s = v.toString().trim();
                    if (s.length() > 120) {
                        throw new BusinessException(f.label() + ": texto longo demais.");
                    }
                    return s;
                }
                default -> {
                    return v;
                }
            }
        } catch (NumberFormatException | DateTimeParseException | ArithmeticException e) {
            throw new BusinessException(f.label() + ": valor inválido (" + v + ").");
        }
    }

    private static void checkRange(FieldSpec f, double d) {
        if (f.min() != null && d < f.min()) {
            throw new BusinessException(f.label() + ": o mínimo é " + fmt(f.min()) + ".");
        }
        if (f.max() != null && d > f.max()) {
            throw new BusinessException(f.label() + ": o máximo é " + fmt(f.max()) + ".");
        }
        if (f.min() == null && (f.kind() == MONEY || f.kind() == NUMBER) && d <= 0) {
            throw new BusinessException(f.label() + ": informe um valor maior que zero.");
        }
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d).replace('.', ',');
    }

    private static boolean isEmpty(Object v) {
        return v == null || (v instanceof String s && s.isBlank()) || (v instanceof Collection<?> c && c.isEmpty());
    }

    private static String lower(String label) {
        return label.isEmpty() ? label : Character.toLowerCase(label.charAt(0)) + label.substring(1);
    }

    // ------------------------------------------------------------------ montagem

    private static void add(GoalType type, String description, List<FieldSpec> fields, List<ScopeKind> scopes,
                            ReservationRule reservation, List<String> calculates, List<String> data,
                            List<String> limitations) {
        add(type, description, fields, scopes, reservation, calculates, data, limitations, false);
    }

    private static void add(GoalType type, String description, List<FieldSpec> fields, List<ScopeKind> scopes,
                            ReservationRule reservation, List<String> calculates, List<String> data,
                            List<String> limitations, boolean assumptions) {
        List<FieldSpec> all = new ArrayList<>(fields);
        // Premissas das projecoes (Anexo A 6.1): fixa-las e opcional; vazias, sao automaticas (a tela mostra o automatico)
        if (assumptions || type.projection()) {
            all.add(field("returnRate", "Rendimento esperado (% ao ano)", PERCENT, false, null, null,
                    "Vem preenchido com o automático (CDI dos últimos 12 meses ou a rentabilidade da carteira). Altere para fixar um valor; apague para voltar ao automático", -50.0, 100.0, null));
            all.add(choice("returnSource", "Rendimento automático pelo", false, "CDI", RETURN_SOURCE));
            all.add(field("monthlyContribution", "Aporte por mês", MONEY, false, null, null,
                    "Vem preenchido com a sua média de aportes dos últimos 12 meses. Altere para fixar um valor; apague para voltar ao automático", 0.0, null, null));
            all.add(field("inflation", "Inflação esperada (% ao ano)", PERCENT, false, null, null,
                    "Vem preenchido com o IPCA dos últimos 12 meses. Altere para fixar um valor; apague para voltar ao automático", -20.0, 100.0, null));
        }
        SPECS.put(type, new GoalTypeSpec(type, description, List.copyOf(all), scopes, reservation, calculates, data,
                limitations));
    }

    private static FieldSpec money(String key, String label, boolean required) {
        return field(key, label, MONEY, required, null, null, null, 0.01, null, null);
    }

    private static FieldSpec date(String key, String label, boolean required) {
        return field(key, label, DATE, required, null, null, null, null, null, null);
    }

    private static FieldSpec choice(String key, String label, boolean required, Object def, List<FieldSpec.Option> options) {
        return new FieldSpec(key, label, CHOICE, required, def, options, null, null, null, null);
    }

    private static FieldSpec field(String key, String label, FieldKind kind, boolean required, Object def,
                                   List<FieldSpec.Option> options, String help, Double min, Double max, String requiredIf) {
        return new FieldSpec(key, label, kind, required, def, options, help, min, max, requiredIf);
    }

    private static FieldSpec.Option opt(String value, String label) {
        return new FieldSpec.Option(value, label);
    }
}
