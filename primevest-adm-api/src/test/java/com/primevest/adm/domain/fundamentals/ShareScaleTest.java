package com.primevest.adm.domain.fundamentals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Numeros reais do DFP 2025 e do FRE 2025. */
class ShareScaleTest {

    @Test
    @DisplayName("FRE decide: Itau informa 11.026.869 (mil) e o FRE 11.026.869.192; BB ja vem em unidades")
    void peloFre() {
        var itau = ShareScale.decide(11_026_869L, 11_026_524L, 11_026_869_192L, null, null);
        assertThat(itau).isEqualTo(new ShareScale.Decision(1000, ShareScale.Status.OK, ShareScale.Check.FRE));

        var bb = ShareScale.decide(5_730_834_040L, 5_708_463_641L, 5_730_834_040L, null, null);
        assertThat(bb).isEqualTo(new ShareScale.Decision(1, ShareScale.Status.OK, ShareScale.Check.FRE));
    }

    @Test
    @DisplayName("Sem FRE, o LPA decide: Itau 44.857 mi / 11,03 bi = 4,07 contra 4,05 informado")
    void peloLpa() {
        var itau = ShareScale.decide(11_026_869L, 11_026_524L, null,
                new BigDecimal("44857000000"), new BigDecimal("4.05"));
        assertThat(itau).isEqualTo(new ShareScale.Decision(1000, ShareScale.Status.OK, ShareScale.Check.LPA));
    }

    @Test
    @DisplayName("Sem FRE e sem lucro: REVISAR sem fator (a passada pelo vizinho decide depois)")
    void revisar() {
        var semNada = ShareScale.decide(2_915_428L, 2_839_448L, null, BigDecimal.ZERO, null);
        assertThat(semNada.status()).isEqualTo(ShareScale.Status.REVISAR);
        assertThat(semNada.factor()).isNull();
    }

    @Test
    @DisplayName("FRE incoerente (desdobramento de 10x depois da aprovacao) nao decide; o LPA assume")
    void freForaDaJanela() {
        var d = ShareScale.decide(1_000_000_000L, 1_000_000_000L, 100_000_000L,
                new BigDecimal("2000000000"), new BigDecimal("2.00"));
        assertThat(d).isEqualTo(new ShareScale.Decision(1, ShareScale.Status.OK, ShareScale.Check.LPA));
    }

    @Test
    @DisplayName("Petrobras 2010, ano da capitalizacao: 35.189 mi / 13,04 bi = 2,70 contra LPA 3,57 ainda confirma 1")
    void mediaPonderada() {
        assertThat(ShareScale.byEps(13_044_496_930L, new BigDecimal("35189366000"), new BigDecimal("3.57"))).isEqualTo(1);
    }
}
