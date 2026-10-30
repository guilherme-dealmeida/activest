package com.activest.adm.domain.foreign;

import com.activest.adm.domain.notice.AdminNotices;
import com.activest.adm.integration.foreign.NareitClient;
import com.activest.adm.integration.foreign.NasdaqTraderClient;
import com.activest.adm.integration.foreign.OpenFigiClient;
import com.activest.adm.integration.foreign.OpenFigiClient.Figi;
import com.activest.adm.integration.foreign.SecClient;
import com.activest.domain.notification.NotificationService;
import com.activest.domain.portfolio.OpenPositions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.activest.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Catálogo vivo dos EUA (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, F4): prévia sem gravar, gravação com curadoria,
 * renomeação pelo FIGI e saída com prova (Form 25 e 30 dias fora da lista), sem refazer a decisão do administrador. As
 * fontes são os arquivos reais gravados em {@code src/test/resources/foreign} (lista de tickers da SEC, séries dos
 * fundos, PDF da Nareit); a lista da bolsa e o OpenFIGI são montados aqui.
 */
class ForeignCatalogIT extends IntegrationTestBase {

    private static final List<String> TICKERS = List.of("AAPL", "AAPX", "BRK.B", "JPM-C", "META", "PLD", "SPY", "VOO",
            "QQQ", "ZZNEW", "ZZFX");
    private static final String HEADER = "Nasdaq Traded|Symbol|Security Name|Listing Exchange|Market Category|ETF|"
            + "Round Lot Size|Test Issue|Financial Status|CQS Symbol|NASDAQ Symbol|NextShares";

    @Autowired PlatformTransactionManager txManager;
    @Autowired AdminNotices notices;
    @Autowired OpenPositions positions;
    @TempDir Path dir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Figi> figis = new HashMap<>();
    private NasdaqTraderClient nasdaq;
    private ForeignCatalogService service;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        today = NotificationService.today();
        // SEC: a lista de tickers e as séries dos fundos (arquivos reais), frescas no disco = sem rede
        Path sec = Files.createDirectories(dir.resolve("data/sec/funds"));
        Files.copy(resource("company_tickers_exchange.json"), sec.getParent().resolve("company_tickers_exchange.json"));
        Files.copy(resource("series_class.csv"), sec.resolve("investment-company-series-class-" + today.getYear() + ".csv"));
        SecClient secClient = spy(new SecClient("http://127.0.0.1:9", "http://127.0.0.1:9", "teste@activest.com.br",
                dir.resolve("data").toString()));
        doReturn(Optional.empty()).when(secClient).profile(anyString(), any());
        doReturn(Optional.of(profile("320193", "3571", List.of()))).when(secClient).profile(eq("320193"), any());
        doReturn(Optional.of(profile("19617", "6021", List.of()))).when(secClient).profile(eq("19617"), any());
        doReturn(Optional.of(profile("1067983", "6331", List.of(new SecClient.Filing("25-NSE", today.minusDays(20),
                "0000000000-26-000001"))))).when(secClient).profile(eq("1067983"), any());

        NareitClient nareit = spy(new NareitClient("http://127.0.0.1:9", dir.resolve("data").toString()));
        doReturn(Optional.empty()).when(nareit).constituents(anyInt(), anyBoolean());
        doReturn(Optional.of(resource("FNUSIC2026.pdf"))).when(nareit).constituents(eq(today.getYear()), eq(false));

        OpenFigiClient figi = mock(OpenFigiClient.class);
        when(figi.hasKey()).thenReturn(true);
        when(figi.mapTickers(anyList(), any(Duration.class))).thenAnswer(inv -> {
            Map<String, Figi> out = new HashMap<>();
            for (Object t : (List<?>) inv.getArgument(0)) {
                if (figis.containsKey(t)) {
                    out.put((String) t, figis.get(t));
                }
            }
            return out;
        });
        figi("AAPL", "BBG000B9XRY4", "Common Stock");
        figi("BRK/B", "BBG000DWG505", "Common Stock");
        figi("META", "BBG000MM2P62", "Common Stock");
        figi("PLD", "BBG000B9Z0J8", "REIT");
        figi("SPY", "BBG000BDTBL9", "ETP");
        figi("VOO", "BBG000BHRB54", "ETP");
        figi("QQQ", "BBG000BSWKH7", "ETP");
        figi("ZZNEW", "BBGZZ0000001", "Common Stock");
        figi("ZZFX", "BBGZZ0000002", "ETP");

