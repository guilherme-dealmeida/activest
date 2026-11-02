package com.activest.adm.domain.foreign;

import com.activest.adm.integration.foreign.ForeignEventsProvider;
import com.activest.adm.integration.foreign.ForeignEventsProvider.Dividend;
import com.activest.adm.integration.foreign.ForeignEventsProvider.Split;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Proventos e desdobramentos do exterior (docs/planos/PLANO_PROVENTOS_INTERNACIONAIS.md): data-com pelo pregão anterior
 * à ex-date, papel listado na data-com, moeda, correção sem duplicar e os fatores de desdobramento e grupamento.
 */
class ForeignCorporateEventsIT extends IntegrationTestBase {

    private static final YearMonth NOV = YearMonth.of(2026, 11);

    @MockBean
    ForeignEventsProvider provider;

    /** O MassiveClient é as duas fontes: o mock de uma tira a outra do contexto. */
    @MockBean
    com.activest.adm.integration.foreign.ForeignQuotesProvider quotes;

    @Autowired
    ForeignCorporateEventsService service;

    @BeforeEach
    void catalog() {
        when(provider.sourceName()).thenReturn("Massive");
        insert("ZZKO", "STOCK", null);
        insert("ZZREU", "STOCK", LocalDate.of(2026, 10, 29));
        insert("ZZREU", "STOCK", null);
        // pregões carregados: quinta 29/10 e sexta 30/10
        for (String d : List.of("2026-10-29", "2026-10-30")) {
            jdbc.update("""
                    INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
                    SELECT id, CAST(? AS DATE), 10, 'MASSIVE' FROM assets WHERE ticker = 'ZZKO'""", d);
        }
        when(provider.splits(any(), anyBoolean())).thenReturn(new ForeignEventsProvider.Month<>(NOV, List.of(), false, 1));
    }

    @AfterEach
    void clear() {
        jdbc.update("DELETE FROM asset_price_history WHERE source = 'MASSIVE'");
        jdbc.update("DELETE FROM asset_cash_events WHERE source = 'MASSIVE'");
        jdbc.update("DELETE FROM asset_stock_events WHERE source = 'MASSIVE'");
        jdbc.update("DELETE FROM assets WHERE ticker LIKE 'ZZ%' AND country_code = 'US'");
    }

    private void insert(String ticker, String type, LocalDate delistedOn) {
        jdbc.update("""
                INSERT INTO assets (ticker, name, active, asset_type_id, currency_code, country_code, delisted_on,
                                    delisting_source)
                SELECT ?, ?, ?, id, 'USD', 'US', ?, ? FROM asset_types WHERE name = ?""",
                ticker, "Teste " + ticker, delistedOn == null, delistedOn, delistedOn == null ? null : "FORM_25", type);
    }

    private static Dividend dividend(String id, String ticker, String ex, String amount, String currency) {
        return new Dividend(id, ticker, LocalDate.parse(ex), LocalDate.parse(ex).plusDays(14), LocalDate.parse(ex),
                LocalDate.parse(ex).minusDays(10), new BigDecimal(amount), currency, "CD");
    }

    private void month(Dividend... ds) {
        when(provider.dividends(any(), anyBoolean())).thenReturn(new ForeignEventsProvider.Month<>(NOV, List.of(ds),
                false, 1));
    }

    private List<Map<String, Object>> events(String ticker) {
        return jdbc.queryForList("""
                SELECT e.data_com, e.value_per_share, e.label, e.payment_date, a.delisted_on FROM asset_cash_events e
                JOIN assets a ON a.id = e.asset_id WHERE a.ticker = ? AND e.source = 'MASSIVE' ORDER BY e.data_com""",
                ticker);
    }

