package com.primevest.integration.b3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Cadastro de uma empresa ({@code GetDetail}): de onde sai o ISIN de cada ticker. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record B3CompanyDetail(String issuingCompany, String tradingName, String cnpj, String codeCVM,
                              List<OtherCode> otherCodes) {

    public List<OtherCode> codes() {
        return otherCodes != null ? otherCodes : List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OtherCode(String code, String isin) {
    }
}
