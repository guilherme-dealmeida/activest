package com.activest.domain.goal;

import com.activest.domain.asset.Asset;
import com.activest.domain.asset.AssetResolver;
import com.activest.domain.goal.dto.GoalDTOs.*;
import com.activest.domain.goal.engine.*;
import com.activest.domain.goal.type.GoalTypeCatalog;
import com.activest.domain.goal.type.GoalTypeSpec;
import com.activest.domain.goal.type.ScopeKind;
import com.activest.domain.performance.PortfolioTimeline;
import com.activest.domain.performance.ScopeSeries;
import com.activest.domain.performance.PerformanceBasis;
import com.activest.domain.transaction.TransactionType;
import com.activest.domain.user.User;
import com.activest.domain.user.UserRepository;
import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Metas financeiras (docs/planos/PLANO_METAS.md): cadastro pelo catalogo de tipos, ciclo de vida com revisoes e
 * eventos, avaliacao na leitura ({@link GoalEvaluator}) e a evolucao. A fotografia diaria, a conclusao automatica e os
 * avisos ficam no {@link GoalTracker}.
 */
@Service
@Transactional(readOnly = true)
public class GoalService {

    static final List<GoalLifecycle> TRACKED = List.of(GoalLifecycle.ATIVA, GoalLifecycle.PAUSADA);

    private final GoalRepository goals;
    private final GoalEventRepository events;
    private final GoalSnapshotRepository snapshots;
    private final UserRepository users;
    private final AssetResolver assets;
    private final GoalFactsLoader factsLoader;
    private final GoalTracker tracker;
    private final GoalCodec codec;

    public GoalService(GoalRepository goals, GoalEventRepository events, GoalSnapshotRepository snapshots,
                       UserRepository users, AssetResolver assets, GoalFactsLoader factsLoader, GoalTracker tracker,
                       GoalCodec codec) {
        this.goals = goals;
        this.events = events;
        this.snapshots = snapshots;
        this.users = users;
        this.assets = assets;
        this.factsLoader = factsLoader;
        this.tracker = tracker;
        this.codec = codec;
    }

    // ------------------------------------------------------------------ catalogo

    public CatalogDTO catalog() {
        List<CategoryDTO> cats = Arrays.stream(GoalCategory.values()).map(c -> new CategoryDTO(c.name(), c.label())).toList();
        List<GoalTypeDTO> types = GoalTypeCatalog.all().stream().map(GoalService::typeDTO).toList();
        return new CatalogDTO(cats, types);
    }

    private static GoalTypeDTO typeDTO(GoalTypeSpec s) {
        GoalType t = s.type();
        return new GoalTypeDTO(t.name(), t.code(), t.label(), t.category().name(), t.category().label(),
                t.nature().name(), t.projection(), s.description(), s.fields(),
                s.scopes().stream().map(k -> new Option(k.name(), k.label())).toList(), s.reservation().name(),
                s.calculates(), s.data(), s.limitations());
    }

    /** Opcoes de escopo e de dimensao (ALO-1, RIS-1) a partir das posicoes do usuario. */
    public ScopeOptionsDTO scopeOptions(UUID userId) {
        GoalFacts facts = factsLoader.load(userId);
        Map<String, List<Option>> out = new LinkedHashMap<>();
        for (ScopeKind k : ScopeKind.values()) {
            if (k == ScopeKind.ALL) {
                continue;
            }
            Map<String, String> seen = new TreeMap<>();
            // Ticker em mais de um ativo da carteira (META cripto e META acao): o rotulo leva o tipo
            Map<String, Set<Long>> idsByTicker = new HashMap<>();
            facts.positions().values().forEach(p -> idsByTicker
                    .computeIfAbsent(p.ticker().toUpperCase(), x -> new HashSet<>()).add(p.assetId()));
            for (PositionInfo p : facts.positions().values()) {
                String key = p.key(k);
                if (key == null) {
                    continue;
                }
                String label = k == ScopeKind.POSITIONS
                        ? p.ticker() + (p.issuer() != null && p.fixedIncome() ? " · " + p.issuer() : "")
                            + (p.settled() ? " (encerrada)" : "")
                        : k == ScopeKind.ASSET && idsByTicker.get(p.ticker().toUpperCase()).size() > 1
                        ? p.ticker() + " · " + p.label(ScopeKind.TYPE)
                        : p.label(k);
                seen.putIfAbsent(key, label);
            }
            out.put(k.name(), seen.entrySet().stream().map(e -> new Option(e.getKey(), e.getValue()))
                    .sorted(Comparator.comparing(Option::label)).toList());
        }
        return new ScopeOptionsDTO(out);
    }

