package com.primevest.adm.integration.b3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layout posicional da serie historica da B3, com linhas reais de 24 e
 * 25/09/2026 (src/test/resources/b3/cotahist).
 */
class CotahistQuoteTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("So vista e fracionario: cabecalho, rodape e opcao ficam de fora")
    void filtro() {
        List<CotahistQuote> quotes = new ArrayList<>();
        CotahistQuote.forEach(B3Fixtures.cotahistZip(tmp, 2026), quotes::add);

        assertThat(quotes).extracting(CotahistQuote::ticker)
                .containsExactlyInAnyOrder("PETR4", "PETR4", "PETR4F", "PETR4F", "HGLG11", "HGLG11", "KLBN11", "KLBN11F");
    }

    @Test
    @DisplayName("PETR4 em 25/09/2026: precos em reais, quantidade e ISIN")
    void petr4() {
        List<CotahistQuote> quotes = new ArrayList<>();
        CotahistQuote.forEach(B3Fixtures.cotahistZip(tmp, 2026), quotes::add);

        CotahistQuote q = quotes.stream()
                .filter(x -> x.ticker().equals("PETR4") && x.date().equals(LocalDate.of(2026, 9, 25)))
                .findFirst().orElseThrow();
        assertThat(q.market()).isEqualTo(CotahistQuote.MARKET_SPOT);
        assertThat(q.bdi()).isEqualTo("02");
        assertThat(q.close()).isEqualByComparingTo("47.99");
        assertThat(q.high()).isGreaterThanOrEqualTo(q.low());
        assertThat(q.quantity()).isPositive();
        assertThat(q.isin()).isEqualTo("BRPETRACNPR6");

        CotahistQuote frac = quotes.stream().filter(x -> x.ticker().equals("PETR4F")).findFirst().orElseThrow();
        assertThat(frac.market()).isEqualTo(CotahistQuote.MARKET_ODD_LOT);
        // o fracionario tem o mesmo ISIN do lote padrao
        assertThat(frac.isin()).isEqualTo("BRPETRACNPR6");
    }

    @Test
    @DisplayName("Fator de cotacao 1.000: preco por lote de mil vira preco unitario")
    void fatorMil() throws Exception {
        Path txt = Path.of(CotahistQuoteTest.class.getResource("/b3/cotahist/COTAHIST_A2026.TXT").toURI());
        String line = Files.readAllLines(txt, java.nio.charset.Charset.forName("ISO-8859-1")).stream()
                .filter(l -> l.startsWith("012026092502PETR4 ")).findFirst().orElseThrow();
        String porMil = line.substring(0, 210) + "0001000" + line.substring(217);

        assertThat(CotahistQuote.parse(porMil).close()).isEqualByComparingTo("0.04799");
    }
}
