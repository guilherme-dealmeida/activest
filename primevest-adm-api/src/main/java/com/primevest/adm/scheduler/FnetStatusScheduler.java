package com.primevest.adm.scheduler;

import com.primevest.adm.domain.dividend.ProventosCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Situacao dos avisos do FNET, toda semana (docs/PENDENCIAS.md 33.3; docs/planos/PLANO_CORRECAO_PROVENTOS.md, R10/R11):
 * domingo, 03:00 — sem pregao e sem outra rotina no horario.
 *
 * <p>A situacao de um aviso (ativo, inativo, cancelado) so era relida quando a rotina diaria buscava aviso novo do
 * fundo. A retificacao que so muda o pagamento (R10) e o aviso inativado depois de publicado sob o CNPJ de outro fundo
 * (R11) ficavam esperando. Aqui: (1) a situacao de todos os fundos com aviso em disco ({@code carga/data/fnet/status},
 * ~1 h — uma busca por vez, com pausa); (2) a rodada de proventos relendo todos os avisos em disco
 * ({@code fnet=todos}), que aplica o que mudou. Sob demanda: {@code /api/v1/admin/proventos/fnet-status} e
 * {@code /api/v1/admin/proventos?fnet=todos}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "primevest.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class FnetStatusScheduler {

    private final ProventosCatalogService proventosCatalogService;

    @Value("${primevest.proventos.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${primevest.fnet-status.cron:0 0 3 * * SUN}", zone = "America/Sao_Paulo")
    public void weekly() {
        if (!enabled) {
            log.info("Proventos: situacao semanal do FNET desligada (primevest.proventos.enabled=false)");
            return;
        }
        int failures = proventosCatalogService.refreshFnetStatus("semanal", false);
        if (failures > 0) {
            // as falhas ficam com o arquivo anterior; a rodada abaixo usa o que houver
            log.warn("Proventos: situacao semanal do FNET com {} fundos sem resposta", failures);
        }
        proventosCatalogService.syncAll("semanal", true);
    }
}