    // ------------------------------------------------------------------ leitura

    public GoalListDTO list(UUID userId, String lifecycle, String category, String situation) {
        List<Goal> all = goals.findByUserIdOrderByCreatedAtDesc(userId);
        GoalFacts facts = factsLoader.load(userId);
        List<GoalDTO> dtos = new ArrayList<>();
        int active = 0, paused = 0, completed = 0, archived = 0, onTrack = 0, behind = 0, outside = 0, noData = 0, achieved = 0;
        int inProgress = 0;
        for (Goal g : all) {
            GoalDTO dto = toDTO(g, facts);
            switch (g.getLifecycle()) {
                case ATIVA -> active++;
                case PAUSADA -> paused++;
                case CONCLUIDA -> completed++;
                case ARQUIVADA -> archived++;
            }
            if (dto.evaluation() != null && g.getLifecycle() == GoalLifecycle.ATIVA) {
                switch (dto.evaluation().situation()) {
                    case NO_RITMO, DENTRO_DO_OBJETIVO -> onTrack++;
                    case ATRASADA, PRAZO_VENCIDO -> behind++;
                    case FORA_DO_OBJETIVO -> outside++;
                    case SEM_DADOS -> noData++;
                    case ATINGIDA -> achieved++;
                    case EM_ANDAMENTO, ESTIMATIVA -> inProgress++;
                }
            }
            boolean show = lifecycle == null || lifecycle.isBlank()
                    ? g.getLifecycle() != GoalLifecycle.ARQUIVADA
                    : g.getLifecycle().name().equalsIgnoreCase(lifecycle);
            if (category != null && !category.isBlank() && !g.getCategory().name().equalsIgnoreCase(category)) {
                show = false;
            }
            if (situation != null && !situation.isBlank()
                    && (dto.evaluation() == null || !dto.evaluation().situation().name().equalsIgnoreCase(situation))) {
                show = false;
            }
            if (show) {
                dtos.add(dto);
            }
        }
        return new GoalListDTO(new SummaryDTO(active, paused, completed, archived, onTrack, behind, outside, noData,
                achieved, inProgress), dtos);
    }

    public GoalDTO get(UUID userId, UUID id) {
        Goal g = owned(userId, id);
        return toDTO(g, factsLoader.load(userId));
    }

    public PreviewDTO preview(UUID userId, GoalRequest req) {
        GoalType type = type(req.type());
        GoalTypeCatalog.Validated v = validate(type, req, null);
        GoalFacts facts = factsLoader.load(userId);
        GoalDefinition d = new GoalDefinition(type, req.name(), v.targetValue(), v.targetDate(), v.scope(), v.params(),
                facts.today());
        return new PreviewDTO(GoalEvaluator.objective(d), GoalCodec.scopeMap(v.scope()), scopeLabel(type, v.scope()),
                GoalEvaluator.evaluate(d, facts));
    }

    /**
     * Valores automaticos das premissas da projecao (revisao de 25/10/2026): o formulario os mostra ja preenchidos,
     * mesmo antes de o cadastro estar completo (sem validar alvo e prazo). Escopo invalido ou incompleto: a carteira.
     */
    public List<Evaluation.Assumption> automaticAssumptions(UUID userId, GoalRequest req) {
        GoalType type = type(req.type());
        GoalScope scope;
        try {
            scope = GoalCodec.scope(req.scope());
            if (scope.kind() != ScopeKind.ALL && scope.values().isEmpty()) {
                scope = GoalScope.all(scope.reservation());
            }
        } catch (RuntimeException e) {
            scope = GoalScope.all(null);
        }
        if (scope.reservation() == null) {
            scope = new GoalScope(scope.kind(), scope.values(), GoalScope.Reservation.WITH, scope.excludeTypes(),
                    scope.excludeCategories());
        }
        GoalFacts facts = factsLoader.load(userId);
        Map<String, Object> params = req.params() != null ? req.params() : Map.of();
        return GoalEvaluator.automaticAssumptions(new GoalDefinition(type, req.name(), req.targetValue(),
                req.targetDate(), scope, params, facts.today()), facts);
    }

