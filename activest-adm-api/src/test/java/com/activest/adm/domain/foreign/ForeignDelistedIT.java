package com.activest.adm.domain.foreign;

import com.activest.adm.integration.foreign.NareitClient;
import com.activest.adm.integration.foreign.SecClient;
import com.activest.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Deslistados dos EUA (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, F5): Form 25 → classe e bolsa → ticker pelo XBRL
 * inline do último 8-K → cadastro inativo com a data de saída e a prova; nota e empresa ainda listada ficam fora; a
 * segunda rodada não duplica. A SEC é respondida aqui com trechos reais (CIKs e tickers fictícios ZZ).
 */
class ForeignDelistedIT extends IntegrationTestBase {

    @Autowired PlatformTransactionManager txManager;
    @TempDir Path dir;

    private ForeignDelistedService service;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        Path data = dir.resolve("data");
        SecClient sec = spy(new SecClient("http://127.0.0.1:9", "http://127.0.0.1:9", "teste@activest.com.br",
                data.toString()));
        doReturn(List.of()).when(sec).form25Index(anyInt(), anyInt());
        doReturn(List.of(
                entry("25-NSE", "ZZ DELISTED CORP", "9990001", "edgar/data/9990001/0000876661-24-000901.txt"),
                entry("25-NSE", "NEW YORK STOCK EXCHANGE LLC", "876661", "edgar/data/876661/0000876661-24-000901.txt"),
                entry("25-NSE", "ZZ NOTES INC", "9990002", "edgar/data/9990002/0000876661-24-000902.txt"),
                entry("25-NSE", "ZZ STILL LISTED INC", "9990003", "edgar/data/9990003/0000876661-24-000903.txt")))
                .when(sec).form25Index(2024, 1);
        doReturn(Optional.of(form(data, "9990001", "ZZ DELISTED CORP", "Common Stock", "000901")))
                .when(sec).filingByIndexPath("edgar/data/9990001/0000876661-24-000901.txt");
        doReturn(Optional.of(form(data, "9990002", "ZZ NOTES INC", "4.000% Notes due 2024", "000902")))
                .when(sec).filingByIndexPath("edgar/data/9990002/0000876661-24-000902.txt");
        doReturn(Optional.of(form(data, "9990003", "ZZ STILL LISTED INC", "Common Stock", "000903")))
                .when(sec).filingByIndexPath("edgar/data/9990003/0000876661-24-000903.txt");
        doReturn(Optional.of(new ObjectMapper().readTree("""
                {"filings": {"recent": {"form": ["25-NSE", "8-K"], "filingDate": ["2024-02-15", "2024-02-01"],
                  "accessionNumber": ["0000876661-24-000901", "0009990001-24-000001"],
                  "primaryDocument": ["primary_doc.xml", "zzd-20240201.htm"], "isXBRL": [0, 1],
                  "isInlineXBRL": [0, 1]}, "files": []}}"""))).when(sec).submissionsJson("9990001");
        doReturn(Optional.of("<ix:nonNumeric contextRef=\"c-1\" name=\"dei:TradingSymbol\">ZZDL</ix:nonNumeric>"))
                .when(sec).readText(contains("zzd-20240201.htm"), anyInt());
        doReturn(Optional.empty()).when(sec).seriesClass(anyInt(), (java.time.Duration) org.mockito.ArgumentMatchers.any());
        NareitClient nareit = spy(new NareitClient("http://127.0.0.1:9", data.toString()));
        doReturn(Optional.empty()).when(nareit).constituents(anyInt(), anyBoolean());

        // a ZZ STILL LISTED ainda está no catálogo vivo (troca de bolsa): o Form 25 dela não é saída
        Long live = jdbc.queryForObject("""
                INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, currency_code, country_code, active)
                VALUES ('ZZSTL', 'ZZ Still Listed', (SELECT id FROM asset_types WHERE name = 'STOCK'),
                        (SELECT c.id FROM asset_categories c JOIN asset_types t ON t.id = c.asset_type_id
                          WHERE t.name = 'STOCK' AND c.name = 'FINANCAS'), 'USD', 'US', true) RETURNING id""", Long.class);
        jdbc.update("INSERT INTO foreign_listings (asset_id, exchange, cik) VALUES (?, 'NASDAQ', '9990003')", live);
        service = new ForeignDelistedService(sec, nareit, new SicCategories(), jdbc, txManager, data.toString());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM assets WHERE ticker IN ('ZZDL', 'ZZSTL')");
    }

    private static SecClient.FormEntry entry(String form, String company, String cik, String path) {
        return new SecClient.FormEntry(form, company, cik, LocalDate.of(2024, 2, 15), path);
    }

    private static Path form(Path data, String cik, String company, String cls, String n) throws Exception {
        Path f = data.resolve("sec/filings/" + cik + "/form-" + n + ".txt");
        Files.createDirectories(f.getParent());
        Files.writeString(f, Form25Test.NSE.replace("0000874761", String.format("%010d", Long.parseLong(cik)))
                .replace("AES CORP", company).replace("[4991]", "[7372]")
                .replace("<descriptionClassSecurity>Common Stock<", "<descriptionClassSecurity>" + cls + "<"));
        return f;
    }

    @Test
    @DisplayName("Cadastra o deslistado inativo com o ticker do XBRL e a saída do anexo; nota e empresa viva fora; sem duplicar")
    void delisted() throws Exception {
        ForeignDelistedService.Result preview = service.run(2024, 2024, false, "teste", () -> { });
        assertThat(preview.capital()).isEqualTo(1);
        assertThat(preview.withTicker()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE ticker = 'ZZDL'", Long.class)).isZero();
        String json = Files.readString(preview.preview());
        assertThat(json).contains("classe fora do capital").contains("empresa ainda listada");

        ForeignDelistedService.Result first = service.run(2024, 2024, true, "teste", () -> { });
        assertThat(first.created()).isEqualTo(1);
        Map<String, Object> a = jdbc.queryForMap("""
                SELECT a.active, a.delisted_on, a.delisting_source, a.currency_code, c.name AS category, f.cik,
                       f.exchange, f.ticker_source, f.sic_code, t.name AS type
                  FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
                  JOIN asset_categories c ON c.id = a.asset_category_id JOIN foreign_listings f ON f.asset_id = a.id
                 WHERE a.ticker = 'ZZDL'""");
        assertThat(a).containsEntry("active", false).containsEntry("delisting_source", "FORM_25")
                .containsEntry("delisted_on", java.sql.Date.valueOf(LocalDate.of(2024, 2, 26)))
                .containsEntry("type", "STOCK").containsEntry("category", "SERVICOS_DE_TECNOLOGIA")
                .containsEntry("cik", "9990001").containsEntry("exchange", "NYSE").containsEntry("ticker_source", "XBRL")
                .containsEntry("sic_code", "7372").containsEntry("currency_code", "USD");

        ForeignDelistedService.Result second = service.run(2024, 2024, true, "teste", () -> { });
        assertThat(second.created()).isZero();
        assertThat(second.existing()).isEqualTo(1);
    }
}
