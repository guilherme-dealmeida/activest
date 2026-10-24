package com.activest.adm.domain.dividend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.adm.integration.b3.B3Client;
import com.activest.adm.integration.b3.B3Fixtures;
import com.activest.adm.integration.b3.B3HistoryPage;
import com.activest.adm.integration.b3.B3Supplement;
import com.activest.adm.integration.b3.FnetClient;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * docs/planos/PLANO_CORRECAO_PROVENTOS.md — as classes de erro achadas na investigação de 20–21/10/2026
 * (docs/planos/PLANO_INVESTIGACAO_PROVENTOS_DUPLICADOS.md), com as respostas reais da B3 e os avisos reais do FNET.
 */
@TestPropertySource(properties = {"activest.dividends.scope=ALL", "activest.data.dir=target/it-data-correcao"})
class DividendCorrectionIT extends IntegrationTestBase {

    @MockBean
    B3Client b3;

    @SpyBean
    FnetClient fnet;

    @Autowired
    DividendCatalogService service;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void scenario() throws Exception {
        Path data = fnet.cacheDir().getParent();
        if (Files.exists(data)) {
            try (var files = Files.walk(data)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        }
        Files.createDirectories(fnet.cacheDir());
        ((Map<?, ?>) org.springframework.test.util.ReflectionTestUtils.getField(service, "lastFnetSearch")).clear();
        doReturn(List.of()).when(fnet).incomeNoticeIds(anyString());
        // historico longo das empresas: nada alem do que cada teste der
        when(b3.cashDividendsHistory(anyString(), anyInt(), anyInt())).thenReturn(new B3HistoryPage(null, List.of()));
        fnet.reindex();
    }

    private void issuer(String kind, String cnpj, String name, String... tickerIsin) {
        jdbc.update("INSERT INTO issuers (kind, cnpj, legal_name) VALUES (?, ?, ?) ON CONFLICT (cnpj) DO NOTHING",
                kind, cnpj, name);
        for (int i = 0; i < tickerIsin.length; i += 2) {
            jdbc.update("UPDATE assets SET issuer_id = (SELECT id FROM issuers WHERE cnpj = ?), isin = ? WHERE ticker = ?",
                    cnpj, tickerIsin[i + 1], tickerIsin[i]);
        }
    }

    private B3Supplement company(String issuer) throws Exception {
        return json.readValue(B3Fixtures.read("company_" + issuer + ".json"), B3Supplement[].class)[0];
    }

    private void notice(long... ids) throws Exception {
        for (long id : ids) {
            Files.writeString(fnet.cacheDir().resolve(id + ".xml"), B3Fixtures.read("fnet/" + id + ".xml"),
                    StandardCharsets.UTF_8);
        }
        fnet.reindex();
    }

    private void status(String cnpj, String content) throws Exception {
        Path dir = fnet.cacheDir().resolve("status");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(cnpj + ".json"), content, StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> events(String ticker, String dataComDe) {
        return jdbc.queryForList("""
                SELECT e.id, e.data_com, e.payment_date, e.value_per_share, e.seq, e.source, e.source_ref
                FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id
                WHERE a.ticker = ? AND e.data_com >= ?::date ORDER BY e.payment_date NULLS LAST, e.value_per_share, e.id""",
                ticker, dataComDe);
    }

    private static List<String> summary(List<Map<String, Object>> events) {
        return events.stream().map(e -> e.get("payment_date") + "/"
                + ((java.math.BigDecimal) e.get("value_per_share")).stripTrailingZeros().toPlainString() + "/s" + e.get("seq"))
                .toList();
    }

    // ------------------------------------------------------------------ classe A

    @Test
    @DisplayName("A — KLBN: parcelas com a ultima arredondada (…225 x 3 e …224) nao duplicam nem somem, em duas rodadas")
    void b3InstallmentsWithRounding() throws Exception {
        issuer("COMPANY", "89637490000145", "KLABIN", "KLBN3", "BRKLBNACNOR2", "KLBN4", "BRKLBNACNPR9",
                "KLBN11", "BRKLBNCDAM18");
        when(b3.companySupplement("KLBN")).thenReturn(company("KLBN"));

        service.syncAll("teste");
        service.syncAll("teste");
        service.syncAll("teste");

        assertThat(summary(events("KLBN4", "2025-12-15").stream()
                .filter(e -> e.get("data_com").toString().equals("2025-12-15")).toList()))
                .containsExactly("2026-02-27/0.04559717225/s1", "2026-05-20/0.04559717225/s2",
                        "2026-08-19/0.04559717225/s3", "2026-11-12/0.04559717224/s1");
    }

    @Test
    @DisplayName("A — RECV3, TRIS3 e ETER3: a parcela de valor menor nao vira duplicata das outras")
    void otherInstallments() throws Exception {
        issuer("COMPANY", "03342704000130", "PETRORECONCAVO", "RECV3", "BRRECVACNOR3");
        issuer("COMPANY", "08811643000127", "TRISUL", "TRIS3", "BRTRISACNOR4");
        issuer("COMPANY", "61092037000181", "ETERNIT", "ETER3", "BRETERACNOR3");
        when(b3.companySupplement("RECV")).thenReturn(company("RECV"));
        when(b3.companySupplement("TRIS")).thenReturn(company("TRIS"));
        when(b3.companySupplement("ETER")).thenReturn(company("ETER"));

        service.syncAll("teste");
        service.syncAll("teste");

        assertThat(summary(events("RECV3", "2026-01-08").stream()
                .filter(e -> e.get("data_com").toString().equals("2026-01-08")).toList()))
                .containsExactly("2026-12-31/0.341323/s1", "2027-12-31/0.341323/s2", "2028-12-31/0.341322/s1");
        assertThat(summary(events("TRIS3", "2025-12-26").stream()
                .filter(e -> e.get("data_com").toString().equals("2025-12-26")).toList()))
                .containsExactly("2026-04-30/0.1427447909/s1", "2026-05-29/0.1427447909/s2", "2026-06-30/0.1427447908/s1");
        assertThat(summary(events("ETER3", "2026-03-30").stream()
                .filter(e -> e.get("data_com").toString().equals("2026-03-30")).toList()))
                .containsExactly("2026-06-22/0.08535843599/s1", "2026-09-18/0.08535843598/s1");
    }

    // ------------------------------------------------------------------ classe B

    @Test
    @DisplayName("B — TRXF11: o aviso traz a cota e quatro recibos (TRXF13, 14, 15 e 26); so a cota entra")
    void subscriptionReceipt() throws Exception {
        issuer("FII", "28548288000152", "TRX REAL ESTATE", "TRXF11", "BRTRXFCTF003");
        notice(1072931, 1073440);

        service.syncAll("teste");

        assertThat(summary(events("TRXF11", "2025-12-30"))).containsExactly("2026-01-15/1/s1");
    }

    @Test
    @DisplayName("B — ARRI11: recibos com o ticker da cota no aviso (R30 0,03) ficam fora; vale o ISIN")
    void receiptWithShareTicker() throws Exception {
        issuer("FII", "32006821000121", "ARRI", "ARRI11", "BRARRICTF009");
        notice(709290);

        service.syncAll("teste");

        assertThat(summary(events("ARRI11", "2024-07-01"))).containsExactly("2024-08-07/0.1/s1");
    }

    // ------------------------------------------------------------------ classe C

    @Test
    @DisplayName("C — AIEC11: aviso retificado (data-base corrigida); o inativo no FNET nao vira evento")
    void amendedNoticeInactive() throws Exception {
        issuer("FII", "35765826000126", "AUTONOMY", "AIEC11", "BRAIECCTF009");
        notice(1072793, 1073063);
        status("35765826000126", """
                [{"id":1072793,"status":"IC","versao":1},{"id":1073063,"status":"AC","versao":1}]""");

        service.syncAll("teste");

        assertThat(events("AIEC11", "2025-12-01")).singleElement().satisfies(e -> {
            assertThat(e.get("source_ref")).isEqualTo("1073063");
            assertThat(e.get("data_com").toString()).isEqualTo("2025-12-30");
        });
    }

    @Test
    @DisplayName("C — AIEC11: o aviso antigo ja gravado vira o novo no lugar (mesmo id) quando o FNET o marca inativo")
    void noticeAmendedAfterStored() throws Exception {
        issuer("FII", "35765826000126", "AUTONOMY", "AIEC11", "BRAIECCTF009");
        notice(1072793);
        service.syncAll("teste");
        Object id = events("AIEC11", "2025-12-01").get(0).get("id");

        notice(1073063);
        status("35765826000126", """
                [{"id":1072793,"status":"IC","versao":1},{"id":1073063,"status":"AC","versao":1}]""");
        service.syncAll("teste");

        assertThat(events("AIEC11", "2025-12-01")).singleElement().satisfies(e -> {
            assertThat(e.get("id")).isEqualTo(id);
            assertThat(e.get("source_ref")).isEqualTo("1073063");
            assertThat(e.get("data_com").toString()).isEqualTo("2025-12-30");
        });
    }

    @Test
    @DisplayName("C — aviso republicado (mesma data-base): o evento passa a apontar para o aviso ativo, sem duplicar")
    void republishedNotice() throws Exception {
        issuer("FII", "35765826000126", "AUTONOMY", "AIEC11", "BRAIECCTF009");
        notice(1073063);
        service.syncAll("teste");
        Object id = events("AIEC11", "2025-12-01").get(0).get("id");

        // a reapresentacao: o mesmo conteudo num documento novo; o FNET inativa o anterior
        Files.writeString(fnet.cacheDir().resolve("1073064.xml"), B3Fixtures.read("fnet/1073063.xml"), StandardCharsets.UTF_8);
        fnet.reindex();
        status("35765826000126", """
                [{"id":1073063,"status":"IC","versao":1},{"id":1073064,"status":"AC","versao":2}]""");
        service.syncAll("teste");

        assertThat(events("AIEC11", "2025-12-01")).singleElement().satisfies(e -> {
            assertThat(e.get("id")).isEqualTo(id);
            assertThat(e.get("source_ref")).isEqualTo("1073064");
        });
    }

    @Test
    @DisplayName("C — MXRF11: a retificacao que so muda o pagamento (07/11 -> 14/11/2025) atualiza o evento no lugar")
    void amendmentChangesOnlyPayment() throws Exception {
        issuer("FII", "97521225000125", "MAXI RENDA", "MXRF11", "BRMXRFCTF008");
        notice(1025775);
        service.syncAll("teste");
        Object id = events("MXRF11", "2025-10-31").get(0).get("id");

        notice(1026624);
        status("97521225000125", """
                [{"id":1025775,"status":"IC","versao":1},{"id":1026624,"status":"AC","versao":2}]""");
        service.syncAll("teste");
        service.syncAll("teste");

        assertThat(events("MXRF11", "2025-10-31")).singleElement().satisfies(e -> {
            assertThat(e.get("id")).isEqualTo(id);
            assertThat(e.get("source_ref")).isEqualTo("1026624");
            assertThat(e.get("payment_date").toString()).isEqualTo("2025-11-14");
        });
    }

    @Test
    @DisplayName("C — MXRF11: sem o aviso antigo inativo no FNET, pagamento diferente nao muda o evento")
    void differentPaymentWithoutStatusUnchanged() throws Exception {
        issuer("FII", "97521225000125", "MAXI RENDA", "MXRF11", "BRMXRFCTF008");
        notice(1025775);
        service.syncAll("teste");

        notice(1026624);
        status("97521225000125", """
                [{"id":1025775,"status":"AC","versao":1},{"id":1026624,"status":"AC","versao":2}]""");
        service.syncAll("teste");

        assertThat(events("MXRF11", "2025-10-31")).singleElement()
                .satisfies(e -> assertThat(e.get("source_ref")).isEqualTo("1025775"));
    }

    @Test
    @DisplayName("C — CYCR11: aviso listado no FNET sob o CNPJ de outro fundo (CBOP11) e inativado vale pela situacao")
    void inactiveNoticeListedUnderOtherCnpj() throws Exception {
        issuer("FII", "36501233000115", "CYRELA CREDITO", "CYCR11", "BRCYCRCTF004");
        issuer("FII", "17144039000185", "CASTELLO BRANCO", "CBOP11", "BRCBOPCTF000");
        notice(1073501, 1073779, 1073791);
        status("36501233000115", """
                [{"id":1073501,"status":"IC","versao":1},{"id":1073791,"status":"AC","versao":2}]""");
        status("17144039000185", """
                [{"id":1073779,"status":"IC","versao":2}]""");

        service.syncAll("teste");

        assertThat(events("CYCR11", "2025-12-01")).singleElement().satisfies(e -> {
            assertThat(e.get("source_ref")).isEqualTo("1073791");
            assertThat(e.get("payment_date").toString()).isEqualTo("2026-01-15");
        });
    }

    @Test
    @DisplayName("C — CYCR11: o evento ja gravado pelo aviso listado sob outro CNPJ passa para o aviso ativo")
    void noticeFromOtherCnpjAlreadyStoredMovesToTheAsset() throws Exception {
        issuer("FII", "36501233000115", "CYRELA CREDITO", "CYCR11", "BRCYCRCTF004");
        issuer("FII", "17144039000185", "CASTELLO BRANCO", "CBOP11", "BRCBOPCTF000");
        notice(1073779);
        service.syncAll("teste");
        Object id = events("CYCR11", "2025-12-01").get(0).get("id");

        notice(1073791);
        status("36501233000115", """
                [{"id":1073791,"status":"AC","versao":2}]""");
        status("17144039000185", """
                [{"id":1073779,"status":"IC","versao":2}]""");
        service.syncAll("teste");

        assertThat(events("CYCR11", "2025-12-01")).singleElement().satisfies(e -> {
            assertThat(e.get("id")).isEqualTo(id);
            assertThat(e.get("source_ref")).isEqualTo("1073791");
        });
    }

    @Test
    @DisplayName("C — a busca no FNET grava a situacao de cada aviso em carga/data/fnet/status")
    void statusStoredOnFetch() throws Exception {
        String body = """
                {"data":[{"id":1072793,"status":"IC","versao":1,"modalidade":"AP","dataEntrega":"30/12/2025 17:00"},
                         {"id":1073063,"status":"AC","versao":1,"modalidade":"AP","dataEntrega":"30/12/2025 18:00"}]}""";
        FnetClient real = new FnetClient(org.springframework.web.reactive.function.client.WebClient.builder()
                .exchangeFunction(r -> reactor.core.publisher.Mono.just(
                        org.springframework.web.reactive.function.client.ClientResponse.create(org.springframework.http.HttpStatus.OK)
                                .header("Content-Type", "application/json").body(body).build())),
                "http://fnet", 5, 1, 0, 1, 5, 1, fnet.cacheDir().getParent().toString(), json);

        assertThat(real.incomeNoticeIds("35.765.826/0001-26")).containsExactly(1072793L, 1073063L);
        assertThat(real.inactiveNoticeIds("35765826000126")).containsExactly(1072793L);
        assertThat(fnet.cacheDir().resolve("status/35765826000126.json")).exists();
    }

    @Test
    @DisplayName("C — fundo com exatamente 100 avisos: a busca para no total (o FNET nao responde a pagina vazia)")
    void searchStopsAtTotal() {
        StringBuilder runItemList = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            runItemList.append(i > 1 ? "," : "").append("{\"id\":").append(i).append(",\"status\":\"AC\"}");
        }
        String body = "{\"recordsTotal\":100,\"data\":[" + runItemList + "]}";
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        FnetClient real = new FnetClient(org.springframework.web.reactive.function.client.WebClient.builder()
                .exchangeFunction(r -> {
                    calls.incrementAndGet();
                    return reactor.core.publisher.Mono.just(
                            org.springframework.web.reactive.function.client.ClientResponse.create(org.springframework.http.HttpStatus.OK)
                                    .header("Content-Type", "application/json").body(body).build());
                }),
                "http://fnet", 5, 1, 0, 1, 5, 1, fnet.cacheDir().getParent().toString(), json);

        assertThat(real.incomeNoticeIds("11111111000111")).hasSize(100);
        assertThat(calls.get()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ classe D

    @Test
    @DisplayName("D — SMRE11: a reserva da B3 (data-com 01/06) e absorvida pelo aviso do FNET (data-base 29/05)")
    void b3ReserveAbsorbedByFnet() throws Exception {
        issuer("FII", "53730029000195", "SMRE", "SMRE11", "BRSMRECTF001");
        jdbc.update("""
                INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source, isin)
                SELECT id, 'RENDIMENTO', 'RENDIMENTO', DATE '2026-06-01', DATE '2026-06-10', 1.01171811085, 'B3', 'BRSMRECTF001'
                FROM assets WHERE ticker = 'SMRE11'""");
        Object id = events("SMRE11", "2026-05-01").get(0).get("id");
        notice(1203633);

        service.syncAll("teste");

        assertThat(events("SMRE11", "2026-05-01")).singleElement().satisfies(e -> {
            assertThat(e.get("id")).isEqualTo(id);
            assertThat(e.get("source")).isEqualTo("FNET");
            assertThat(e.get("data_com").toString()).isEqualTo("2026-05-29");
        });
    }

    @Test
    @DisplayName("D — SMRE11: com o aviso do FNET gravado, a B3 com outra data-com nao grava a reserva")
    void b3DoesNotDuplicateFnet() throws Exception {
        issuer("FII", "53730029000195", "SMRE", "SMRE11", "BRSMRECTF001");
        notice(1203633);
        when(b3.fundSupplement("SMRE")).thenReturn(new B3Supplement("FII SMRE", "SMRE", null, List.of(
                new B3Supplement.CashDividend("BRSMRECTF001", "10/06/2026", "1,01171811085", "Maio/2026", "01/06/2026",
                        "BRSMRECTF001", "RENDIMENTO", "01/06/2026", "")), List.of()));

        service.syncAll("teste");
        service.syncAll("teste");

        assertThat(events("SMRE11", "2026-05-01")).singleElement()
                .satisfies(e -> assertThat(e.get("source")).isEqualTo("FNET"));
    }

    @Test
    @DisplayName("C8 — reler todos os avisos (fnet=all) refaz a linha que faltava e nao duplica a que o FNET completou")
    void rereadAllNotices() throws Exception {
        issuer("FII", "53730029000195", "SMRE", "SMRE11", "BRSMRECTF001");
        // a B3 gravou antes, com outras casas; o FNET completa o evento e fica com o valor da B3
        jdbc.update("""
                INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source, isin)
                SELECT id, 'RENDIMENTO', 'RENDIMENTO', DATE '2026-05-29', DATE '2026-06-10', 1.01171811084, 'B3', 'BRSMRECTF001'
                FROM assets WHERE ticker = 'SMRE11'""");
        notice(1203633);
        service.syncAll("teste");
        assertThat(events("SMRE11", "2026-05-01")).singleElement()
                .satisfies(e -> assertThat(e.get("source")).isEqualTo("FNET"));

        service.syncAll("teste", true);
        service.syncAll("teste", true);

        assertThat(events("SMRE11", "2026-05-01")).singleElement();
    }

    // ------------------------------------------------------------------ classe E

    @Test
    @DisplayName("E — BRIP11: aviso com o ticker do fundo e o ISIN de VCRI11 (erro de preenchimento) vale pelo ticker")
    void isinOfOtherFundWithFundTicker() throws Exception {
        issuer("FII", "34895752000180", "BRIP", "BRIP11", "BRBRIPCTF001");
        issuer("FII", "41081374000166", "VCRI", "VCRI11", "BRVCRICTF007");
        notice(833386);

        service.syncAll("teste");

        assertThat(summary(events("BRIP11", "2025-02-01"))).containsExactly("2025-02-14/36.85/s1");
        assertThat(events("VCRI11", "2025-02-01")).isEmpty();
    }

    @Test
    @DisplayName("E — sem o ticker do fundo, o ISIN da cota de outro fundo do catalogo (incorporacao) fica fora")
    void isinOfOtherFundWithoutTicker() throws Exception {
        issuer("FII", "34895752000180", "BRIP", "BRIP11", "BRBRIPCTF001");
        issuer("FII", "41081374000166", "VCRI", "VCRI11", "BRVCRICTF007");
        String xml = B3Fixtures.read("fnet/833386.xml").replace("<CodNegociacao>BRIP11</CodNegociacao>",
                "<CodNegociacao></CodNegociacao>");
        Files.writeString(fnet.cacheDir().resolve("833386.xml"), xml, StandardCharsets.UTF_8);
        fnet.reindex();

        service.syncAll("teste");

        assertThat(events("BRIP11", "2025-02-01")).isEmpty();
    }

    @Test
    @DisplayName("E — XLPR11: aviso de outra classe do mesmo CNPJ (ANGC11, R$ 770) no dia do rendimento da cota fica fora")
    void otherClassOnShareDay() throws Exception {
        issuer("FII", "35689733000160", "XLPR", "XLPR11", "BRXLPRCTF009");
        notice(1096577, 1096586);

        service.syncAll("teste");

        assertThat(summary(events("XLPR11", "2026-01-01"))).containsExactly("2026-02-13/0.52055031479/s1");
    }

    @Test
    @DisplayName("E — EURO11: no dia da cota (ISIN BREUROCTF011, R$ 1,23), o recibo BREURO01M15 com o ticker EURO11 fica fora")
    void receiptWithTickerOnShareDay() throws Exception {
        issuer("FII", "05437916000127", "EUROPAR", "EURO11", "BREUROCTF011");
        notice(11024, 11025, 11026);

        service.syncAll("teste");

        assertThat(summary(events("EURO11", "2017-03-01"))).containsExactly("2017-04-19/1.23/s1");
    }

    @Test
    @DisplayName("B — URPR11: a linha de URPR13 (R$ 0,078) com o ISIN da cota, no aviso da cota (R$ 0,30), fica fora")
    void otherClassWithShareIsin() throws Exception {
        issuer("FII", "34508872000187", "URCA PRIME RENDA", "URPR11", "BRURPRCTF002");
        notice(1335853);

        service.syncAll("teste");

        assertThat(summary(events("URPR11", "2026-09-01"))).containsExactly("2026-10-15/0.3/s1");
    }

    @Test
    @DisplayName("B — EXES11: rendimento da cota publicado so com o ticker EXES13 (sem a linha de EXES11) fica")
    void otherClassTickerAloneOnDay() throws Exception {
        issuer("FII", "41076851000103", "EXES", "EXES11", "BREXESCTF006");
        notice(366956);

        service.syncAll("teste");

        assertThat(summary(events("EXES11", "2022-10-01"))).containsExactly("2022-10-27/0.81/s1");
    }

    // ------------------------------------------------------------------ classe G

    @Test
    @DisplayName("G — KLABIN S/A: o historico e pedido a B3 sem a barra (KLABIN SA); com a barra ela devolve vazio")
    void tradingNameWithSlash() throws Exception {
        issuer("COMPANY", "89637490000145", "KLABIN", "KLBN3", "BRKLBNACNOR2", "KLBN4", "BRKLBNACNPR9",
                "KLBN11", "BRKLBNCDAM18");
        when(b3.companySupplement("KLBN")).thenReturn(company("KLBN"));
        Path filePath = fnet.cacheDir().getParent().resolve("b3/proventos/KLBN.json");
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "[]");

        service.syncAll("teste");

        verify(b3, org.mockito.Mockito.atLeastOnce())
                .cashDividendsHistory(org.mockito.ArgumentMatchers.eq("KLABIN SA"), anyInt(), anyInt());
        verify(b3, org.mockito.Mockito.never())
                .cashDividendsHistory(org.mockito.ArgumentMatchers.contains("/"), anyInt(), anyInt());
    }

    @Test
    @DisplayName("G — historico vazio em disco de quem tem provento nos 12 meses: busca de novo e grava")
    void emptyHistoryFetchesAgain() throws Exception {
        issuer("COMPANY", "33000167000101", "PETROBRAS", "PETR3", "BRPETRACNOR9", "PETR4", "BRPETRACNPR6");
        B3Client real = B3Fixtures.client();
        when(b3.companySupplement("PETR")).thenReturn(real.companySupplement("PETR"));
        when(b3.cashDividendsHistory(anyString(), anyInt(), anyInt()))
                .thenAnswer(i -> real.cashDividendsHistory(i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        Path filePath = fnet.cacheDir().getParent().resolve("b3/proventos/PETR.json");
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "[]");

        service.syncAll("teste");

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id
                WHERE a.ticker = 'PETR3' AND e.source = 'B3_HISTORICO'""", Integer.class)).isPositive();
        assertThat(Files.readString(filePath)).isNotEqualTo("[]");
        verify(b3, times(1)).cashDividendsHistory(anyString(), org.mockito.ArgumentMatchers.eq(1), anyInt());
    }
}
