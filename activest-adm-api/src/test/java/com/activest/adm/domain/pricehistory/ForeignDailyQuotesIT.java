package com.activest.adm.domain.pricehistory;

import com.activest.adm.integration.foreign.ForeignQuotesProvider;
import com.activest.adm.integration.foreign.ForeignQuotesProvider.DayQuotes;
import com.activest.adm.integration.foreign.ForeignQuotesProvider.Quote;
import com.activest.adm.integration.foreign.ForeignQuotesProvider.Status;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Rotina do preço diário dos internacionais (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md): gravação no histórico e no
 * preço atual, só os vivos, preferencial pela grafia do catálogo, idempotência, pregão antigo sem trocar o preço atual
 * (I2), dia não liberado ou sem pregão (I4, I5) e a fonte que recusa a chave (I5, I3).
 */
class ForeignDailyQuotesIT extends IntegrationTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

    @MockBean
    ForeignQuotesProvider provider;

    @Autowired
    ForeignDailyQuotesService service;

    @BeforeEach
    void catalog() {
        when(provider.sourceName()).thenReturn("Massive");
        insert("ZZIAU", "ETF_INTERNACIONAL", null);
        insert("ZZAPL", "STOCK", null);
        insert("ZZO-E", "REIT", null);
        insert("ZZGONE", "STOCK", null);
        insert("ZZOLD", "STOCK", LocalDate.of(2015, 3, 2));
    }

    @AfterEach
    void clear() {
        jdbc.update("DELETE FROM asset_price_history WHERE source = 'MASSIVE'");
        jdbc.update("DELETE FROM assets WHERE ticker LIKE 'ZZ%' AND country_code = 'US'");
    }

    private void insert(String ticker, String type, LocalDate delistedOn) {
        jdbc.update("""
                INSERT INTO assets (ticker, name, active, asset_type_id, currency_code, country_code, delisted_on,
                                    delisting_source)
                SELECT ?, ?, ?, id, 'USD', 'US', ?, ? FROM asset_types WHERE name = ?""",
                ticker, "Teste " + ticker, delistedOn == null, delistedOn, delistedOn == null ? null : "FORM_25", type);
    }

    private static Quote quote(String ticker, String close, long volume) {
        BigDecimal c = new BigDecimal(close);
        return new Quote(ticker, c, c, c, c, volume);
    }

    private static DayQuotes ok(LocalDate d, Quote... quotes) {
        return new DayQuotes(d, Status.OK, List.of(quotes), false);
    }

    private BigDecimal currentPrice(String ticker) {
        return jdbc.queryForObject("SELECT current_price FROM assets WHERE ticker = ?", BigDecimal.class, ticker);
    }

    private LocalDateTime priceUpdatedAt(String ticker) {
        return jdbc.queryForObject("SELECT price_updated_at FROM assets WHERE ticker = ?", LocalDateTime.class, ticker);
    }

    private int days(String ticker) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM asset_price_history h JOIN assets a ON a.id = h.asset_id "
                + "WHERE a.ticker = ? AND h.source = 'MASSIVE'", Integer.class, ticker);
    }

    @Test
    @DisplayName("Pregão: vivos casados (preferencial com hífen), deslistado e papel fora do catálogo ignorados; rodar de novo não muda nada")
    void writesLiveAssets() {
        when(provider.day(DAY)).thenReturn(ok(DAY, quote("ZZIAU", "72.92", 6_971_672), quote("ZZAPL", "344.63", 10),
                quote("ZZO-E", "17.12", 12_034), quote("ZZOLD", "1.00", 1), quote("NOTINCATALOG", "5", 1)));

        ForeignDailyQuotesService.Result r = service.importDays(DAY, DAY, "teste");

        assertThat(currentPrice("ZZIAU")).isEqualByComparingTo("72.92");
        assertThat(priceUpdatedAt("ZZIAU")).isEqualTo(DAY.atTime(18, 0));
        assertThat(currentPrice("ZZO-E")).isEqualByComparingTo("17.12");
        assertThat(currentPrice("ZZGONE")).isNull();
        assertThat(currentPrice("ZZOLD")).isNull();
        assertThat(days("ZZOLD")).isZero();
        assertThat(jdbc.queryForObject("SELECT volume FROM asset_price_history h JOIN assets a ON a.id = h.asset_id "
                + "WHERE a.ticker = 'ZZIAU'", Long.class)).isEqualTo(6_971_672L);
        assertThat(r.matched()).isGreaterThanOrEqualTo(3);
        assertThat(r.missingStock()).isGreaterThanOrEqualTo(1);
        assertThat(r.quotesWritten()).isEqualTo(3);
        assertThat(r.pricesUpdated()).isEqualTo(3);

        ForeignDailyQuotesService.Result again = service.importDays(DAY, DAY, "teste");
        assertThat(again.quotesWritten()).isZero();
        assertThat(again.pricesUpdated()).isZero();
        assertThat(days("ZZIAU")).isEqualTo(1);
    }

    @Test
    @DisplayName("Pregão antigo (recuperação) grava o histórico sem trocar o preço atual mais novo (I2)")
    void olderDayKeepsCurrentPrice() {
        LocalDate before = LocalDate.of(2026, 10, 29);
        when(provider.day(DAY)).thenReturn(ok(DAY, quote("ZZIAU", "72.92", 1)));
        when(provider.day(before)).thenReturn(ok(before, quote("ZZIAU", "71.50", 1)));

        service.importDays(DAY, DAY, "teste");
        service.importDays(before, before, "teste");

        assertThat(currentPrice("ZZIAU")).isEqualByComparingTo("72.92");
        assertThat(days("ZZIAU")).isEqualTo(2);
    }

    @Test
    @DisplayName("Dia não liberado e dia sem pregão não são falha; sábado e domingo nem vão à fonte")
    void notReleasedAndNoSession() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        when(provider.day(DAY)).thenReturn(DayQuotes.of(DAY, Status.NO_SESSION));
        when(provider.day(monday)).thenReturn(DayQuotes.of(monday, Status.NOT_RELEASED));

        ForeignDailyQuotesService.Result r = service.importDays(DAY, monday, "teste");

        assertThat(r.days()).extracting(ForeignDailyQuotesService.DayResult::status)
                .containsExactly(Status.NO_SESSION, Status.NOT_RELEASED);
        assertThat(r.quotesWritten()).isZero();
    }

    @Test
    @DisplayName("Chave recusada: a rodada falha e o administrador recebe FONTE_INDISPONIVEL")
    void authFailure() {
        when(provider.day(any())).thenThrow(new ForeignQuotesProvider.AuthException("Massive recusou a chave (HTTP 401)"));

        assertThatThrownBy(() -> service.importDays(DAY, DAY, "teste"))
                .isInstanceOf(ForeignQuotesProvider.AuthException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'FONTE_INDISPONIVEL'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Carga retroativa: ticker reaproveitado casa com o papel listado no dia (deslistado até a saída, depois o vivo); dia fora do plano é pulado")
    void historyByListingDate() {
        insert("ZZREU", "STOCK", LocalDate.of(2026, 10, 28));
        insert("ZZREU", "STOCK", null);
        LocalDate d27 = LocalDate.of(2026, 10, 27);
        LocalDate d28 = LocalDate.of(2026, 10, 28);
        LocalDate d29 = LocalDate.of(2026, 10, 29);
        when(provider.day(d27)).thenThrow(new ForeignQuotesProvider.OutOfRangeException("fora do plano"));
        when(provider.day(d28)).thenReturn(ok(d28, quote("ZZREU", "10", 1)));
        when(provider.day(d29)).thenReturn(ok(d29, quote("ZZREU", "20", 1)));

        ForeignDailyQuotesService.Result r = service.importHistory(d27, d29, "teste");

        assertThat(r.daysOutOfPlan()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT h.close_price FROM asset_price_history h JOIN assets a ON a.id = h.asset_id
                WHERE a.ticker = 'ZZREU' AND a.delisted_on IS NOT NULL AND h.reference_date = ?""",
                BigDecimal.class, d28)).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("""
                SELECT h.close_price FROM asset_price_history h JOIN assets a ON a.id = h.asset_id
                WHERE a.ticker = 'ZZREU' AND a.delisted_on IS NULL AND h.reference_date = ?""",
                BigDecimal.class, d29)).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM asset_price_history h JOIN assets a ON a.id = h.asset_id
                WHERE a.ticker = 'ZZREU' AND h.source = 'MASSIVE'""", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> service.importDays(d27, d27, "teste"))
                .isInstanceOf(ForeignQuotesProvider.OutOfRangeException.class);
    }
}
