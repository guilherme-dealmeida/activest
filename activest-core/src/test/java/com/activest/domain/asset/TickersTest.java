package com.activest.domain.asset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra do ticker base (docs/planos/PLANO_TICKER_BASE.md, E2). A tabela fixa as tres familias que o catalogo tem:
 * o fracionario com lote padrao, o fracionario de classe B (F depois de letra), o fracionario que so existe no
 * fracionario (o lote padrao nao esta no catalogo) e o ticker com hifen (que nao e fracionario).
 */
class TickersTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        // fracionario com lote padrao
        "PETR4F|PETR4",
        "VALE3F|VALE3",
        "ITSA4F|ITSA4",
        // fracionario de classe B (o F vem depois de letra)
        "MRSA3BF|MRSA3B",
        "MRSA5BF|MRSA5B",
        "MRSA6BF|MRSA6B",
        "EQMA3BF|EQMA3B",
        // fracionario que so existe no fracionario (o lote padrao nao esta no catalogo)
        "EKTR3F|EKTR3",
        "CEDO3F|CEDO3",
        "BMIN3F|BMIN3",
        // ticker com hifen: nao e fracionario
        "TRTN-F|TRTN-F",
        "BANC-F|BANC-F",
        "RITM-F|RITM-F",
        "ABR-F|ABR-F",
        // papel inteiro
        "PETR4|PETR4",
        "MGLU3|MGLU3",
        // letra antes do F, mas sem base no catalogo
        "KVACUF|KVACU"
    })
    void base(String ticker, String expected) {
        assertThat(Tickers.base(ticker)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "PETR4F|true",
        "MRSA3BF|true",
        "EKTR3F|true",
        "KVACUF|true",
        "PETR4|false",
        "TRTN-F|false",
        "ABR-F|false",
        "PETR3|false"
    })
    void isOddLot(String ticker, boolean expected) {
        assertThat(Tickers.isOddLot(ticker)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Ticker com espaço e minúsculas: normalizado")
    void normaliza() {
        assertThat(Tickers.base("  petr4f ")).isEqualTo("PETR4");
        assertThat(Tickers.isOddLot("  petr4f ")).isTrue();
    }

    @Test
    @DisplayName("Ticker de 5 caracteres terminado em F nao e fracionario")
    void curtoNaoEhFracionario() {
        assertThat(Tickers.base("ABCF")).isEqualTo("ABCF");
        assertThat(Tickers.isOddLot("ABCF")).isFalse();
    }

    @Test
    @DisplayName("Ticker nulo nao quebra")
    void nulo() {
        assertThat(Tickers.base(null)).isNull();
        assertThat(Tickers.isOddLot(null)).isFalse();
    }

    @Test
    @DisplayName("O F de classe B sai; o ticker com hifen fica inteiro")
    void classes() {
        assertThat(Tickers.base("MRSA3BF")).isEqualTo("MRSA3B");
        assertThat(Tickers.base("TRTN-F")).isEqualTo("TRTN-F");
    }
}