        // logos (C10/P4): o do BDR da mesma empresa; a preferencial leva o da empresa; a imagem genérica da BRAPI não
        Path logos = Files.createDirectories(dir.resolve("data/logos/STOCK")).getParent();
        Files.createDirectories(logos.resolve("ETF_INTERNACIONAL"));
        Files.writeString(logos.resolve("AAPL34.svg"), "<svg>apple</svg>");
        for (String g : List.of("BERK34.png", "ZZG134.png", "ZZG234.png")) {
            Files.writeString(logos.resolve(g), "generica");
        }
        Files.writeString(logos.resolve("STOCK/JPM.png"), "jpm");
        nasdaq = mock(NasdaqTraderClient.class);
        jdbc.update("""
                INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, currency_code, active)
                VALUES ('ZZFX', 'Cripto de teste', (SELECT id FROM asset_types WHERE name = 'CRIPTO'),
                        (SELECT MIN(c.id) FROM asset_categories c JOIN asset_types t ON t.id = c.asset_type_id
                          WHERE t.name = 'CRIPTO'), 'USD', true)""");
        service = new ForeignCatalogService(nasdaq, figi, secClient, nareit, new SicCategories(), jdbc, txManager,
                notices, positions, dir.resolve("data").toString());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM notifications WHERE kind IN ('CATALOGO_TICKER_INATIVADO', 'CATALOGO_TICKER_RENOMEADO')");
        for (String t : TICKERS) {
            jdbc.update("""
                    DELETE FROM assets WHERE ticker = ? AND asset_type_id IN (SELECT id FROM asset_types
                     WHERE name IN ('STOCK', 'REIT', 'ETF_INTERNACIONAL', 'CRIPTO'))""", t);
        }
    }

    private static Path resource(String name) throws Exception {
        return Path.of(ForeignCatalogIT.class.getResource("/foreign/" + name).toURI());
    }

    private static SecClient.Profile profile(String cik, String sic, List<SecClient.Filing> forms) {
        return new SecClient.Profile(cik, "Empresa " + cik, sic, null, List.of(), forms);
    }

    private void figi(String bloomberg, String composite, String type) {
        figis.put(bloomberg, new Figi(composite, composite, bloomberg, bloomberg, "US", type, null, "Equity", null));
    }

    /** A lista da bolsa do dia: linhas no formato do Nasdaq Trader. */
    private void listing(String... lines) throws Exception {
        Path f = dir.resolve("lista-" + System.nanoTime() + ".txt");
        Files.writeString(f, HEADER + "\n" + String.join("\n", lines) + "\nFile Creation Time: 1029202611:02|||||\n");
        when(nasdaq.snapshot(any())).thenReturn(f);
    }

    private static final String AAPL = "Y|AAPL|Apple Inc. - Common Stock|Q|Q|N|40|N|N||AAPL|N";
    private static final String BRK = "Y|BRK.B|Berkshire Hathaway Inc. New Common Stock|N| |N|40|N||BRK.B|BRK.B|N";
    private static final String JPM_C = "Y|JPM$C|J P Morgan Chase & Co Depositary Shares, each representing a 1/400th "
            + "interest in a share of 6.00% Non-Cumulative Preferred Stock, Series EE|N| |N|100|N||JPMpC|JPM-C|N";
    private static final String META = "Y|META|Meta Platforms, Inc. - Class A Common Stock|Q|Q|N|40|N|N||META|N";
    private static final String PLD = "Y|PLD|Prologis, Inc. Common Stock|N| |N|100|N||PLD|PLD|N";
    private static final String SPY = "Y|SPY|State Street SPDR S&P 500 ETF Trust|P| |Y|40|N||SPY|SPY|N";
    private static final String VOO = "Y|VOO|Vanguard S&P 500 ETF|P| |Y|40|N||VOO|VOO|N";
    private static final String QQQ = "Y|QQQ|Invesco QQQ Trust, Series 1|Q|G|Y|40|N|N||QQQ|N";
    private static final String ZZNEW = "Y|ZZNEW|ZZ Nova Empresa Inc. Common Stock|N| |N|100|N||ZZNEW|ZZNEW|N";
    private static final String ZZFX = "Y|ZZFX|ZZ Homonima Equity ETF|P| |Y|40|N||ZZFX|ZZFX|N";
    private static final String WARRANT = "Y|ACAHW|Atlantic Coastal Acquisition Corp. II Warrant|Q|G|N|100|N|N||ACAHW|N";
    private static final String TEST_ISSUE = "Y|ZZZTX|TXSE Test Stk 5 Common Stock|N| |N|40|Y||ZZZTX|ZZZTX|N";

    private Map<String, Object> asset(String ticker, String type) {
        return jdbc.queryForMap("""
                SELECT a.id, a.ticker, a.name, a.active, a.delisted_on, a.delisting_source, a.currency_code,
                       a.country_code, c.name AS category, f.composite_figi, f.cik, f.sec_series_id, f.exchange,
                       f.type_source, f.category_source, f.ticker_source, f.sic_code, f.last_seen_on
                  FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
                  LEFT JOIN asset_categories c ON c.id = a.asset_category_id
                  LEFT JOIN foreign_listings f ON f.asset_id = a.id
                 WHERE a.ticker = ? AND t.name = ?""", ticker, type);
    }

    private JsonNode row(JsonNode preview, String ticker) {
        for (JsonNode r : preview.path("ativos")) {
            if (r.path("ticker").asText().equals(ticker)) {
                return r;
            }
        }
        throw new AssertionError(ticker + " fora da prévia");
    }

    @Test
    @DisplayName("WRITE=false: a prévia classifica tipo, categoria (BDR, SIC, Nareit, regra do ETF), série e exclusões, sem gravar")
    void previewOnly() throws Exception {
        listing(AAPL, BRK, JPM_C, META, PLD, SPY, VOO, QQQ, ZZNEW, ZZFX, WARRANT, TEST_ISSUE);

        ForeignCatalogService.Result r = service.run(false, "teste", () -> { });

        assertThat(r.wrote()).isFalse();
        assertThat(r.listed()).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets a JOIN asset_types t ON t.id = a.asset_type_id "
                + "WHERE t.name IN ('STOCK', 'REIT', 'ETF_INTERNACIONAL') AND a.ticker = 'AAPL'", Long.class)).isZero();
        JsonNode p = mapper.readTree(r.preview().toFile());
        assertThat(row(p, "AAPL").path("category").asText()).isEqualTo("TECNOLOGIA_ELETRONICA");
        assertThat(row(p, "AAPL").path("categorySource").asText()).isEqualTo("BDR");
        assertThat(row(p, "AAPL").path("cik").asText()).isEqualTo("320193");
        assertThat(row(p, "AAPL").path("name").asText()).isEqualTo("Apple Inc.");
        assertThat(row(p, "BRK.B").path("category").asText()).isEqualTo("FINANCAS");
        assertThat(row(p, "BRK.B").path("bdrTicker").asText()).isEqualTo("BERK34");
        assertThat(row(p, "JPM-C").path("type").asText()).isEqualTo("STOCK");
        assertThat(row(p, "JPM-C").path("typeSource").asText()).isEqualTo("LIST");
        // a preferencial casa a empresa pelo papel principal (JPM) na SEC: BDR do JPMorgan e o SIC 6021 da ficha
        assertThat(row(p, "JPM-C").path("categorySource").asText()).isEqualTo("BDR");
        assertThat(row(p, "JPM-C").path("category").asText()).isEqualTo("FINANCAS");
        assertThat(row(p, "JPM-C").path("sic").asText()).isEqualTo("6021");
        assertThat(row(p, "JPM-C").path("sicCategory").asText()).isEqualTo("FINANCAS");
        assertThat(row(p, "AAPL").path("sicCategory").asText()).isEqualTo("TECNOLOGIA_ELETRONICA");
        assertThat(row(p, "PLD").path("type").asText()).isEqualTo("REIT");
        assertThat(row(p, "PLD").path("category").asText()).isEqualTo("INDUSTRIAL");
        assertThat(row(p, "PLD").path("categorySource").asText()).isEqualTo("NAREIT");
        assertThat(row(p, "VOO").path("seriesId").asText()).isEqualTo("S000002839");
        assertThat(row(p, "VOO").path("category").asText()).isEqualTo("ACOES_EUA");
        assertThat(row(p, "SPY").path("typeSource").asText()).isEqualTo("OPENFIGI");
        assertThat(row(p, "ZZNEW").path("action").asText()).isEqualTo("SEM_CATEGORIA");
        assertThat(p.path("excluidos").toString()).contains("ACAHW").contains("warrant").doesNotContain("ZZZTX");
        assertThat(p.path("colisoes").toString()).contains("ZZFX").contains("CRIPTO");
        assertThat(r.created()).isEqualTo(9);
    }

    @Test
    @DisplayName("WRITE=true grava com a curadoria; depois renomeia pelo FIGI e inativa com Form 25 ou 30 dias fora, sem mexer no MANUAL")
    void writeRenameAndDelist() throws Exception {
        listing(AAPL, BRK, JPM_C, META, PLD, SPY, VOO, QQQ, ZZNEW, ZZFX, WARRANT);
        Path cur = Files.createDirectories(dir.resolve("data/levantamento/internacional")).resolve("curadoria.json");
        Files.writeString(cur, """
                {"itens": [{"ticker": "ZZNEW", "categoria": "SERVICOS_COMERCIAIS"},
                           {"ticker": "ZZOUT", "excluir": true}]}""");

        ForeignCatalogService.Result first = service.run(true, "teste", () -> { });

        assertThat(first.created()).isEqualTo(10);
        assertThat(first.curated()).isEqualTo(1);
        assertThat(first.curationProblems()).isEqualTo(1);
        Map<String, Object> aapl = asset("AAPL", "STOCK");
        assertThat(aapl).containsEntry("currency_code", "USD").containsEntry("country_code", "US")
                .containsEntry("composite_figi", "BBG000B9XRY4").containsEntry("exchange", "NASDAQ")
                .containsEntry("sic_code", "3571").containsEntry("category_source", "BDR")
                .containsEntry("last_seen_on", java.sql.Date.valueOf(today));
        assertThat(asset("ZZNEW", "STOCK")).containsEntry("category", "SERVICOS_COMERCIAIS")
                .containsEntry("category_source", "CURATION");
        assertThat(asset("VOO", "ETF_INTERNACIONAL")).containsEntry("sec_series_id", "S000002839")
                .containsEntry("cik", "36405");
        assertThat(asset("ZZFX", "ETF_INTERNACIONAL")).containsEntry("active", true).containsEntry("category", "ACOES_EUA");
        Path logos = dir.resolve("data/logos/STOCK");
        assertThat(Files.readString(logos.resolve("AAPL.svg"))).isEqualTo("<svg>apple</svg>");
        assertThat(Files.readString(logos.resolve("JPM-C.png"))).isEqualTo("jpm");
        assertThat(logos.resolve("BRK.B.png")).doesNotExist();

        // segunda semana: AAPL vira AAPX (mesmo FIGI); SPY fora há 31 dias; BRK.B com Form 25; QQQ reativado à mão
        figi("AAPX", "BBG000B9XRY4", "Common Stock");
        Long aaplId = (Long) aapl.get("id");
        jdbc.update("UPDATE foreign_listings SET last_seen_on = ? WHERE asset_id = (SELECT id FROM assets WHERE "
                + "ticker = 'SPY' AND currency_code = 'USD')", java.sql.Date.valueOf(today.minusDays(31)));
        jdbc.update("UPDATE foreign_listings SET last_seen_on = ? WHERE asset_id = (SELECT id FROM assets WHERE "
                + "ticker = 'BRK.B')", java.sql.Date.valueOf(today.minusDays(15)));
        jdbc.update("UPDATE assets SET delisting_source = 'MANUAL' WHERE ticker = 'QQQ' AND currency_code = 'USD'");
        listing(AAPL.replace("AAPL", "AAPX"), JPM_C, META, PLD, VOO, ZZNEW, ZZFX);
        // a curadoria troca o tipo de um ativo já gravado (a regra nunca troca sozinha)
        Files.writeString(cur, """
                {"itens": [{"ticker": "ZZNEW", "tipo": "REIT", "categoria": "ESPECIAIS"}]}""");

        ForeignCatalogService.Result second = service.run(true, "teste", () -> { });

        assertThat(second.renamed()).isEqualTo(1);
        assertThat(second.inactivated()).isEqualTo(2);
        assertThat(asset("AAPX", "STOCK")).containsEntry("id", aaplId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'CATALOGO_TICKER_RENOMEADO' "
                + "AND dedup_key LIKE '%EUA:AAPL>AAPX'", Long.class)).isEqualTo(1);
        assertThat(asset("SPY", "ETF_INTERNACIONAL")).containsEntry("active", false)
                .containsEntry("delisting_source", "LIST_GONE")
                .containsEntry("delisted_on", java.sql.Date.valueOf(today.minusDays(31)));
        assertThat(asset("BRK.B", "STOCK")).containsEntry("active", false).containsEntry("delisting_source", "FORM_25")
                .containsEntry("delisted_on", java.sql.Date.valueOf(today.minusDays(10)));
        assertThat(asset("QQQ", "ETF_INTERNACIONAL")).containsEntry("active", true);
        assertThat(asset("ZZNEW", "REIT")).containsEntry("category", "ESPECIAIS").containsEntry("type_source", "CURATION")
                .containsEntry("category_source", "CURATION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'CATALOGO_TICKER_INATIVADO'",
                Long.class)).isEqualTo(2);
    }
}
