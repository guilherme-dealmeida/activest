package com.activest.adm.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.config.SecurityUtils;
import com.activest.domain.task.Task;
import com.activest.domain.task.TaskApi;
import com.activest.domain.task.TaskAttribute;
import com.activest.domain.task.TaskCatalogService;
import com.activest.domain.task.TaskCommands;
import com.activest.domain.task.TaskExecution;
import com.activest.domain.task.TaskExecutionLog;
import com.activest.domain.task.TaskExecutionLogRepository;
import com.activest.domain.task.TaskExecutionRepository;
import com.activest.domain.task.TaskExecutionStatus;
import com.activest.domain.task.TaskExecutor;
import com.activest.domain.task.TaskRepository;
import com.activest.domain.task.TaskTime;
import com.activest.domain.task.TaskType;
import com.activest.domain.task.TaskTypeAttribute;
import com.activest.domain.task.TaskTypeRepository;
import com.activest.domain.user.User;
import com.activest.domain.user.UserRepository;
import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * API da tela de Tarefas (docs/planos/PLANO_TAREFAS.md, T5; D9: na adm-api). Só administrador. Cadastrar e excluir
 * não têm rota neste escopo (D15).
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    /** Agendadas calculadas por tarefa no período (um cron de segundo em segundo não enche a resposta). */
    private static final int MAX_SCHEDULED_PER_TASK = 200;
    /** Espera pela rodada pedida ao outro serviço (D29). */
    private static final long REMOTE_RUN_WAIT_MILLIS = 5_000;

    private final TaskRepository tasks;
    private final TaskTypeRepository types;
    private final TaskExecutionRepository executions;
    private final TaskExecutionLogRepository logs;
    private final TaskCatalogService catalog;
    private final TaskExecutor executor;
    private final TaskCommands commands;
    private final UserRepository users;
    private final ObjectMapper json;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    // ---------------------------------------------------------------- DTOs

    public record AttributeDTO(Long typeAttributeId, String name, String valueType, boolean required,
                               String description, String defaultValue, String value) {
    }

    public record LastRunDTO(Long id, String status, LocalDateTime startedAt, LocalDateTime finishedAt) {
    }

    public record TaskDTO(Long id, String name, String description, String effectiveDescription, String type,
                          String typeDescription, String service, boolean typeEnabled, boolean enabled,
                          boolean runOnStartup, String cron,
                          LocalDateTime nextRun, boolean stoppable, boolean running, LastRunDTO lastRun,
                          List<AttributeDTO> attributes) {
    }

    public record TypeAttributeDTO(Long id, String name, String valueType, boolean required, String description,
                                   String defaultValue) {
    }

    public record TaskTypeDTO(Long id, String type, String description, String service, boolean enabled,
                              boolean stoppable, List<TypeAttributeDTO> attributes) {
    }

    public record TaskUpdateDTO(String name, String description, String cron, Boolean enabled,
                                Boolean runOnStartup, Map<String, String> attributes) {
    }

    public record RunResponseDTO(Long executionId, String status, String message) {
    }

    public record ExecutionDTO(Long id, Long taskId, String taskName, String type, String status, String triggerType,
                               LocalDateTime scheduledFor, LocalDateTime startedAt, LocalDateTime finishedAt,
                               Long durationSeconds, String requestedBy, LocalDateTime stopRequestedAt,
                               String summary, String errorMessage, int logLinesOmitted, boolean stoppable) {
    }

    public record ExecutionDetailDTO(ExecutionDTO execution, Map<String, String> parameters, String stopRequestedBy,
                                     long logLines) {
    }

    public record LogLineDTO(Long id, LocalDateTime loggedAt, String level, String message) {
    }

    public record LogPageDTO(List<LogLineDTO> lines, int page, int size, long total) {
    }

    // ---------------------------------------------------------------- tarefas (Configuração)

    @GetMapping
    @Transactional(readOnly = true)
    public List<TaskDTO> list() {
        requireAdmin();
        Map<Long, TaskExecution> last = executions.findLastOfEachTask().stream()
            .collect(Collectors.toMap(e -> e.getTask().getId(), Function.identity(), (a, b) -> a));
        Set<Long> running = runningTaskIds();
        return tasks.findAllAlive().stream().map(t -> toDTO(t, last.get(t.getId()), running.contains(t.getId())))
            .toList();
    }

    @GetMapping("/types")
    @Transactional(readOnly = true)
    public List<TaskTypeDTO> listTypes() {
        requireAdmin();
        return types.findAllByOrderByTypeAsc().stream().map(y -> new TaskTypeDTO(y.getId(), y.getType(),
            y.getDescription(), y.getService().name(), y.isEnabled(), y.isStoppable(),
            y.getAttributes().stream().map(a -> new TypeAttributeDTO(a.getId(), a.getAttName(),
                a.getValueType().name(), a.isRequired(), a.getDescription(), a.getAttValue())).toList())).toList();
    }

    @PutMapping("/{id}")
    public TaskDTO update(@PathVariable Long id, @RequestBody TaskUpdateDTO body) {
        requireAdmin();
        catalog.update(id, new TaskCatalogService.TaskChanges(body.name(), body.description(), body.cron(),
            body.enabled(), body.runOnStartup(), body.attributes()));
        TaskApi service = tasks.findAlive(id).orElseThrow().getTaskType().getService();
        // depois do commit da edicao: o reagendamento le o que foi gravado
        commands.changed(service);
        return tx.execute(s -> {
            Task t = tasks.findAlive(id).orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));
            TaskExecution last = executions.findByTask(id, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
            return toDTO(t, last, runningTaskIds().contains(id));
        });
    }

    /** Run (D23): esta API roda as tarefas da adm-api; as da app-api vão pelo Redis (D29). */
    @PostMapping("/{id}/run")
    public ResponseEntity<RunResponseDTO> run(@PathVariable Long id) {
        UUID user = requireAdmin().getId();
        Task task = tasks.findAlive(id).orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));
        TaskType type = task.getTaskType();
        if (!type.isEnabled()) {
            throw new BusinessException("O tipo " + type.getType() + " está desligado.");
        }
        if (executor.handles(type.getType())) {
            TaskExecution e = executor.runManual(id, user);
            return ResponseEntity.accepted().body(new RunResponseDTO(e.getId(), e.getStatus().name(),
                e.getStatus() == TaskExecutionStatus.SKIPPED ? e.getErrorMessage() : "Rodada iniciada."));
        }
        LocalDateTime asked = TaskTime.now().minusSeconds(1);
        Long before = executions.findByTask(id, PageRequest.of(0, 1)).stream().findFirst().map(TaskExecution::getId)
            .orElse(0L);
        try {
            commands.runElsewhere(type.getService(), id, user);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
        long until = System.currentTimeMillis() + REMOTE_RUN_WAIT_MILLIS;
        while (System.currentTimeMillis() < until) {
            TaskExecution newest = executions.findByTask(id, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
            if (newest != null && newest.getId() > before && !newest.getStartedAt().isBefore(asked)) {
                return ResponseEntity.accepted().body(new RunResponseDTO(newest.getId(), newest.getStatus().name(),
                    newest.getStatus() == TaskExecutionStatus.SKIPPED ? newest.getErrorMessage() : "Rodada iniciada."));
            }
            sleep(200);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "A " + label(type.getService()) + " não respondeu: a rodada não começou. Confira se ela está no ar.");
    }

    // ---------------------------------------------------------------- rodadas (Acompanhamento)

    /**
     * Rodadas iniciadas no período e, de agora até {@code to}, as agendadas calculadas do cron ({@code SCHEDULED},
     * D3). Padrão: 7 dias para trás e 7 para frente (D35). {@code status} aceita vários valores
     * ({@code status=FAILED,SUCCEEDED}); ausente, vêm todos. A aba Agendamentos pede só {@code SCHEDULED}, o
     * Acompanhamento os que estiverem marcados.
     */
    @GetMapping("/executions")
    @Transactional(readOnly = true)
    public List<ExecutionDTO> executions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) List<String> status) {
        requireAdmin();
        LocalDate today = LocalDate.now(TaskTime.ZONE);
        LocalDate first = from != null ? from : today.minusDays(7);
        LocalDate last = to != null ? to : today.plusDays(7);
        if (last.isBefore(first)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido.");
        }
        LocalDateTime start = first.atStartOfDay();
        LocalDateTime end = last.plusDays(1).atStartOfDay();

        Set<String> statuses = status == null ? Set.of() : status.stream()
            .filter(v -> v != null && !v.isBlank()).map(String::trim).collect(Collectors.toSet());
        boolean wantsRuns = statuses.isEmpty() || statuses.stream().anyMatch(v -> !"SCHEDULED".equals(v));
        boolean wantsScheduled = statuses.isEmpty() || statuses.contains("SCHEDULED");

        List<TaskExecution> rows = wantsRuns ? executions.findStartedBetween(start, end) : List.of();
        Map<UUID, String> names = userNames(rows.stream().map(TaskExecution::getRequestedBy).toList());
        Map<String, Boolean> stoppable = stoppableByType();
        List<ExecutionDTO> out = new ArrayList<>(rows.stream()
            .map(e -> toDTO(e, names, stoppable.getOrDefault(e.getTask().getTaskType().getType(), false)))
            .toList());

        LocalDateTime now = TaskTime.now();
        if (wantsScheduled && end.isAfter(now)) {
            LocalDateTime from0 = start.isAfter(now) ? start : now;
            for (Task t : tasks.findAllAlive()) {
                if (!t.isActive() || t.getCron() == null || !CronExpression.isValidExpression(t.getCron())) {
                    continue;
                }
                CronExpression cron = CronExpression.parse(t.getCron());
                ZonedDateTime next = from0.atZone(TaskTime.ZONE);
                for (int i = 0; i < MAX_SCHEDULED_PER_TASK; i++) {
                    next = cron.next(next);
                    if (next == null || !next.toLocalDateTime().isBefore(end)) {
                        break;
                    }
                    out.add(new ExecutionDTO(null, t.getId(), t.getName(), t.getTaskType().getType(), "SCHEDULED",
                        "SCHEDULE", next.toLocalDateTime(), null, null, null, null, null, null, null, 0, false));
                }
            }
        }
        return out.stream()
            .filter(e -> type == null || type.isBlank() || type.equals(e.type()))
            .filter(e -> statuses.isEmpty() || statuses.contains(e.status()))
            .sorted(Comparator.comparing(ExecutionDTO::scheduledFor).reversed()
                .thenComparing(e -> e.id() == null ? 0L : e.id(), Comparator.reverseOrder()))
            .toList();
    }

    @GetMapping("/executions/{id}")
    @Transactional(readOnly = true)
    public ExecutionDetailDTO execution(@PathVariable Long id) {
        requireAdmin();
        TaskExecution e = executions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rodada não encontrada"));
        Map<UUID, String> names = userNames(List.of(Objects.requireNonNullElse(e.getRequestedBy(), new UUID(0, 0)),
            Objects.requireNonNullElse(e.getStopRequestedBy(), new UUID(0, 0))));
        boolean stoppable = stoppableByType().getOrDefault(e.getTask().getTaskType().getType(), false);
        Map<String, String> parameters = new LinkedHashMap<>();
        if (e.getParameters() != null) {
            try {
                parameters = json.readValue(e.getParameters(), new TypeReference<LinkedHashMap<String, String>>() {
                });
            } catch (Exception ex) {
                parameters = Map.of("?", e.getParameters());
            }
        }
        return new ExecutionDetailDTO(toDTO(e, names, stoppable), parameters,
            e.getStopRequestedBy() == null ? null : names.get(e.getStopRequestedBy()), logs.countByExecutionId(id));
    }

    @GetMapping("/executions/{id}/logs")
    @Transactional(readOnly = true)
    public LogPageDTO executionLogs(@PathVariable Long id,
                                    @RequestParam(required = false) List<String> level,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "200") int size) {
        requireAdmin();
        if (!executions.existsById(id)) {
            throw new ResourceNotFoundException("Rodada não encontrada");
        }
        PageRequest req = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 1_000));
        Page<TaskExecutionLog> p = level == null || level.isEmpty()
            ? logs.findByExecutionIdOrderByIdAsc(id, req)
            : logs.findByExecutionIdAndLevelInOrderByIdAsc(id, level.stream().map(String::toUpperCase).toList(), req);
        return new LogPageDTO(p.getContent().stream()
            .map(l -> new LogLineDTO(l.getId(), l.getLoggedAt(), l.getLevel(), l.getMessage())).toList(),
            p.getNumber(), p.getSize(), p.getTotalElements());
    }

    /** Stop (D8): só registra o pedido; a rotina para no próximo ponto de parada, no serviço onde roda. */
    @PostMapping("/executions/{id}/stop")
    @Transactional
    public ResponseEntity<Void> stop(@PathVariable Long id) {
        UUID user = requireAdmin().getId();
        TaskExecution e = executions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rodada não encontrada"));
        if (e.getStatus() != TaskExecutionStatus.RUNNING) {
            throw new BusinessException("A rodada não está em execução.");
        }
        if (!stoppableByType().getOrDefault(e.getTask().getTaskType().getType(), false)) {
            throw new BusinessException("Esta tarefa ainda não tem ponto de parada: espere terminar.");
        }
        if (e.getStopRequestedAt() == null) {
            e.setStopRequestedAt(TaskTime.now());
            e.setStopRequestedBy(user);
            executions.save(e);
        }
        return ResponseEntity.accepted().build();
    }

    // ---------------------------------------------------------------- apoio

    private TaskDTO toDTO(Task t, TaskExecution last, boolean running) {
        TaskType y = t.getTaskType();
        LocalDateTime next = null;
        if (t.isActive() && t.getCron() != null && CronExpression.isValidExpression(t.getCron())) {
            ZonedDateTime z = CronExpression.parse(t.getCron()).next(TaskTime.now().atZone(TaskTime.ZONE));
            next = z == null ? null : z.toLocalDateTime();
        }
        // na ordem do tipo (a do seed), nao na ordem em que as linhas da tarefa foram criadas
        List<AttributeDTO> attributes = t.getAttributes().stream()
            .sorted(Comparator.comparing((TaskAttribute x) -> x.getTypeAttribute().getPosition())
                .thenComparing(x -> x.getTypeAttribute().getId()))
            .map(this::toDTO).toList();
        return new TaskDTO(t.getId(), t.getName(), t.getDescription(), t.getEffectiveDescription(), y.getType(),
            y.getDescription(), y.getService().name(), y.isEnabled(), t.isEnabled(), t.isRunOnStartup(), t.getCron(),
            next,
            y.isStoppable(), running,
            last == null ? null : new LastRunDTO(last.getId(), last.getStatus().name(), last.getStartedAt(),
                last.getFinishedAt()),
            attributes);
    }

    private AttributeDTO toDTO(TaskAttribute a) {
        TaskTypeAttribute d = a.getTypeAttribute();
        return new AttributeDTO(d.getId(), d.getAttName(), d.getValueType().name(), d.isRequired(), d.getDescription(),
            d.getAttValue(), a.getAttValue());
    }

    private ExecutionDTO toDTO(TaskExecution e, Map<UUID, String> names, boolean stoppable) {
        Long duration = e.getFinishedAt() == null
            ? Duration.between(e.getStartedAt(), TaskTime.now()).toSeconds()
            : Duration.between(e.getStartedAt(), e.getFinishedAt()).toSeconds();
        return new ExecutionDTO(e.getId(), e.getTask().getId(), e.getTask().getName(),
            e.getTask().getTaskType().getType(), e.getStatus().name(), e.getTriggerType().name(),
            e.getScheduledFor(), e.getStartedAt(), e.getFinishedAt(), duration,
            e.getRequestedBy() == null ? null : names.get(e.getRequestedBy()), e.getStopRequestedAt(),
            e.getSummary(), e.getErrorMessage(), e.getLogLinesOmitted(),
            stoppable && e.getStatus() == TaskExecutionStatus.RUNNING);
    }

    private Set<Long> runningTaskIds() {
        Set<Long> ids = new HashSet<>();
        for (TaskApi api : TaskApi.values()) {
            executions.findRunningByService(api).forEach(e -> ids.add(e.getTask().getId()));
        }
        return ids;
    }

    private Map<String, Boolean> stoppableByType() {
        Map<String, Boolean> m = new HashMap<>();
        types.findAll().forEach(y -> m.put(y.getType(), y.isStoppable()));
        return m;
    }

    private Map<UUID, String> userNames(Collection<UUID> ids) {
        Set<UUID> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> m = new HashMap<>();
        users.findAllById(wanted).forEach(u -> m.put(u.getId(), u.getName()));
        return m;
    }

    private static String label(TaskApi api) {
        return api == TaskApi.APP_API ? "app-api" : "adm-api";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private User requireAdmin() {
        User user = users.findById(SecurityUtils.getCurrentUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente administrador.");
        }
        return user;
    }
}
