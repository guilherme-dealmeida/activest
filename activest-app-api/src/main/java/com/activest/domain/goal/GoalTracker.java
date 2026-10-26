package com.activest.domain.goal;

import com.activest.domain.dividend.DividendCalendar;
import com.activest.domain.goal.engine.*;
import com.activest.domain.notification.NotificationDraft;
import com.activest.domain.notification.NotificationKind;
import com.activest.domain.notification.NotificationService;
import com.activest.domain.task.TaskRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Acompanhamento das metas (docs/planos/PLANO_METAS.md, Anexo A 8): fotografia do dia (upsert em
 * {@code goal_snapshots}), eventos de resultado, conclusao automatica da meta atingida (D13) e os avisos da D20 pela
 * caixa de Notificacoes. Chamado na rodada diaria ({@code GOALS_SNAPSHOT}), ao criar/editar/retomar e depois de
 * transacao gravada ou "catalogo mudou" ({@link GoalReevaluationQueue}).
 */
@Slf4j
@Component
public class GoalTracker {

    private final GoalRepository goals;
    private final GoalSnapshotRepository snapshots;
    private final GoalEventRepository events;
    private final GoalFactsLoader factsLoader;
    private final NotificationService notifications;
    private final GoalCodec codec;
    private final DividendCalendar calendar;

    public GoalTracker(GoalRepository goals, GoalSnapshotRepository snapshots, GoalEventRepository events,
                       GoalFactsLoader factsLoader, NotificationService notifications, GoalCodec codec,
                       DividendCalendar calendar) {
        this.goals = goals;
        this.snapshots = snapshots;
        this.events = events;
        this.factsLoader = factsLoader;
        this.notifications = notifications;
        this.codec = codec;
        this.calendar = calendar;
    }

    public LocalDate today() {
        return calendar.today();
    }

    /** Uma meta, logo depois de criada, editada ou retomada. */
    @Transactional
    public void track(UUID userId, UUID goalId) {
        goals.findOwned(goalId, userId).ifPresent(g -> process(g, factsLoader.load(userId)));
    }

    /** Todas as metas ativas de um usuario (uma leitura dos fatos). Devolve quantas foram fotografadas. */
    @Transactional
    public int trackUser(UUID userId) {
        List<Goal> active = goals.findByUserIdAndLifecycleIn(userId, List.of(GoalLifecycle.ATIVA));
        if (active.isEmpty()) {
            return 0;
        }
        GoalFacts facts = factsLoader.load(userId);
        int n = 0;
        for (Goal g : active) {
            TaskRun.checkStop();
            process(g, facts);
            n++;
        }
        return n;
    }

    public List<UUID> usersWithActiveGoals() {
        return goals.usersWith(List.of(GoalLifecycle.ATIVA));
    }

