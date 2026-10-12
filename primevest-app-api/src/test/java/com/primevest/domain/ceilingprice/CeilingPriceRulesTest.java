package com.primevest.domain.ceilingprice;

import com.primevest.domain.ceilingprice.CeilingPriceRules.Inputs;
import com.primevest.domain.ceilingprice.CeilingPriceRules.Issue;
import com.primevest.domain.ceilingprice.CeilingPriceRules.Level;
import com.primevest.domain.ceilingprice.CeilingPriceRules.Limits;
import com.primevest.domain.ceilingprice.CeilingPriceRules.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modelo PREÇO TETO V2 (docs/planos/PLANO_PRECO_TETO.md, 8.1). O primeiro caso é a linha VALE3 da planilha
 * {@code PRECO_TETO_V2.xlsx} (aba Valuation V2), com os valores que o Excel calculou.
 */
class CeilingPriceRulesTest {

    static final List<String> LABELS = List.of("3T25", "4T25", "1T26", "2T26");

    static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    static Inputs inputs(List<BigDecimal> quarters, Long shares, String roe, String payout, String d, String g,
                         String ms, String price) {
        return new Inputs(quarters, LABELS, shares, roe == null ? null : bd(roe), payout == null ? null : bd(payout),
                d == null ? null : bd(d), g == null ? null : bd(g), ms == null ? null : bd(ms),
                price == null ? null : bd(price));
    }

    /** A linha VALE3 da planilha. */
    static Inputs planilha() {
        return inputs(List.of(bd("9956933910"), bd("6940807500"), bd("9956933910"), bd("6940807500")),
                4262140830L, "19.26", "48.19", "13.75", "3", "15", "67.63");
    }

    static Result calc(Inputs in) {
        return CeilingPriceRules.calculate(in, Limits.defaults());
    }

    /** Igual ao Excel a 1e-12 relativo (o Excel calcula em double). */
    static void close(BigDecimal actual, String expected) {
        BigDecimal e = bd(expected);
        BigDecimal diff = actual.subtract(e).abs();
        BigDecimal tol = e.abs().multiply(bd("1e-12")).max(bd("1e-9"));
        assertThat(diff).as("%s ≈ %s", actual, expected).isLessThanOrEqualTo(tol);
    }

    static List<String> codes(Result r) {
        return r.issues().stream().map(Issue::code).toList();
    }

    @Test
    @DisplayName("Cenário 12: a linha VALE3 da PREÇO TETO V2 — todos os valores iguais aos do Excel")
    void planilhaV2Vale3() {
        Result r = calc(planilha());

        close(r.netIncomeTtm(), "33795482820");
        close(r.growth(), "0.09978606");
        close(r.fc1(), "33795482820");
        close(r.pv1(), "29710314567.032967");
        close(r.fc2(), "37167800896.40549");
        close(r.pv2(), "28725265757.395863");
        close(r.fc3(), "40876629306.72226");
        close(r.pv3(), "27772876351.45434");
        close(r.terminalValue(), "391655145915.57135");
        close(r.terminalPv(), "266102908297.6555");
        close(r.equityValue(), "352311364973.5387");
        close(r.ceilingPrice(), "82.66065787730874");
        close(r.ceilingMargin(), "0.22224837908189765");
        close(r.idealPrice(), "70.26155919571242");
        close(r.idealMargin(), "0.038911122219613015");
        assertThat(r.ceilingPrice().subtract(bd("82.66065787730874")).abs()).isLessThan(bd("1e-6"));
        assertThat(r.hasBlocking()).isFalse();
        assertThat(r.issues()).isEmpty();
    }

    @Test
    @DisplayName("Cenário 1: quatro trimestres positivos")
    void quatroPositivos() {
        Result r = calc(inputs(List.of(bd("100"), bd("200"), bd("300"), bd("400")), 10L, "20", "50", "10", "3", "15",
                "50"));
        assertThat(r.netIncomeTtm()).isEqualByComparingTo("1000");
        assertThat(r.growth()).isEqualByComparingTo("0.1");
        assertThat(r.fc2()).isEqualByComparingTo("1100");
        assertThat(r.fc3()).isEqualByComparingTo("1210");
        // VT = 1210 × 1,03 ÷ 0,07
        close(r.terminalValue(), new BigDecimal("1246.3").divide(bd("0.07"), MathContext.DECIMAL128).toPlainString());
        assertThat(r.ceilingPrice()).isNotNull();
        assertThat(r.hasBlocking()).isFalse();
    }

