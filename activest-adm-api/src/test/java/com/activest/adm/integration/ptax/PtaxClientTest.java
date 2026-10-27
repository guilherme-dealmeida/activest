package com.activest.adm.integration.ptax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Leitura da resposta da API PTAX (docs/planos/PLANO_CADASTRO_MOEDAS.md, D7 e D22). */
class PtaxClientTest {

    private static final String BODY = """
        {"@odata.context": "x", "value": [
          {"cotacaoCompra": 4.8507, "cotacaoVenda": 4.8513, "dataHoraCotacao": "2024-01-02 10:08:28.473", "tipoBoletim": "Abertura"},
          {"cotacaoCompra": 4.8800, "cotacaoVenda": 4.8806, "dataHoraCotacao": "2024-01-02 11:04:20.311", "tipoBoletim": "Intermediário"},
          {"cotacaoCompra": 4.8910, "cotacaoVenda": 4.8916, "dataHoraCotacao": "2024-01-02 13:04:20.311", "tipoBoletim": "Fechamento"},
          {"cotacaoCompra": 4.9213, "cotacaoVenda": 4.9219, "dataHoraCotacao": "2024-01-03 13:06:30.000", "tipoBoletim": "Fechamento PTAX"},
          {"cotacaoCompra": 4.9300, "cotacaoVenda": 4.9306, "dataHoraCotacao": "2024-01-04 10:08:28.473", "tipoBoletim": "Abertura"}
        ]}""";

    @Test
    @DisplayName("Fica só o boletim de fechamento de cada dia, com compra, venda e hora; dia sem fechamento não entra")
    void parse() {
        List<PtaxClient.Bulletin> b = PtaxClient.parse(BODY);
        assertThat(b).extracting(PtaxClient.Bulletin::date)
            .containsExactly(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 3));
        assertThat(b.get(0).buy()).isEqualByComparingTo("4.8910");
        assertThat(b.get(0).sell()).isEqualByComparingTo("4.8916");
        assertThat(b.get(0).at().getHour()).isEqualTo(13);
    }

    @Test
    @DisplayName("Corpo sem a lista value não é aceito para gravar em disco")
    void valid() {
        assertThat(PtaxClient.isValid(BODY)).isTrue();
        assertThat(PtaxClient.isValid("{\"error\": 1}")).isFalse();
        assertThat(PtaxClient.isValid("<html>")).isFalse();
    }
}
