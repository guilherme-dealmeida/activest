package com.activest.domain.goal;

import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.notification.CatalogReaction;
import com.activest.domain.transaction.PositionWrittenEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reavaliacao das metas depois que os dados mudam (docs/planos/PLANO_METAS.md, Anexo A 8): transacao gravada
 * ({@link PositionWrittenEvent}, depois do commit) e "catalogo mudou" (preco, proventos, indices). Os usuarios ficam
 * marcados e o {@link GoalReevaluationWorker} os processa juntos — varias transacoes seguidas viram uma reavaliacao.
 * Nos testes o worker fica desligado e o teste chama {@link #drain()}.
 */
@Slf4j
@Component
public class GoalReevaluationQueue implements CatalogReaction {

    private final Set<UUID> dirty = new LinkedHashSet<>();
    private final GoalTracker tracker;

    public GoalReevaluationQueue(GoalTracker tracker) {
        this.tracker = tracker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWrite(PositionWrittenEvent e) {
        mark(e.userId());
    }

    @Override
    public void after(CatalogChange change) {
        try {
            tracker.usersWithActiveGoals().forEach(this::mark);
        } catch (RuntimeException ex) {
            log.warn("Metas: nao foi possivel marcar a reavaliacao depois do catalogo: {}", ex.getMessage());
        }
    }

    public synchronized void mark(UUID userId) {
        if (userId != null) {
            dirty.add(userId);
        }
    }

    /** Processa os usuarios marcados; devolve quantas metas foram fotografadas. */
    public int drain() {
        List<UUID> batch;
        synchronized (this) {
            batch = List.copyOf(dirty);
            dirty.clear();
        }
        int n = 0;
        for (UUID u : batch) {
            try {
                n += tracker.trackUser(u);
            } catch (RuntimeException ex) {
                log.warn("Metas: reavaliacao do usuario {} falhou: {}", u, ex.getMessage());
            }
        }
        return n;
    }
}
