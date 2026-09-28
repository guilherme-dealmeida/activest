package com.primevest.domain.integration.source;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * O que uma busca na fonte trouxe e ate onde ela chegou.
 *
 * @param movements movimentos normalizados, em qualquer ordem
 * @param cursors   posicao nova de cada fluxo ('spot:BTCBRL' -> ultimo id),
 *                  gravada em integration_cursors so depois de a importacao
 *                  ser confirmada
 * @param balances  saldo de cada ativo na fonte no momento da busca
 *                  ('BTC' -> 0.00831073), para o aviso de divergencia (E3)
 */
public record FetchResult(List<ImportedMovement> movements, Map<String, String> cursors,
                          Map<String, BigDecimal> balances) {

    public FetchResult {
        movements = List.copyOf(movements);
        cursors = Map.copyOf(cursors);
        balances = balances == null ? Map.of() : Map.copyOf(balances);
    }

    public FetchResult(List<ImportedMovement> movements, Map<String, String> cursors) {
        this(movements, cursors, Map.of());
    }
}