    public List<EventDTO> events(UUID userId, UUID id) {
        owned(userId, id);
        return events.findByGoalIdOrderByOccurredAtAscIdAsc(id).stream()
                .map(e -> new EventDTO(e.getId(), e.getKind().name(), e.getRevision(), map(e.getDefinition()),
                        map(e.getDetail()), e.getOccurredAt())).toList();
    }

    // ------------------------------------------------------------------ escrita

    @Transactional
    public GoalDTO create(UUID userId, GoalRequest req) {
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        GoalType type = type(req.type());
        GoalTypeCatalog.Validated v = validate(type, req, null);
        Goal g = Goal.builder()
                .user(user)
                .name(name(req, type))
                .type(type)
                .category(type.category())
                .lifecycle(GoalLifecycle.ATIVA)
                .targetValue(v.targetValue())
                .targetDate(v.targetDate())
                .scope(write(GoalCodec.scopeMap(v.scope())))
                .params(write(v.params()))
                .notify(req.notifications() == null || req.notifications())
                .notes(blankToNull(req.notes()))
                .revision(1)
                .build();
        g = goals.save(g);
        event(g, GoalEventKind.CRIADA, definition(g), null);
        tracker.track(userId, g.getId());
        return toDTO(goals.findById(g.getId()).orElseThrow(), factsLoader.load(userId));
    }

    @Transactional
    public GoalDTO update(UUID userId, UUID id, GoalRequest req) {
        Goal g = owned(userId, id);
        if (g.getLifecycle() == GoalLifecycle.ARQUIVADA) {
            throw new BusinessException("Desarquive a meta para editá-la.");
        }
        // concluída (revisão de 26/10/2026): editar corrige um cadastro errado e reabre a meta; se o alvo continuar
        // atingido, ela conclui de novo sozinha
        boolean reopened = g.getLifecycle() == GoalLifecycle.CONCLUIDA;
        if (reopened) {
            g.setLifecycle(GoalLifecycle.ATIVA);
            g.setFinalResult(null);
        }
        GoalType type = type(req.type());
        if (type != g.getType()) {
            throw new BusinessException("O tipo da meta não muda: crie uma meta nova.");
        }
        GoalTypeCatalog.Validated v = validate(type, req, g.getTargetDate());
        g.setName(name(req, type));
        g.setTargetValue(v.targetValue());
        g.setTargetDate(v.targetDate());
        g.setScope(write(GoalCodec.scopeMap(v.scope())));
        g.setParams(write(v.params()));
        g.setNotify(req.notifications() == null || req.notifications());
        g.setNotes(blankToNull(req.notes()));
        g.setRevision(g.getRevision() + 1);
        g.setAchievedOn(null);
        g = goals.save(g);
        event(g, GoalEventKind.EDITADA, definition(g), reopened ? Map.of("reopened", true) : null);
        tracker.track(userId, g.getId());
        return toDTO(goals.findById(g.getId()).orElseThrow(), factsLoader.load(userId));
    }

    @Transactional
    public GoalDTO transition(UUID userId, UUID id, String action) {
        Goal g = owned(userId, id);
        GoalLifecycle from = g.getLifecycle();
        switch (action) {
            case "pause" -> {
                require(from == GoalLifecycle.ATIVA, "Só a meta ativa pode ser pausada.");
                g.setLifecycle(GoalLifecycle.PAUSADA);
                event(g, GoalEventKind.PAUSADA, null, null);
            }
            case "resume" -> {
                require(from == GoalLifecycle.PAUSADA, "Só a meta pausada pode ser retomada.");
                g.setLifecycle(GoalLifecycle.ATIVA);
                event(g, GoalEventKind.RETOMADA, null, null);
            }
            case "complete" -> {
                require(from == GoalLifecycle.ATIVA || from == GoalLifecycle.PAUSADA,
                        "Só a meta ativa ou pausada pode ser concluída.");
                GoalFacts facts = factsLoader.load(userId);
                Evaluation e = GoalEvaluator.evaluate(definition(g, facts.today()), facts);
                g.setLifecycle(GoalLifecycle.CONCLUIDA);
                g.setFinalResult(write(tracker.finalResult(e, facts.today(), "USUARIO")));
                event(g, GoalEventKind.CONCLUIDA, null, Map.of("by", "USUARIO"));
            }
            case "archive" -> {
                require(from != GoalLifecycle.ARQUIVADA, "A meta já está arquivada.");
                g.setLifecycleBeforeArchive(from);
                g.setLifecycle(GoalLifecycle.ARQUIVADA);
                event(g, GoalEventKind.ARQUIVADA, null, null);
            }
            case "unarchive" -> {
                require(from == GoalLifecycle.ARQUIVADA, "A meta não está arquivada.");
                g.setLifecycle(g.getLifecycleBeforeArchive() != null ? g.getLifecycleBeforeArchive() : GoalLifecycle.ATIVA);
                g.setLifecycleBeforeArchive(null);
                event(g, GoalEventKind.DESARQUIVADA, null, null);
            }
            default -> throw new ResourceNotFoundException("Ação desconhecida");
        }
        goals.save(g);
        if (g.getLifecycle() == GoalLifecycle.ATIVA) {
            tracker.track(userId, g.getId());
        } else {
            tracker.clearNotices(g);
        }
        return toDTO(goals.findById(id).orElseThrow(), factsLoader.load(userId));
    }

