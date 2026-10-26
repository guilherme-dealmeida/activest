package com.activest.domain.goal.type;

import java.util.List;

/**
 * Um campo que o usuario informa (G1: nunca um valor calculavel). {@code key} e onde o valor mora na requisicao:
 * {@code targetValue}, {@code targetDate} ou o nome dentro de {@code params}.
 *
 * @param requiredIf campo obrigatorio so quando outro campo tem um destes valores ("rule=MIN,RANGE"); nulo = sempre
 *                   que {@code required}
 */
public record FieldSpec(String key, String label, FieldKind kind, boolean required, Object defaultValue,
                        List<Option> options, String help, Double min, Double max, String requiredIf) {

    public record Option(String value, String label) {
    }

    public static final String TARGET_VALUE = "targetValue";
    public static final String TARGET_DATE = "targetDate";

    public boolean isColumn() {
        return TARGET_VALUE.equals(key) || TARGET_DATE.equals(key);
    }
}
