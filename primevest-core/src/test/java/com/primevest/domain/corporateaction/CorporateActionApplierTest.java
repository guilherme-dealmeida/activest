package com.primevest.domain.corporateaction;

import com.primevest.domain.corporateaction.CorporateActionApplier.Holding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra de cada evento societario, sem banco e sem Spring (PLANO_BRAPI.md, F6).
 *
 * <p>Os fatores sao os que a B3 publica, conferidos em casos reais (A12):
 * desdobramento e bonificacao em percentual de acoes novas, grupamento como
 * multiplicador.</p>
 */
class CorporateActionApplierTest {

    private static Holding h(String shares, String cost) {
        return new Holding(new BigDecimal(shares), new BigDecimal(cost));
    }

    private static Holding apply(Holding holding, StockEventType type, String factor, String assignedCost) {
        return CorporateActionApplier.apply(holding, type, new BigDecimal(factor),
                assignedCost == null ? null : new BigDecimal(assignedCost));
    }

    private static BigDecimal average(Holding holding) {
        return holding.cost().divide(holding.shares(), 4, RoundingMode.HALF_UP);
    }

    @Nested
    @DisplayName("Desdobramento")
    class Desdobramento {

        @Test
        @DisplayName("1:2 (fator 100, PETR 2008): dobra as cotas e mantem o custo")
        void umParaDois() {
            Holding depois = apply(h("100", "3000"), StockEventType.DESDOBRAMENTO, "100", null);

            assertThat(depois.shares()).isEqualByComparingTo("200");
            assertThat(depois.cost()).isEqualByComparingTo("3000");
            assertThat(average(depois)).isEqualByComparingTo("15.0000");
        }

        @Test
        @DisplayName("1:3 (fator 200): triplica as cotas, preco medio cai a um terco")
        void umParaTres() {
            Holding depois = apply(h("100", "3000"), StockEventType.DESDOBRAMENTO, "200", null);

            assertThat(depois.shares()).isEqualByComparingTo("300");
            assertThat(average(depois)).isEqualByComparingTo("10.0000");
        }

        @Test
        @DisplayName("1:4 (fator 300, MGLU 2020)")
        void umParaQuatro() {
            assertThat(apply(h("10", "400"), StockEventType.DESDOBRAMENTO, "300", null).shares())
                    .isEqualByComparingTo("40");
        }
    }

    @Nested
    @DisplayName("Grupamento")
    class Grupamento {

        @Test
        @DisplayName("10:1 (fator 0,1, MGLU 2024) sem fracao: cotas / 10 e o mesmo custo")
        void dezParaUmSemFracao() {
            Holding depois = apply(h("100", "1000"), StockEventType.GRUPAMENTO, "0.1", null);

            assertThat(depois.shares()).isEqualByComparingTo("10");
            assertThat(depois.cost()).isEqualByComparingTo("1000");
            assertThat(average(depois)).isEqualByComparingTo("100.0000");
        }

        @Test
        @DisplayName("10:1 com fracao: 105 cotas viram 10, e 1/21 do custo sai com a fracao leiloada")
        void dezParaUmComFracao() {
            Holding depois = apply(h("105", "2100"), StockEventType.GRUPAMENTO, "0.1", null);

            assertThat(depois.shares()).isEqualByComparingTo("10");
            assertThat(depois.cost()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("Menos cotas que o grupo: a posicao inteira vira fracao e zera")
        void tudoViraFracao() {
            Holding depois = apply(h("5", "50"), StockEventType.GRUPAMENTO, "0.1", null);

            assertThat(depois.shares()).isEqualByComparingTo("0");
            assertThat(depois.cost()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Bonificacao")
    class Bonificacao {

        @Test
        @DisplayName("10% com custo atribuido: cotas novas entram a esse custo")
        void comCusto() {
            Holding depois = apply(h("100", "2000"), StockEventType.BONIFICACAO, "10", "15.00");

            assertThat(depois.shares()).isEqualByComparingTo("110");
            assertThat(depois.cost()).isEqualByComparingTo("2150");
        }

        @Test
        @DisplayName("10% sem custo informado (B27): cotas novas com custo zero provisorio")
        void semCustoZeroProvisorio() {
            Holding depois = apply(h("100", "2000"), StockEventType.BONIFICACAO, "10", null);

            assertThat(depois.shares()).isEqualByComparingTo("110");
            assertThat(depois.cost()).isEqualByComparingTo("2000");
            assertThat(average(depois)).isEqualByComparingTo("18.1818");
        }

        @Test
        @DisplayName("5% sobre 15 cotas: a fracao (0,75) nao entra")
        void fracaoNaoEntra() {
            Holding depois = apply(h("15", "300"), StockEventType.BONIFICACAO, "5", null);

            assertThat(depois.shares()).isEqualByComparingTo("15");
        }
    }

    @Test
    @DisplayName("Posicao zerada nao muda: evento antes da compra ou depois da venda total")
    void posicaoZerada() {
        Holding vazia = h("0", "0");

        assertThat(apply(vazia, StockEventType.DESDOBRAMENTO, "100", null)).isEqualTo(vazia);
        assertThat(apply(vazia, StockEventType.BONIFICACAO, "10", null)).isEqualTo(vazia);
    }
}