    /** D15: so a arquivada, com confirmacao na tela; arquivar e o caminho normal. */
    @Transactional
    public void delete(UUID userId, UUID id) {
        Goal g = owned(userId, id);
        if (g.getLifecycle() != GoalLifecycle.ARQUIVADA) {
            throw new BusinessException("Arquive a meta antes de excluí-la.");
        }
        tracker.clearNotices(g);
        goals.delete(g);
    }

    // ------------------------------------------------------------------ historico (D3)

    public HistoryDTO history(UUID userId, UUID id, LocalDate from, LocalDate to) {
        Goal g = owned(userId, id);
        GoalFacts facts = factsLoader.load(userId);
        GoalDefinition d = definition(g, facts.today());
        LocalDate end = to != null ? to : facts.today();
        LocalDate start = from != null ? from : end.minusYears(5);
        List<SnapshotDTO> snaps = snapshots.findByGoalIdAndReferenceDateBetweenOrderByReferenceDateAsc(id, start, end)
                .stream().map(s -> new SnapshotDTO(s.getReferenceDate(), s.getRevision(), s.getIndicator(), s.getTarget(),
                        s.getProgressPct(), s.getSituation(), s.getQuality())).toList();
        List<TargetDTO> targets = new ArrayList<>();
        for (GoalEvent e : events.findByGoalIdOrderByOccurredAtAscIdAsc(id)) {
            if (e.getDefinition() != null) {
                Object t = map(e.getDefinition()).get("targetValue");
                targets.add(new TargetDTO(e.getRevision(), t != null ? new BigDecimal(t.toString()) : null, e.getOccurredAt()));
            }
        }
        Series s = series(d, facts, start, end);
        boolean comparable = !(d.type() == GoalType.LP_1 && d.targetValue() != null)
                && d.type() != GoalType.RNT_2 && d.type() != GoalType.ATV_3;
        // alvo que anda (o aporte médio em REN-3) não vira linha fixa: ele já é uma das linhas
        boolean fixedTarget = comparable && d.type() != GoalType.REN_3;
        return new HistoryDTO(s.unit, s.label, s.points, targets, snaps, s.note, comparable, s.extras, fixedTarget);
    }

    private record Series(String unit, String label, List<PointDTO> points, String note, List<LineDTO> extras) {
        Series(String unit, String label, List<PointDTO> points, String note) {
            this(unit, label, points, note, List.of());
        }
    }

    private static List<PointDTO> points(List<GoalEvaluator.ChartPoint> pts, boolean money) {
        return pts.stream().map(p -> new PointDTO(p.date(), money ? EvaluationBuilderAccess.money(p.value())
                : EvaluationBuilderAccess.pct(p.value()))).toList();
    }

    /** Graficos do motor (o indicador como estava em cada dia): a primeira linha e a evolucao; as outras, apoio. */
    private static Series chart(List<GoalEvaluator.ChartLine> lines, String unit, String fallback) {
        if (lines.isEmpty()) {
            return new Series(unit, fallback, List.of(), "Carteira sem histórico");
        }
        boolean money = "BRL".equals(unit);
        List<LineDTO> extras = lines.stream().skip(1)
                .map(l -> new LineDTO(l.key(), l.label(), points(l.points(), money))).toList();
        return new Series(unit, lines.get(0).label(), points(lines.get(0).points(), money), null, extras);
    }