    @Test
    @DisplayName("Data-com = pregão anterior à ex-date; outra moeda e ticker fora do catálogo ignorados; deslistado até a saída")
    void dividends() {
        month(dividend("E1", "ZZKO", "2026-11-02", "0.51", "USD"),
                dividend("E2", "ZZKO", "2026-11-03", "0.10", "CAD"),
                dividend("E3", "OTCX", "2026-11-02", "1", "USD"),
                dividend("E4", "ZZREU", "2026-10-30", "0.20", "USD"),
                dividend("E5", "ZZREU", "2026-11-02", "0.30", "USD"));

        ForeignCorporateEventsService.Result r = service.importMonths(NOV, NOV, true, "teste");

        assertThat(r.inserted()).isEqualTo(3);
        assertThat(r.otherCurrency()).isEqualTo(1);
        assertThat(r.notInCatalog()).isEqualTo(1);
        assertThat(events("ZZKO")).singleElement().satisfies(e -> {
            assertThat(e.get("data_com")).hasToString("2026-10-30"); // segunda 02/11 → sexta 30/10
            assertThat((BigDecimal) e.get("value_per_share")).isEqualByComparingTo("0.51");
            assertThat(e.get("label")).isEqualTo("CD");
            assertThat(e.get("payment_date")).hasToString("2026-11-16");
        });
        // ex 30/10 → data-com 29/10 (o deslistado saiu em 29/10); ex 02/11 → data-com 30/10 (o vivo)
        assertThat(events("ZZREU")).hasSize(2).satisfies(list -> {
            assertThat(list.get(0).get("delisted_on")).isNotNull();
            assertThat(list.get(1).get("delisted_on")).isNull();
        });
    }

    @Test
    @DisplayName("Correção da fonte (mesmo id, valor novo) atualiza o evento sem duplicar; sem mudança, nada muda")
    void correction() {
        month(dividend("E1", "ZZKO", "2026-11-02", "0.51", "USD"));
        service.importMonths(NOV, NOV, true, "teste");

        month(dividend("E1", "ZZKO", "2026-11-02", "0.52", "USD"));
        ForeignCorporateEventsService.Result r = service.importMonths(NOV, NOV, true, "teste");

        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.inserted()).isZero();
        assertThat(events("ZZKO")).singleElement()
                .satisfies(e -> assertThat((BigDecimal) e.get("value_per_share")).isEqualByComparingTo("0.52"));

        ForeignCorporateEventsService.Result again = service.importMonths(NOV, NOV, true, "teste");
        assertThat(again.unchanged()).isEqualTo(1);
        assertThat(again.updated()).isZero();
    }

    @Test
    @DisplayName("Desdobramento 1→2 = DESDOBRAMENTO 100; grupamento 10→1 = GRUPAMENTO 0,1; data-com pelo pregão anterior")
    void splits() {
        month();
        when(provider.splits(any(), anyBoolean())).thenReturn(new ForeignEventsProvider.Month<>(NOV, List.of(
                new Split("S1", "ZZKO", LocalDate.of(2026, 11, 2), BigDecimal.ONE, BigDecimal.valueOf(2)),
                new Split("S2", "ZZREU", LocalDate.of(2026, 11, 2), BigDecimal.TEN, BigDecimal.ONE)), false, 1));

        ForeignCorporateEventsService.Result r = service.importMonths(NOV, NOV, true, "teste");

        assertThat(r.splitsInserted()).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT a.ticker, e.type, e.factor, e.data_com FROM asset_stock_events e JOIN assets a ON a.id = e.asset_id
                WHERE e.source = 'MASSIVE' ORDER BY a.ticker""")).satisfies(list -> {
            assertThat(list.get(0).get("type")).isEqualTo("DESDOBRAMENTO");
            assertThat((BigDecimal) list.get(0).get("factor")).isEqualByComparingTo("100");
            assertThat(list.get(0).get("data_com")).hasToString("2026-10-30");
            assertThat(list.get(1).get("type")).isEqualTo("GRUPAMENTO");
            assertThat((BigDecimal) list.get(1).get("factor")).isEqualByComparingTo("0.1");
        });
        assertThat(service.importMonths(NOV, NOV, true, "teste").splitsInserted()).isZero();
    }

    @Test
    @DisplayName("Data-com sem pregão carregado (antes da série): o dia útil anterior")
    void dataComWithoutSessions() {
        TreeSet<LocalDate> sessions = new TreeSet<>(List.of(LocalDate.of(2024, 11, 1)));
        assertThat(ForeignCorporateEventsService.dataCom(LocalDate.of(2015, 3, 2), sessions)).hasToString("2015-02-27");
        assertThat(ForeignCorporateEventsService.dataCom(LocalDate.of(2015, 3, 3), sessions)).hasToString("2015-03-02");
    }
}
