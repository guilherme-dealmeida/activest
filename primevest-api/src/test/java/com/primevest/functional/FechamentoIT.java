package com.primevest.functional;

import com.primevest.domain.pricehistory.ClosingPriceService;
import com.primevest.domain.user.User;
import com.primevest.integration.brapi.BrApiCandle;
import com.primevest.integration.brapi.BrApiException;
import com.primevest.integration.brapi.BrApiHistory;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rotina de fechamento (PLANO_BRAPI.md, secao 3, F2 e F4): so ativos em
 * carteira, 1 por chamada, OHLCV no historico e ultimo fechamento no preco.
 */
class FechamentoIT extends FuncionalBase {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired private ClosingPriceService closingPriceService;

    private User dono;

    @BeforeEach
    void cenario() {
        reset(brApiClient, coinGeckoClient);
        dono = usuarioComCarteira("fechamento@primevest.test");
        when(brApiClient.hasToken()).thenReturn(true);
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN (SELECT id FROM assets WHERE ticker IN ('PETR4', 'HGLG11', 'BTC'))");
        jdbc.update("UPDATE assets SET current_price = NULL, price_updated_at = NULL WHERE ticker IN ('PETR4', 'HGLG11', 'BTC')");
    }

    private static long epoch(LocalDate day) {
        return day.atStartOfDay(SP).toEpochSecond();
    }

    private static BrApiHistory historico(String ticker, String simbolo, boolean mudou) {
        LocalDate hoje = LocalDate.now(SP);
        return new BrApiHistory(ticker, simbolo, mudou, List.of(
            new BrApiCandle(epoch(hoje), new BigDecimal("48.55"), new BigDecimal("50.06"), new BigDecimal("48.52"),
                new BigDecimal("49.60"), 43570600L, new BigDecimal("49.60")),
            new BrApiCandle(epoch(hoje.minusDays(1)), new BigDecimal("47.56"), new BigDecimal("48.54"),
                new BigDecimal("47.30"), new BigDecimal("48.35"), 48852400L, new BigDecimal("48.35"))));
    }

    @Test
    @DisplayName("So ativos em carteira, 1 por chamada: grava OHLCV dos pregoes e poe o ultimo fechamento no preco")
    void soCarteira() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "45.00"));
        lancarComSucesso(dono, compra(CatalogIds.FII, "2026-09-01", "10", "160.00"));
        when(brApiClient.fetchDailyHistory(eq("PETR4"), anyString())).thenReturn(historico("PETR4", "PETR4", false));
        when(brApiClient.fetchDailyHistory(eq("HGLG11"), anyString()))
            .thenThrow(new BrApiException(404, "NOT_FOUND", "sem cotacao", null));

        ClosingPriceService.RoundResult r = closingPriceService.runB3Closing("teste");

        assertThat(r.assets()).isEqualTo(2);
        assertThat(r.updated()).isEqualTo(1);
        verify(brApiClient, times(2)).fetchDailyHistory(anyString(), anyString());
        // Sem historico gravado: carga inicial de 3 meses (F4).
        verify(brApiClient).fetchDailyHistory("PETR4", "3mo");
        List<Map<String, Object>> linhas = jdbc.queryForList("""
            SELECT h.open_price, h.close_price, h.volume FROM asset_price_history h
            JOIN assets a ON a.id = h.asset_id WHERE a.ticker = 'PETR4' ORDER BY h.reference_date""");
        assertThat(linhas).hasSize(2);
        assertThat(numero(linhas.get(1).get("close_price"))).isEqualByComparingTo("49.60");
        assertThat(jdbc.queryForObject("SELECT current_price FROM assets WHERE ticker = 'PETR4'", BigDecimal.class))
            .isEqualByComparingTo("49.60");
        // A valorizacao da posicao acompanha o preco novo.
        assertThat(numero(posicaoDe(dono, CatalogIds.ACAO).get("valorization"))).isEqualByComparingTo("46.00");
    }

    @Test
    @DisplayName("Com historico em dia, a rodada seguinte pede so 5 dias")
    void segundaRodadaCincoDias() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "45.00"));
        when(brApiClient.fetchDailyHistory(eq("PETR4"), anyString())).thenReturn(historico("PETR4", "PETR4", false));

        closingPriceService.runB3Closing("teste");
        closingPriceService.runB3Closing("teste");

        verify(brApiClient).fetchDailyHistory("PETR4", "5d");
    }

    @Test
    @DisplayName("Ticker renomeado: so registra; o catalogo nao e trocado sozinho")
    void renomeNaoTroca() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "45.00"));
        when(brApiClient.fetchDailyHistory(eq("PETR4"), anyString())).thenReturn(historico("PETR4", "PETR9", true));

        closingPriceService.runB3Closing("teste");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE ticker = 'PETR4'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE ticker = 'PETR9'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("401/403: a rodada para no primeiro ativo, sem insistir nos outros")
    void tokenInvalidoPara() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "45.00"));
        lancarComSucesso(dono, compra(CatalogIds.FII, "2026-09-01", "10", "160.00"));
        when(brApiClient.fetchDailyHistory(eq("HGLG11"), anyString()))
            .thenThrow(new BrApiException(401, "UNAUTHORIZED", "token", null));

        ClosingPriceService.RoundResult r = closingPriceService.runB3Closing("teste");

        assertThat(r.aborted()).isTrue();
        verify(brApiClient, never()).fetchDailyHistory(eq("PETR4"), anyString());
    }

    @Test
    @DisplayName("Posicao vendida por inteiro nao entra na rodada")
    void posicaoZeradaFora() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "45.00"));
        lancarComSucesso(dono, venda(CatalogIds.ACAO, "2026-09-02", "10", "46.00"));

        ClosingPriceService.RoundResult r = closingPriceService.runB3Closing("teste");

        assertThat(r.assets()).isZero();
        verify(brApiClient, never()).fetchDailyHistory(anyString(), anyString());
    }

    @Test
    @DisplayName("Recuperacao: sem o ultimo fechamento, a subida roda a rodada; depois dela, nada a recuperar")
    void recuperacao() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "45.00"));
        when(brApiClient.fetchDailyHistory(eq("PETR4"), anyString())).thenReturn(historico("PETR4", "PETR4", false));

        assertThat(closingPriceService.b3NeedsRecovery()).isTrue();
        closingPriceService.runB3Closing("recuperacao");
        assertThat(closingPriceService.b3NeedsRecovery()).isFalse();
    }

    @Test
    @DisplayName("Cripto: preco em reais pelo id da CoinGecko e fechamento do dia no historico")
    void cripto() {
        lancarComSucesso(dono, compra("BTC", "2026-09-01", "0.01", "300000.00"));
        when(coinGeckoClient.fetchPricesInBrl(any())).thenReturn(Map.of("bitcoin", new BigDecimal("350000.00")));

        ClosingPriceService.RoundResult r = closingPriceService.runCryptoClosing("teste");

        assertThat(r.updated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_price FROM assets WHERE ticker = 'BTC'", BigDecimal.class))
            .isEqualByComparingTo("350000.00");
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM asset_price_history h JOIN assets a ON a.id = h.asset_id
            WHERE a.ticker = 'BTC' AND h.reference_date = ?""", Integer.class, LocalDate.now(SP))).isEqualTo(1);
    }
}
