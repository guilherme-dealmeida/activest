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

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    private static Dividend paidOn(String data) {
        return Dividend.builder().paymentDate(data == null ? null : LocalDate.parse(data))
            .cashEvent(AssetCashEvent.builder().source(CashEventSource.B3).build()).build();
    }

    private static Dividend withoutDateOf(CashEventSource sourceName) {
        return Dividend.builder().cashEvent(AssetCashEvent.builder().source(sourceName).build()).build();
    }

    @Test
    @DisplayName("Pago ontem: recebido")
    void yesterday() {
        assertThat(paidOn("2026-09-23").isReceived(TODAY)).isTrue();
    }

    @Test
    @DisplayName("Pago hoje (D0): recebido")
    void today() {
        assertThat(paidOn("2026-09-24").isReceived(TODAY)).isTrue();
    }

    @Test
    @DisplayName("Pago amanha: a receber")
    void tomorrow() {
        assertThat(paidOn("2026-09-25").isReceived(TODAY)).isFalse();
    }

    @Test
    @DisplayName("Sem data, vindo do historico longo da B3: recebido (a fonte so lista o passado)")
    void withoutDateHistory() {
        assertThat(withoutDateOf(CashEventSource.B3_HISTORICO).isReceived(TODAY)).isTrue();
    }

    @Test
    @DisplayName("Sem data, vindo da fonte recente (pagamento a definir): a receber")
    void withoutDateRecentSource() {
        assertThat(withoutDateOf(CashEventSource.B3).isReceived(TODAY)).isFalse();
        assertThat(withoutDateOf(CashEventSource.FNET).isReceived(TODAY)).isFalse();
    }

    @Test
    @DisplayName("Sem data e sem evento (MANUAL): recebido")
    void manualWithoutDate() {
        assertThat(Dividend.builder().build().isReceived(TODAY)).isTrue();
    }
}
