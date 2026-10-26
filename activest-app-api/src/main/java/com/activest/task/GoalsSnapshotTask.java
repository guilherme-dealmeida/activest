package com.activest.task;

import com.activest.domain.goal.GoalTracker;
import com.activest.domain.task.TaskHandler;
import com.activest.domain.task.TaskRunContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Rodada diaria das metas (docs/planos/PLANO_METAS.md, D24): depois do {@code POSITIONS_REFRESH}, fotografa as metas
 * ativas de cada usuario, grava os eventos e os avisos. Stop entre usuarios e entre metas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalsSnapshotTask implements TaskHandler {

    private final GoalTracker tracker;

    @Override
    public String type() {
        return "GOALS_SNAPSHOT";
    }

    @Override
    public void run(TaskRunContext ctx) {
        List<UUID> users = tracker.usersWithActiveGoals();
        int goals = 0;
        int failed = 0;
        for (UUID u : users) {
            ctx.checkStop();
            try {
                goals += tracker.trackUser(u);
            } catch (com.activest.domain.task.TaskStoppedException e) {
                throw e;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Metas do usuario {}: {}", u, e.getMessage());
            }
        }
        ctx.summary(goals + " meta(s) de " + users.size() + " usuário(s) fotografada(s)"
                + (failed > 0 ? "; " + failed + " usuário(s) com falha" : ""));
        if (failed > 0 && failed == users.size()) {
            throw new IllegalStateException("Nenhum usuário processado");
        }
    }

    @Override
    public boolean stoppable() {
        return true;
    }
}
