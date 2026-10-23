package com.primevest.adm.integration.indices;

import com.primevest.domain.calendar.BusinessCalendar;
import com.primevest.domain.marketindex.IndexPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2 do docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md: o calendario de dias uteis calculado bate com os dias em que o
 * Banco Central publicou o CDI (resposta real de 2024). A mesma conferencia contra 2010-2026 no banco do DEV deu zero
 * divergencias em 05/10/2026.
 */
class BusinessCalendarCdiTest {

    @Test
    @DisplayName("Todo dia com CDI em 2024 e dia util, e todo dia util de 2024 tem CDI")
    void calendarMatchesCdi() {
        Set<LocalDate> cdi = BcbSgsClient.parse(IndexFixtures.body("sgs_12_2024")).stream()
                .map(IndexPoint::date).collect(Collectors.toCollection(TreeSet::new));
        Set<LocalDate> businessDays = new TreeSet<>();
        for (LocalDate d = LocalDate.of(2024, 1, 1); d.getYear() == 2024; d = d.plusDays(1)) {
            if (BusinessCalendar.isBusinessDay(d)) {
                businessDays.add(d);
            }
        }
        assertThat(cdi).hasSize(253).isEqualTo(businessDays);
    }
}
