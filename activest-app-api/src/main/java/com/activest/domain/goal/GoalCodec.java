package com.activest.domain.goal;

import com.activest.domain.goal.engine.GoalDefinition;
import com.activest.domain.goal.engine.GoalScope;
import com.activest.domain.goal.type.ScopeKind;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON das colunas da meta (escopo, parametros, resultado) e a {@link GoalDefinition} de uma meta gravada. */
@Component
public class GoalCodec {

    private final ObjectMapper json;

    public GoalCodec(ObjectMapper json) {
        this.json = json;
    }

    public String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Map<String, Object> map(String s) {
        if (s == null) {
            return null;
        }
        if (s.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(s, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public GoalDefinition definition(Goal g, LocalDate today) {
        LocalDate created = g.getCreatedAt() != null ? g.getCreatedAt().toLocalDate() : today;
        Map<String, Object> params = map(g.getParams());
        return new GoalDefinition(g.getType(), g.getName(), g.getTargetValue(), g.getTargetDate(), scope(map(g.getScope())),
                params != null ? params : Map.of(), created);
    }

    public static Map<String, Object> scopeMap(GoalScope s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", s.kind().name());
        m.put("values", s.values());
        m.put("reservation", s.reservation() != null ? s.reservation().name() : null);
        m.put("excludeTypes", s.excludeTypes());
        m.put("excludeCategories", s.excludeCategories());
        return m;
    }

    @SuppressWarnings("unchecked")
    public static GoalScope scope(Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return GoalScope.all(null);
        }
        ScopeKind kind = m.get("kind") != null ? ScopeKind.valueOf(m.get("kind").toString()) : ScopeKind.ALL;
        GoalScope.Reservation r = m.get("reservation") != null
                ? GoalScope.Reservation.valueOf(m.get("reservation").toString()) : null;
        return new GoalScope(kind, (List<String>) m.getOrDefault("values", List.of()), r,
                (List<String>) m.getOrDefault("excludeTypes", List.of()),
                (List<String>) m.getOrDefault("excludeCategories", List.of()));
    }
}
