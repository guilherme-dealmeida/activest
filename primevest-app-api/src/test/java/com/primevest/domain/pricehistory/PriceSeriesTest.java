package com.primevest.domain.pricehistory;

import com.primevest.domain.corporateaction.StockEventType;
import com.primevest.domain.pricehistory.PriceSeries.Event;
import com.primevest.domain.pricehistory.PriceSeries.Interval;
import com.primevest.domain.pricehistory.PriceSeries.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras da cotacao historica (docs/planos/PLANO_TELA_EMISSORES.md, 2.3, B5, D9). */
class PriceSeriesTest {

    private static NavigableMap<LocalDate, BigDecimal> closes(Object... pairs) {
        NavigableMap<LocalDate, BigDecimal> m = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(LocalDate.parse((String) pairs[i]), new BigDecimal((String) pairs[i + 1]));
        }
        return m;
    }

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    @Test
    @DisplayName("Desdobramento 1:5 (SBSP3, 04/2026): precos ate a data-com divididos por 5; sem degrau")
    void desdobramento() {
        var c = closes("2026-04-01", "600.00", "2026-04-02", "610.00", "2026-04-06", "122.00", "2026-04-07", "123.00");
        var adj = PriceSeries.adjust(c, List.of(new Event(LocalDate.parse("2026-04-02"), StockEventType.DESDOBRAMENTO, v("400"))));
        assertThat(adj.get(LocalDate.parse("2026-04-01"))).isEqualByComparingTo("120");
        assertThat(adj.get(LocalDate.parse("2026-04-02"))).isEqualByComparingTo("122");
        assertThat(adj.get(LocalDate.parse("2026-04-06"))).isEqualByComparingTo("122");
        assertThat(adj.get(LocalDate.parse("2026-04-07"))).isEqualByComparingTo("123");
    }

    @Test
    @DisplayName("Eventos acumulados: desdobramento 1:2 e depois grupamento 10:1 — o mais antigo carrega os dois")
    void acumulados() {
        var c = closes("2020-01-02", "10.00", "2021-01-04", "6.00", "2024-01-02", "40.00", "2024-06-03", "41.00");
        var events = List.of(
                new Event(LocalDate.parse("2020-06-01"), StockEventType.DESDOBRAMENTO, v("100")), // 1 -> 2
                new Event(LocalDate.parse("2023-06-01"), StockEventType.GRUPAMENTO, v("0.1")));  // 10 -> 1
        var adj = PriceSeries.adjust(c, events);
        assertThat(adj.get(LocalDate.parse("2020-01-02"))).isEqualByComparingTo("50");   // 10 / (2 * 0,1)
        assertThat(adj.get(LocalDate.parse("2021-01-04"))).isEqualByComparingTo("60");   // 6 / 0,1
        assertThat(adj.get(LocalDate.parse("2024-01-02"))).isEqualByComparingTo("40");
        assertThat(PriceSeries.adjustedIn(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-06-03"),
                LocalDate.parse("2024-06-03"), events)).isFalse();
        assertThat(PriceSeries.adjustedIn(LocalDate.parse("2021-01-01"), LocalDate.parse("2024-06-03"),
                LocalDate.parse("2024-06-03"), events)).isTrue();
    }

    @Test
    @DisplayName("Bonificacao de 10%: preco anterior dividido por 1,1; evento na data do ultimo pregao nao ajusta")
    void bonificacaoEEventoNoUltimoPregao() {
        var c = closes("2025-01-02", "11.00", "2025-02-03", "10.00");
        var adj = PriceSeries.adjust(c, List.of(new Event(LocalDate.parse("2025-01-10"), StockEventType.BONIFICACAO, v("10"))));
        assertThat(adj.get(LocalDate.parse("2025-01-02"))).isEqualByComparingTo("10");
        var semAjuste = PriceSeries.adjust(c, List.of(new Event(LocalDate.parse("2025-02-03"), StockEventType.DESDOBRAMENTO, v("100"))));
        assertThat(semAjuste.get(LocalDate.parse("2025-01-02"))).isEqualByComparingTo("11");
        assertThat(semAjuste.get(LocalDate.parse("2025-02-03"))).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Janelas: 1D = pregao anterior; 7D; YTD; MAX = primeiro pregao; atalho maior que a vida do ativo")
    void janelas() {
        var c = closes("2021-03-01", "10", "2026-01-02", "11", "2026-09-18", "12", "2026-09-24", "13", "2026-09-25", "14");
        assertThat(PriceSeries.start(Range.D1, c)).isEqualTo("2026-09-24");
        assertThat(PriceSeries.start(Range.D7, c)).isEqualTo("2026-09-18");
        assertThat(PriceSeries.start(Range.YTD, c)).isEqualTo("2026-01-01");
        assertThat(PriceSeries.start(Range.Y1, c)).isEqualTo("2025-09-25");
        assertThat(PriceSeries.start(Range.MAX, c)).isEqualTo("2021-03-01");
        assertThat(PriceSeries.start(Range.Y5, c)).isEqualTo("2021-09-25");
        var novo = closes("2026-06-01", "10", "2026-09-25", "11");
        assertThat(PriceSeries.start(Range.Y5, novo)).isEqualTo("2026-06-01");
    }

    @Test
    @DisplayName("Atalhos pelo rotulo da tela e pelo nome; invalido falha")
    void rotulos() {
        assertThat(Range.parse("1a")).isEqualTo(Range.Y1);
        assertThat(Range.parse("MAX")).isEqualTo(Range.MAX);
        assertThat(Range.parse("ytd")).isEqualTo(Range.YTD);
        assertThatThrownBy(() -> Range.parse("2A")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Densidade: diario ate ~1 ano, semanal ate ~5, mensal acima")
    void densidade() {
        LocalDate end = LocalDate.parse("2026-09-25");
        assertThat(PriceSeries.interval(end.minusYears(1), end)).isEqualTo(Interval.DAY);
        assertThat(PriceSeries.interval(end.minusYears(5), end)).isEqualTo(Interval.WEEK);
        assertThat(PriceSeries.interval(LocalDate.parse("2010-01-04"), end)).isEqualTo(Interval.MONTH);
    }

    @Test
    @DisplayName("Pontos mensais: primeiro fechamento da janela + ultimo de cada mes")
    void pontosMensais() {
        var c = closes("2026-01-05", "1", "2026-01-30", "2", "2026-02-02", "3", "2026-02-27", "4", "2026-03-03", "5");
        List<Map.Entry<LocalDate, BigDecimal>> p = PriceSeries.points(c, Interval.MONTH);
        assertThat(p).extracting(e -> e.getKey().toString())
                .containsExactly("2026-01-05", "2026-01-30", "2026-02-27", "2026-03-03");
        var semana = PriceSeries.points(c, Interval.WEEK);
        assertThat(semana.get(semana.size() - 1).getKey()).isEqualTo("2026-03-03");
    }
}
