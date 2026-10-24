package com.activest.domain.task;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * Validação e leitura dos valores de atributo pelo {@link TaskValueType} (regra pura). Valor vazio = sem valor; a
 * obrigatoriedade é conferida por quem chama ({@link TaskCatalogService} e o executor). Mínimo, máximo e códigos
 * válidos ficam no handler de cada tipo.
 */
public final class TaskAttributeValues {

    private TaskAttributeValues() {
    }

    /** Valor normalizado (aparado; vazio vira {@code null}). */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.strip();
        return v.isEmpty() ? null : v;
    }

    /** Mensagem do erro, ou {@code null} se o valor serve ao tipo. Valor vazio sempre serve. */
    public static String validate(TaskValueType type, String raw) {
        String v = normalize(raw);
        if (v == null) {
            return null;
        }
        return switch (type) {
            case YEAR -> v.matches("\\d{4}") ? null : "ano com 4 dígitos";
            case DATE -> {
                try {
                    LocalDate.parse(v);
                    yield null;
                } catch (DateTimeParseException e) {
                    yield "data no formato aaaa-mm-dd";
                }
            }
            case BOOLEAN -> "true".equals(v) || "false".equals(v) ? null : "true ou false";
            case INTEGER -> v.matches("-?\\d{1,9}") ? null : "número inteiro";
            case TEXT, LIST -> null;
        };
    }

    public static Integer asYear(String raw) {
        return asInteger(raw);
    }

    public static Integer asInteger(String raw) {
        String v = normalize(raw);
        return v == null ? null : Integer.valueOf(v);
    }

    public static LocalDate asDate(String raw) {
        String v = normalize(raw);
        return v == null ? null : LocalDate.parse(v);
    }

    /** {@code null} para vazio — quem chama decide o padrão. */
    public static Boolean asBoolean(String raw) {
        String v = normalize(raw);
        return v == null ? null : Boolean.valueOf(v);
    }

    /** Itens aparados e sem vazios; vazio = lista vazia. */
    public static List<String> asList(String raw) {
        String v = normalize(raw);
        if (v == null) {
            return List.of();
        }
        return Arrays.stream(v.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
