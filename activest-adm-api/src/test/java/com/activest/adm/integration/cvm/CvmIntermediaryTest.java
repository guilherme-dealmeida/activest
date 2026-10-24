package com.activest.adm.integration.cvm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Recortes reais do cadastro de intermediarios e do informe de CRI da CVM (16/10/2026). */
class CvmIntermediaryTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("Uma entrada por CNPJ, com os tipos de participacao somados e o nome comercial")
    void intermediarios() {
        Map<String, CvmIntermediary> m = CvmIntermediary.parse(CvmFixtures.zip("cad_intermed", 2026, tmp));

        assertThat(m).containsOnlyKeys("33264668000103", "62169875000179", "92875780000131", "02332886000104");
        CvmIntermediary nu = m.get("62169875000179");
        assertThat(nu.tradeName()).isEqualTo("Nu Invest");
        assertThat(nu.hasType("CORRETORAS")).isTrue();
        assertThat(nu.hasType("ESCRITURADORES")).isTrue();
        assertThat(nu.status()).isEqualTo(CvmIntermediary.ACTIVE);
        // Warren: uma linha de custodiante cancelada e outra em funcionamento — vale a em funcionamento
        assertThat(m.get("92875780000131").status()).isEqualTo(CvmIntermediary.ACTIVE);
        assertThat(m.get("33264668000103").hasType("BANCOS")).isTrue();
    }

    @Test
    @DisplayName("Securitizadoras do informe de CRI: CNPJ e o nome informado")
    void securitizadoras() {
        Map<String, String> out = new HashMap<>();
        CvmSecuritizer.parse(CvmFixtures.zip("inf_mensal_cri_2026", 2026, tmp), "cri", 2026, out, new HashMap<>());

        assertThat(out).containsOnlyKeys("02773542000122", "09304427000158");
        assertThat(out.get("09304427000158")).isEqualTo("HABITASEC SECURITIZADORA S.A.");
    }
}
