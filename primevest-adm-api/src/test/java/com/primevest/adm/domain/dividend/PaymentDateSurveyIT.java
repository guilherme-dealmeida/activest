package com.primevest.adm.domain.dividend;

import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.dividend.CashEventSource;
import com.primevest.domain.dividend.CashEventWriter;
import com.primevest.domain.dividend.DividendType;
import com.primevest.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Datas de pagamento do levantamento no banco (docs/planos/PLANO_CARGA_INICIAL.md, 11 — L6/L7): grava so em
 * evento sem data, com origem LEVANTAMENTO; nao passa por cima de data existente; A DEFINIR nao grava.
 */
// data propria: o teste escreve o arquivo do levantamento e nao pode sujar a carga/data/ real do projeto
@TestPropertySource(properties = "primevest.data.dir=target/it-data-levantamento")
class PaymentDateSurveyIT extends IntegrationTestBase {

    @Autowired PaymentDateSurveyService service;
    @Autowired CashEventWriter writer;
    @Autowired AssetRepository assets;
    @Value("${primevest.data.dir}") String dataDir;

    private void evento(String ticker, String com, String valor, String pagamento) {
        writer.save(assets.findByTicker(ticker).orElseThrow(), new CashEventWriter.CashEventData(DividendType.JCP,
                "JRS CAP PROPRIO", LocalDate.parse(com), pagamento == null ? null : LocalDate.parse(pagamento),
                new BigDecimal(valor), null, null, null, null, CashEventSource.B3_HISTORICO, null, (short) 1));
    }

    private Map<String, Object> data(String ticker, String com) {
        return jdbc.queryForMap("""
                SELECT e.payment_date, e.payment_date_source FROM asset_cash_events e JOIN assets a ON a.id = e.asset_id
                WHERE a.ticker = ? AND e.data_com = ?""", ticker, java.sql.Date.valueOf(com));
    }

    private void arquivo(String linhas) throws Exception {
        Path f = Path.of(dataDir).resolve(PaymentDateSurveyService.FILE);
        Files.createDirectories(f.getParent());
        Files.writeString(f, "{\"linhas\": [" + linhas + "]}", StandardCharsets.UTF_8);
    }

    private static String linha(String ticker, String com, String valor, String pagamento) {
        return "{\"ticker\": \"" + ticker + "\", \"ticker_fracionario\": null, \"tipo\": \"JCP\", \"data_com\": \"" + com
                + "\", \"valor_por_acao\": \"" + valor + "\", \"parcela\": 1, \"data_pagamento\": "
                + (pagamento == null ? "null" : "\"" + pagamento + "\"") + "}";
    }

    @Test
    @DisplayName("Completa o evento sem data (origem LEVANTAMENTO), mantem a data que ja existe e nao grava o A DEFINIR")
    void aplica() throws Exception {
        evento("PETR4", "2013-04-29", "0.38617", null);
        evento("PETR4", "2013-11-01", "0.10", "2013-11-29");
        evento("CMIG4", "2012-12-26", "0.50", null);
        arquivo(String.join(",",
                linha("PETR4", "2013-04-29", "0.38617", "2013-05-27"),
                linha("PETR4", "2013-11-01", "0.10", "2013-12-02"),
                linha("CMIG4", "2012-12-26", "0.50", null),
                linha("VALE3", "2010-01-04", "0.20", "2010-01-29")));

        PaymentDateSurveyService.Result r = service.run("teste");

        assertThat(data("PETR4", "2013-04-29")).containsEntry("payment_date", java.sql.Date.valueOf("2013-05-27"))
                .containsEntry("payment_date_source", "LEVANTAMENTO");
        assertThat(data("PETR4", "2013-11-01")).containsEntry("payment_date", java.sql.Date.valueOf("2013-11-29"))
                .containsEntry("payment_date_source", null);
        assertThat(data("CMIG4", "2012-12-26")).containsEntry("payment_date", null);
        assertThat(r.filled()).isEqualTo(1);
        assertThat(r.alreadyDifferent()).isEqualTo(1);
        assertThat(r.toBeDefined()).isEqualTo(1);
        assertThat(r.notFound()).isEqualTo(1);
    }

    @Test
    @DisplayName("Rodar de novo nao muda nada; a data da propria fonte do evento substitui a do levantamento")
    void idempotenteEFonteManda() throws Exception {
        evento("PETR4", "2013-04-29", "0.38617", null);
        arquivo(linha("PETR4", "2013-04-29", "0.38617", "2013-05-27"));
        service.run("teste");

        assertThat(service.run("teste").filled()).isZero();

        evento("PETR4", "2013-04-29", "0.38617", "2013-05-28");
        assertThat(data("PETR4", "2013-04-29")).containsEntry("payment_date", java.sql.Date.valueOf("2013-05-28"))
                .containsEntry("payment_date_source", null);
    }
}
