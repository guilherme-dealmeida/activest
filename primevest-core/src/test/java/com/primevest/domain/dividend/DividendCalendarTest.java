package com.primevest.domain.dividend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O "hoje" dos proventos e o de Sao Paulo, nao o do banco (UTC).
 */
class DividendCalendarTest {

    private static DividendCalendar em(String instanteUtc) {
        return new DividendCalendar(Clock.fixed(Instant.parse(instanteUtc), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("23:30 em Sao Paulo ja e o dia seguinte em UTC, mas hoje continua sendo o dia de Sao Paulo")
    void noiteEmSaoPaulo() {
        // 24/09/2026 23:30 em Sao Paulo (UTC-3) = 25/09/2026 02:30 UTC
        assertThat(em("2026-09-25T02:30:00Z").today()).isEqualTo(LocalDate.of(2026, 9, 24));
    }

    @Test
    @DisplayName("Meia-noite em Sao Paulo vira o dia")
    void meiaNoite() {
        assertThat(em("2026-09-25T02:59:59Z").today()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(em("2026-09-25T03:00:00Z").today()).isEqualTo(LocalDate.of(2026, 9, 25));
    }
}
