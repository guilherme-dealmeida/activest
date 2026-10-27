package com.activest.domain.fund;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FundKeysTest {

    @Test
    @DisplayName("CNPJ sem pontuacao, com mascara e alfanumerico (07/2026)")
    void cnpj() {
        assertThat(FundKeys.cnpj("03.926.431/0001-71")).isEqualTo("03926431000171");
        assertThat(FundKeys.cnpj("12.abc.345/01de-35")).isEqualTo("12ABC34501DE35");
        assertThat(FundKeys.cnpj("123")).isNull();
        assertThat(FundKeys.format("03926431000171")).isEqualTo("03.926.431/0001-71");
        assertThat(InvestmentFund.ticker("03926431000171", null)).isEqualTo("03926431000171");
        assertThat(InvestmentFund.ticker("03926431000171", "MZMRC1747322915")).isEqualTo("03926431000171-MZMRC1747322915");
    }

    @Test
    @DisplayName("Texto de busca sem acento e em maiusculas")
    void normalize() {
        assertThat(FundKeys.normalize("Caixa  Previdência   Ações")).isEqualTo("CAIXA PREVIDENCIA ACOES");
    }

    @Test
    @DisplayName("Categoria: 1o nivel ANBIMA; sem ANBIMA, a CVM; sem nenhuma, Outros")
    void category() {
        assertThat(FundCategory.of("Previdência RF Duração Livre Grau de Inv", "Renda Fixa")).isEqualTo("Previdência");
        assertThat(FundCategory.of("Multimercados Invest. no Exterior", "Multimercado")).isEqualTo("Multimercado");
        assertThat(FundCategory.of("Renda Fixa Duração Livre Crédito Livre", "")).isEqualTo("Renda Fixa");
        assertThat(FundCategory.of("Fechados de Ações", "Ações")).isEqualTo("Ações");
        assertThat(FundCategory.of("Fundos de Mono Ação", "Ações")).isEqualTo("Ações");
        assertThat(FundCategory.of("Ações FMP - FGTS", "FMP-FGTS")).isEqualTo("Ações");
        assertThat(FundCategory.of("Cambial", "Cambial")).isEqualTo("Cambial");
        assertThat(FundCategory.of("", "FMP-FGTS")).isEqualTo("Ações");
        assertThat(FundCategory.of("FII", "Multimercado")).isEqualTo("Multimercado");
        assertThat(FundCategory.of(null, null)).isEqualTo("Outros");
    }
}
