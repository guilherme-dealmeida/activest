package com.primevest.adm.domain.pricehistory;

import com.primevest.adm.integration.tesouro.TesouroPriceClient;
import com.primevest.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * R1 do docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md: PU de venda do Tesouro com um recorte real do
 * PrecoTaxaTesouroDireto.csv (02/01/2025, 01/10 e 02/10/2026 de quatro titulos), mais uma linha de titulo que o
 * catalogo nao tem e uma de 2011 sem PU de venda (fica o PU base).
 */
class TreasuryPriceIT extends IntegrationTestBase {

    @TempDir
    static Path tmp;

    @MockBean
    TesouroPriceClient client;

    @Autowired
    TreasuryPriceService service;

    @BeforeEach
    void arquivo() throws Exception {
        Path file = tmp.resolve("PrecoTaxaTesouroDireto.csv");
        try (InputStream in = getClass().getResourceAsStream("/tesouro/PrecoTaxaTesouroDireto.csv")) {
            Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        when(client.fetch()).thenReturn(file);
    }

    /** O catalogo e compartilhado entre os testes: o preco do Tesouro nao pode vazar para outro cenario. */
    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM asset_price_history WHERE source = 'TESOURO'");
        jdbc.update("UPDATE assets SET current_price = NULL WHERE asset_type_id = "
                + "(SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO')");
    }

    private BigDecimal currentPrice(String ticker) {
        return jdbc.queryForObject("SELECT current_price FROM assets WHERE ticker = ?", BigDecimal.class, ticker);
    }

    private int days(String ticker) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM asset_price_history h JOIN assets a ON a.id = h.asset_id "
                + "WHERE a.ticker = ? AND h.source = 'TESOURO'", Integer.class, ticker);
    }

    @Test
    @DisplayName("PU de venda por titulo, casado por tipo + vencimento (Renda+ 2065 vence em 2084); o ultimo vira o preco atual")
    void carga() {
        TreasuryPriceService.Result r = service.importFrom(2010, "teste");

        assertThat(r.titles()).isEqualTo(4);
        assertThat(r.rows()).isEqualTo(12);
        assertThat(r.lastDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 2));
        assertThat(r.priced()).isEqualTo(4);
        assertThat(r.unmatched()).containsExactly("Tesouro Inexistente 2099-01-01");

        assertThat(currentPrice("Tesouro IPCA+ 2029")).isEqualByComparingTo("3936.95");
        assertThat(currentPrice("Tesouro Renda+ Aposentadoria Extra 2065")).isEqualByComparingTo("201.18");
        assertThat(days("Tesouro IPCA+ 2029")).isEqualTo(3);
        // 2011 sem PU de venda: fica o PU base
        assertThat(days("Tesouro Selic 2028")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT h.close_price FROM asset_price_history h JOIN assets a ON a.id = h.asset_id "
                + "WHERE a.ticker = 'Tesouro Selic 2028' AND h.reference_date = '2011-01-03'", BigDecimal.class))
                .isEqualByComparingTo("4567.89");
    }

    @Test
    @DisplayName("Idempotente: a segunda rodada nao grava nada nem muda preco; o inicio filtra os dias")
    void idempotente() {
        service.importFrom(2010, "teste");

        TreasuryPriceService.Result de = service.importFrom(2010, "teste");
        assertThat(de.rows()).isZero();
        assertThat(de.priced()).isZero();

        jdbc.update("DELETE FROM asset_price_history WHERE source = 'TESOURO'");
        TreasuryPriceService.Result desde2025 = service.importFrom(2025, "teste");
        assertThat(desde2025.rows()).isEqualTo(11);
    }

    @Test
    @DisplayName("Rodada diaria sem nada gravado faz a carga desde 2010; depois so rele os ultimos dias")
    void diaria() {
        assertThat(service.needsRecovery()).isTrue();

        TreasuryPriceService.Result r = service.importRecent("teste");

        assertThat(r.rows()).isEqualTo(12);
        assertThat(service.importRecent("teste").rows()).isZero();
    }
}
