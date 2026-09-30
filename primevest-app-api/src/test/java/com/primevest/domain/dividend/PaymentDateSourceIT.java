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
 * Precedencia no {@link CashEventWriter} (PLANO_CARGA_INICIAL.md): a data de pagamento que veio
 * de outra fonte (relatorio de proventos da CVM) cede a data da propria fonte do evento; e o FNET
 * substitui a B3 como fonte do provento de FII (D15).
 */
class PaymentDateSourceIT extends com.primevest.functional.FuncionalBase {

    @Autowired private CashEventWriter cashEventWriter;
    @Autowired private AssetRepository assetRepository;

    private Map<String, Object> evento(String ticker) {
        return jdbc.queryForMap("""
            SELECT e.source, e.source_ref, e.payment_date, e.payment_date_source FROM asset_cash_events e
            JOIN assets a ON a.id = e.asset_id WHERE a.ticker = ?""", ticker);
    }

    private CashEventWriter.CashEventData dado(CashEventSource fonte, String pagamento) {
        return new CashEventWriter.CashEventData(DividendType.JCP, "JRS CAP PROPRIO", LocalDate.of(2025, 3, 25),
            pagamento == null ? null : LocalDate.parse(pagamento), new BigDecimal("0.09455892373"), null, null, null,
            null, fonte, null, (short) 2);
    }

    @Test
    @DisplayName("Data da CVM e substituida pela data da B3, e a origem volta a nula")
    void oficialSubstituiCvm() {
        Asset cmig = assetRepository.findByTicker("CMIG4").orElseThrow();
        cashEventWriter.save(cmig, dado(CashEventSource.B3_HISTORICO, null));
        jdbc.update("UPDATE asset_cash_events SET payment_date = DATE '2026-12-30', payment_date_source = 'CVM'");

        cashEventWriter.save(cmig, dado(CashEventSource.B3, "2026-12-28"));

        assertThat(evento("CMIG4")).containsEntry("payment_date", java.sql.Date.valueOf("2026-12-28"))
            .containsEntry("payment_date_source", null);
    }

    @Test
    @DisplayName("FII: o aviso do FNET assume o provento gravado pela B3 como reserva (fonte, aviso e data)")
    void fnetAssumeReservaDaB3() {
        Asset hglg = assetRepository.findByTicker("HGLG11").orElseThrow();
        LocalDate dataCom = LocalDate.of(2025, 8, 29);
        cashEventWriter.save(hglg, new CashEventWriter.CashEventData(DividendType.RENDIMENTO, "RENDIMENTO", dataCom,
            LocalDate.of(2025, 9, 12), new BigDecimal("1.10"), null, null, null, null, CashEventSource.B3, null, (short) 1));

        boolean novo = cashEventWriter.save(hglg, new CashEventWriter.CashEventData(DividendType.RENDIMENTO, "RENDIMENTO",
            dataCom, LocalDate.of(2025, 9, 12), new BigDecimal("1.10"), null, "Agosto", null, true,
            CashEventSource.FNET, "980881", (short) 1));

        assertThat(novo).isFalse();
        assertThat(evento("HGLG11")).containsEntry("source", "FNET").containsEntry("source_ref", "980881");
    }
}
