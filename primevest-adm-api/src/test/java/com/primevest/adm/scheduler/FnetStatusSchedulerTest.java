package com.primevest.adm.scheduler;

import com.primevest.adm.domain.dividend.ProventosCatalogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** [A12] do docs/SCHEDULE_TASKS.md — situacao semanal dos avisos do FNET (PENDENCIAS 33.3). */
class FnetStatusSchedulerTest {

    @Test
    @DisplayName("Domingo 03:00: rele a situacao de todos os fundos e depois roda os proventos relendo todos os avisos")
    void situacaoDepoisProventos() {
        ProventosCatalogService service = mock(ProventosCatalogService.class);
        when(service.refreshFnetStatus(anyString(), anyBoolean())).thenReturn(2);
        FnetStatusScheduler scheduler = new FnetStatusScheduler(service);
        ReflectionTestUtils.setField(scheduler, "enabled", true);

        scheduler.weekly();

        InOrder order = inOrder(service);
        order.verify(service).refreshFnetStatus("semanal", false);
        order.verify(service).syncAll("semanal", true);
    }

    @Test
    @DisplayName("Com primevest.proventos.enabled=false nao vai ao FNET")
    void desligada() {
        ProventosCatalogService service = mock(ProventosCatalogService.class);
        FnetStatusScheduler scheduler = new FnetStatusScheduler(service);
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.weekly();

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("O padrao do cron e domingo, 03:00, no fuso de Sao Paulo")
    void cron() throws Exception {
        Scheduled s = FnetStatusScheduler.class.getMethod("weekly").getAnnotation(Scheduled.class);
        assertThat(s.cron()).isEqualTo("${primevest.fnet-status.cron:0 0 3 * * SUN}");
        assertThat(s.zone()).isEqualTo("America/Sao_Paulo");
    }
}
