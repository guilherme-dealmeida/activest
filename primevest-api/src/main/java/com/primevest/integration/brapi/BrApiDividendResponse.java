package com.primevest.integration.brapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO para a resposta de dividendos da BrAPI.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BrApiDividendResponse {

    private List<BrApiDividend> dividends;
    private String symbol;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrApiDividend {
        private String assetIssued;
        private BigDecimal rate;
        private String relatedTo;
        private String paymentDate;
        private String lastDatePrior;
        private String type;
    }
}
