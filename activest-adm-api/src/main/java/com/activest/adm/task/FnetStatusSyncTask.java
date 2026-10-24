package com.activest.adm.task;

import com.activest.adm.domain.dividend.DividendCatalogService;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import com.activest.domain.task.TaskSkippedException;
import com.activest.domain.task.TaskSummaries;
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

    private final DividendCatalogService dividends;

    @Override
    public String type() {
        return "FNET_STATUS_SYNC";
    }

    @Override
    public void run(TaskRunContext ctx) {
        int failures = dividends.refreshFnetStatus(ctx.reason(), ctx.bool("ONLY_MISSING", false));
        if (failures > 0) {
            // as falhas ficam com o arquivo anterior; a rodada abaixo usa o que houver
            log.warn("Proventos: situacao do FNET com {} fundos sem resposta", failures);
        }
        String summary = "situacao do FNET: " + failures + " fundo(s) sem resposta";
        if (ctx.bool("WITH_DIVIDENDS_REREAD", true)) {
            var result = dividends.syncAll(ctx.reason(), true);
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
