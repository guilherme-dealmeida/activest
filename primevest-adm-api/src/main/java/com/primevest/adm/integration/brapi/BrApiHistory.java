package com.primevest.adm.integration.brapi;

import java.util.List;

/**
 * Historico diario de um ativo ({@code GET /api/v2/stocks/historical}).
 *
 * @param requestedSymbol o ticker pedido
 * @param symbol          o ticker atual — difere do pedido quando houve renome
 * @param changed         a BRAPI resolveu um renome (BRML3 → ALOS3). O catalogo
 *                        NAO e trocado sozinho: a rotina so registra no log.
 * @param candles         pregoes, do mais recente para o mais antigo
 */
public record BrApiHistory(String requestedSymbol, String symbol, boolean changed, List<BrApiCandle> candles) {
}
