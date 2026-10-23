package com.primevest.adm.domain.pricehistory;

import com.primevest.adm.integration.PublicFileCache;
import com.primevest.adm.integration.treasury.TreasuryPriceClient;
import com.primevest.adm.integration.treasury.TreasurySalesClient;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    TreasuryPriceClient client;

    @MockBean
    TreasurySalesClient salesClient;

    @Autowired
    TreasuryPriceService service;

    @BeforeEach
    void filePath() throws Exception {
        when(client.fetch()).thenReturn(ok(copy("PrecoTaxaTesouroDireto.csv")));
        when(salesClient.fetch()).thenReturn(ok(copy("VendasTesouroDireto.csv")));
    }

    private static PublicFileCache.Result ok(Path file) {
        return new PublicFileCache.Result(PublicFileCache.Outcome.DOWNLOADED, file, null, 200, null);
    }

    private static PublicFileCache.Result refused(Path file) {
        return new PublicFileCache.Result(PublicFileCache.Outcome.REFUSED, file, "HTTP 429, Retry-After 30", 429, "30");
    }

    private int notices(String kind, String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = ? "
                + "AND status IS NOT DISTINCT FROM CAST(? AS VARCHAR)", Integer.class, kind, status);
    }

    private Path copy(String name) throws Exception {
        Path file = tmp.resolve(name);
        try (InputStream in = getClass().getResourceAsStream("/treasury/" + name)) {
            Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    /** O catalogo e compartilhado entre os testes: o preco do Tesouro nao pode vazar para outro cenario. */
    @AfterEach
    void clear() {
        jdbc.update("DELETE FROM notifications WHERE kind IN ('FONTE_INDISPONIVEL', 'FONTE_DESATUALIZADA')");
        jdbc.update("DELETE FROM treasury_offers");
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
    void load() {
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

    private java.util.Map<String, Object> offer(String ticker) {
        return jdbc.queryForMap("SELECT o.* FROM treasury_offers o JOIN assets a ON a.id = o.asset_id WHERE a.ticker = ?",
                ticker);
    }

    @Test
    @DisplayName("Oferta (V59): taxa do ultimo dia de cada titulo; a venda = vendeu nos 3 ultimos dias de liquidacao")
    void treasuryOffer() {
        TreasuryPriceService.Result r = service.importRecent("teste");

        // IPCA+ 2029 e Renda+ 2084 (02/10) e Selic 2028 (01/10); o Prefixado 2027 so vendeu no 4o dia (25/09) e em
        // 30/09 a quantidade e zero; o titulo fora do catalogo nao conta
        assertThat(r.onSale()).isEqualTo(3);
        java.util.Map<String, Object> ipca = offer("Tesouro IPCA+ 2029");
        assertThat((BigDecimal) ipca.get("buy_rate")).isEqualByComparingTo("7.39");
        assertThat((BigDecimal) ipca.get("sell_rate")).isEqualByComparingTo("7.51");
        assertThat(ipca.get("rate_date").toString()).isEqualTo("2026-10-02");
        assertThat(ipca.get("on_sale")).isEqualTo(true);
        assertThat(ipca.get("sale_date").toString()).isEqualTo("2026-10-02");
        assertThat(offer("Tesouro Selic 2028").get("on_sale")).isEqualTo(true);
        assertThat(offer("Tesouro Prefixado 2027").get("on_sale")).isEqualTo(false);

        // arquivo de vendas fora do ar: a carga segue, a taxa e regravada e a oferta fica a da ultima rodada
        when(salesClient.fetch()).thenReturn(refused(null));
        TreasuryPriceService.Result withoutSales = service.importRecent("teste");
        assertThat(withoutSales.onSale()).isEqualTo(-1);
        assertThat(offer("Tesouro IPCA+ 2029").get("on_sale")).isEqualTo(true);
    }

    @Test
    @DisplayName("Idempotente: a segunda rodada nao grava nada nem muda preco; o inicio filtra os dias")
    void idempotente() {
        service.importFrom(2010, "teste");

        TreasuryPriceService.Result de = service.importFrom(2010, "teste");
        assertThat(de.rows()).isZero();
        assertThat(de.priced()).isZero();

        jdbc.update("DELETE FROM asset_price_history WHERE source = 'TESOURO'");
        TreasuryPriceService.Result since2025 = service.importFrom(2025, "teste");
        assertThat(since2025.rows()).isEqualTo(11);
    }

    @Test
    @DisplayName("Rodada diaria sem nada gravado faz a carga desde 2010; depois so rele os ultimos dias")
    void daily() {
        assertThat(service.needsRecovery()).isTrue();

        TreasuryPriceService.Result r = service.importRecent("teste");

        assertThat(r.rows()).isEqualTo(12);
        assertThat(service.importRecent("teste").rows()).isZero();
    }

    @Test
    @DisplayName("Fonte recusou com copia em disco (D2): le a copia, avisa FONTE_INDISPONIVEL e devolve o problema")
    void rejectionWithCopy() throws Exception {
        when(client.fetch()).thenReturn(refused(copy("PrecoTaxaTesouroDireto.csv")));

        TreasuryPriceService.Result r = service.importRecent("teste");

        assertThat(r.sourceProblem()).contains("Tesouro Transparente indisponivel").contains("HTTP 429");
        assertThat(r.rows()).isEqualTo(12); // a carga inicial ainda aproveita a copia
        assertThat(r.staleSince()).isNull(); // sem resposta da fonte nao se sabe se ela parou
        assertThat(notices("FONTE_INDISPONIVEL", null)).isEqualTo(1);
        assertThat(notices("FONTE_DESATUALIZADA", "NAO_RESOLVIDA")).isZero();
    }

    @Test
    @DisplayName("Fonte recusou sem copia em disco: erro claro e FONTE_INDISPONIVEL")
    void rejectionWithoutCopy() {
        when(client.fetch()).thenReturn(refused(null));

        assertThatThrownBy(() -> service.importRecent("teste"))
                .isInstanceOf(PublicFileCache.UnavailableException.class)
                .hasMessageContaining("HTTP 429").hasMessageContaining("sem copia");
        assertThat(notices("FONTE_INDISPONIVEL", null)).isEqualTo(1);
        assertThat(days("Tesouro IPCA+ 2029")).isZero();
    }

    @Test
    @DisplayName("Copia em disco que e a pagina de recusa (21/10/2026): erro, nao 'zero linhas' (R6)")
    void pageInsteadOfFile() throws Exception {
        Path html = tmp.resolve("pagina.csv");
        Files.writeString(html, "<html><head><title>Request Rejected</title></head><body>The requested URL was "
                + "rejected.</body></html>");
        when(client.fetch()).thenReturn(new PublicFileCache.Result(PublicFileCache.Outcome.NOT_MODIFIED, html, null,
                304, null));

        assertThatThrownBy(() -> service.importRecent("teste"))
                .isInstanceOf(java.io.UncheckedIOException.class)
                .hasMessageContaining("nao e o arquivo do Tesouro");
    }

    @Test
    @DisplayName("Fonte respondeu sem dado novo ha mais de 2 dias uteis (D3): pendencia; com o dado novo, resolve")
    void sourceStopped() throws Exception {
        // o recorte vai ate 02/10/2026: atrasado em qualquer dia de hoje em diante
        TreasuryPriceService.Result stopped = service.importRecent("teste");
        assertThat(stopped.staleSince()).isEqualTo(java.time.LocalDate.of(2026, 10, 2));
        assertThat(stopped.sourceProblem()).isNull();
        assertThat(notices("FONTE_DESATUALIZADA", "NAO_RESOLVIDA")).isEqualTo(1);
        service.importRecent("teste");
        assertThat(notices("FONTE_DESATUALIZADA", "NAO_RESOLVIDA")).isEqualTo(1); // pendencia aberta nao repete

        Path created = tmp.resolve("novo.csv");
        String yesterday = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).minusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        Files.writeString(created, TreasuryPriceClient.HEADER_PREFIX + "Data Vencimento;Data Base;Taxa Compra Manha;"
                + "Taxa Venda Manha;PU Compra Manha;PU Venda Manha;PU Base Manha\r\n"
                + "Tesouro IPCA+;15/05/2029;" + yesterday + ";7,39;7,51;3952,28;3936,95;3936,95\r\n");
        when(client.fetch()).thenReturn(ok(created));

        TreasuryPriceService.Result upToDate = service.importRecent("teste");
        assertThat(upToDate.staleSince()).isNull();
        assertThat(notices("FONTE_DESATUALIZADA", "NAO_RESOLVIDA")).isZero();
        assertThat(notices("FONTE_DESATUALIZADA", "RESOLVIDA")).isEqualTo(1);
    }
}
