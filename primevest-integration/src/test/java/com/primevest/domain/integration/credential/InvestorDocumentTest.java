package com.primevest.domain.integration.credential;

import com.primevest.domain.integration.IntegrationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CPF da conexao com a B3 (docs/planos/PLANO_INTEGRACAO_B3.md, G1 e D10). CPF de exemplo, ficticio. */
class InvestorDocumentTest {

    @Test
    @DisplayName("Aceita CPF com ou sem mascara e guarda so os digitos")
    void normaliza() {
        assertThat(new InvestorDocument("529.982.247-25").documentNumber()).isEqualTo("52998224725");
        assertThat(new InvestorDocument(" 52998224725 ").documentNumber()).isEqualTo("52998224725");
    }

    @Test
    @DisplayName("Recusa CPF com digito verificador errado, curto ou repetido")
    void recusaInvalido() {
        for (String cpf : new String[] {"529.982.247-24", "1234567890", "111.111.111-11", "", null}) {
            assertThatThrownBy(() -> new InvestorDocument(cpf)).as(String.valueOf(cpf))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CPF");
        }
    }

    @Test
    @DisplayName("Fingerprint sao os 2 ultimos digitos; toString nunca mostra o CPF")
    void mascarado() {
        InvestorDocument d = new InvestorDocument("529.982.247-25");
        assertThat(d.fingerprint()).isEqualTo("25");
        assertThat(d.toString()).doesNotContain("529982247").endsWith("25]");
    }

    @Test
    @DisplayName("A fonte de um movimento sai do prefixo do externalId")
    void fonteDoExternalId() {
        assertThat(IntegrationProvider.fromExternalId("binance:spot:BTCBRL:1")).isEqualTo(IntegrationProvider.BINANCE);
        assertThat(IntegrationProvider.fromExternalId("b3:negociacao:2025-01-02:PETR4:C")).isEqualTo(IntegrationProvider.B3);
        assertThatThrownBy(() -> IntegrationProvider.fromExternalId("semprefixo"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
