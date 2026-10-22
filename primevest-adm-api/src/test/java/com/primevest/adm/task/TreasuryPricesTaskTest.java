package com.primevest.adm.task;

import com.primevest.adm.domain.pricehistory.TreasuryPriceService;
import com.primevest.adm.integration.PublicFileCache;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskTriggerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** docs/planos/PLANO_FONTE_TESOURO.md, D2: fonte que recusou termina a rodada em FAILED, com o resumo gravado. */
class TreasuryPricesTaskTest {

    private final TreasuryPriceService service = mock(TreasuryPriceService.class);
    private final TreasuryPricesTask task = new TreasuryPricesTask(service);

    private TaskRunContext ctx() {
        return new TaskRunContext(1L, "TREASURY_PRICES", TaskTriggerType.MANUAL, Map.of(), () -> false);
    }

    @Test
    @DisplayName("Fonte recusou (copia em disco lida): resumo gravado e a rodada falha com o motivo")
    void fonteRecusou() {
        when(service.importRecent(anyString())).thenReturn(new TreasuryPriceService.Result(58, 0,
                LocalDate.of(2026, 10, 15), 0, -1, List.of(),
                "Tesouro Transparente indisponivel (HTTP 429, Retry-After 30); lida a copia do arquivo em disco", null));
        TaskRunContext ctx = ctx();

        assertThatThrownBy(() -> task.run(ctx))
                .isInstanceOf(PublicFileCache.UnavailableException.class)
                .hasMessageContaining("HTTP 429");
        assertThat(ctx.summary()).contains("titles=58").contains("sourceProblem=");
    }

    @Test
    @DisplayName("Fonte respondeu, mesmo parada: a rodada termina bem (a pendencia e o aviso)")
    void fonteParada() {
        when(service.importRecent(anyString())).thenReturn(new TreasuryPriceService.Result(58, 0,
                LocalDate.of(2026, 10, 15), 0, 28, List.of(), null, LocalDate.of(2026, 10, 15)));
        TaskRunContext ctx = ctx();

        task.run(ctx);

        assertThat(ctx.summary()).contains("staleSince=2026-10-15");
    }
}
