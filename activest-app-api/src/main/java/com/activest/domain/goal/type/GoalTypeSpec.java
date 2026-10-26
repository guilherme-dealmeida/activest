package com.activest.domain.goal.type;

import com.activest.domain.goal.GoalType;

import java.util.List;

/**
 * O que o catalogo diz de um tipo (docs/planos/PLANO_METAS.md, Anexo A 11, {@code GET /goals/types}): campos que o
 * usuario informa, escopos aceitos, regra da reserva, o que o sistema calcula, dados que usa e limitacoes.
 */
public record GoalTypeSpec(GoalType type, String description, List<FieldSpec> fields, List<ScopeKind> scopes,
                           ReservationRule reservation, List<String> calculates, List<String> data,
                           List<String> limitations) {

    public FieldSpec field(String key) {
        return fields.stream().filter(f -> f.key().equals(key)).findFirst().orElse(null);
    }
}