    @Test
    @DisplayName("Cenário 2: um prejuízo e três lucros — entra na soma, aviso de informação")
    void umPrejuizo() {
        // VALE3 real: 3T25, 4T25 (impairment), 1T26, 2T26 — soma = net_income_parent_ttm do catálogo
        Result r = calc(inputs(List.of(bd("14617000000"), bd("-21048000000"), bd("9953000000"), bd("6847000000")),
                4072366000L, "19.26", "48.19", "13.75", "3", "15", "70.77"));
        assertThat(r.netIncomeTtm()).isEqualByComparingTo("10369000000");
        assertThat(codes(r)).containsExactly("TRIMESTRE_COM_PREJUIZO");
        assertThat(r.issues().get(0).level()).isEqualTo(Level.INFORMACAO);
        assertThat(r.issues().get(0).message()).contains("4T25").contains("21,05 bi");
        assertThat(r.ceilingPrice()).isNotNull();
    }

    @Test
    @DisplayName("Cenário 3: LL TTM zero — teto zero, alerta")
    void ttmZero() {
        Result r = calc(inputs(List.of(bd("100"), bd("-100"), bd("50"), bd("-50")), 10L, "20", "50", "10", "3", "15",
                "50"));
        assertThat(r.netIncomeTtm()).isEqualByComparingTo("0");
        assertThat(r.ceilingPrice()).isEqualByComparingTo("0");
        assertThat(codes(r)).contains("LL_TTM_ZERO");
        assertThat(r.hasBlocking()).isFalse();
    }

    @Test
    @DisplayName("Cenário 4: LL TTM negativo — teto negativo, sem limitar")
    void ttmNegativo() {
        Result r = calc(inputs(List.of(bd("-100"), bd("-100"), bd("-100"), bd("-100")), 10L, "20", "50", "10", "3",
                "15", "50"));
        assertThat(r.netIncomeTtm()).isEqualByComparingTo("-400");
        assertThat(r.ceilingPrice().signum()).isNegative();
        assertThat(r.idealPrice().signum()).isNegative();
        assertThat(codes(r)).contains("LL_TTM_NEGATIVO");
    }

    @Test
    @DisplayName("Cenário 5: trimestre ausente — bloqueia o TTM e tudo a jusante, dizendo qual falta")
    void trimestreAusente() {
        Result r = calc(inputs(Arrays.asList(bd("100"), null, bd("300"), bd("400")), 10L, "20", "50", "10", "3", "15",
                "50"));
        assertThat(r.netIncomeTtm()).isNull();
        assertThat(r.fc1()).isNull();
        assertThat(r.equityValue()).isNull();
        assertThat(r.ceilingPrice()).isNull();
        assertThat(r.ceilingMargin()).isNull();
        Issue i = r.issues().stream().filter(x -> x.code().equals("TRIMESTRE_AUSENTE")).findFirst().orElseThrow();
        assertThat(i.level()).isEqualTo(Level.BLOQUEIO);
        assertThat(i.field()).isEqualTo("quarters[1]");
        assertThat(i.message()).contains("4T25");
    }

    @Test
    @DisplayName("Cenário 8: d > G — valor terminal calculado")
    void descontoMaiorQueG() {
        Result r = calc(planilha());
        assertThat(r.terminalValue()).isNotNull();
        assertThat(r.terminalWeight()).isBetween(bd("0.75"), bd("0.76"));   // 266 de 352 bi
    }

    @Test
    @DisplayName("Cenário 9: d = G e d < G — terminal não calculado, mensagem clara")
    void descontoMenorOuIgualG() {
        for (String d : List.of("3", "2")) {
            Result r = calc(inputs(planilha().quarters(), 4262140830L, "19.26", "48.19", d, "3", "15", "67.63"));
            assertThat(r.pv1()).isNotNull();
            assertThat(r.pv3()).isNotNull();
            assertThat(r.terminalValue()).isNull();
            assertThat(r.terminalPv()).isNull();
            assertThat(r.equityValue()).isNull();
            assertThat(r.ceilingPrice()).isNull();
            assertThat(r.idealPrice()).isNull();
            assertThat(r.ceilingMargin()).isNull();
            Issue i = r.issues().stream().filter(x -> x.code().equals("DESCONTO_MENOR_IGUAL_G")).findFirst()
                    .orElseThrow();
            assertThat(i.level()).isEqualTo(Level.BLOQUEIO);
            assertThat(i.message()).contains("precisa ser maior").contains("3,00%");
        }
    }

