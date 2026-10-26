package com.activest.domain.goal.engine;

import com.activest.domain.goal.GoalType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** O que o usuario definiu, ja validado pelo catalogo: o que a avaliacao precisa (a meta gravada ou um preview). */
public record GoalDefinition(GoalType type, String name, BigDecimal targetValue, LocalDate targetDate,
                             GoalScope scope, Map<String, Object> params, LocalDate createdOn) {

    public String text(String key) {
        Object v = params.get(key);
        return v != null ? v.toString() : null;
    }

    public Double number(String key) {
        Object v = params.get(key);
        return v == null ? null : Double.valueOf(v.toString());
    }

    public Integer integer(String key) {
        Object v = params.get(key);
        return v == null ? null : (int) Math.round(Double.parseDouble(v.toString()));
    }

    public LocalDate date(String key) {
        Object v = params.get(key);
        return v == null ? null : LocalDate.parse(v.toString());
    }

    public boolean flag(String key) {
        Object v = params.get(key);
        return v != null && Boolean.parseBoolean(v.toString());
    }

    public double target() {
        return targetValue != null ? targetValue.doubleValue() : 0;
    }
}