    void process(Goal g, GoalFacts facts) {
        if (g.getLifecycle() != GoalLifecycle.ATIVA) {
            return;
        }
        LocalDate today = facts.today();
        GoalDefinition d = codec.definition(g, today);
        Evaluation e = GoalEvaluator.evaluate(d, facts);
        GoalSituation previous = snapshots.findFirstByGoalIdAndReferenceDateLessThanOrderByReferenceDateDesc(g.getId(), today)
                .map(s -> parse(s.getSituation())).orElse(null);
        snapshots.save(GoalSnapshot.builder()
                .goalId(g.getId()).referenceDate(today).revision(g.getRevision())
                .indicator(e.indicator()).target(e.target()).progressPct(e.progressPct())
                .situation(e.situation().name()).quality(e.quality().name())
                .result(codec.write(snapshotResult(e))).build());

        GoalSituation now = e.situation();
        if (e.achieved() && g.getAchievedOn() == null && g.getType().nature() != com.activest.domain.goal.GoalNature.CONTROLE) {
            g.setAchievedOn(today);
            event(g, GoalEventKind.ATINGIDA, Map.of("indicator", String.valueOf(e.indicator())));
        }
        if (previous == GoalSituation.DENTRO_DO_OBJETIVO && now == GoalSituation.FORA_DO_OBJETIVO) {
            event(g, GoalEventKind.SAIU_DO_OBJETIVO, Map.of("indicator", String.valueOf(e.indicator())));
        } else if (previous == GoalSituation.FORA_DO_OBJETIVO && now == GoalSituation.DENTRO_DO_OBJETIVO) {
            event(g, GoalEventKind.VOLTOU_AO_OBJETIVO, Map.of("indicator", String.valueOf(e.indicator())));
        }
        String key = g.getId().toString();
        if (now == GoalSituation.PRAZO_VENCIDO && previous != GoalSituation.PRAZO_VENCIDO) {
            event(g, GoalEventKind.PRAZO_VENCIDO, Map.of("indicator", String.valueOf(e.indicator())));
            if (g.getFinalResult() == null) {
                g.setFinalResult(codec.write(finalResult(e, today, "PRAZO")));
            }
            notice(g, NotificationKind.META_PRAZO_VENCIDO, key + ":" + g.getTargetDate(),
                    "Prazo da meta vencido: " + g.getName(), e.objective() + ". " + summary(e));
        }

        // D13: crescente e sequencia que atingem o alvo se concluem sozinhas
        com.activest.domain.goal.GoalNature nature = g.getType().nature();
        boolean concludes = (nature == com.activest.domain.goal.GoalNature.CRESCENTE
                || nature == com.activest.domain.goal.GoalNature.SEQUENCIA) && now == GoalSituation.ATINGIDA;
        if (concludes) {
            g.setLifecycle(GoalLifecycle.CONCLUIDA);
            g.setFinalResult(codec.write(finalResult(e, today, "SISTEMA")));
            event(g, GoalEventKind.CONCLUIDA, Map.of("by", "SISTEMA"));
            notice(g, NotificationKind.META_CONCLUIDA, key + ":" + g.getRevision(), "Meta atingida: " + g.getName(),
                    e.objective() + ". " + summary(e));
            clearNotices(g);
            goals.save(g);
            return;
        }
        goals.save(g);

        // D20: pendencias que se resolvem quando a condicao volta
        if (now == GoalSituation.FORA_DO_OBJETIVO) {
            notice(g, NotificationKind.META_FORA_DO_OBJETIVO, key, "Meta fora do objetivo: " + g.getName(),
                    e.objective() + ". " + summary(e));
        } else {
            notifications.resolve(NotificationKind.META_FORA_DO_OBJETIVO, g.getUser().getId(), key);
        }
        if (now == GoalSituation.ATRASADA && previous == GoalSituation.NO_RITMO) {
            notice(g, NotificationKind.META_ATRASADA, key, "Meta atrasada: " + g.getName(),
                    e.objective() + ". Com as premissas atuais, não chega no prazo.");
        } else if (now != GoalSituation.ATRASADA) {
            notifications.resolve(NotificationKind.META_ATRASADA, g.getUser().getId(), key);
        }
        if (now == GoalSituation.SEM_DADOS) {
            notice(g, NotificationKind.META_SEM_DADOS, key, "Meta sem dados suficientes: " + g.getName(),
                    e.situationNote() != null ? e.situationNote() : "Faltam dados para calcular a meta.");
        } else {
            notifications.resolve(NotificationKind.META_SEM_DADOS, g.getUser().getId(), key);
        }
    }

    /** Pausada, concluida, arquivada ou excluida: as pendencias da meta se resolvem. */
    public void clearNotices(Goal g) {
        String key = g.getId().toString();
        UUID user = g.getUser().getId();
        notifications.resolve(NotificationKind.META_FORA_DO_OBJETIVO, user, key);
        notifications.resolve(NotificationKind.META_ATRASADA, user, key);
        notifications.resolve(NotificationKind.META_SEM_DADOS, user, key);
    }

    /** Resultado congelado na conclusao ou no prazo (o que a meta mostrava naquele dia). */
    public Map<String, Object> finalResult(Evaluation e, LocalDate date, String by) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString());
        m.put("by", by);
        m.put("indicator", e.indicator());
        m.put("indicatorLabel", e.indicatorLabel());
        m.put("unit", e.unit().name());
        m.put("target", e.target());
        m.put("progressPct", e.progressPct());
        m.put("situation", e.situation().name());
        m.put("quality", e.quality().name());
        m.put("objective", e.objective());
        m.put("figures", e.figures());
        return m;
    }

    private Map<String, Object> snapshotResult(Evaluation e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("indicatorLabel", e.indicatorLabel());
        m.put("unit", e.unit().name());
        m.put("remaining", e.remaining());
        m.put("achieved", e.achieved());
        m.put("issues", e.issues());
        m.put("figures", e.figures());
        m.put("projection", e.projection());
        m.put("sources", e.sources());
        return m;
    }

    private void notice(Goal g, NotificationKind kind, String key, String title, String body) {
        if (!g.isNotify()) {
            return;
        }
        notifications.notify(NotificationDraft.of(kind).user(g.getUser().getId()).title(title).body(body)
                .ref("GOAL", g.getId()).key(key).link("Ver meta", "/goals/" + g.getId()).build());
    }

    private void event(Goal g, GoalEventKind kind, Map<String, Object> detail) {
        events.save(GoalEvent.builder().goalId(g.getId()).kind(kind).revision(g.getRevision())
                .detail(codec.write(detail)).build());
    }

    private static String summary(Evaluation e) {
        return e.indicatorLabel() + ": " + (e.indicator() != null ? e.indicator().toPlainString() : "—")
                + (e.target() != null ? " (alvo " + e.target().toPlainString() + ")" : "");
    }

    private static GoalSituation parse(String s) {
        try {
            return GoalSituation.valueOf(s);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