    @Test
    @DisplayName("Cenário 10: quantidade de ações ausente ou zero — sem teto, sem ideal, sem margens")
    void acoesAusentes() {
        for (Long n : Arrays.asList(null, 0L)) {
            Result r = calc(inputs(planilha().quarters(), n, "19.26", "48.19", "13.75", "3", "15", "67.63"));
            assertThat(r.equityValue()).isNotNull();
            assertThat(r.ceilingPrice()).isNull();
            assertThat(r.idealPrice()).isNull();
            assertThat(r.ceilingMargin()).isNull();
            assertThat(codes(r)).contains("ACOES_AUSENTES");
        }
    }

    @Test
    @DisplayName("Cenário 11: margem de segurança zero (ideal = teto) e maior que zero")
    void margemDeSeguranca() {
        Result zero = calc(inputs(planilha().quarters(), 4262140830L, "19.26", "48.19", "13.75", "3", "0", "67.63"));
        assertThat(zero.idealPrice()).isEqualByComparingTo(zero.ceilingPrice());
        Result quinze = calc(planilha());
        close(quinze.idealPrice(), "70.26155919571242");
        Result invalida = calc(inputs(planilha().quarters(), 4262140830L, "19.26", "48.19", "13.75", "3", "100",
                "67.63"));
        assertThat(invalida.idealPrice()).isNull();
        assertThat(invalida.ceilingPrice()).isNotNull();
        assertThat(codes(invalida)).contains("MARGEM_INVALIDA");
    }

    @Test
    @DisplayName("Preço zero ou ausente: só as margens deixam de ser calculadas")
    void precoInvalido() {
        Result r = calc(inputs(planilha().quarters(), 4262140830L, "19.26", "48.19", "13.75", "3", "15", "0"));
        assertThat(r.ceilingPrice()).isNotNull();
        assertThat(r.ceilingMargin()).isNull();
        assertThat(r.idealMargin()).isNull();
        assertThat(codes(r)).containsExactly("PRECO_INVALIDO");
    }

    @Test
    @DisplayName("D2: payout acima de 100% — alerta de premissa e de crescimento negativo, sem alterar nada")
    void payoutAcimaDe100() {
        Result r = calc(inputs(planilha().quarters(), 4262140830L, "14.69", "146", "13.75", "3", "15", "67.63"));
        assertThat(r.growth().signum()).isNegative();
        assertThat(codes(r)).contains("PREMISSA_EXTREMA", "G_NEGATIVO");
        assertThat(r.hasBlocking()).isFalse();
        assertThat(r.ceilingPrice()).isNotNull();
    }

    @Test
    @DisplayName("D2: faixas de alerta — desconto fora de 5–30%, perpetuidade acima de 6%, crescimento ≥ desconto")
    void faixasDeAlerta() {
        Result r = calc(inputs(planilha().quarters(), 4262140830L, "60", "10", "40", "7", "15", "67.63"));
        assertThat(r.issues()).extracting(Issue::field).contains("discountRatePct", "perpetualGrowthPct", "growth");
        assertThat(codes(r)).contains("G_MAIOR_QUE_D");
        // desconto negativo: só o alerta de extremo, não o de faixa
        Result neg = calc(inputs(planilha().quarters(), 4262140830L, "19.26", "48.19", "-5", "-10", "15", "67.63"));
        assertThat(neg.issues().stream().filter(i -> i.field() != null && i.field().equals("discountRatePct")).count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Premissa vazia: bloqueio com o nome do campo")
    void campoVazio() {
        Result r = calc(inputs(planilha().quarters(), 4262140830L, null, "48.19", "13.75", "3", "15", "67.63"));
        assertThat(r.growth()).isNull();
        assertThat(r.fc2()).isNull();
        assertThat(r.pv1()).isNotNull();
        assertThat(r.ceilingPrice()).isNull();
        assertThat(r.issues()).extracting(Issue::code, Issue::field).contains(
                org.assertj.core.groups.Tuple.tuple("CAMPO_VAZIO", "roePct"));
    }
}
