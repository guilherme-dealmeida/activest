package com.primevest.adm.integration.b3;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Proventos recentes (~12 meses) e eventos societarios de um emissor.
 *
 * <p>Mesma estrutura para empresa ({@code GetListedSupplementCompany}) e fundo
 * ({@code GetListedSupplementFunds}); no fundo o nome de pregao vem em
 * {@code fund}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record B3Supplement(
    @JsonAlias("fund") String tradingName,
    String code,
    String codeCVM,
    List<CashDividend> cashDividends,
    List<StockDividend> stockDividends
) {

    public List<CashDividend> cash() {
        return cashDividends != null ? cashDividends : List.of();
    }

    public List<StockDividend> stock() {
        return stockDividends != null ? stockDividends : List.of();
    }

    /**
     * Provento em dinheiro. {@code lastDatePrior} e a data-com; {@code rate}, o
     * valor por cota; {@code label}, o tipo ('DIVIDENDO', 'JRS CAP PROPRIO',
     * 'RENDIMENTO'...).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CashDividend(String assetIssued, String paymentDate, String rate, String relatedTo,
                               String approvedOn, String isinCode, String label, String lastDatePrior,
                               String remarks) {
    }

    /** Desdobramento, grupamento, bonificacao (e outros rotulos que nao mexem na quantidade). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockDividend(String assetIssued, String factor, String approvedOn, String isinCode,
                                String label, String lastDatePrior, String remarks) {
    }
}