    /** Evolucao recalculada desde antes da meta, pelos dados de hoje (uma transacao retroativa muda a curva). */
    private Series series(GoalDefinition d, GoalFacts f, LocalDate start, LocalDate end) {
        switch (d.type()) {
            case PAT_1, ATV_2, LP_1 -> {
                PortfolioTimeline t = f.timeline().get();
                if (t.isEmpty()) {
                    return new Series("BRL", "Patrimônio", List.of(), "Carteira sem histórico");
                }
                ScopeSeries ss = ScopeSeries.of(t, p -> {
                    PositionInfo info = f.positions().get(p.id());
                    if (info == null) {
                        return false;
                    }
                    return d.type() == GoalType.ATV_2 ? Objects.equals(info.assetId(), d.assetId()) : d.scope().includes(info);
                }, PerformanceBasis.GROSS);
                List<PointDTO> pts = new ArrayList<>();
                int step = Math.max(1, t.dates().size() / 260);
                for (int i = 0; i < t.dates().size(); i += step) {
                    LocalDate day = t.dates().get(i);
                    if (!day.isBefore(start) && !day.isAfter(end) && !ss.unavailable[i]) {
                        pts.add(new PointDTO(day, EvaluationBuilderAccess.money(ss.value[i])));
                    }
                }
                return new Series("BRL", d.type() == GoalType.ATV_2 ? "Valor de mercado" : "Patrimônio", pts,
                        "Valor de mercado dia a dia (base bruta), da linha do tempo da Rentabilidade");
            }
            case RNT_1 -> {
                return chart(GoalEvaluator.returnChart(d, f, start, end), "PERCENT", "Rentabilidade");
            }
            case RNT_2 -> {
                Series c = chart(GoalEvaluator.beatIndexChart(d, f, start, end), "PERCENT", "Rentabilidade");
                return new Series(c.unit, c.label, c.points, "Carteira e índice no período da meta, terminando em cada dia: a distância entre as linhas é o indicador", c.extras);
            }
            case RIS_2 -> {
                return chart(GoalEvaluator.drawdownChart(d, f, start, end), "PERCENT", "Queda desde o pico");
            }
            case APO_1, APO_2 -> {
                Set<UUID> ids = new HashSet<>();
                f.positions().values().stream().filter(p -> d.scope().includes(p)).forEach(p -> ids.add(p.id()));
                LocalDate s0 = d.type() == GoalType.APO_1 && d.date("startDate") != null ? d.date("startDate") : start;
                double acc = 0;
                List<PointDTO> pts = new ArrayList<>();
                for (GoalFacts.Flow fl : f.flows()) {
                    if (!ids.contains(fl.positionId()) || fl.date().isBefore(s0) || fl.date().isAfter(end)) {
                        continue;
                    }
                    acc += fl.type() == TransactionType.BUY ? fl.amount().doubleValue() : -fl.amount().doubleValue();
                    pts.add(new PointDTO(fl.date(), EvaluationBuilderAccess.money(acc)));
                }
                // a linha vai até hoje (encosta na fotografia e na projeção, que começam hoje)
                if (!pts.isEmpty() && pts.get(pts.size() - 1).date().isBefore(end)) {
                    pts.add(new PointDTO(end, EvaluationBuilderAccess.money(acc)));
                }
                return new Series("BRL", "Aporte líquido acumulado", pts, null);
            }
            case REN_1, REN_2, REN_3, ATV_3 -> {
                Set<UUID> ids = new HashSet<>();
                f.positions().values().stream()
                        .filter(p -> d.type() == GoalType.ATV_3 ? Objects.equals(p.assetId(), d.assetId()) : d.scope().includes(p))
                        .forEach(p -> ids.add(p.id()));
                TreeMap<LocalDate, Double> byMonth = new TreeMap<>();
                for (GoalFacts.Income i : f.incomes()) {
                    if (ids.contains(i.positionId()) && i.received() && i.isIncome() && i.refDate() != null
                            && !i.refDate().isBefore(start) && !i.refDate().isAfter(end)) {
                        byMonth.merge(i.refDate().withDayOfMonth(1), i.net() != null ? i.net().doubleValue() : 0, Double::sum);
                    }
                }
                // cada mês no último dia dele (a fotografia do mês seguinte encosta no ponto)
                List<PointDTO> monthly = new ArrayList<>();
                byMonth.forEach((m, v) -> monthly.add(new PointDTO(m.plusMonths(1).minusDays(1), EvaluationBuilderAccess.money(v))));
                if (d.type() == GoalType.ATV_3) {
                    return new Series("BRL", "Proventos líquidos por mês", monthly, null);
                }
                // o que o indicador mede, mês a mês: a média dos k meses fechados (renda anual: a soma de 12)
                boolean yearly = d.type() == GoalType.REN_1 && "YEAR".equals(d.text("unit"));
                int k = yearly ? 12 : d.integer("window") != null ? d.integer("window") : 12;
                LocalDate lastClosed = f.today().withDayOfMonth(1).minusMonths(1);
                // despesa de hoje, como o indicador (corrigida pelo IPCA desde a data dela, se a meta pede)
                double expenseNow = d.number("expense") != null ? d.number("expense") : 0;
                LocalDate expenseDate = d.date("expenseDate") != null ? d.date("expenseDate") : d.createdOn();
                if (d.type() == GoalType.REN_2 && d.flag("ipcaCorrection") && expenseDate.isBefore(f.today())) {
                    Optional<GoalFacts.Accumulated> ipca = f.indices().accumulated("IPCA", expenseDate, f.today());
                    if (ipca.isPresent()) {
                        expenseNow = expenseNow * (1 + ipca.get().rate());
                    }
                }
                List<PointDTO> rolling = new ArrayList<>();
                if (!byMonth.isEmpty()) {
                    for (LocalDate m = byMonth.firstKey(); !m.isAfter(lastClosed); m = m.plusMonths(1)) {
                        double sum = byMonth.subMap(m.minusMonths(k - 1L), true, m, true).values().stream()
                                .mapToDouble(Double::doubleValue).sum();
                        double v = yearly ? sum : sum / k;
                        if (d.type() == GoalType.REN_2) {
                            if (expenseNow <= 0) {
                                continue;
                            }
                            v = v / expenseNow * 100;
                        }
                        rolling.add(new PointDTO(m.plusMonths(1).minusDays(1), d.type() == GoalType.REN_2
                                ? EvaluationBuilderAccess.pct(v) : EvaluationBuilderAccess.money(v)));
                    }
                }
                if (d.type() == GoalType.REN_2) {
                    return new Series("PERCENT", "Cobertura das despesas (média de " + k + " meses)", rolling,
                            "Com a despesa de hoje" + (d.flag("ipcaCorrection") ? " (corrigida pelo IPCA)" : ""));
                }
                String label = yearly ? "Renda anual (12 meses fechados)" : "Renda média mensal (" + k + " meses)";
                if (d.type() == GoalType.REN_3) {
                    // o alvo desta meta anda: é o aporte médio da mesma janela — vira uma linha, mês a mês
                    TreeMap<LocalDate, Double> flows = new TreeMap<>();
                    for (GoalFacts.Flow fl : f.flows()) {
                        if (ids.contains(fl.positionId())) {
                            flows.merge(fl.date().withDayOfMonth(1), fl.type() == TransactionType.BUY
                                    ? fl.amount().doubleValue() : -fl.amount().doubleValue(), Double::sum);
                        }
                    }
                    List<PointDTO> contrib = new ArrayList<>();
                    for (PointDTO r : rolling) {
                        LocalDate m = r.date().withDayOfMonth(1);
                        double sum = flows.subMap(m.minusMonths(k - 1L), true, m, true).values().stream()
                                .mapToDouble(Double::doubleValue).sum();
                        contrib.add(new PointDTO(r.date(), EvaluationBuilderAccess.money(Math.max(sum / k, 0))));
                    }
                    return new Series("BRL", label, rolling,
                            "A meta é a renda média passar do aporte médio na mesma janela de " + k
                                    + " meses fechados: as duas linhas andam juntas, e o cruzamento é a meta atingida",
                            List.of(new LineDTO("contribution", "Aporte médio mensal (" + k + " meses)", contrib),
                                    new LineDTO("monthly", "Proventos do mês", monthly)));
                }
                return new Series("BRL", label, rolling, null,
                        List.of(new LineDTO("monthly", "Proventos do mês", monthly)));
            }
            default -> {
                return new Series(null, null, List.of(), "Esta meta compara a situação de hoje com o limite: a evolução fica nas fotografias");
            }
        }
    }

