package com.primevest.domain.integration.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * {@link ImportedMovement} em JSON e de volta. E o formato de
 * {@code integration_run_items.raw} e de {@code transactions.source_snapshot}:
 * o movimento normalizado, com o bruto da fonte dentro — o suficiente para
 * reaplicar as regras na confirmacao e no "restaurar" (regra E4).
 */
@Component
public class MovementJson {

    private final ObjectMapper mapper;

    public MovementJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(ImportedMovement m) {
        try {
            return mapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o movimento " + m.externalId(), e);
        }
    }

    public ImportedMovement read(String json) {
        try {
            return mapper.readValue(json, ImportedMovement.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Movimento gravado em formato inesperado.", e);
        }
    }
}
