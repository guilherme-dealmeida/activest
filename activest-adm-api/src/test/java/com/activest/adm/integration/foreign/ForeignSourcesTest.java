package com.activest.adm.integration.foreign;

import com.activest.domain.asset.ForeignExchange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Leitura das fontes do exterior com arquivos reais gravados (recortes de 29/10/2026) — docs/planos/
 * PLANO_ATIVOS_INTERNACIONAIS.md, F3: Nasdaq Trader, SEC (tickers, índice de formulários, séries e classes), OpenFIGI e
 * Nareit.
 */
class ForeignSourcesTest {

    @TempDir
    Path tmp;

    private static Path resource(String name) throws Exception {
        return Path.of(ForeignSourcesTest.class.getResource("/foreign/" + name).toURI());
    }

    @Test
    @DisplayName("Nasdaq Trader: bolsa pela D1 (TXSE fica de fora), ETF, preferencial com $ e test issue")
    void nasdaqTrader() throws Exception {
        Map<String, NasdaqTraderClient.Listing> bySymbol = NasdaqTraderClient.parse(resource("nasdaqtraded.txt"))
                .stream().collect(Collectors.toMap(NasdaqTraderClient.Listing::symbol, l -> l));

        assertThat(bySymbol).hasSize(11);
        assertThat(bySymbol.get("AAPL").exchange()).isEqualTo(ForeignExchange.NASDAQ);
        assertThat(bySymbol.get("BRK.B").exchange()).isEqualTo(ForeignExchange.NYSE);
        assertThat(bySymbol.get("SPY")).satisfies(l -> {
            assertThat(l.exchange()).isEqualTo(ForeignExchange.NYSE_ARCA);
            assertThat(l.etf()).isTrue();
        });
        assertThat(bySymbol.get("ABR$E").preferred()).isTrue();
        assertThat(bySymbol.get("ABR$E").cqsSymbol()).isEqualTo("ABRpE");
        assertThat(bySymbol.get("ZZZTX").testIssue()).isTrue();
        assertThat(bySymbol.get("CECO")).satisfies(l -> {
            assertThat(l.exchangeCode()).isEqualTo("F");
            assertThat(l.exchange()).isNull();
        });
    }

    @Test
    @DisplayName("SEC: tickers com bolsa, Form 25 do índice trimestral (colunas fixas) e séries e classes com aspas")
    void sec() throws Exception {
        SecClient sec = new SecClient("https://www.sec.gov", "https://data.sec.gov", tmp, null);
        List<SecClient.Ticker> tickers = sec.parseCompanyTickers(resource("company_tickers_exchange.json"));
        assertThat(tickers).hasSize(20).anySatisfy(t -> {
            assertThat(t.ticker()).isEqualTo("AAPL");
            assertThat(t.cik()).isEqualTo("320193");
            assertThat(t.exchange()).isEqualTo("Nasdaq");
        });

        List<SecClient.FormEntry> forms = SecClient.parseFormIndex(resource("form.idx"), Set.of("25", "25-NSE"));
        assertThat(forms).hasSize(7);
        assertThat(forms).filteredOn(f -> f.cik().equals("874761")).singleElement().satisfies(f -> {
            assertThat(f.formType()).isEqualTo("25-NSE");
            assertThat(f.company()).isEqualTo("AES CORP");
            assertThat(f.filed()).hasToString("2024-02-15");
            assertThat(f.accession()).isEqualTo("0000876661-24-000102");
        });

        List<SecClient.FundClass> classes = SecClient.parseSeriesClass(resource("series_class.csv"));
        assertThat(classes).extracting(SecClient.FundClass::ticker).contains("VOO", "IVV");
        assertThat(classes).filteredOn(c -> c.ticker().equals("VOO")).singleElement().satisfies(c -> {
            assertThat(c.cik()).isEqualTo("36405");
            assertThat(c.seriesId()).isEqualTo("S000002839");
            assertThat(c.seriesName()).isEqualTo("Vanguard 500 Index Fund");
        });
        assertThat(SecClient.cik10("320193")).isEqualTo("0000320193");
        assertThat(SecClient.userAgent("Activest <contato@exemplo.com.br>")).isEqualTo("Activest/1.0 contato@exemplo.com.br");
    }

    @Test
    @DisplayName("OpenFIGI: o tipo vem do securityType (Common Stock, REIT, ETP); sem resposta, nada")
    void openFigi() throws Exception {
        JsonNode answer = new ObjectMapper().readTree(resource("openfigi_mapping.json").toFile());

        assertThat(OpenFigiClient.pick(answer.get(0))).get().satisfies(f -> {
            assertThat(f.ticker()).isEqualTo("AAPL");
            assertThat(f.compositeFigi()).isEqualTo("BBG000B9XRY4");
            assertThat(f.securityType()).isEqualTo("Common Stock");
        });
        assertThat(OpenFigiClient.pick(answer.get(1))).get().extracting(OpenFigiClient.Figi::securityType).isEqualTo("REIT");
        assertThat(OpenFigiClient.pick(answer.get(2))).get().extracting(OpenFigiClient.Figi::securityType).isEqualTo("ETP");
        assertThat(OpenFigiClient.pick(answer.get(3))).get().extracting(OpenFigiClient.Figi::ticker).isEqualTo("BRK/B");
        assertThat(OpenFigiClient.pick(answer.get(6))).isEmpty();

        OpenFigiClient client = new OpenFigiClient(URI.create("http://localhost:1/nada"), "", tmp);
        assertThat(client.cacheFile("BRK/B").getFileName()).hasToString("BRK_B.json");
    }

    @Test
    @DisplayName("Nareit: REIT, ticker e setor por ano (formato de 2026 e de 2011, com subsetor)")
    void nareit() throws Exception {
        List<NareitClient.Constituent> y2026 = NareitClient.parse(resource("FNUSIC2026.pdf"), 2026);
        List<NareitClient.Constituent> y2011 = NareitClient.parse(resource("FNUSIC2011.pdf"), 2011);

        assertThat(y2026).hasSize(180);
        assertThat(y2026).filteredOn(c -> c.ticker().equals("BXP")).singleElement().satisfies(c -> {
            assertThat(c.investmentSector()).isEqualTo("Equity");
            assertThat(c.propertySector()).isEqualTo("Office");
        });
        assertThat(y2026).filteredOn(c -> c.ticker().equals("PLD")).singleElement()
                .extracting(NareitClient.Constituent::propertySector).isEqualTo("Industrial");
        assertThat(y2026).filteredOn(c -> c.ticker().equals("SACH")).singleElement().satisfies(c -> {
            assertThat(c.investmentSector()).isEqualTo("Mortgage");
            assertThat(c.subsector()).isEqualTo("Commercial Financing");
        });
        assertThat(y2011).hasSize(160);
        assertThat(y2011).filteredOn(c -> c.ticker().equals("BXP")).singleElement().satisfies(c -> {
            assertThat(c.propertySector()).isEqualTo("Industrial/Office");
            assertThat(c.subsector()).isEqualTo("Office");
        });
    }
}
