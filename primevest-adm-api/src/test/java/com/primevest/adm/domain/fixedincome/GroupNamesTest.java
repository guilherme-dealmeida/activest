package com.primevest.adm.domain.fixedincome;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Nome automatico do grupo — a mesma regra da sugestao "automatica" da planilha de curadoria de 09/10/2026. */
class GroupNamesTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "XP - PRUDENCIAL|XP",
        "BCO DO NORDESTE DO BRASIL S.A. - PRUDENCIAL|Banco do Nordeste do Brasil",
        "BCO CARGILL S.A. - PRUDENCIAL|Banco Cargill",
        "SUPERLÓGICA SCD PRUDENCIAL|Superlógica",
        "BLU IP - PRUDENCIAL|Blu",
        "PEAK SEP - PRUDENCIAL|Peak SEP",
        "AMARIL FRANKLIN CTV LTDA - PRUDENCIAL|Amaril Franklin",
        "HARMOS SCFI - PRUDENCIAL|Harmos",
        "BRINKS PAY IP - PRUDENCIAL|Brinks Pay",
        "DLOCAL BRASIL INSTITUIÇÃO DE PAGAMENTO - PRUDENCIA|Dlocal Brasil Instituição de Pagamento",
        "BCO DES. DO ES S.A. - PRUDENCIAL|Banco Des. do ES"
    })
    void isAutomatic(String bcb, String expected) {
        assertThat(GroupNames.automatic(bcb)).isEqualTo(expected);
    }
}
