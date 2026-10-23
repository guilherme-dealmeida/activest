package com.primevest.adm.domain.issuer;

import com.primevest.domain.issuer.ShareClass;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Os formatos de composicao de unit vistos no FCA de 2024 a 2026. */
class UnitCompositionTextTest {

    @Test
    @DisplayName("Formatos reais do FCA")
    void formats() {
        assertThat(UnitCompositionText.parse("1 KLBN3 + 4 KLBN4")).isEqualTo(Map.of(ShareClass.ON, 1, ShareClass.PN, 4));
        assertThat(UnitCompositionText.parse("1 ON / 2 PN")).isEqualTo(Map.of(ShareClass.ON, 1, ShareClass.PN, 2));
        assertThat(UnitCompositionText.parse("1 ON e 2 PN")).isEqualTo(Map.of(ShareClass.ON, 1, ShareClass.PN, 2));
        assertThat(UnitCompositionText.parse("1 ON+ 2PN")).isEqualTo(Map.of(ShareClass.ON, 1, ShareClass.PN, 2));
        assertThat(UnitCompositionText.parse("1 ON e 2PNs")).isEqualTo(Map.of(ShareClass.ON, 1, ShareClass.PN, 2));
        assertThat(UnitCompositionText.parse("1 ON E 2 PNA")).isEqualTo(Map.of(ShareClass.ON, 1, ShareClass.PNA, 2));
        assertThat(UnitCompositionText.parse("2 ações preferenciais e 1 ação ordinária"))
                .isEqualTo(Map.of(ShareClass.ON, 1, ShareClass.PN, 2));
        assertThat(UnitCompositionText.parse("1 ação ordinária e 4 ações preferenciais"))
                .isEqualTo(Map.of(ShareClass.ON, 1, ShareClass.PN, 4));
        assertThat(UnitCompositionText.parse("04 Ações Ordinárias")).isEqualTo(Map.of(ShareClass.ON, 4));
    }

    @Test
    @DisplayName("Recibo e bonus de subscricao ficam de fora; texto sem classe devolve vazio")
    void ignored() {
        assertThat(UnitCompositionText.parse("1 PN + 3 Recibos de Subscrição")).isEqualTo(Map.of(ShareClass.PN, 1));
        assertThat(UnitCompositionText.parse("3.720.752")).isEmpty();
        assertThat(UnitCompositionText.parse("Não Aplicável")).isEmpty();
        assertThat(UnitCompositionText.parse(null)).isEmpty();
    }
}
