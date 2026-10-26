package com.activest.domain.goal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Esvazia a {@link GoalReevaluationQueue} a cada 15 s (desligado sem as rotinas, como o worker das integracoes). */
@Component
@ConditionalOnExpression("${activest.scheduling.enabled:true}")
public class GoalReevaluationWorker {

    private final GoalReevaluationQueue queue;

    public GoalReevaluationWorker(GoalReevaluationQueue queue) {
        this.queue = queue;
    }

    @Scheduled(fixedDelayString = "${activest.goals.reevaluation-delay-ms:15000}", initialDelay = 20_000)
    public void run() {
        queue.drain();
    }
}
