package com.activest.adm.integration.b3;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um provento de um "Aviso aos Cotistas - Estruturado" do FNET.
 *
 * @param amortization amortizacao (true) ou rendimento (false)
 */
public record FnetDividend(String isin, String ticker, boolean amortization, LocalDate dataBase,
                           BigDecimal value, LocalDate paymentDate, String referencePeriod,
                           Boolean incomeTaxExempt) {
}
