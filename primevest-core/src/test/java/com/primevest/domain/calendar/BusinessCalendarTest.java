package com.primevest.domain.calendar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** R2 do docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md: feriados nacionais calculados (a prova contra o CDI esta na adm-api). */
class BusinessCalendarTest {

    @Test
    @DisplayName("Pascoa e feriados moveis de 2024 e 2026")
    void moveis() {
        assertThat(BusinessCalendar.easter(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(BusinessCalendar.easter(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(BusinessCalendar.holidays(2026)).contains(LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17),
                LocalDate.of(2026, 4, 3), LocalDate.of(2026, 6, 4));
    }

    @Test
    @DisplayName("20/11 so a partir de 2024; 2024 tem 253 dias uteis (os 253 dias do CDI)")
    void consciencia() {
        assertThat(BusinessCalendar.isBusinessDay(LocalDate.of(2023, 11, 20))).isTrue();
        assertThat(BusinessCalendar.isBusinessDay(LocalDate.of(2024, 11, 20))).isFalse();
        assertThat(BusinessCalendar.businessDaysBetween(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))).isEqualTo(253);
        assertThat(BusinessCalendar.businessDaysInMonth(LocalDate.of(2024, 1, 15))).isEqualTo(22);
        assertThat(BusinessCalendar.isBusinessDay(LocalDate.of(2026, 10, 3))).isFalse(); // sabado
    }
}
