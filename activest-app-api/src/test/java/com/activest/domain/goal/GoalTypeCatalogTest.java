package com.activest.domain.goal;

import com.activest.domain.goal.engine.GoalScope;
import com.activest.domain.goal.type.FieldSpec;
import com.activest.domain.goal.type.GoalTypeCatalog;
import com.activest.domain.goal.type.GoalTypeSpec;
import com.activest.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Catalogo de tipos e validacao do cadastro (docs/planos/PLANO_METAS.md, Anexo A 4, 5 e cenario 17). */
class GoalTypeCatalogTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    @Test
    @DisplayName("Cenário 17: os 17 tipos existem e nenhum declara campo calculável (valor atual, progresso, falta...)")
    void contract() {
        assertThat(GoalTypeCatalog.all()).hasSize(17);
        for (GoalTypeSpec s : GoalTypeCatalog.all()) {
            for (FieldSpec f : s.fields()) {
                assertThat(GoalTypeCatalog.CALCULATED).doesNotContain(f.key());
                assertThat(f.key().toLowerCase()).doesNotContain("current").doesNotContain("progress")
                        .doesNotContain("remaining").doesNotContain("estimated");
            }
        }
    }

    @Test
    @DisplayName("Cenário 17: mandar um campo calculável ou que o tipo não declara é 400")
    void rejectsCalculatedAndUnknown() {
        assertThatThrownBy(() -> validate(GoalType.PAT_1, "1000", Map.of("reservation", "WITH"), Map.of("currentValue", 10)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("calculado");
        assertThatThrownBy(() -> validate(GoalType.PAT_1, "1000", Map.of("reservation", "WITH"), Map.of("foo", 1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("não aceita");
        assertThatThrownBy(() -> GoalTypeCatalog.validate(GoalType.ALO_2, null, TODAY.plusDays(10), Map.of(), Map.of(),
                TODAY, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("D7: patrimônio exige a escolha explícita da reserva; alocação nasce sem a reserva")
    void reservation() {
        assertThatThrownBy(() -> validate(GoalType.PAT_1, "1000", Map.of(), Map.of()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("reserva");
        assertThat(validate(GoalType.PAT_1, "1000", Map.of("reservation", "WITHOUT"), Map.of()).scope().reservation())
                .isEqualTo(GoalScope.Reservation.WITHOUT);
        assertThat(validate(GoalType.RIS_1, "20", Map.of(), Map.of("dimension", "ASSET")).scope().reservation())
                .isEqualTo(GoalScope.Reservation.WITHOUT);
    }

    @Test
    @DisplayName("Cenário 16: alvo zero ou negativo é recusado; prazo no passado também")
    void bounds() {
        assertThatThrownBy(() -> validate(GoalType.PAT_1, "0", Map.of("reservation", "WITH"), Map.of()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validate(GoalType.PAT_1, "-5", Map.of("reservation", "WITH"), Map.of()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> GoalTypeCatalog.validate(GoalType.PAT_1, new BigDecimal("100"), TODAY.minusDays(1),
                Map.of("reservation", "WITH"), Map.of(), TODAY, null)).isInstanceOf(BusinessException.class);
        // o mesmo prazo, ja vencido, continua valendo na edicao
        assertThat(GoalTypeCatalog.validate(GoalType.PAT_1, new BigDecimal("100"), TODAY.minusDays(1),
                Map.of("reservation", "WITH"), Map.of(), TODAY, TODAY.minusDays(1)).targetDate()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    @DisplayName("Padrões aplicados e campos condicionais: ALO-1 em faixa pede mínimo e máximo, e mínimo ≤ máximo")
    void defaultsAndConditionals() {
        var v = validate(GoalType.REN_1, "500", Map.of(), Map.of());
        assertThat(v.params()).containsEntry("unit", "MONTH").containsEntry("window", 12);
        assertThatThrownBy(() -> validate(GoalType.ALO_1, null, Map.of(), Map.of("dimension", "TYPE", "item", "FII",
                "rule", "RANGE", "min", 10))).isInstanceOf(BusinessException.class).hasMessageContaining("máximo");
        assertThatThrownBy(() -> validate(GoalType.ALO_1, null, Map.of(), Map.of("dimension", "TYPE", "item", "FII",
                "rule", "RANGE", "min", 30, "max", 10))).isInstanceOf(BusinessException.class);
        assertThat(validate(GoalType.ALO_1, null, Map.of(), Map.of("dimension", "TYPE", "item", "FII", "rule", "MIN",
                "min", 25)).params()).containsEntry("min", new BigDecimal("25"));
        assertThatThrownBy(() -> validate(GoalType.RNT_2, null, Map.of(), Map.of("index", "CDI", "form", "PLUS_RATE",
                "rate", 5))).isInstanceOf(BusinessException.class).hasMessageContaining("IPCA");
        assertThatThrownBy(() -> validate(GoalType.APO_2, "100", Map.of(), Map.of("periodicity", "WEEK")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("opção");
    }

    @Test
    @DisplayName("Escopo: forma não aceita pelo tipo e lista vazia são recusadas; o ativo é o id e o rótulo é ignorado")
    void scope() {
        assertThatThrownBy(() -> validate(GoalType.PAT_1, "1000", Map.of("kind", "TYPE", "reservation", "WITH"), Map.of()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ao menos um");
        assertThatThrownBy(() -> validate(GoalType.ALO_1, null, Map.of("kind", "TYPE", "values", java.util.List.of("FII")),
                Map.of("dimension", "TYPE", "item", "FII", "rule", "MIN", "min", 5)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("não aceita o escopo");
        // V13: o ativo da meta é o id; ticker e tipo são rótulos que o servidor grava (aceitos e ignorados na entrada)
        assertThat(validate(GoalType.ATV_1, "10", Map.of(), Map.of("assetId", "42", "ticker", "PETR4")).params())
                .containsEntry("assetId", 42L).doesNotContainKey("ticker");
        assertThatThrownBy(() -> validate(GoalType.ATV_1, "10", Map.of(), Map.of("ticker", "PETR4")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Informe ativo");
        assertThatThrownBy(() -> validate(GoalType.ATV_1, "10", Map.of(), Map.of("assetId", "PETR4")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("valor inválido");
    }

    private static GoalTypeCatalog.Validated validate(GoalType type, String target, Map<String, Object> scope,
                                                      Map<String, Object> params) {
        return GoalTypeCatalog.validate(type, target != null ? new BigDecimal(target) : null, null, scope, params, TODAY,
                null);
    }
}
