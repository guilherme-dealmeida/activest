package com.primevest.adm.task;

import com.primevest.adm.domain.dividend.ProventosCatalogService;
import com.primevest.domain.task.TaskHandler;
import com.primevest.domain.task.TaskRunContext;
import com.primevest.domain.task.TaskSkippedException;
import com.primevest.domain.task.TaskSummaries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Situação dos avisos do FNET [A12] e, com {@code WITH_DIVIDENDS_REREAD}, os proventos relendo todos os avisos
 * (docs/planos/PLANO_TAREFAS.md, T2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FnetStatusSyncTask implements TaskHandler {

    private final ProventosCatalogService proventos;

    @Override
    public String type() {
        return "FNET_STATUS_SYNC";
    }

    @Override
    public void run(TaskRunContext ctx) {
        int failures = proventos.refreshFnetStatus(ctx.reason(), ctx.bool("ONLY_MISSING", false));
        if (failures > 0) {
            // as falhas ficam com o arquivo anterior; a rodada abaixo usa o que houver
            log.warn("Proventos: situacao do FNET com {} fundos sem resposta", failures);
        }
        String summary = "situacao do FNET: " + failures + " fundo(s) sem resposta";
        if (ctx.bool("WITH_DIVIDENDS_REREAD", true)) {
            var result = proventos.syncAll(ctx.reason(), true);
            if (result == null) {
                throw new TaskSkippedException("Situação do FNET atualizada (" + failures + " falhas), mas já havia "
                    + "uma rodada completa de proventos em andamento: a releitura dos avisos ficou para a próxima.");
            }
            summary += "; proventos: " + TaskSummaries.of(result);
        }
        ctx.summary(summary);
    }

    /** Confere o Stop entre unidades de trabalho (docs/planos/PLANO_TAREFAS.md, D8 e 6.4). */
    @Override
    public boolean stoppable() {
        return true;
    }
}
