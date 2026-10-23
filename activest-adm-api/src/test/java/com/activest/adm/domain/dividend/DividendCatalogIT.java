package com.activest.adm.domain.dividend;

import com.activest.adm.integration.b3.B3Client;
import com.activest.adm.integration.b3.B3Fixtures;
import com.activest.adm.integration.b3.FnetClient;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * F8 do docs/planos/PLANO_EMPRESAS_BALANCOS.md: a rodada de proventos cobre o catalogo (ativos ligados a
 * emissor, sem ninguem ter posicao) e le o historico de FII do FNET pelo CNPJ, so do
 * cache {@code carga/data/fnet} — avisos reais de VISC11, PLAG11 e MAXR11, nos dois formatos.
 *
 * <p>A B3 nao responde (mock sem resposta): o FNET segue sozinho, como deve.</p>
 */
// data propria: a rotina grava em carga/data/ (b3/proventos, b3/proventos-12m, fnet) e o teste nao pode
// sujar a carga/data/ real do projeto (docs/planos/PLANO_CARGA_INICIAL.md)
@TestPropertySource(properties = {"activest.dividends.scope=ALL", "activest.data.dir=target/it-data-proventos"})
class DividendCatalogIT extends IntegrationTestBase {

    static final String VISC = "17554274000125";
    static final String PLAG = "32754734000152";
    static final String MAXR = "11274415000170";

    @MockBean
    B3Client b3;

    @org.springframework.boot.test.mock.mockito.SpyBean
    FnetClient fnet;

    @Autowired
    DividendCatalogService service;

