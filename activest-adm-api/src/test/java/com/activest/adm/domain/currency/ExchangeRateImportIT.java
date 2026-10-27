package com.activest.adm.domain.currency;

import com.activest.adm.integration.indices.IndexSourceException;
import com.activest.adm.integration.ptax.PtaxClient;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.activest.adm.domain.notice.AdminNotices;
import com.activest.domain.catalog.CatalogMessaging.CatalogChangePublisher;
import com.activest.domain.currency.CurrencyRepository;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * M2 do docs/planos/PLANO_CADASTRO_MOEDAS.md: carga do câmbio com a PTAX mockada no cliente; SQL e banco de verdade.
 * O serviço é montado à mão com o cliente mockado (sem {@code @MockBean}): um contexto Spring a mais estoura o limite
 * de conexões do Postgres de teste na bateria completa.
 */
class ExchangeRateImportIT extends IntegrationTestBase {

    PtaxClient ptax;
    ExchangeRateImportService service;

    @Autowired CurrencyRepository currencies;
    @Autowired PlatformTransactionManager txm;
    @Autowired CatalogChangePublisher publisher;
    @Autowired AdminNotices notices;

    private static PtaxClient.Bulletin b(LocalDate d, String buy, String sell) {
        return new PtaxClient.Bulletin(d, new BigDecimal(buy), new BigDecimal(sell), d.atTime(13, 4));
    }

    @BeforeEach
    void setUp() {
        ptax = Mockito.mock(PtaxClient.class);
        service = new ExchangeRateImportService(currencies, ptax, jdbc, txm, publisher,
            java.time.Clock.system(java.time.ZoneId.of("America/Sao_Paulo")));
        service.notices = notices;
        jdbc.update("DELETE FROM exchange_rates");
        jdbc.update("DELETE FROM notifications");
        when(ptax.year(anyString(), anyInt())).thenReturn(List.of());
        when(ptax.year("USD", 2024)).thenReturn(List.of(
            b(LocalDate.of(2024, 1, 2), "4.8910", "4.8916"),
            b(LocalDate.of(2024, 1, 3), "4.9213", "4.9219"),
            b(LocalDate.of(2024, 1, 4), "4.9150", "4.9156")));
    }

    private int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM exchange_rates WHERE currency_code = 'USD'", Integer.class);
    }

    private BigDecimal sell(LocalDate d) {
        return jdbc.queryForObject("SELECT sell_rate FROM exchange_rates WHERE currency_code = 'USD' AND reference_date = ?",
            BigDecimal.class, Date.valueOf(d));
    }

    @Test
    @DisplayName("Sem série: carga desde 2010; a segunda rodada não muda nada; correção da fonte sobrescreve")
    void idempotent() {
        ExchangeRateImportService.Result r = service.importRecent("teste");
        assertThat(r.created()).isEqualTo(3);
        assertThat(r.failures()).isEmpty();
        assertThat(r.currencies().get(0).from()).isEqualTo(ExchangeRateImportService.HISTORY_FROM);
        assertThat(count()).isEqualTo(3);
        assertThat(sell(LocalDate.of(2024, 1, 3))).isEqualByComparingTo("4.9219");

        // janela de revisao: do ultimo boletim menos 10 dias
        ExchangeRateImportService.Result again = service.importRecent("teste");
        assertThat(again.created()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(again.currencies().get(0).from()).isEqualTo(LocalDate.of(2023, 12, 25));

        when(ptax.year("USD", 2024)).thenReturn(List.of(
            b(LocalDate.of(2024, 1, 2), "4.8910", "4.8916"),
            b(LocalDate.of(2024, 1, 3), "4.9213", "4.9300"),
            b(LocalDate.of(2024, 1, 4), "4.9150", "4.9156")));
        ExchangeRateImportService.Result fixed = service.importFrom(List.of("USD"), LocalDate.of(2024, 1, 1), "teste");
        assertThat(fixed.updated()).isEqualTo(1);
        assertThat(fixed.changed()).containsExactly("USD");
        assertThat(sell(LocalDate.of(2024, 1, 3))).isEqualByComparingTo("4.93");
    }

    @Test
    @DisplayName("C1a: fonte respondeu com boletim antigo (mais de 2 dias úteis) → aviso de fonte desatualizada")
    void staleSource() {
        ExchangeRateImportService.Result r = service.importRecent("teste");
        assertThat(r.currencies().get(0).staleSince()).isEqualTo(LocalDate.of(2024, 1, 4));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'FONTE_DESATUALIZADA'",
            Integer.class)).isEqualTo(1);
        assertThat(service.behind()).isTrue();
    }

    @Test
    @DisplayName("Fonte fora do ar num ano: registra a falha e segue; moeda desconhecida é recusada")
    void failures() {
        when(ptax.year("USD", 2025)).thenThrow(new IndexSourceException("HTTP 503"));
        ExchangeRateImportService.Result r = service.importRecent("teste");
        assertThat(r.failures()).containsExactly("USD");
        assertThat(count()).isEqualTo(3);
        assertThatThrownBy(() -> service.validateCodes(List.of("XYZ"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateCodes(List.of("BRL"))).isInstanceOf(IllegalArgumentException.class);
    }
}