    // ------------------------------------------------------------------ apoio

    GoalDTO toDTO(Goal g, GoalFacts facts) {
        GoalDefinition d = definition(g, facts.today());
        Evaluation e = g.getLifecycle() == GoalLifecycle.CONCLUIDA ? null : GoalEvaluator.evaluate(d, facts);
        GoalType t = g.getType();
        return new GoalDTO(g.getId(), g.getName(), t.name(), t.code(), t.label(), g.getCategory().name(),
                g.getCategory().label(), t.nature().name(), g.getLifecycle().name(), g.getTargetValue(),
                g.getTargetDate(), map(g.getScope()), scopeLabel(t, d.scope()), map(g.getParams()), g.isNotify(),
                g.getNotes(), g.getRevision(), g.getAchievedOn(), map(g.getFinalResult()), g.getCreatedAt(),
                g.getUpdatedAt(), e);
    }

    GoalDefinition definition(Goal g, LocalDate today) {
        return codec.definition(g, today);
    }

    private Map<String, Object> definition(Goal g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", g.getName());
        m.put("type", g.getType().name());
        m.put("targetValue", g.getTargetValue());
        m.put("targetDate", g.getTargetDate() != null ? g.getTargetDate().toString() : null);
        m.put("scope", map(g.getScope()));
        m.put("params", map(g.getParams()));
        m.put("notify", g.isNotify());
        m.put("notes", g.getNotes());
        return m;
    }

