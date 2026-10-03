package com.primevest.adm.integration.brapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Um renome de ticker ({@code GET /api/v2/tickers/renames}, sem custo de cota). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrApiRename(String oldSymbol, String newSymbol, String canonicalSymbol, String effectiveDate) {
}