    @BeforeEach
    void scenario() throws Exception {
        // os testes de emissores apagam issuers e desligam os ativos: cada fundo e garantido aqui
        for (String[] f : new String[][]{{VISC, "VISC11"}, {PLAG, "PLAG11"}, {MAXR, "MAXR11"}}) {
            jdbc.update("INSERT INTO issuers (kind, cnpj, legal_name) VALUES ('FII', ?, ?) ON CONFLICT (cnpj) DO NOTHING",
                    f[0], f[1]);
            jdbc.update("""
                    UPDATE assets SET issuer_id = (SELECT id FROM issuers WHERE cnpj = ?)
                    WHERE ticker = ?""", f[0], f[1]);
        }
        // cada teste parte sem os 12 meses acumulados e sem busca recente no FNET (P1, D15); a busca
        // de verdade nunca sai para a rede aqui
        Path accumulated = fnet.cacheDir().getParent().resolve("b3/proventos-12m");
        if (Files.exists(accumulated)) {
            try (var files = Files.walk(accumulated)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        }
        ((Map<?, ?>) org.springframework.test.util.ReflectionTestUtils.getField(service, "lastFnetSearch")).clear();
        org.mockito.Mockito.doReturn(List.of()).when(fnet).incomeNoticeIds(anyString());
        Path dir = fnet.cacheDir();
        Files.createDirectories(dir);
        for (long id : new long[]{1002416, 19693, 20789, 36458, 1002663, 7078}) {
            Files.writeString(dir.resolve(id + ".xml"), B3Fixtures.read("fnet/" + id + ".xml"), StandardCharsets.UTF_8);
        }
        // indice pelo CNPJFundo de cada XML. VISC11: 20789 tem data-base 28/12/2017 depois do
        // pagamento 15/12; 36458, data-base em 2108
        fnet.reindex();
    }

    private List<Map<String, Object>> events(String ticker) {
        return jdbc.queryForList("""
                SELECT e.data_com, e.value_per_share, e.payment_date, e.source, e.source_ref
                FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id WHERE a.ticker = ? ORDER BY e.data_com""",
                ticker);
    }

    @Test
    @DisplayName("Catalogo sem posicao: FNET pelo CNPJ, formato antigo e atual, sem recibo nem data-base invalida")
    void fnetByCnpj() {
        DividendCatalogService.SyncResult r = service.syncAll("teste");

        assertThat(r.failures()).isZero();
        assertThat(r.issuers()).isGreaterThanOrEqualTo(3);

        assertThat(events("VISC11")).extracting(e -> e.get("data_com").toString())
                .containsExactly("2017-11-30", "2025-09-30");
        assertThat(events("VISC11")).allSatisfy(e -> assertThat(e.get("source")).isEqualTo("FNET"));
        // PLAG13 e recibo: o provento dele nao entra na cota
        assertThat(events("PLAG11")).singleElement()
                .satisfies(e -> assertThat((java.math.BigDecimal) e.get("value_per_share")).isEqualByComparingTo("0.48"));
        // CNPJ pontuado no aviso e ticker antigo (MAXR11B): liga pelo CNPJ
        assertThat(events("MAXR11")).singleElement()
                .satisfies(e -> assertThat(e.get("data_com").toString()).isEqualTo("2016-12-07"));
        // so disco: a busca no FNET nao e chamada
        verify(fnet, never()).incomeNoticeIds(anyString());
    }

    @Test
    @DisplayName("D15: B3 com provento mais novo que o FNET — busca no FNET uma vez; sem aviso novo, a B3 fica de reserva")
    void fnetPrimaryFetchesWhenB3HasNews() {
        jdbc.update("UPDATE assets SET isin = 'BRVISCCTF003' WHERE ticker = 'VISC11'");
        org.mockito.Mockito.when(b3.fundSupplement("VISC")).thenReturn(new com.activest.adm.integration.b3.B3Supplement(
                "FII VINCI SC", "VISC", null, List.of(
                new com.activest.adm.integration.b3.B3Supplement.CashDividend("BRVISCCTF003", "13/02/2026",
                        "0,82000000000", "Janeiro/2026", "30/01/2026", "BRVISCCTF003", "RENDIMENTO", "30/01/2026", "")),
                List.of()));
        org.mockito.Mockito.doReturn(List.of(1002416L, 20789L)).when(fnet).incomeNoticeIds(VISC);

        service.syncAll("teste");
        service.syncAll("teste");

        assertThat(events("VISC11")).extracting(e -> e.get("data_com").toString() + "/" + e.get("source"))
                .containsExactly("2017-11-30/FNET", "2025-09-30/FNET", "2026-01-30/B3");
        // uma busca so: a segunda rodada esta dentro do intervalo de um dia
        verify(fnet, org.mockito.Mockito.times(1)).incomeNoticeIds(VISC);
        // a resposta da B3 ficou em carga/data/b3/proventos-12m (P1)
        assertThat(fnet.cacheDir().getParent().resolve("b3/proventos-12m/fundos/VISC.json")).exists();
    }

    @Test
    @DisplayName("Rodar de novo nao grava nada; o mesmo provento da B3 com outras casas nao duplica")
    void idempotentWithoutDuplicates() {
        jdbc.update("""
                INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source)
                SELECT id, 'RENDIMENTO', 'RENDIMENTO', DATE '2025-09-30', DATE '2025-10-14', 0.8100000001, 'B3'
                FROM assets WHERE ticker = 'VISC11'""");

        service.syncAll("teste");
        DividendCatalogService.SyncResult again = service.syncAll("teste");

        assertThat(again.newCashEvents()).isZero();
        assertThat(events("VISC11")).extracting(e -> e.get("data_com").toString())
                .containsExactly("2017-11-30", "2025-09-30");
    }

    @Test
    @DisplayName("FII que ainda nao distribuiu (APXR11): B3 sem proventos e nada em disco — sem falha, zero eventos")
    void fundWithoutDistribution() {
        // resposta real da B3 em 28/09/2026: cashDividends e stockDividends vazios
        org.mockito.Mockito.when(b3.fundSupplement("APXR")).thenReturn(B3Fixtures.client().fundSupplement("APXR"));
        jdbc.update("INSERT INTO issuers (kind, cnpj, legal_name) VALUES ('FII', '52446271000179', 'APEX REALTY FII') "
                + "ON CONFLICT (cnpj) DO NOTHING");
        jdbc.update("""
                UPDATE assets SET issuer_id = (SELECT id FROM issuers WHERE cnpj = '52446271000179')
                WHERE ticker = 'APXR11'""");

        DividendCatalogService.SyncResult r = service.syncAll("teste");

        assertThat(r.failures()).isZero();
        assertThat(events("APXR11")).isEmpty();
        // sem aviso em disco: nada a ler; quando distribuir, entra pela B3 na rotina diaria
        assertThat(service.syncAll("teste").newCashEvents()).isZero();
    }

    @Test
    @DisplayName("AMORTIZACAO RF da B3 e amortizacao: completa a do FNET (outras casas) e entra quando so a B3 tem")
    void fiAmortization() {
        jdbc.update("UPDATE assets SET isin = 'BRVISCCTF003' WHERE ticker = 'VISC11'");
        jdbc.update("""
                INSERT INTO asset_cash_events (asset_id, type, label, data_com, value_per_share, source, source_ref)
                SELECT id, 'AMORTIZACAO', 'AMORTIZACAO', DATE '2026-02-20', 1.2499999999, 'FNET', '999'
                FROM assets WHERE ticker = 'VISC11'""");
        org.mockito.Mockito.when(b3.fundSupplement("VISC")).thenReturn(new com.activest.adm.integration.b3.B3Supplement(
                "FII VINCI SC", "VISC", null, List.of(
                new com.activest.adm.integration.b3.B3Supplement.CashDividend("BRVISCCTF003", "02/03/2026",
                        "1,25000000000", "Fevereiro/2026", "20/02/2026", "BRVISCCTF003", "AMORTIZACAO RF", "20/02/2026", ""),
                new com.activest.adm.integration.b3.B3Supplement.CashDividend("BRVISCCTF003", "29/05/2026",
                        "0,50000000000", "Maio/2026", "20/05/2026", "BRVISCCTF003", "AMORTIZACAO RF", "20/05/2026", "")),
                List.of()));

        service.syncAll("teste");

        List<Map<String, Object>> amort = jdbc.queryForList("""
                SELECT e.data_com, e.value_per_share, e.payment_date, e.source, e.label
                FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id
                WHERE a.ticker = 'VISC11' AND e.type = 'AMORTIZACAO' ORDER BY e.data_com""");
        assertThat(amort).hasSize(2);
        // a do FNET, completada com o pagamento da B3 — sem segunda linha
        assertThat(amort.get(0).get("source")).isEqualTo("FNET");
        assertThat(amort.get(0).get("payment_date").toString()).isEqualTo("2026-03-02");
        // so a B3 tem: entra como amortizacao
        assertThat(amort.get(1).get("source")).isEqualTo("B3");
        assertThat(amort.get(1).get("label")).isEqualTo("AMORTIZACAO RF");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asset_cash_events WHERE type IS NULL", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("Historico da B3 em disco (F10): baixado uma vez por emissor; sem evento gravado, relido do arquivo")
    void companyHistoryOnDisk() throws Exception {
        jdbc.update("INSERT INTO issuers (kind, cnpj, legal_name) VALUES ('COMPANY', '33000167000101', 'PETROBRAS') "
                + "ON CONFLICT (cnpj) DO NOTHING");
        jdbc.update("UPDATE assets SET issuer_id = (SELECT id FROM issuers WHERE cnpj = '33000167000101') "
                + "WHERE ticker IN ('PETR3', 'PETR4')");
        com.activest.adm.integration.b3.B3Client real = B3Fixtures.client();
        org.mockito.Mockito.when(b3.companySupplement("PETR")).thenReturn(real.companySupplement("PETR"));
        org.mockito.Mockito.when(b3.companyDetail(anyString())).thenAnswer(i -> real.companyDetail(i.getArgument(0)));
        org.mockito.Mockito.when(b3.cashDividendsHistory(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(i -> real.cashDividendsHistory(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        String history = "SELECT count(*) FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id "
                + "WHERE a.ticker = 'PETR3' AND e.source = 'B3_HISTORICO'";

        service.syncAll("teste");
        int stored = jdbc.queryForObject(history, Integer.class);
        // o recorte real do historico da B3 so tem a especie ON
        assertThat(stored).isPositive();
        Path filePath = fnet.cacheDir().getParent().resolve("b3/proventos/PETR.json");
        assertThat(filePath).exists();

        // rodada seguinte: o ativo ja tem o historico — nenhuma pagina pedida de novo
        service.syncAll("teste");
        // eventos apagados (banco novo com a pasta data): voltam do arquivo, sem a B3
        jdbc.update("DELETE FROM asset_cash_events WHERE source = 'B3_HISTORICO'");
        service.syncAll("teste");

        assertThat(jdbc.queryForObject(history, Integer.class)).isEqualTo(stored);
        verify(b3, org.mockito.Mockito.times(1)).cashDividendsHistory(anyString(), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.anyInt());
    }
}
