package com.activest.adm.domain.pricehistory;

import com.activest.adm.integration.binance.BinanceKlinesClient;
import com.activest.adm.integration.binance.BinanceKlinesClientAccess;
import com.activest.adm.integration.coingecko.CoinGeckoClient;
import com.activest.adm.integration.coingecko.CoinGeckoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** docs/planos/PLANO_RENTABILIDADE.md, 3.3: o dia de cada ponto, o arquivo que guarda a janela e a queda da fonte. */
class CryptoHistoryServiceTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @TempDir
    Path tmp;

    private final CoinGeckoClient coinGecko = mock(CoinGeckoClient.class);
    /** "Hoje" = 17/10/2026, 15:00 em Sao Paulo. */
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-17T18:00:00Z"), SP);

    private CryptoHistoryService service() {
        return new CryptoHistoryService(mock(JdbcTemplate.class), coinGecko, mock(BinanceKlinesClient.class), tmp, clock);
    }

    private static CoinGeckoClient.HistoryPoint p(String instant, String price) {
        return new CoinGeckoClient.HistoryPoint(Instant.parse(instant), new BigDecimal(price));
    }

    @Test
    @DisplayName("Ultimo ponto de cada dia de Sao Paulo; o diario das 00:00 UTC e o fechamento do dia anterior; hoje fica de fora")
    void saoPauloDay() {
        when(coinGecko.fetchHistoryBrl(eq("bitcoin"), any(), any())).thenReturn(List.of(
                p("2026-10-15T00:00:00Z", "100"),   // 14/10 21:00 em SP
                p("2026-10-15T20:00:00Z", "110"),   // 15/10 17:00
                p("2026-10-16T02:00:00Z", "111"),   // 15/10 23:00 — o ultimo do dia 15
                p("2026-10-16T12:00:00Z", "120"),   // 16/10
                p("2026-10-17T12:00:00Z", "130"))); // hoje: fora

        TreeMap<LocalDate, BigDecimal> days = service().coinGeckoDays("bitcoin", LocalDate.now(clock));

        assertThat(days).containsOnlyKeys(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 16));
        assertThat(days.get(LocalDate.of(2026, 10, 14))).isEqualByComparingTo("100");
        assertThat(days.get(LocalDate.of(2026, 10, 15))).isEqualByComparingTo("111");
        assertThat(days.get(LocalDate.of(2026, 10, 16))).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("O arquivo guarda o que saiu da janela; sem rede, o arquivo responde")
    void fileKeepsTheWindow() throws Exception {
        Files.writeString(tmp.resolve("bitcoin.json"), "{\"2025-11-13\":\"500000\",\"2026-10-15\":\"1\"}");
        when(coinGecko.fetchHistoryBrl(eq("bitcoin"), any(), any())).thenReturn(List.of(
                p("2026-10-16T00:00:00Z", "600000")));

        TreeMap<LocalDate, BigDecimal> days = service().coinGeckoDays("bitcoin", LocalDate.now(clock));

        assertThat(days.get(LocalDate.of(2025, 11, 13))).isEqualByComparingTo("500000");
        assertThat(days.get(LocalDate.of(2026, 10, 15))).isEqualByComparingTo("600000"); // a fonte vence
        assertThat(CryptoHistoryService.readFile(tmp.resolve("bitcoin.json"))).hasSize(2);

        when(coinGecko.fetchHistoryBrl(eq("bitcoin"), any(), any())).thenThrow(new CoinGeckoException(0, "fora"));
        assertThat(service().coinGeckoDays("bitcoin", LocalDate.now(clock))).hasSize(2);
    }

    @Test
    @DisplayName("Binance: um candle por data UTC, fechamento na quinta posicao")
    void binance() {
        String body = "[[1602547200000,\"63921.00\",\"64000.00\",\"63000.00\",\"63500.50\",\"1.2\",1602633599999],"
                + "[1602633600000,\"63500.50\",\"65000.00\",\"63400.00\",\"64800.00\",\"2.0\",1602719999999]]";
        List<BinanceKlinesClient.DailyCandle> k = BinanceKlinesClientAccess.parse(body);
        assertThat(k).extracting(BinanceKlinesClient.DailyCandle::date)
                .containsExactly(LocalDate.of(2020, 10, 13), LocalDate.of(2020, 10, 14));
        assertThat(k.get(0).close()).isEqualByComparingTo("63500.50");
    }
}