    private void event(Goal g, GoalEventKind kind, Map<String, Object> definition, Map<String, Object> detail) {
        events.save(GoalEvent.builder().goalId(g.getId()).kind(kind).revision(g.getRevision())
                .definition(definition != null ? write(definition) : null)
                .detail(detail != null ? write(detail) : null).build());
    }

    private GoalTypeCatalog.Validated validate(GoalType type, GoalRequest req, LocalDate currentTargetDate) {
        GoalTypeCatalog.Validated v = GoalTypeCatalog.validate(type, req.targetValue(), req.targetDate(), req.scope(),
                req.params(), factsLoaderToday(), currentTargetDate);
        // Meta por ativo: o ativo e o id; ticker e tipo vao junto como rotulo (V13 — o ticker se repete entre tipos)
        Object assetId = v.params().get("assetId");
        if (assetId != null) {
            Asset a = assets.byId(Long.valueOf(assetId.toString())).orElseThrow(() -> new BusinessException(
                    "Ativo não encontrado no catálogo. Selecione um ativo existente na busca."));
            v.params().put("ticker", a.getTicker());
            v.params().put("assetType", a.getAssetType().getName());
        }
        if (v.scope().kind() == ScopeKind.ASSET) {
            for (String t : v.scope().values()) {
                if (assetOf(t).isEmpty()) {
                    throw new BusinessException("Ativo não encontrado no catálogo: " + t);
                }
            }
        }
        return v;
    }

    /** O ativo de um valor do escopo por ativo (o id em texto). */
    private Optional<Asset> assetOf(String value) {
        try {
            return assets.byId(Long.valueOf(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private LocalDate factsLoaderToday() {
        return tracker.today();
    }

    private static GoalType type(String raw) {
        GoalType t = GoalType.parse(raw);
        if (t == null) {
            throw new BusinessException("Tipo de meta desconhecido: " + raw);
        }
        return t;
    }

    private static String name(GoalRequest req, GoalType type) {
        String n = req.name() != null ? req.name().trim() : "";
        if (n.isEmpty()) {
            throw new BusinessException("Informe o nome da meta.");
        }
        return n;
    }

    private Goal owned(UUID userId, UUID id) {
        return goals.findOwned(id, userId).orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada"));
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new BusinessException(message);
        }
    }

    private String scopeLabel(GoalType type, GoalScope s) {
        GoalTypeSpec spec = GoalTypeCatalog.of(type);
        if (spec == null || spec.reservation() == com.activest.domain.goal.type.ReservationRule.NONE) {
            return null;
        }
        return s.kind() == ScopeKind.ASSET ? s.describe(v -> assetOf(v).map(Asset::getTicker).orElse(v)) : s.describe();
    }

    String write(Object o) {
        return codec.write(o);
    }

    Map<String, Object> map(String s) {
        return codec.map(s);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Arredondamento das series (R$ 2 casas, % 2 casas). */
    static final class EvaluationBuilderAccess {
        static BigDecimal money(double v) {
            return BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP);
        }

        static BigDecimal pct(double v) {
            return BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }
}
