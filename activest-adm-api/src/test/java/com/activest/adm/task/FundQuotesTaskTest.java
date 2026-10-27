package com.activest.adm.task;

import com.activest.adm.domain.fund.FundCatalogService;
import com.activest.adm.integration.PublicFileCache;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskTriggerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tarefa FUND_QUOTES (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, 11.4, D8, D9). */
class FundQuotesTaskTest {

    private final FundCatalogService service = mock(FundCatalogService.class);
    private final FundQuotesTask task = new FundQuotesTask(service);

    private static TaskRunContext ctx(Map<String, String> values) {
        return new TaskRunContext(1L, "FUND_QUOTES", TaskTriggerType.MANUAL, values, () -> false);
    }

    private static FundCatalogService.Result result(String problem) {
        return new FundCatalogService.Result(10, 0, 8, 8, 0, LocalDate.of(2026, 10, 23), problem, null);
    }

    @Test
    @DisplayName("Uma rodada automatica por dia (D8), interrompivel")
    void oncePerDay() {
        assertThat(task.oncePerDay()).isTrue();
        assertThat(task.stoppable()).isTrue();
        assertThat(task.type()).isEqualTo("FUND_QUOTES");
    }

    @Test
    @DisplayName("Sem atributo: mes corrente e os 12 anteriores; com FROM_MONTH, a partir dele")
    void window() {
        when(service.runDaily(any(), anyString())).thenReturn(result(null));
        TaskRunContext plain = ctx(Map.of());
        task.run(plain);
        verify(service).runDaily(isNull(), anyString());
        assertThat(plain.summary()).contains("10 fundos").contains("2026-10-23");

        task.run(ctx(Map.of("FROM_MONTH", "2015-03-10")));
        verify(service).runDaily(eq(YearMonth.of(2015, 3)), anyString());
    }

    @Test
    @DisplayName("FROM_MONTH antes de 2010 ou no futuro: rodada recusada")
    void invalidMonth() {
        assertThatThrownBy(() -> task.run(ctx(Map.of("FROM_MONTH", "2009-12-01"))))
            .isInstanceOf(TaskInputException.class);
        assertThatThrownBy(() -> task.run(ctx(Map.of("FROM_MONTH", LocalDate.now().plusMonths(2).toString()))))
            .isInstanceOf(TaskInputException.class);
    }

    @Test
    @DisplayName("Outra rodada dos fundos em andamento: a rodada e pulada")
    void skipped() {
        when(service.runDaily(any(), anyString())).thenReturn(null);
        assertThatThrownBy(() -> task.run(ctx(Map.of()))).isInstanceOf(TaskSkippedException.class);
    }

    @Test
    @DisplayName("CVM recusou e so a copia em disco foi lida: resumo gravado e a rodada falha (como no Tesouro)")
    void sourceRejected() {
        when(service.runDaily(any(), anyString())).thenReturn(result("UNREACHABLE timeout (usada a cópia em disco)"));
        TaskRunContext c = ctx(Map.of());
        assertThatThrownBy(() -> task.run(c)).isInstanceOf(PublicFileCache.UnavailableException.class)
            .hasMessageContaining("timeout");
        assertThat(c.summary()).contains("fonte: UNREACHABLE");
    }
}
