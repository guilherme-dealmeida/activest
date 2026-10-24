package com.activest.domain.dividend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Resumo dos proventos. Os totais sao LIQUIDOS (docs/planos/PLANO_IR_PROVENTOS.md, D3):
 * o que entrou na conta; tipo sem regra de IR entra pelo bruto.
 *
 * <p>Separados por situacao ({@link com.activest.domain.dividend.Dividend#isReceived}):
 * totalDividends, byType, totalGross e totalTax sao so do que ja foi RECEBIDO;
 * os toReceive*, do que ainda sera pago.</p>
 */
public record DividendSummaryDTO(
    /** Total liquido recebido. */
    BigDecimal totalDividends,
    /** Liquido recebido por tipo de provento. */
    Map<String, BigDecimal> byType,
    List<DividendResponseDTO> recent,
    /** Total bruto recebido (antes do IR). */
    BigDecimal totalGross,
    /** IR retido no que foi recebido. */
    BigDecimal totalTax,
    /** Total liquido a receber. */
    BigDecimal totalToReceive,
    /** Bruto a receber. */
    BigDecimal toReceiveGross,
    /** IR a reter no que falta receber. */
    BigDecimal toReceiveTax,
    /**
     * Liquido recebido nos ultimos 12 meses (pagamento de hoje - 12 meses, exclusive, ate hoje; sem data de
     * pagamento, vale a data ex) — card Proventos do Dashboard (17/10/2026).
     */
    BigDecimal last12Months
) {}
