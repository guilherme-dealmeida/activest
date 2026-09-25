package com.primevest.domain.dividend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recebido x a receber (Dividend.isReceived): pagamento ate hoje, contando o
 * proprio dia; sem data, conforme a origem.
 */
class DividendReceivedTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 24);

    private static Dividend pagoEm(String data) {
        return Dividend.builder().paymentDate(data == null ? null : LocalDate.parse(data))
            .cashEvent(AssetCashEvent.builder().source(CashEventSource.B3).build()).build();
    }

    private static Dividend semDataDe(CashEventSource fonte) {
        return Dividend.builder().cashEvent(AssetCashEvent.builder().source(fonte).build()).build();
    }

    @Test
    @DisplayName("Pago ontem: recebido")
    void ontem() {
        assertThat(pagoEm("2026-09-23").isReceived(HOJE)).isTrue();
    }

    @Test
    @DisplayName("Pago hoje (D0): recebido")
    void hoje() {
        assertThat(pagoEm("2026-09-24").isReceived(HOJE)).isTrue();
    }

    @Test
    @DisplayName("Pago amanha: a receber")
    void amanha() {
        assertThat(pagoEm("2026-09-25").isReceived(HOJE)).isFalse();
    }

    @Test
    @DisplayName("Sem data, vindo do historico longo da B3: recebido (a fonte so lista o passado)")
    void semDataHistorico() {
        assertThat(semDataDe(CashEventSource.B3_HISTORICO).isReceived(HOJE)).isTrue();
    }

    @Test
    @DisplayName("Sem data, vindo da fonte recente (pagamento a definir): a receber")
    void semDataFonteRecente() {
        assertThat(semDataDe(CashEventSource.B3).isReceived(HOJE)).isFalse();
        assertThat(semDataDe(CashEventSource.FNET).isReceived(HOJE)).isFalse();
    }

    @Test
    @DisplayName("Sem data e sem evento (MANUAL): recebido")
    void manualSemData() {
        assertThat(Dividend.builder().build().isReceived(HOJE)).isTrue();
    }
}
