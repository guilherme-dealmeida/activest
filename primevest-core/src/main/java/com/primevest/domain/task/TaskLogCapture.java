package com.primevest.domain.task;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Log da rodada (D6, meio-termo): as linhas que a rotina já escreve no log, marcadas com o MDC
 * {@value TaskExecutor#MDC_KEY}, vão para {@code task_execution_logs} — INFO, WARN e ERROR; até {@value #MAX_LINES}
 * linhas por rodada; passado o teto, WARN e ERROR continuam entrando e o INFO só é contado
 * ({@code task_executions.log_lines_omitted}). Gravação em lote a cada segundo, fora da thread da rotina. O console não
 * muda.
 */
@Component
public class TaskLogCapture {

    static final int MAX_LINES = 2_000;
    private static final int MAX_MESSAGE = 4_000;
    private static final String APPENDER_NAME = "TASK_EXECUTION_LOG";

    private record Line(long executionId, LocalDateTime loggedAt, String level, String message) {
    }

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final Queue<Line> pending = new ConcurrentLinkedQueue<>();
    private final Map<Long, AtomicInteger> written = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> omitted = new ConcurrentHashMap<>();
    private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "task-log-flush");
        t.setDaemon(true);
        return t;
    });
    private Appender appender;

    public TaskLogCapture(JdbcTemplate jdbc,
                          @Value("${primevest.tasks.log-capture.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @PostConstruct
    void start() {
        if (!enabled || !(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return;
        }
        appender = new Appender();
        appender.setName(APPENDER_NAME);
        appender.setContext(context);
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        flusher.scheduleWithFixedDelay(this::flushSafely, 1, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        if (appender != null && LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
            appender.stop();
        }
        flushSafely();
        flusher.shutdown();
    }

    /** Fim da rodada: grava o que falta dela e esquece os contadores. */
    public void close(Long executionId) {
        flushSafely();
        written.remove(executionId);
        omitted.remove(executionId);
    }

    void accept(ILoggingEvent event) {
        String id = event.getMDCPropertyMap().get(TaskExecutor.MDC_KEY);
        if (id == null || !event.getLevel().isGreaterOrEqual(Level.INFO)) {
            return;
        }
        long executionId;
        try {
            executionId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            return;
        }
        boolean important = event.getLevel().isGreaterOrEqual(Level.WARN);
        int n = written.computeIfAbsent(executionId, k -> new AtomicInteger()).incrementAndGet();
        if (n > MAX_LINES && !important) {
            omitted.computeIfAbsent(executionId, k -> new AtomicInteger()).incrementAndGet();
            return;
        }
        pending.add(new Line(executionId,
            LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), TaskTime.ZONE),
            event.getLevel().toString(), message(event)));
    }

    private static String message(ILoggingEvent event) {
        String text = event.getFormattedMessage();
        IThrowableProxy t = event.getThrowableProxy();
        if (t != null) {
            text = text + " [" + t.getClassName() + (t.getMessage() == null ? "" : ": " + t.getMessage()) + "]";
        }
        return text.length() > MAX_MESSAGE ? text.substring(0, MAX_MESSAGE) + "…" : text;
    }

    private synchronized void flushSafely() {
        try {
            List<Object[]> batch = new ArrayList<>();
            Line line;
            while ((line = pending.poll()) != null) {
                batch.add(new Object[]{line.executionId(), Timestamp.valueOf(line.loggedAt()), line.level(),
                    line.message()});
                if (batch.size() >= 500) {
                    insert(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                insert(batch);
            }
            Map<Long, Integer> counts = new HashMap<>();
            omitted.forEach((id, c) -> {
                int v = c.getAndSet(0);
                if (v > 0) {
                    counts.put(id, v);
                }
            });
            counts.forEach((id, v) -> jdbc.update(
                "UPDATE task_executions SET log_lines_omitted = log_lines_omitted + ? WHERE id = ?", v, id));
        } catch (RuntimeException e) {
            // sem log aqui: o log desta classe nao pode voltar para ela
            System.err.println("Tarefas: linhas de log da rodada nao gravadas: " + e.getMessage());
        }
    }

    private void insert(List<Object[]> batch) {
        jdbc.batchUpdate("INSERT INTO task_execution_logs (execution_id, logged_at, level, message) "
            + "SELECT ?, ?, ?, ? WHERE EXISTS (SELECT 1 FROM task_executions WHERE id = ?)",
            batch.stream().map(r -> new Object[]{r[0], r[1], r[2], r[3], r[0]}).toList());
    }

    private final class Appender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            accept(event);
        }
    }
}
