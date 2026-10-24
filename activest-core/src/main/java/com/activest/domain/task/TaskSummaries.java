package com.activest.domain.task;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Resumo de uma rodada a partir do resultado que a rotina já devolve (os records {@code Result} dos services): os
 * números da linha de conclusão, em {@code nome=valor}. Coleções aparecem pelo tamanho (a lista inteira fica no log).
 */
public final class TaskSummaries {

    private static final int MAX = 2_000;

    private TaskSummaries() {
    }

    public static String of(Object result) {
        if (result == null) {
            return null;
        }
        String text;
        if (result.getClass().isRecord()) {
            StringJoiner j = new StringJoiner(", ");
            for (RecordComponent c : result.getClass().getRecordComponents()) {
                j.add(c.getName() + "=" + render(value(c, result)));
            }
            text = j.toString();
        } else {
            text = String.valueOf(result);
        }
        return text.length() > MAX ? text.substring(0, MAX) + "…" : text;
    }

    /** Junta os resumos das partes de uma rotina composta ("balanços: ...; informe FII: ..."). */
    public static String join(Map<String, Object> parts) {
        StringJoiner j = new StringJoiner("; ");
        parts.forEach((label, result) -> {
            if (result != null) {
                j.add(label + ": " + of(result));
            }
        });
        String s = j.toString();
        return s.isEmpty() ? null : s;
    }

    private static Object value(RecordComponent c, Object record) {
        try {
            return c.getAccessor().invoke(record);
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }

    private static String render(Object v) {
        if (v instanceof Collection<?> c) {
            return String.valueOf(c.size());
        }
        if (v instanceof Map<?, ?> m) {
            return m.toString();
        }
        return String.valueOf(v);
    }
}
