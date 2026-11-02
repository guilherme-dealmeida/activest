package com.activest.adm.task;

import com.activest.adm.domain.pricehistory.ForeignDailyQuotesService;
import com.activest.adm.integration.foreign.ForeignQuotesProvider;
import com.activest.domain.task.TaskInputException;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskTriggerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tarefa FOREIGN_DAILY_QUOTES (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md, D7 e I6): período e resumo. */
class ForeignDailyQuotesTaskTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);
    private final ForeignDailyQuotesService service = mock(ForeignDailyQuotesService.class);
    private final ForeignDailyQuotesTask task = new ForeignDailyQuotesTask(service);

    private static TaskRunContext ctx(Map<String, String> values) {
        return new TaskRunContext(1L, "FOREIGN_DAILY_QUOTES", TaskTriggerType.MANUAL, values, () -> false);
    }

    private static ForeignDailyQuotesService.Result result() {
        return new ForeignDailyQuotesService.Result(DAY, DAY, List.of(), 12_008, 12_008, 12_180, DAY, 12_008, 170, 1,
                1, "98.6%", null, 0);
    }

    @Test
    @DisplayName("Só FROM_DATE: o próprio pregão; resumo com a cobertura")
    void singleDay() {
        when(service.importDays(eq(DAY), eq(DAY), anyString())).thenReturn(result());
        TaskRunContext ctx = ctx(Map.of("FROM_DATE", "2026-10-30"));

        task.run(ctx);

        assertThat(ctx.summary()).contains("matched=12008").contains("coverage=98.6%");
    }

    @Test
    @DisplayName("Sem datas: rodada incremental")
    void recent() {
        when(service.importRecent(anyString())).thenReturn(result());

        task.run(ctx(Map.of()));

        verify(service).importRecent(anyString());
    }

    @Test
    @DisplayName("Período acima de 31 dias, invertido, TO_DATE sozinho ou data fora do plano: erro de entrada")
    void invalid() {
        assertThatThrownBy(() -> task.run(ctx(Map.of("FROM_DATE", "2026-01-01", "TO_DATE", "2026-03-01"))))
                .isInstanceOf(TaskInputException.class);
        assertThatThrownBy(() -> task.run(ctx(Map.of("FROM_DATE", "2026-10-30", "TO_DATE", "2026-10-01"))))
                .isInstanceOf(TaskInputException.class);
        assertThatThrownBy(() -> task.run(ctx(Map.of("TO_DATE", "2026-10-30"))))
                .isInstanceOf(TaskInputException.class);
        when(service.importDays(eq(LocalDate.of(2024, 10, 30)), eq(LocalDate.of(2024, 10, 30)), anyString()))
                .thenThrow(new ForeignQuotesProvider.OutOfRangeException("fora do plano"));
        assertThatThrownBy(() -> task.run(ctx(Map.of("FROM_DATE", "2024-10-30"))))
                .isInstanceOf(TaskInputException.class).hasMessageContaining("fora do plano");
    }

    @Test
    @DisplayName("Outra rodada em andamento: pulada")
    void skipped() {
        when(service.importDays(eq(DAY), eq(DAY), anyString())).thenReturn(null);

        assertThatThrownBy(() -> task.run(ctx(Map.of("FROM_DATE", "2026-10-30"))))
                .isInstanceOf(TaskSkippedException.class);
    }
}
