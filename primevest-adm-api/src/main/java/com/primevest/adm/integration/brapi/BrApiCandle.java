package com.primevest.adm.integration.brapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Um pregao do historico diario ({@code historicalDataPrice[]}).
 *
 * @param date epoch em segundos, meia-noite de Sao Paulo do pregao
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrApiCandle(
    Long date,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    Long volume,
    BigDecimal adjustedClose
) {

    private static final ZoneId B3 = ZoneId.of("America/Sao_Paulo");

    /** O dia do pregao, no fuso da B3. */
    public LocalDate day() {
        return Instant.ofEpochSecond(date).atZone(B3).toLocalDate();
    }
}
