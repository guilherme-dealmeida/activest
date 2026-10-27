package com.activest.domain.fund;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FundLabelTest {

    @Test
    @DisplayName("Denominacao comercial vigente vence a social: o fundo do Guilherme vira CAIXA FIC PREV 300 RF")
    void tradeNameFirst() {
        assertThat(FundLabel.of("CAIXA FIC PREV 300 RF",
                "CAIXA 300 PREVIDENCIÁRIO FIC DE CLASSE DE FIF RENDA FIXA - RESPONSABILIDADE LIMITADA"))
            .isEqualTo("CAIXA FIC PREV 300 RF");
    }

    @Test
    @DisplayName("Sem comercial, a social abreviada pela tabela fixa")
    void legalNameAbbreviated() {
        assertThat(FundLabel.of(null,
                "CAIXA 300 PREVIDENCIÁRIO FIC DE CLASSE DE FIF RENDA FIXA - RESPONSABILIDADE LIMITADA"))
            .isEqualTo("CAIXA 300 PREV FIC CLASSE FIF RF RL");
        assertThat(FundLabel.abbreviate(
                "FUNDO DE INVESTIMENTO EM COTAS DE FUNDOS DE INVESTIMENTO PREVIDENCIÁRIO CAIXA RENDA FIXA 300"))
            .isEqualTo("FIC FI PREV CAIXA RF 300");
        assertThat(FundLabel.abbreviate("BABY FUNDO DE INVESTIMENTO MULTIMERCADO CRÉDITO PRIVADO INVESTIMENTO NO EXTERIOR"))
            .isEqualTo("BABY FI MULT CP IE");
        assertThat(FundLabel.abbreviate("WP FUNDO DE INVESTIMENTO FINANCEIRO")).isEqualTo("WP FIF");
    }

    @Test
    @DisplayName("Abreviacao so de palavra inteira; hifen dentro de palavra fica")
    void wholeWords() {
        assertThat(FundLabel.abbreviate("XP L/S - DIRECIONAL FIM")).isEqualTo("XP L/S DIRECIONAL FIM");
        assertThat(FundLabel.abbreviate("ITAU PRIVATE-PREVIDENCIARIOS")).isEqualTo("ITAU PRIVATE-PREVIDENCIARIOS");
        assertThat(FundLabel.abbreviate("  ")).isEmpty();
        assertThat(FundLabel.abbreviate(null)).isEmpty();
    }
}
