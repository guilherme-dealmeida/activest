package com.activest.adm.integration.brapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Um ativo da lista oficial ({@code GET /api/v2/tickers}). Esse endpoint nao
 * gasta cota.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrApiTicker(
    String symbol,
    String name,
    String longName,
    String assetType,
    String subType,
    String sector,
    Boolean isActive
) {
}
