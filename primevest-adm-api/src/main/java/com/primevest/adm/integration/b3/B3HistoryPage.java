package com.primevest.adm.integration.b3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Uma pagina do historico longo de proventos de empresa
 * ({@code GetListedCashDividends}), desde os anos 90.
 *
 * <p>Sem data de pagamento. O valor e por lote: {@code quotedPerShares} = 1
 * hoje, 1000 nos anos 90 — por isso o valor por acao e
 * {@code valueCash / quotedPerShares}. No lugar do ISIN vem a especie
 * ({@code typeStock}: ON, PN, PNA, UNT...).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record B3HistoryPage(Page page, List<Item> results) {

    public List<Item> items() {
        return results != null ? results : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(Integer pageNumber, Integer pageSize, Integer totalRecords, Integer totalPages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String typeStock, String dateApproval, String valueCash, String ratio,
                       String corporateAction, String lastDatePriorEx, String quotedPerShares) {
    }
}
