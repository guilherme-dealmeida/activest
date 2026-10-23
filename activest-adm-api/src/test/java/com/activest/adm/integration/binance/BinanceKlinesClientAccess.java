package com.activest.adm.integration.binance;

import java.util.List;

/** Acesso de teste ao parser (pacote do cliente). */
public final class BinanceKlinesClientAccess {

    private BinanceKlinesClientAccess() {
    }

    public static List<BinanceKlinesClient.DailyCandle> parse(String body) {
        return BinanceKlinesClient.parse(body);
    }
}
