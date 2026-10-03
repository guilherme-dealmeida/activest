package com.primevest.adm.domain.pricehistory;

import com.primevest.adm.integration.b3.B3HistoricalQuotesClient;
import com.primevest.adm.integration.b3.B3Fixtures;
import com.primevest.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * F2 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: serie historica da B3 em asset_price_history,
 * com linhas reais de PETR4, PETR4F, HGLG11 e KLBN11.
 */
class HistoricalQuotesImportIT extends IntegrationTestBase {

    private static final String TICKERS = "('PETR4', 'PETR4F', 'HGLG11', 'KLBN11', 'KLBN11F')";

    @TempDir
    static Path tmp;

    @MockBean
    B3HistoricalQuotesClient client;

    @Autowired
    HistoricalQuotesImportService service;

    @BeforeEach
    void cenario() {
        jdbc.update("DELETE FROM asset_price_history WHERE asset_id IN (SELECT id FROM assets WHERE ticker IN " + TICKERS + ")");
        jdbc.update("UPDATE assets SET isin = NULL WHERE ticker IN " + TICKERS);
        when(client.year(anyInt())).thenReturn(Optional.empty());
        when(client.year(2026)).thenReturn(Optional.of(B3Fixtures.cotahistZip(tmp, 2026)));
    }

    private List<Map<String, Object>> linhas(String ticker) {
        return jdbc.queryForList("""
            SELECT h.reference_date, h.close_price, h.volume, h.source FROM asset_price_history h
            JOIN assets a ON a.id = h.asset_id WHERE a.ticker = ? ORDER BY h.reference_date""", ticker);
    }

    @Test
    @DisplayName("Grava os pregoes a vista e fracionario dos ativos do catalogo, com a B3 como origem")
    void importa() {
        HistoricalQuotesImportService.Result r = service.importYears(2025, 2026, "teste");

        // do mais recente para o mais antigo
        assertThat(r.years()).extracting(HistoricalQuotesImportService.YearResult::year).containsExactly(2026, 2025);
        assertThat(r.years()).extracting(HistoricalQuotesImportService.YearResult::found).containsExactly(true, false);
        assertThat(r.quotes()).isEqualTo(8);
        List<Map<String, Object>> petr4 = linhas("PETR4");
        assertThat(petr4).hasSize(2);
        assertThat((BigDecimal) petr4.get(1).get("close_price")).isEqualByComparingTo("47.99");
        assertThat(petr4.get(1).get("source")).isEqualTo("B3_COTAHIST");
        assertThat(linhas("PETR4F")).hasSize(2);
        assertThat(linhas("HGLG11")).hasSize(2);
        assertThat(linhas("KLBN11F")).hasSize(1);
        // ISIN do catalogo pelo pregao mais recente
        assertThat(jdbc.queryForObject("SELECT isin FROM assets WHERE ticker = 'PETR4'", String.class))
                .isEqualTo("BRPETRACNPR6");
        assertThat(jdbc.queryForObject("SELECT isin FROM assets WHERE ticker = 'HGLG11'", String.class))
                .isEqualTo("BRHGLGCTF004");
    }

    @Test
    @DisplayName("Ticker antigo com o mesmo ISIN entra no ativo atual, sem passar por cima do pregao do ticker atual")
    void tickerAntigoPeloIsin() {
        // PETR9 finge ser o ticker de antes de uma renomeacao do PETR4 (mesmo ISIN):
        // um pregao no mesmo dia do PETR4 (fica o do PETR4) e um em dia sem PETR4 (entra)
        String petr4 = B3Fixtures.cotahistLine("012026092502PETR4 ");
        String mesmoDia = petr4.substring(0, 12) + "PETR9       " + petr4.substring(24, 108)
                + "0000000009999" + petr4.substring(121);
        String outroDia = "0120260923" + mesmoDia.substring(10);
        when(client.year(2026)).thenReturn(Optional.of(B3Fixtures.cotahistZip(tmp, 2026, mesmoDia, outroDia)));

        HistoricalQuotesImportService.Result r = service.importYears(2026, 2026, "teste");

        assertThat(r.years().get(0).byIsin()).isEqualTo(2);
        List<Map<String, Object>> linhas = linhas("PETR4");
        assertThat(linhas).hasSize(3);
        assertThat((BigDecimal) linhas.get(0).get("close_price")).isEqualByComparingTo("99.99"); // 23/09, pelo ISIN
        assertThat((BigDecimal) linhas.get(2).get("close_price")).isEqualByComparingTo("47.99"); // 25/09, o proprio
    }

    @Test
    @DisplayName("Catalogo so com o fracionario: o pregao do lote padrao nao cai nele pelo ISIN")
    void fracionarioNaoRecebeLotePadrao() {
        // visto na carga real: CALI3F no catalogo sem CALI3. Aqui, KLBN11 fora do
        // catalogo por um instante e KLBN11F ja com o ISIN (o mesmo da unit)
        jdbc.update("UPDATE assets SET isin = 'BRKLBNCDAM18' WHERE ticker = 'KLBN11F'");
        jdbc.update("UPDATE assets SET ticker = 'KLBN11X' WHERE ticker = 'KLBN11'");
        try {
            HistoricalQuotesImportService.Result r = service.importYears(2026, 2026, "teste");

            assertThat(r.years().get(0).byIsin()).isZero();
            assertThat(linhas("KLBN11F")).hasSize(1); // so o proprio pregao fracionario
            assertThat((BigDecimal) linhas("KLBN11F").get(0).get("close_price")).isEqualByComparingTo("18.10");
        } finally {
            jdbc.update("UPDATE assets SET ticker = 'KLBN11' WHERE ticker = 'KLBN11X'");
        }
    }

    @Test
    @DisplayName("Rodar de novo nao duplica e a serie da B3 substitui o que a BRAPI gravou no dia")
    void idempotenteESubstituiBrapi() {
        jdbc.update("""
            INSERT INTO asset_price_history (asset_id, reference_date, close_price, source)
            SELECT id, DATE '2026-09-25', 50.00, 'BRAPI' FROM assets WHERE ticker = 'PETR4'""");

        service.importYears(2026, 2026, "teste");
        HistoricalQuotesImportService.Result r = service.importYears(2026, 2026, "teste");

        assertThat(linhas("PETR4")).hasSize(2);
        Map<String, Object> dia = linhas("PETR4").get(1);
        assertThat((BigDecimal) dia.get("close_price")).isEqualByComparingTo("47.99");
        assertThat(dia.get("source")).isEqualTo("B3_COTAHIST");
        // na segunda rodada o ISIN ja estava certo
        assertThat(r.isinsUpdated()).isZero();
    }
}
