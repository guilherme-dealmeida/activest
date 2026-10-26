package com.activest.domain.goal.engine;

import com.activest.domain.goal.type.ScopeKind;

import java.util.List;

/**
 * As posicoes que a meta enxerga (docs/planos/PLANO_METAS.md, Anexo A 3): uma forma ({@link ScopeKind}) com os
 * valores escolhidos, a reserva de emergencia (com, sem ou so ela) e as exclusoes de tipo e categoria.
 *
 * <p>Valores por forma: TYPE = nome do tipo ({@code ACAO}); CATEGORY = nome de exibicao da categoria; INDEXER =
 * CDI, SELIC, IPCA, IGPM, INPC, PREFIXADO ou RENDA_VARIAVEL; ISSUER = nome do emissor (o grupo, na renda fixa);
 * ASSET = ticker; POSITIONS = id da posicao.</p>
 */
public record GoalScope(ScopeKind kind, List<String> values, Reservation reservation, List<String> excludeTypes,
                        List<String> excludeCategories) {

    public enum Reservation {
        WITH,
        WITHOUT,
        ONLY
    }

    public GoalScope {
        kind = kind != null ? kind : ScopeKind.ALL;
        values = values != null ? List.copyOf(values) : List.of();
        excludeTypes = excludeTypes != null ? List.copyOf(excludeTypes) : List.of();
        excludeCategories = excludeCategories != null ? List.copyOf(excludeCategories) : List.of();
    }

    public static GoalScope all(Reservation reservation) {
        return new GoalScope(ScopeKind.ALL, List.of(), reservation, List.of(), List.of());
    }

    /** A posicao entra no escopo? */
    public boolean includes(PositionInfo p) {
        if (reservation == Reservation.WITHOUT && p.reservation()) {
            return false;
        }
        if (reservation == Reservation.ONLY && !p.reservation()) {
            return false;
        }
        if (excludeTypes.stream().anyMatch(t -> t.equalsIgnoreCase(p.typeName()))) {
            return false;
        }
        if (p.categoryName() != null && excludeCategories.stream().anyMatch(c -> c.equalsIgnoreCase(p.categoryName()))) {
            return false;
        }
        if (kind == ScopeKind.ALL) {
            return true;
        }
        String key = p.key(kind);
        return key != null && values.stream().anyMatch(v -> v.equalsIgnoreCase(key));
    }

    /** Frase curta para a tela ("Carteira total, sem a reserva"). */
    public String describe() {
        StringBuilder sb = new StringBuilder(kind == ScopeKind.ALL ? "Carteira total" : kind.label() + ": "
                + String.join(", ", kind == ScopeKind.POSITIONS ? List.of(values.size() + " posição(ões)") : values));
        if (reservation == Reservation.WITHOUT) {
            sb.append(", sem a reserva de emergência");
        } else if (reservation == Reservation.ONLY) {
            sb.append(", só a reserva de emergência");
        } else if (reservation == Reservation.WITH) {
            sb.append(", com a reserva de emergência");
        }
        if (!excludeTypes.isEmpty()) {
            sb.append(", exceto ").append(String.join(", ", excludeTypes));
        }
        if (!excludeCategories.isEmpty()) {
            sb.append(", exceto ").append(String.join(", ", excludeCategories));
        }
        return sb.toString();
    }
}
