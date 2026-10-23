package com.primevest.adm.domain.dividend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.primevest.adm.domain.dividend.PaymentDateSurveyService.Event;
import com.primevest.adm.domain.dividend.PaymentDateSurveyService.Line;
import com.primevest.adm.domain.dividend.PaymentDateSurveyService.Matching;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Leitura do arquivo e casamento pela chave natural (docs/planos/PLANO_CARGA_INICIAL.md, 11 — L2). */
class PaymentDateSurveyServiceTest {

    private static final LocalDate COM = LocalDate.of(2015, 12, 11);

    private static Line row(String ticker, String fracionario, String value, String payment) {
        return new Line(ticker, fracionario, "JCP", COM, new BigDecimal(value), (short) 1,
                payment == null ? null : LocalDate.parse(payment));
    }

    private static Event eventRow(long id, String ticker, String value) {
        return new Event(id, 100 + id, ticker, "JCP", COM, (short) 1, new BigDecimal(value), null);
    }

    @Test
    @DisplayName("Casa no ticker e no fracionario; sem fracionario na linha, tenta o ticker + F")
    void fracionario() {
        Matching m = PaymentDateSurveyService.match(
                List.of(row("ODER4", null, "0.07", "2022-07-20"), row("AZEV4", "AZEV4F", "0.0064", "2016-01-15")),
                List.of(eventRow(1, "ODER4F", "0.07000000000"), eventRow(2, "AZEV4", "0.00640000000"),
                        eventRow(3, "AZEV4F", "0.00640000000")));

        assertThat(m.matches()).extracting(x -> x.event().id()).containsExactly(1L, 2L, 3L);
        assertThat(m.notFound()).isEmpty();
    }

    @Test
    @DisplayName("Valor exato primeiro; dentro da tolerancia, o mais proximo; cada evento uma vez so")
    void closestValue() {
        // AZEV3 11/12/2015: o banco tem dois eventos que diferem na 9a casa, e a planilha uma linha de cada
        Matching m = PaymentDateSurveyService.match(
                List.of(row("AZEV3", "-", "0.006419042", "2016-01-15"), row("AZEV3", "-", "0.006419041", "2016-01-15")),
                List.of(eventRow(1, "AZEV3", "0.00641904100"), eventRow(2, "AZEV3", "0.00641904200")));

        assertThat(m.matches()).extracting(x -> x.event().id()).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("Fora da tolerancia, em outro tipo ou em outra data-com: sem evento")
    void withoutEvent() {
        Line l = row("PETR4", "PETR4F", "0.50", "2013-05-27");
        Matching m = PaymentDateSurveyService.match(List.of(l), List.of(
                eventRow(1, "PETR4", "0.501"),
                new Event(2, 2, "PETR4", "DIVIDENDO", COM, (short) 1, new BigDecimal("0.50"), null),
                new Event(3, 3, "PETR4", "JCP", COM.plusDays(1), (short) 1, new BigDecimal("0.50"), null)));

        assertThat(m.matches()).isEmpty();
        assertThat(m.notFound()).containsExactly(l);
    }

    @Test
    @DisplayName("Le o arquivo: A DEFINIR vem sem data; fracionario vazio vira nulo")
    void readsFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(PaymentDateSurveyService.FILE);
        Files.createDirectories(f.getParent());
        Files.writeString(f, """
                {"origem": "x.xlsx", "linhas": [
                 {"ticker": "BBDC3", "ticker_fracionario": "BBDC3F", "isin": "BRBBDCACNOR1", "tipo": "JCP",
                  "data_com": "2016-09-30", "valor_por_acao": "0.57112346600", "parcela": 1,
                  "data_pagamento": "2017-03-08", "status": "CONFIRMADO", "fonte": "SEC 6-K", "regra": ""},
                 {"ticker": "ODER4", "ticker_fracionario": null, "isin": null, "tipo": "DIVIDENDO",
                  "data_com": "2013-04-25", "valor_por_acao": "0.00374046000", "parcela": 2,
                  "data_pagamento": null, "status": "A_DEFINIR", "fonte": null, "regra": ""}]}""");
        PaymentDateSurveyService s = new PaymentDateSurveyService(null, new ObjectMapper(), null, dir.toString());

        List<Line> l = s.read(f);

        assertThat(l).containsExactly(
                new Line("BBDC3", "BBDC3F", "JCP", LocalDate.of(2016, 9, 30), new BigDecimal("0.57112346600"),
                        (short) 1, LocalDate.of(2017, 3, 8)),
                new Line("ODER4", null, "DIVIDENDO", LocalDate.of(2013, 4, 25), new BigDecimal("0.00374046000"),
                        (short) 2, null));
    }

    @Test
    @DisplayName("Sem o arquivo (data pull nao feito): nada a fazer, sem erro")
    void withoutFile(@TempDir Path dir) {
        PaymentDateSurveyService s = new PaymentDateSurveyService(null, new ObjectMapper(), null, dir.toString());

        assertThat(s.run("teste").lines()).isZero();
    }
}
