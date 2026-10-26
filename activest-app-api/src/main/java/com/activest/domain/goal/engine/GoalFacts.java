package com.activest.domain.goal.engine;

import com.activest.domain.dividend.DividendType;
import com.activest.domain.performance.BenchmarkData;
import com.activest.domain.performance.PortfolioTimeline;
import com.activest.domain.strategy.dto.RebalanceDTO;
import com.activest.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Os fatos da carteira que as metas leem (docs/planos/PLANO_METAS.md, 5.4), carregados uma vez por pedido para todas
 * as metas do usuario. Valor atual e o da Carteira ({@code PortfolioService.getPortfolioAssets}, bruto — P2); proventos
 * pela regra da tela Dividendos; estrategia pelo Rebalanceamento; a linha do tempo da Rentabilidade so lida (I1 = a).
 */
public record GoalFacts(LocalDate today, Map<UUID, PositionInfo> positions, List<Holding> holdings,
                        List<Flow> flows, List<Income> incomes, Supplier<RebalanceDTO> rebalance,
                        Supplier<PortfolioTimeline> timeline, BenchmarkData benchmarks, IndexReader indices,
                        int pendingImports, Map<String, LocalDate> sourceDates,
                        java.util.function.Function<String, BigDecimal> catalogPrice) {

    /** Acumulado de um indice entre datas (CDI e IPCA das premissas, correcao da despesa); vazio sem dado. */
    @FunctionalInterface
    public interface IndexReader {
        java.util.Optional<Accumulated> accumulated(String code, LocalDate from, LocalDate to);
    }

    /** Fracao acumulada (0,1088 = 10,88%) e o ultimo dado usado. */
    public record Accumulated(double rate, LocalDate until) {
    }

    /**
     * Uma posicao em aberto com o valor da Carteira.
     *
     * @param cost        custo que esta na posicao (cotas) ou aplicado − resgatado (renda fixa)
     * @param priceDate   data do preco/valor: cotacao, PU do Tesouro ou ultimo CDI da curva; nula sem preco
     * @param noPrice     tipo sem cotacao (MANUAL) valorizado pelo custo (D8)
     * @param noCurve     renda fixa sem curva (L3)
     * @param pendingBonus bonificacoes sem custo informado
     */
    public record Holding(PositionInfo info, BigDecimal currentValue, BigDecimal cost, BigDecimal shares,
                          BigDecimal currentPrice, LocalDate priceDate, boolean noPrice, boolean noCurve,
                          int pendingBonus) {
    }

    /**
     * Uma transacao: compra pelo total, venda pelo que devolveu (liquido onde existe — o mesmo do card Aportes do
     * Dashboard, D6).
     */
    public record Flow(UUID positionId, LocalDate date, TransactionType type, BigDecimal amount, BigDecimal shares) {
    }

    /** Um provento: liquido (D5) e bruto, pagamento, tipo e se ja foi recebido (regra da tela Dividendos). */
    public record Income(UUID positionId, LocalDate exDate, LocalDate paymentDate, BigDecimal net, BigDecimal gross,
                         BigDecimal netPerShare, DividendType type, boolean received) {

        /** Amortizacao e restituicao devolvem capital: fora da renda passiva (Anexo A, 3). */
        public boolean isIncome() {
            return type != DividendType.AMORTIZACAO && type != DividendType.RESTITUICAO_CAPITAL;
        }

        /** Data que conta para a janela: pagamento, ou a data ex sem ele (a regra do card Proventos 12 meses). */
        public LocalDate refDate() {
            return paymentDate != null ? paymentDate : exDate;
        }
    }

    public boolean empty() {
        return positions.isEmpty();
    }
}
