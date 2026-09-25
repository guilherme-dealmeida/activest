package com.primevest.domain.dividend;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A data de pagamento do extrato ou deduzida cede a fonte oficial (V24): quando
 * a B3 ou o FNET trazem a data, ela substitui e a origem volta a nula.
 */
class PaymentDateSourceIT extends IntegrationTestBase {

    @Autowired private ProventosSyncService proventosSyncService;
    @Autowired private AssetRepository assetRepository;

    private Map<String, Object> evento() {
        return jdbc.queryForMap("""
            SELECT e.source, e.payment_date, e.payment_date_source FROM asset_cash_events e
            JOIN assets a ON a.id = e.asset_id WHERE a.ticker = 'CMIG4'""");
    }

    private ProventosSyncService.CashEventData dado(CashEventSource fonte, String pagamento) {
        return new ProventosSyncService.CashEventData(DividendType.JCP, "JRS CAP PROPRIO", LocalDate.of(2025, 3, 25),
            pagamento == null ? null : LocalDate.parse(pagamento), new BigDecimal("0.09455892373"), null, null, null,
            null, fonte, null, (short) 2);
    }

    @Test
    @DisplayName("Data deduzida e substituida pela data oficial da B3, e a origem volta a nula")
    void oficialSubstituiDeduzida() {
        Asset cmig = assetRepository.findByTicker("CMIG4").orElseThrow();
        proventosSyncService.saveCashEvent(cmig, dado(CashEventSource.B3_HISTORICO, null));
        jdbc.update("UPDATE asset_cash_events SET payment_date = DATE '2026-12-30', payment_date_source = 'DEDUZIDA'");

        proventosSyncService.saveCashEvent(cmig, dado(CashEventSource.B3, "2026-12-28"));

        assertThat(evento()).containsEntry("payment_date", java.sql.Date.valueOf("2026-12-28"))
            .containsEntry("payment_date_source", null);
    }

    @Test
    @DisplayName("Evento novo do extrato nasce com a origem da data EXTRATO_B3; o extrato nao substitui data oficial")
    void extratoNaoSubstituiOficial() {
        Asset cmig = assetRepository.findByTicker("CMIG4").orElseThrow();
        proventosSyncService.saveCashEvent(cmig, dado(CashEventSource.EXTRATO_B3, "2026-12-30"));
        assertThat(evento()).containsEntry("source", "EXTRATO_B3").containsEntry("payment_date_source", "EXTRATO_B3");

        jdbc.update("UPDATE asset_cash_events SET payment_date = DATE '2026-12-28', payment_date_source = NULL");
        proventosSyncService.saveCashEvent(cmig, dado(CashEventSource.EXTRATO_B3, "2026-12-30"));

        assertThat(evento()).containsEntry("payment_date", java.sql.Date.valueOf("2026-12-28"))
            .containsEntry("payment_date_source", null);
    }
}
