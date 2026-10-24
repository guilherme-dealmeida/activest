package com.activest.adm.domain.fundamentals;

import com.activest.adm.integration.cvm.CvmFixtures;
import com.activest.adm.integration.cvm.CvmFreCapital;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FRE 2015 e FRE 2025 reais (src/test/resources/cvm). */
class FreCapitalHistoryTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("Petrobras: a quantidade de 2015 vem do FRE de 2015 (13,04 bi), a de 2025 do FRE de 2025 (12,89 bi)")
    void nearestFre() {
        List<CvmFreCapital.Event> events = new ArrayList<>(CvmFreCapital.read(CvmFixtures.zip("fre2015", 2015, tmp), 2015));
        events.addAll(CvmFreCapital.read(CvmFixtures.zip("fre_cia_aberta_2026", 2025, tmp), 2025));
        FreCapitalHistory h = new FreCapitalHistory(events);

        assertThat(h.at("33000167000101", LocalDate.of(2015, 12, 31)).total()).isEqualTo(13_044_496_930L);
        assertThat(h.at("33000167000101", LocalDate.of(2025, 12, 31)).total()).isEqualTo(12_888_732_761L);
        // Itau em unidades no FRE; so ha o FRE 2025 no recorte
        assertThat(h.at("60872504000123", LocalDate.of(2025, 12, 31)).total()).isEqualTo(11_026_869_192L);
        assertThat(h.at("99999999000199", LocalDate.of(2025, 12, 31))).isNull();
    }
}
