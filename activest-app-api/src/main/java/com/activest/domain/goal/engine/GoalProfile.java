package com.activest.domain.goal.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Instrumentacao temporaria do escopo D (docs/planos/PLANO_PERFIL_JAVA.md, E1): o tempo de cada etapa do
 * {@code GET /goals/}. Sai antes do commit — o numero fica no relatorio do perfil.
 */
public final class GoalProfile {

    private static final Logger LOG = LoggerFactory.getLogger(GoalProfile.class);
    private static final Map<String, long[]> TOTALS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Long> INICIO = new ThreadLocal<>();

    private GoalProfile() {
    }

    /** Abre o retrato da requisicao. */
    public static void begin() {
        INICIO.set(System.nanoTime());
        TOTALS.clear();
    }

    /** Cronometra um bloco e soma no total da fase. */
    public static <T> T time(String fase, Supplier<T> bloco) {
        long t = System.nanoTime();
        try {
            return bloco.get();
        } finally {
            add(fase, System.nanoTime() - t);
        }
    }

    /** Cronometra um bloco sem valor de retorno. */
    public static void time(String fase, Runnable bloco) {
        long t = System.nanoTime();
        try {
            bloco.run();
        } finally {
            add(fase, System.nanoTime() - t);
        }
    }

    /** Soma um tempo medido a fora (bloco ja cronometrado). */
    public static void add(String fase, long nanos) {
        long[] v = TOTALS.computeIfAbsent(fase, k -> new long[2]);
        v[0]++;
        v[1] += nanos;
    }

    /** Descarrega o retrato da requisicao no log (prefixo GOALPROF). */
    public static void dump(String tela) {
        Long inicio = INICIO.get();
        if (inicio == null) {
            return;
        }
        Map<String, long[]> copia = new LinkedHashMap<>(TOTALS);
        long total = System.nanoTime() - inicio;
        StringBuilder sb = new StringBuilder("GOALPROF tela=").append(tela)
                .append(" servico=").append(ms(total)).append("ms");
        copia.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .forEach(e -> sb.append(" | ").append(e.getKey())
                        .append("=").append(ms(e.getValue()[1])).append("ms")
                        .append("(n=").append(e.getValue()[0]).append(")"));
        LOG.info(sb.toString());
        TOTALS.clear();
        INICIO.remove();
    }

    private static long ms(long nanos) {
        return Math.round(nanos / 1_000_000.0);
    }
}
