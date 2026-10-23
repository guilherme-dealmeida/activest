package com.activest.domain.performance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Respostas de {@code /api/v1/performance} (docs/planos/PLANO_RENTABILIDADE.md, secao 5). Retornos em <b>pontos
 * percentuais</b> (12,5 = 12,5%), como o resto da API; valores em reais com 2 casas. Nulo = nao calculavel (o motivo
 * vem na cobertura ou na nota).
 */
public final class PerformanceDTOs {

    private PerformanceDTOs() {
    }

    // ------------------------------------------------------------------ catalogo

    public record BenchmarkDTO(String code, String name, String category, String kind, String note,
                               boolean acceptsSpread, boolean defaultSelected, String source, String periodicity,
                               LocalDate firstDate, LocalDate lastDate) {
    }

    /**
     * Catalogo e os benchmarks padrao de cada classe (D4; o primeiro e o principal), como o parametro
     * {@code benchmarks} os recebe ("IPCA", "IPCA_MAIS:6.8").
     *
     * @param treasuryIpca o titulo que da a taxa do IPCA + x% padrao do Tesouro; nulo sem taxa
     */
    public record BenchmarkCatalogDTO(List<BenchmarkDTO> benchmarks, Map<String, List<String>> defaultsByType,
                                      TreasuryReferenceDTO treasuryIpca) {
    }

    /** Tesouro IPCA+ a venda mais curto e a taxa de compra (% a.a. acima do IPCA) do dia {@code rateDate}. */
    public record TreasuryReferenceDTO(String ticker, LocalDate maturity, BigDecimal rate, LocalDate rateDate) {
    }

    // ------------------------------------------------------------------ comum

    /** Uma lacuna de preco no periodo: SEM_PRECO (indisponivel), ULTIMO_NEGOCIO (D3) ou ESTIMADO. */
    public record GapDTO(String ticker, LocalDate from, LocalDate to, String status) {
    }

    /**
     * O que o resultado cobre (R10, criterio 10).
     *
     * @param firstDate     primeiro dia com capital no recorte (desde o inicio)
     * @param from          primeiro dia do periodo efetivo (os retornos contam a partir dele)
     * @param baseDate      dia-base (o acumulado e zero nele)
     * @param to            ultimo dia
     * @param available     nenhum dia sem preco no periodo
     * @param unavailable   tickers sem preco no periodo (o motivo de {@code available = false})
     * @param realUntil     o retorno real vai ate aqui (ultimo IPCA publicado)
     * @param basisApplies  o recorte tem renda fixa, Tesouro ou provento com IR (o seletor bruto/liquido muda algo)
     */
    public record CoverageDTO(LocalDate firstDate, LocalDate from, LocalDate baseDate, LocalDate to,
                              boolean available, List<String> unavailable, List<GapDTO> gaps, LocalDate realUntil,
                              boolean basisApplies, String basis, String reservation, boolean empty) {
    }

    /**
     * Um benchmark no periodo.
     *
     * @param until ultimo dia com dado (IPCA: ultimo mes publicado); a diferenca compara a carteira ate ele
     */
    public record BenchmarkResultDTO(String code, String name, Double value, LocalDate until, Double diff,
                                     boolean partial) {
    }

    // ------------------------------------------------------------------ resumo (cards)

    public record SummaryDTO(CoverageDTO coverage, Double twr, BenchmarkResultDTO primary, Double diff, Double real,
                             LocalDate realUntil, Double inflation, Double percentOfCdi, Double cdi, Double xirr,
                             String xirrNote, BigDecimal valueStart, BigDecimal valueEnd, BigDecimal contributions,
                             BigDecimal withdrawals, BigDecimal distributions, BigDecimal result,
                             List<BenchmarkResultDTO> benchmarks) {
    }

    // ------------------------------------------------------------------ grafico acumulado

    public record SeriesPointDTO(LocalDate date, Double portfolio, Map<String, Double> benchmarks,
                                 boolean unavailable, boolean stale, boolean estimated) {
    }

    public record SeriesDTO(CoverageDTO coverage, List<BenchmarkResultDTO> benchmarks, String density,
                            List<SeriesPointDTO> points) {
    }

    // ------------------------------------------------------------------ periodos e tabela

    public record PeriodRowDTO(String key, LocalDate from, LocalDate to, boolean partial, Double portfolio,
                               Map<String, Double> benchmarks) {
    }

    public record PeriodicDTO(CoverageDTO coverage, String granularity, List<BenchmarkResultDTO> benchmarks,
                              List<PeriodRowDTO> rows) {
    }

    /**
     * Uma linha da tabela comparativa; {@code diffPeriod} = carteira menos o indicador no periodo (p.p.).
     *
     * @param until o indicador para antes do fim (IPCA: ultimo mes publicado) — ano, 12 meses, periodo e diferenca vao
     *              ate esta data; nulo = completo
     */
    public record TableRowDTO(String code, String name, boolean portfolio, Double month, Double year,
                              Double months12, Double period, Double diffPeriod, LocalDate until) {
    }

    public record TableDTO(CoverageDTO coverage, LocalDate monthFrom, LocalDate yearFrom, LocalDate months12From,
                           List<TableRowDTO> rows) {
    }

    // ------------------------------------------------------------------ classe e ativo (E05)

    /**
     * @param individual   TWR do grupo nos dias em que existiu (rentabilidade individual)
     * @param gain         resultado em reais no periodo (soma exata = resultado da carteira)
     * @param contribution contribuicao em p.p. para o TWR da carteira (Carino — D6; soma o TWR)
     * @param weight       peso medio no periodo (% do capital)
     * @param closed       sem saldo no fim do periodo (encerrada)
     */
    public record BreakdownRowDTO(String key, String label, String detail, String typeName, String typeDescription,
                                  Long categoryId, String categoryName, UUID positionId, String ticker,
                                  Boolean hasLogo, Double individual, BigDecimal gain, Double contribution,
                                  Double weight, BigDecimal valueEnd, boolean closed, boolean unavailable,
                                  LocalDate firstDate, LocalDate lastDate) {
    }

    public record BreakdownDTO(CoverageDTO coverage, String level, Double total, BigDecimal totalGain,
                               List<BreakdownRowDTO> rows) {
    }

    // ------------------------------------------------------------------ patrimonio (E06)

    /**
     * @param invested      aportes - retiradas acumulados (nominal)
     * @param investedReal  o mesmo, cada fluxo em reais da data-base
     * @param distributions proventos acumulados (sairam da carteira)
     * @param result        patrimonio + retiradas + proventos - aportes
     */
    public record WealthPointDTO(LocalDate date, BigDecimal nominal, BigDecimal real, BigDecimal invested,
                                 BigDecimal investedReal, BigDecimal distributions, BigDecimal result) {
    }

    public record WealthDTO(CoverageDTO coverage, LocalDate realBase, String density, List<WealthPointDTO> points) {
    }

    // ------------------------------------------------------------------ risco (E07)

    public record RiskDTO(CoverageDTO coverage, Double volatility, Double maxDrawdown, LocalDate peak,
                          LocalDate trough, Double sharpe, Double sortino, Double annualReturn, Double annualCdi,
                          int days, LocalDate from, LocalDate to, String note) {
    }
}
