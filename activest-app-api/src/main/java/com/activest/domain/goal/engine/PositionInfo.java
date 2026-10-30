package com.activest.domain.goal.engine;

import com.activest.domain.goal.type.ScopeKind;

import java.util.UUID;

/**
 * Identidade de uma posicao da carteira (aberta ou encerrada) para o escopo das metas: tipo, categoria, indexador,
 * emissor, ticker e reserva.
 *
 * @param indexer CDI, SELIC, IPCA, IGPM, INPC, PREFIXADO (renda fixa e Tesouro) ou RENDA_VARIAVEL
 * @param issuer  nome do emissor: o grupo da emissora na renda fixa, a companhia/fundo do catalogo nos demais
 */
public record PositionInfo(UUID id, Long assetId, String ticker, String typeName, String typeDescription,
                           String categoryName, String indexer, String issuer, boolean reservation, boolean fixedIncome,
                           boolean settled) {

    public static final String VARIABLE_INCOME = "RENDA_VARIAVEL";
    public static final String PREFIXED = "PREFIXADO";

    /** O valor desta posicao na dimensao. */
    public String key(ScopeKind kind) {
        return switch (kind) {
            case ALL -> "ALL";
            case TYPE -> typeName;
            case CATEGORY -> categoryName;
            case INDEXER -> indexer;
            case ISSUER -> issuer;
            case ASSET -> assetId != null ? assetId.toString() : null;
            case POSITIONS -> id.toString();
        };
    }

    /** O rotulo da posicao na dimensao (o tipo pela descricao). */
    public String label(ScopeKind kind) {
        if (kind == ScopeKind.TYPE) {
            return typeDescription != null ? typeDescription : typeName;
        }
        if (kind == ScopeKind.ASSET) {
            return ticker;
        }
        if (kind == ScopeKind.INDEXER) {
            return switch (indexer == null ? "" : indexer) {
                case VARIABLE_INCOME -> "Renda variável";
                case PREFIXED -> "Prefixado";
                case "IGPM" -> "IGP-M";
                default -> indexer;
            };
        }
        String k = key(kind);
        return k != null ? k : "Sem " + kind.label().toLowerCase();
    }
}
