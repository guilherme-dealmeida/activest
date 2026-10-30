package com.activest.domain.goal;

import com.activest.domain.dividend.DividendType;
import com.activest.domain.goal.engine.*;
import com.activest.domain.goal.type.GoalTypeCatalog;
import com.activest.domain.performance.BenchmarkData;
import com.activest.domain.performance.BenchmarkSeries;
import com.activest.domain.performance.PortfolioTimeline;
import com.activest.domain.performance.PriceStatus;
import com.activest.domain.strategy.dto.RebalanceDTO;
import com.activest.domain.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * O motor das metas com fatos montados a mao (docs/planos/PLANO_METAS.md, Anexo A 15): cenarios 1–12 e 16.
 * Hoje fixo em 24/10/2026; CDI de 12 meses 12% e IPCA 4% para as premissas automaticas.
 */
class GoalEvaluatorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    // ------------------------------------------------------------------ fixture

    final Map<UUID, PositionInfo> positions = new LinkedHashMap<>();
    final List<GoalFacts.Holding> holdings = new ArrayList<>();
    final List<GoalFacts.Flow> flows = new ArrayList<>();
    final List<GoalFacts.Income> incomes = new ArrayList<>();
    RebalanceDTO rebalance = new RebalanceDTO(BigDecimal.ZERO, List.of(), null);
    PortfolioTimeline timeline = PortfolioTimeline.empty(TODAY);
    BenchmarkData benchmarks = mock(BenchmarkData.class);
    /** Cotacao do catalogo pelo id do ativo (meta por ativo fora da carteira). */
    Map<Long, Double> prices = new HashMap<>();

    UUID position(String ticker, String type, String category, String indexer, boolean reservation, double value,
                  double cost, Double shares, Double price) {
        UUID id = UUID.randomUUID();
        boolean fixed = type.equals("RENDA_FIXA") || type.equals("TESOURO_DIRETO");
        PositionInfo info = new PositionInfo(id, (long) positions.size() + 1, ticker, type, type, category,
                indexer != null ? indexer : PositionInfo.VARIABLE_INCOME, ticker + " SA", reservation, fixed, false);
        positions.put(id, info);
        holdings.add(new GoalFacts.Holding(info, bd(value), bd(cost), shares != null ? bd(shares) : null,
                price != null ? bd(price) : null, TODAY, false, false, 0));
        return id;
    }

    /** O id do ativo da posicao: a meta por ativo guarda o id (V13 — o ticker se repete entre tipos). */
    long assetOf(UUID position) {
        return positions.get(position).assetId();
    }

    void flow(UUID id, String date, TransactionType type, double amount, Double shares) {
        flows.add(new GoalFacts.Flow(id, LocalDate.parse(date), type, bd(amount), shares != null ? bd(shares) : null));
        flows.sort(Comparator.comparing(GoalFacts.Flow::date));
    }

    void income(UUID id, String payment, double net, double perShare) {
        LocalDate p = LocalDate.parse(payment);
        incomes.add(new GoalFacts.Income(id, p.minusDays(10), p, bd(net), bd(net), bd(perShare), DividendType.DIVIDENDO,
                !p.isAfter(TODAY)));
    }

    GoalFacts facts() {
        GoalFacts.IndexReader indices = (code, from, to) -> switch (code) {
            case "CDI" -> Optional.of(new GoalFacts.Accumulated(0.12, TODAY.minusDays(1)));
            case "IPCA" -> Optional.of(new GoalFacts.Accumulated(0.04, LocalDate.of(2026, 9, 1)));
            default -> Optional.empty();
        };
        return new GoalFacts(TODAY, positions, holdings, flows, incomes, () -> rebalance, () -> timeline, benchmarks,
                indices, 0, Map.of(), t -> prices.containsKey(t) ? bd(prices.get(t)) : null);
    }

    Evaluation eval(GoalType type, String target, LocalDate deadline, Map<String, Object> scope, Map<String, Object> params) {
        GoalTypeCatalog.Validated v = GoalTypeCatalog.validate(type, target != null ? new BigDecimal(target) : null,
                deadline, scope, params, TODAY, null);
        GoalDefinition d = new GoalDefinition(type, "meta", v.targetValue(), v.targetDate(), v.scope(), v.params(),
                TODAY.minusMonths(6));
        return GoalEvaluator.evaluate(d, facts());
    }

    static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    static Evaluation.Figure fig(Evaluation e, String key) {
        return e.figures().stream().filter(f -> f.key().equals(key)).findFirst().orElse(null);
    }

    static Evaluation.Figure projFig(Evaluation e, String key) {
        return e.projection().figures().stream().filter(f -> f.key().equals(key)).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ patrimonio

    @Test
    @DisplayName("Cenário 1: patrimônio abaixo do alvo — progresso, falta e decomposição; sem prazo: em andamento com data estimada")
    void patrimonyBelowTarget() {
        UUID a = position("PETR4", "ACAO", "Petróleo", null, false, 6_000, 5_000, 100.0, 60.0);
        UUID r = position("CDB", "RENDA_FIXA", "CDB", "CDI", true, 4_000, 4_000, null, null);
        flow(a, "2025-01-10", TransactionType.BUY, 5_000, 100.0);
        flow(r, "2025-02-10", TransactionType.BUY, 4_000, null);
        Evaluation e = eval(GoalType.PAT_1, "20000", null, Map.of("reservation", "WITH"), Map.of());
        assertThat(e.indicator()).isEqualByComparingTo("10000.00");
        assertThat(e.progressPct()).isEqualByComparingTo("50.00");
        assertThat(e.remaining()).isEqualByComparingTo("10000.00");
        assertThat(e.situation()).isEqualTo(GoalSituation.EM_ANDAMENTO);
        assertThat(fig(e, "netContributions").value()).isEqualByComparingTo("9000.00");
        assertThat(fig(e, "marketResult").value()).isEqualByComparingTo("1000.00");
        assertThat(projFig(e, "estimatedDate").note()).matches("\\d\\d/\\d{4}");
        assertThat(e.projection().assumptions()).anyMatch(x -> x.key().equals("returnRate") && x.origin().equals("AUTOMATICA"));

        Evaluation sem = eval(GoalType.PAT_1, "20000", null, Map.of("reservation", "WITHOUT"), Map.of());
        assertThat(sem.indicator()).isEqualByComparingTo("6000.00");
    }

    @Test
    @DisplayName("Cenário 2: alvo ultrapassado — progresso acima de 100%, falta zero, atingida")
    void patrimonyAboveTarget() {
        position("PETR4", "ACAO", "Petróleo", null, false, 12_000, 5_000, 100.0, 120.0);
        Evaluation e = eval(GoalType.PAT_1, "10000", null, Map.of("reservation", "WITH"), Map.of());
        assertThat(e.progressPct()).isEqualByComparingTo("120.00");
        assertThat(e.remaining()).isEqualByComparingTo("0.00");
        assertThat(e.achieved()).isTrue();
        assertThat(e.situation()).isEqualTo(GoalSituation.ATINGIDA);
    }

    @Test
    @DisplayName("Cenários 3 e 4: com prazo — no ritmo ou atrasada pela projeção; prazo vencido sem atingir")
    void patrimonyDeadline() {
        UUID a = position("PETR4", "ACAO", "Petróleo", null, false, 10_000, 10_000, 100.0, 100.0);
        flow(a, "2026-03-10", TransactionType.BUY, 10_000, 100.0);
        // 12% a.a. sem aporte: em 12 meses vira ~11.200
        Evaluation ok = eval(GoalType.PAT_1, "11000", TODAY.plusYears(1).plusDays(10), Map.of("reservation", "WITH"),
                Map.of("monthlyContribution", 0));
        assertThat(ok.situation()).isEqualTo(GoalSituation.NO_RITMO);
        Evaluation late = eval(GoalType.PAT_1, "50000", TODAY.plusYears(1).plusDays(10), Map.of("reservation", "WITH"),
                Map.of("monthlyContribution", 0));
        assertThat(late.situation()).isEqualTo(GoalSituation.ATRASADA);
        assertThat(projFig(late, "requiredPayment").value()).isPositive();

        GoalTypeCatalog.Validated v = GoalTypeCatalog.validate(GoalType.PAT_1, new BigDecimal("50000"), TODAY.minusDays(1),
                Map.of("reservation", "WITH"), Map.of(), TODAY, TODAY.minusDays(1));
        Evaluation expired = GoalEvaluator.evaluate(new GoalDefinition(GoalType.PAT_1, "m", v.targetValue(), v.targetDate(),
                v.scope(), v.params(), TODAY.minusYears(1)), facts());
        assertThat(expired.situation()).isEqualTo(GoalSituation.PRAZO_VENCIDO);
    }

    // ------------------------------------------------------------------ renda

    @Test
    @DisplayName("Cenário 5: renda com histórico completo e parcial — soma ÷ janela (D10), sem anualizar pagamento isolado")
    void income() {
        UUID f = position("MXRF11", "FII", "Papel", null, false, 12_000, 12_000, 1000.0, 12.0);
        flow(f, "2025-01-05", TransactionType.BUY, 12_000, 1000.0);
        for (int m = 1; m <= 12; m++) {
            income(f, LocalDate.of(2025, 10, 15).plusMonths(m - 1).toString(), 100, 0.1);
        }
        Evaluation e = eval(GoalType.REN_1, "150", null, Map.of(), Map.of());
        // janela: 01/10/2025 a 30/09/2026 — 12 pagamentos de outubro/2025 a setembro/2026
        assertThat(e.indicator()).isEqualByComparingTo("100.00");
        assertThat(e.progressPct()).isEqualByComparingTo("66.67");

        // historico de 5 meses: soma das 5 ÷ 12, nunca ÷ 5
        positions.clear();
        holdings.clear();
        flows.clear();
        incomes.clear();
        UUID g = position("MXRF11", "FII", "Papel", null, false, 12_000, 12_000, 1000.0, 12.0);
        flow(g, "2026-05-02", TransactionType.BUY, 12_000, 1000.0);
        for (int m = 0; m < 5; m++) {
            income(g, LocalDate.of(2026, 5, 15).plusMonths(m).toString(), 120, 0.12);
        }
        Evaluation p = eval(GoalType.REN_1, "150", null, Map.of(), Map.of());
        assertThat(p.indicator()).isEqualByComparingTo("50.00");
        assertThat(fig(p, "monthlyAverage").note()).contains("5 meses de histórico");
    }

    // ------------------------------------------------------------------ alocacao e concentracao

    @Test
    @DisplayName("Cenário 7: participação mínima — dentro e fora, com o aporte para voltar (fórmula da reserva)")
    void allocation() {
        position("PETR4", "ACAO", "Petróleo", null, false, 8_000, 8_000, 100.0, 80.0);
        position("MXRF11", "FII", "Papel", null, false, 2_000, 2_000, 200.0, 10.0);
        position("CDB", "RENDA_FIXA", "CDB", "CDI", true, 90_000, 90_000, null, null);
        Map<String, Object> p = Map.of("dimension", "TYPE", "item", "FII", "rule", "MIN", "min", 30);
        Evaluation e = eval(GoalType.ALO_1, null, null, Map.of(), p);
        // base sem a reserva: 10.000; FII 20%
        assertThat(e.indicator()).isEqualByComparingTo("20.00");
        assertThat(e.situation()).isEqualTo(GoalSituation.FORA_DO_OBJETIVO);
        assertThat(e.progressPct()).isNull();
        // (0,3 × 10.000 − 2.000) ÷ 0,7 = 1.428,57
        assertThat(fig(e, "toMin").value()).isEqualByComparingTo("1428.57");
        Evaluation in = eval(GoalType.ALO_1, null, null, Map.of(), Map.of("dimension", "TYPE", "item", "FII", "rule", "MIN", "min", 20));
        assertThat(in.situation()).isEqualTo(GoalSituation.DENTRO_DO_OBJETIVO);
    }

    @Test
    @DisplayName("Cenário 8: concentração no limite exato está dentro; acima, lista o item com o excesso")
    void concentration() {
        position("PETR4", "ACAO", "Petróleo", null, false, 2_500, 2_500, 100.0, 25.0);
        position("VALE3", "ACAO", "Mineração", null, false, 7_500, 7_500, 100.0, 75.0);
        Evaluation at = eval(GoalType.RIS_1, "75", null, Map.of(), Map.of("dimension", "ASSET"));
        assertThat(at.situation()).isEqualTo(GoalSituation.DENTRO_DO_OBJETIVO);
        Evaluation over = eval(GoalType.RIS_1, "50", null, Map.of(), Map.of("dimension", "ASSET"));
        assertThat(over.situation()).isEqualTo(GoalSituation.FORA_DO_OBJETIVO);
        assertThat(over.items()).anyMatch(i -> "VALE3".equals(i.get("item")) && Boolean.TRUE.equals(i.get("above")));
        // reduzir VALE3 ate 50%: (7.500 − 0,5 × 10.000) ÷ 0,5 = 5.000
        assertThat(over.items()).anyMatch(i -> "VALE3".equals(i.get("item"))
                && new BigDecimal("5000.00").compareTo((BigDecimal) i.get("reduce")) == 0);
    }

    @Test
    @DisplayName("ALO-2: sem estratégia é 'sem dados'; com estratégia, tipos fora da tolerância")
    void strategy() {
        Evaluation none = eval(GoalType.ALO_2, null, null, Map.of(), Map.of());
        assertThat(none.situation()).isEqualTo(GoalSituation.SEM_DADOS);
        assertThat(none.quality()).isEqualTo(Evaluation.Quality.INDISPONIVEL);
        rebalance = new RebalanceDTO(bd(10_000), List.of(
                new RebalanceDTO.RebalanceItem("Ações", bd(50), bd(60), bd(10), bd(6000), bd(5000), bd(-1000)),
                new RebalanceDTO.RebalanceItem("FIIs", bd(50), bd(40), bd(-10), bd(4000), bd(5000), bd(1000))), null);
        Evaluation out = eval(GoalType.ALO_2, "5", null, Map.of(), Map.of());
        assertThat(out.indicator()).isEqualByComparingTo("10.00");
        assertThat(out.situation()).isEqualTo(GoalSituation.FORA_DO_OBJETIVO);
        assertThat(fig(out, "distance").value()).isEqualByComparingTo("10.00");
        Evaluation in = eval(GoalType.ALO_2, "10", null, Map.of(), Map.of());
        assertThat(in.situation()).isEqualTo(GoalSituation.DENTRO_DO_OBJETIVO);
    }

    // ------------------------------------------------------------------ por ativo

    @Test
    @DisplayName("Cenário 9: quantidade de um ativo pelas cotas da Carteira; custo da falta é estimativa")
    void assetShares() {
        UUID k = position("KLBN4", "ACAO", "Papel", null, false, 3_500, 3_000, 1000.0, 3.5);
        flow(k, "2026-01-10", TransactionType.BUY, 1_200, 400.0);
        Evaluation e = eval(GoalType.ATV_1, "1500", null, Map.of(), Map.of("assetId", assetOf(k)));
        assertThat(e.indicator()).isEqualByComparingTo("1000.00000000");
        assertThat(e.remaining()).isEqualByComparingTo("500.00000000");
        assertThat(fig(e, "costOfRemaining").nature()).isEqualTo(Evaluation.Nature.ESTIMATIVA);
        assertThat(fig(e, "costOfRemaining").value()).isEqualByComparingTo("1800.00"); // 1.750 arredondado a R$ 100
    }

    @Test
    @DisplayName("Cenário 12: proventos compram cotas — sem provento em 12 meses é 'sem dados', nunca zero")
    void dividendsBuyShares() {
        UUID b = position("BBAS3", "ACAO", "Bancos", null, false, 2_000, 2_000, 100.0, 20.0);
        Evaluation none = eval(GoalType.ATV_3, null, null, Map.of(), Map.of("assetId", assetOf(b)));
        assertThat(none.situation()).isEqualTo(GoalSituation.SEM_DADOS);
        for (int m = 0; m < 12; m++) {
            income(b, LocalDate.of(2025, 10, 20).plusMonths(m).toString(), 20, 0.2);
        }
        Evaluation e = eval(GoalType.ATV_3, null, null, Map.of(), Map.of("assetId", assetOf(b)));
        // 0,20/mes por cota × 100 cotas ÷ R$ 20 = 1 cota/mes
        assertThat(e.indicator()).isEqualByComparingTo("1.00000000");
        assertThat(e.situation()).isEqualTo(GoalSituation.ATINGIDA);
    }

    // ------------------------------------------------------------------ serie (rentabilidade)

    /** Uma posicao de 3 pregoes: compra 1.000, sobe 10%, aporte de 1.000 e cai 10% (exemplo do cenario 6). */
    private void timelineWithFlows() {
        List<LocalDate> dates = List.of(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6));
        UUID id = position("PETR4", "ACAO", "Petróleo", null, false, 1_890, 2_000, 100.0, 18.9);
        double[] value = {1000, 1100, 1890};
        double[] buy = {1000, 0, 1000};
        double[] zero = {0, 0, 0};
        PortfolioTimeline.PositionSeries s = new PortfolioTimeline.PositionSeries(id, 1L, "PETR4", "Petrobras", "ACAO",
                "Ações", 1L, "Petróleo", null, null, false, false, value, value, buy, zero, zero, zero, zero,
                new boolean[]{true, true, true}, new PriceStatus[]{PriceStatus.OK, PriceStatus.OK, PriceStatus.OK},
                dates.toArray(new LocalDate[0]), List.of());
        timeline = new PortfolioTimeline(dates, List.of(s), LocalDate.of(2026, 1, 6));
    }

    @Test
    @DisplayName("Cenário 6: rentabilidade pela cota com aporte no meio — +10% e −10% dão −1%, não o resultado simples")
    void returnWithFlows() {
        timelineWithFlows();
        Evaluation e = eval(GoalType.RNT_1, "0", null, Map.of(), Map.of("period", "SINCE_START"));
        // dia 1: 1.000/1.000 − 1 = 0; dia 2: +10%; dia 3: 1.890/(1.100+1.000) − 1 = −10%
        assertThat(e.indicator()).isEqualByComparingTo("-1.00");
        assertThat(e.situation()).isEqualTo(GoalSituation.FORA_DO_OBJETIVO);
        // simples: (1.890 − 2.000) ÷ 2.000 = −5,5% — a cota nao confunde aporte com ganho
    }

    @Test
    @DisplayName("Cenário 10: benchmark — índice atrasado encurta o período; sem nenhum dado comum, 'sem dados'")
    void benchmark() {
        timelineWithFlows();
        BenchmarkSeries.Daily cdi = new BenchmarkSeries.Daily(new double[]{0.0005, 0.0005, Double.NaN}, LocalDate.of(2026, 1, 5));
        when(benchmarks.resolve(anyList(), any(), any())).thenReturn(Map.of("CDI",
                new BenchmarkData.Resolved(new BenchmarkData.Ref("CDI", null), "CDI", cdi)));
        Evaluation e = eval(GoalType.RNT_2, null, null, Map.of(), Map.of("index", "CDI", "period", "SINCE_START"));
        assertThat(e.situationNote()).contains("encurtado");
        assertThat(e.situation()).isEqualTo(GoalSituation.DENTRO_DO_OBJETIVO);

        BenchmarkSeries.Daily nothing = new BenchmarkSeries.Daily(new double[]{Double.NaN, Double.NaN, Double.NaN}, null);
        when(benchmarks.resolve(anyList(), any(), any())).thenReturn(Map.of("IPCA",
                new BenchmarkData.Resolved(new BenchmarkData.Ref("IPCA", null), "IPCA", nothing)));
        Evaluation none = eval(GoalType.RNT_2, null, null, Map.of(), Map.of("index", "IPCA", "period", "SINCE_START"));
        assertThat(none.situation()).isEqualTo(GoalSituation.SEM_DADOS);
    }

    @Test
    @DisplayName("Cenário 11: dia sem preço deixa a rentabilidade indisponível (regra da Rentabilidade), com o ativo nomeado")
    void missingPrice() {
        List<LocalDate> dates = List.of(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5));
        UUID id = position("XPTO3", "ACAO", "Outros", null, false, 1_000, 1_000, 10.0, 100.0);
        double[] v = {1000, 0};
        double[] zero = {0, 0};
        PortfolioTimeline.PositionSeries s = new PortfolioTimeline.PositionSeries(id, 1L, "XPTO3", "Xpto", "ACAO",
                "Ações", 1L, "Outros", null, null, false, false, v, v, new double[]{1000, 0}, zero, zero, zero, zero,
                new boolean[]{true, true}, new PriceStatus[]{PriceStatus.OK, PriceStatus.MISSING},
                dates.toArray(new LocalDate[0]), List.of());
        timeline = new PortfolioTimeline(dates, List.of(s), LocalDate.of(2026, 1, 5));
        Evaluation e = eval(GoalType.RNT_1, "5", null, Map.of(), Map.of("period", "SINCE_START"));
        assertThat(e.quality()).isEqualTo(Evaluation.Quality.INDISPONIVEL);
        assertThat(e.situationNote()).contains("XPTO3");
    }

    @Test
    @DisplayName("Cenário 16: carteira vazia não quebra nenhum tipo (sem divisão por zero)")
    void emptyPortfolio() {
        when(benchmarks.resolve(anyList(), any(), any())).thenReturn(Map.of());
        assertThat(eval(GoalType.PAT_1, "1000", null, Map.of("reservation", "WITH"), Map.of()).indicator()).isEqualByComparingTo("0");
        assertThat(eval(GoalType.APO_1, "1000", null, Map.of(), Map.of()).indicator()).isEqualByComparingTo("0");
        assertThat(eval(GoalType.REN_1, "100", null, Map.of(), Map.of()).indicator()).isEqualByComparingTo("0");
        assertThat(eval(GoalType.ALO_1, null, null, Map.of(), Map.of("dimension", "TYPE", "item", "FII", "rule", "MIN", "min", 5))
                .situation()).isEqualTo(GoalSituation.SEM_DADOS);
        assertThat(eval(GoalType.RNT_1, "5", null, Map.of(), Map.of()).situation()).isEqualTo(GoalSituation.SEM_DADOS);
        assertThat(eval(GoalType.RIS_2, "10", null, Map.of(), Map.of()).situation()).isEqualTo(GoalSituation.SEM_DADOS);
        assertThat(eval(GoalType.RIS_3, null, null, Map.of(), Map.of()).situation()).isEqualTo(GoalSituation.DENTRO_DO_OBJETIVO);
        assertThat(eval(GoalType.LP_1, null, TODAY.plusYears(20), Map.of(), Map.of()).situation()).isEqualTo(GoalSituation.ESTIMATIVA);
        assertThat(eval(GoalType.REN_3, null, null, Map.of(), Map.of()).situation()).isNotNull();
        assertThat(eval(GoalType.APO_2, "100", null, Map.of(), Map.of()).situation()).isNotNull();
    }

    @Test
    @DisplayName("Indicador e alvo medem a mesma coisa (revisão 25/10/2026): aporte periódico em R$; aposentadoria em renda")
    void indicatorMatchesTarget() {
        when(benchmarks.resolve(anyList(), any(), any())).thenReturn(Map.of());
        Evaluation apo = eval(GoalType.APO_2, "100", null, Map.of(), Map.of());
        assertThat(apo.unit()).isEqualTo(Evaluation.Unit.BRL);
        assertThat(apo.indicatorLabel()).startsWith("Aporte no período corrente");
        assertThat(apo.target()).isEqualByComparingTo("100");
        Evaluation lp = eval(GoalType.LP_1, "10000", TODAY.plusYears(20), Map.of(), Map.of());
        if (lp.target() != null) {
            assertThat(lp.indicatorLabel()).startsWith("Renda mensal estimada");
        } else {
            assertThat(lp.indicatorLabel()).isEqualTo("Patrimônio de hoje");
        }
    }
}
