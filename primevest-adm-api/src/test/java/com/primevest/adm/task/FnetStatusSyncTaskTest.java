package com.primevest.adm.task;

import com.primevest.adm.domain.dividend.DividendCatalogService;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskTriggerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** [A12] do docs/SCHEDULE_TASKS.md como tarefa FNET_STATUS_SYNC (docs/planos/PLANO_TAREFAS.md, T2). */
class FnetStatusSyncTaskTest {

    private static TaskRunContext ctx(Map<String, String> values) {
        return new TaskRunContext(1L, "FNET_STATUS_SYNC", TaskTriggerType.SCHEDULE, values, () -> false);
    }

    @Test
    @DisplayName("Padrao do seed: rele a situacao de todos os fundos e depois roda os proventos relendo todos os avisos")
    void statusAfterDividends() {
        DividendCatalogService service = mock(DividendCatalogService.class);
        when(service.refreshFnetStatus(anyString(), anyBoolean())).thenReturn(2);
        when(service.syncAll(anyString(), anyBoolean())).thenReturn(new DividendCatalogService.SyncResult(3, 0, 1, 0));
        TaskRunContext c = ctx(Map.of("ONLY_MISSING", "false", "WITH_DIVIDENDS_REREAD", "true"));

        new FnetStatusSyncTask(service).run(c);

        InOrder order = inOrder(service);
        order.verify(service).refreshFnetStatus("FNET_STATUS_SYNC, agendada", false);
        order.verify(service).syncAll("FNET_STATUS_SYNC, agendada", true);
        assertThat(c.summary()).contains("2 fundo(s) sem resposta").contains("issuers=3");
    }

    @Test
    @DisplayName("ONLY_MISSING e sem a releitura dos proventos: o que o endpoint /proventos/fnet-status fazia")
    void onlyStatus() {
        DividendCatalogService service = mock(DividendCatalogService.class);

        new FnetStatusSyncTask(service).run(ctx(Map.of("ONLY_MISSING", "true", "WITH_DIVIDENDS_REREAD", "false")));

        verify(service).refreshFnetStatus("FNET_STATUS_SYNC, agendada", true);
        verify(service, never()).syncAll(anyString(), anyBoolean());
    }
}
