package com.primevest.domain.ceilingprice.dto;

import com.primevest.domain.ceilingprice.CeilingPriceRules.Issue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contratos da API do Preço Teto (docs/planos/PLANO_PRECO_TETO.md, 5.3). Percentuais de entrada em pontos
 * (13,75); taxas de saída ({@code growthPct}, margens, peso do terminal) também em pontos.
 */
public final class CeilingPriceDTOs {

    private CeilingPriceDTOs() {
    }

    // ------------------------------------------------------------------ entrada

    /** Um lucro trimestral como foi digitado. Unidade UNIDADE/MIL/MILHAO/BILHAO; moeda BRL/USD. */
    public record QuarterInput(LocalDate periodEnd, BigDecimal originalValue, String unit, String currency,
                               BigDecimal fxRate) {
    }

    /**
     * Entradas do formulário. Em {@code /calculate} campos vazios são aceitos (viram aviso de bloqueio); ao salvar,
     * todos são obrigatórios. {@code confirmWarnings}: o usuário viu os alertas de atenção e quer salvar assim.
     */
    public record CeilingPriceInput(String ticker, List<QuarterInput> quarters, Long shares, BigDecimal roePct,
                                    BigDecimal payoutPct, BigDecimal discountRatePct, BigDecimal perpetualGrowthPct,
                                    BigDecimal safetyMarginPct, BigDecimal price, String notes,
                                    boolean confirmWarnings) {
    }

    // ------------------------------------------------------------------ padrões

    /** Um padrão: o valor (nulo quando não há) com a referência ou o motivo. */
    public record DefaultValue(BigDecimal value, String ref, String note) {
    }

    /** Um trimestre sugerido pelo catálogo: valor em reais (nulo = falta), rótulo e documento de origem. */
    public record DefaultQuarter(LocalDate periodEnd, String label, BigDecimal value, String ref, String note) {
    }

    /**
     * Os padrões de hoje para um ativo (abrir cálculo novo, ↻ de cada campo, "Restaurar todos os padrões").
     *
     * @param existingId          cálculo já salvo pelo usuário para o ativo, se houver
     * @param latestQuarterEnd    último balanço do catálogo (para "há balanço mais novo que o salvo")
     * @param equityParent        PL da controladora do último balanço (alerta de lucro muito baixo)
     * @param catalogTtm          LL TTM do catálogo no último balanço (alerta de reapresentação)
     */
    public record Defaults(String ticker, String assetName, Long issuerId, Boolean hasLogo, UUID existingId,
                           List<DefaultQuarter> quarters, DefaultValue shares, DefaultValue roePct,
                           DefaultValue payoutPct, DefaultValue discountRatePct, DefaultValue perpetualGrowthPct,
                           DefaultValue safetyMarginPct, DefaultValue price, LocalDate priceDate,
                           LocalDate latestQuarterEnd, BigDecimal equityParent, BigDecimal catalogTtm,
                           List<String> roeYears, List<String> payoutYears) {
    }

    // ------------------------------------------------------------------ resultado

    /** Um passo da memória de cálculo: o valor, a fórmula e a fórmula com os números. */
    public record Step(String key, String label, BigDecimal value, String formula, String substituted) {
    }

    /**
     * Resultado do modelo. Reais por ação com 4 casas, totais com 2, taxas em pontos com 4. Nulo = não calculado
     * (o motivo está em {@code issues}).
     */
    public record Result(BigDecimal netIncomeTtm, BigDecimal growthPct, BigDecimal fc1, BigDecimal pv1,
                         BigDecimal fc2, BigDecimal pv2, BigDecimal fc3, BigDecimal pv3, BigDecimal terminalValue,
                         BigDecimal terminalPv, BigDecimal equityValue, BigDecimal terminalWeightPct,
                         BigDecimal ceilingPrice, BigDecimal idealPrice, BigDecimal ceilingMarginPct,
                         BigDecimal idealMarginPct, List<Step> steps, List<Issue> issues) {
    }

    /** Origem de uma entrada gravada: PADRAO ou INFORMADO, com a referência e o valor do padrão no momento. */
    public record InputOrigin(String origin, String ref, BigDecimal defaultValue) {
    }

    /** Um lucro gravado, com o normalizado e a origem. */
    public record QuarterView(int position, LocalDate periodEnd, String label, BigDecimal originalValue, String unit,
                              String currency, BigDecimal fxRate, BigDecimal valueBrl, String origin) {
    }

    /** O cálculo salvo: entradas, origem de cada uma, resultado e os padrões de hoje (para o ↻). */
    public record Detail(UUID id, String ticker, String assetName, Long issuerId, Boolean hasLogo,
                         List<QuarterView> quarters, Long shares, BigDecimal roePct, BigDecimal payoutPct,
                         BigDecimal discountRatePct, BigDecimal perpetualGrowthPct, BigDecimal safetyMarginPct,
                         BigDecimal price, String notes, Map<String, InputOrigin> origins,
                         LocalDateTime calculatedAt, Result result, Defaults defaults,
                         List<String> changedDefaults) {
    }

    /**
     * Uma linha da lista: o resultado salvo e três comparações (D13) — o preço do cálculo, o fechamento de hoje e
     * o preço médio da carteira (nulo sem o ativo em carteira). Margens em pontos.
     */
    public record ListItem(UUID id, String ticker, String assetName, Long issuerId, Boolean hasLogo,
                           BigDecimal ceilingPrice, BigDecimal idealPrice, BigDecimal safetyMarginPct,
                           BigDecimal calcPrice, BigDecimal calcCeilingMarginPct, BigDecimal calcIdealMarginPct,
                           BigDecimal todayPrice, LocalDate todayPriceDate, BigDecimal todayCeilingMarginPct,
                           BigDecimal todayIdealMarginPct, BigDecimal averagePrice,
                           BigDecimal averageCeilingMarginPct, BigDecimal averageIdealMarginPct, String status,
                           int blockingCount, int warningCount, boolean defaultsChanged, LocalDate lastQuarterEnd,
                           LocalDateTime calculatedAt) {
    }

    /** Recusa ao salvar: a mensagem e os avisos que impediram (bloqueios, ou atenção sem confirmação). */
    public record Rejection(String message, List<Issue> issues) {
    }
}
