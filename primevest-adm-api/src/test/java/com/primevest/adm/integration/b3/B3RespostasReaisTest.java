package com.primevest.adm.integration.b3;

import com.primevest.domain.corporateaction.StockEventType;
import com.primevest.domain.dividend.DividendType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O formato das respostas reais da B3 e do FNET (docs/planos/PLANO_BRAPI.md, F5 e A12).
 */
class B3RespostasReaisTest {

    private final B3Client b3 = B3Fixtures.client();

    @Test
    @DisplayName("PETR: dividendo e dois JCP com a mesma data-com, valor por acao e pagamento")
    void petrobrasProventos() {
        B3Supplement petr = b3.companySupplement("PETR");

        assertThat(petr.codeCVM()).isEqualTo("9512");
        assertThat(petr.tradingName().trim()).isEqualTo("PETROBRAS");
        B3Supplement.CashDividend dividendo = petr.cash().stream()
                .filter(d -> "BRPETRACNOR9".equals(d.isinCode()) && "DIVIDENDO".equals(d.label())
                        && "21/08/2026".equals(d.lastDatePrior()))
                .findFirst().orElseThrow();
        assertThat(B3Values.decimal(dividendo.rate())).isEqualByComparingTo("0.47156696");
        assertThat(B3Values.date(dividendo.paymentDate())).isEqualTo(LocalDate.of(2026, 12, 21));
        assertThat(DividendType.fromSourceLabel("JRS CAP PROPRIO")).isEqualTo(DividendType.JCP);
    }

    @Test
    @DisplayName("A12: fatores reais — desdobramento e bonificacao em %, grupamento como multiplicador")
    void fatoresReais() {
        List<B3Supplement.StockDividend> mglu = b3.companySupplement("MGLU").stock();

        assertThat(mglu).extracting(s -> StockEventType.fromSourceLabel(s.label()))
                .containsExactly(StockEventType.BONIFICACAO, StockEventType.GRUPAMENTO, StockEventType.DESDOBRAMENTO);
        assertThat(mglu).extracting(s -> B3Values.decimal(s.factor()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("5"), new BigDecimal("0.1"), new BigDecimal("300"));
    }

    @Test
    @DisplayName("Rotulos de evento que nao mexem na quantidade ficam de fora (RESG TOTAL RV)")
    void rotulosIgnorados() {
        assertThat(b3.companySupplement("ITSA").stock()).extracting(s -> StockEventType.fromSourceLabel(s.label()))
                .containsExactly(StockEventType.BONIFICACAO, StockEventType.BONIFICACAO, null, null);
    }

    @Test
    @DisplayName("GetDetail: ticker ↔ ISIN, e a unit tem ISIN proprio (TAEE11)")
    void tickerParaIsin() {
        assertThat(b3.companyDetail("9512").codes())
                .contains(new B3CompanyDetail.OtherCode("PETR4", "BRPETRACNPR6"));
        assertThat(b3.companyDetail("20257").codes())
                .contains(new B3CompanyDetail.OtherCode("TAEE11", "BRTAEECDAM10"));
    }

    @Test
    @DisplayName("Fundo: nome de pregao em 'fund', cota BR...CTF e recibos com outro ISIN")
    void fundoMxrf() {
        B3Supplement mxrf = b3.fundSupplement("MXRF");

        assertThat(mxrf.tradingName()).isEqualTo("FII MAXI REN");
        assertThat(mxrf.cash()).extracting(B3Supplement.CashDividend::isinCode)
                .contains("BRMXRFCTF008", "BRMXRFR26M14");
        assertThat(mxrf.cash()).filteredOn(d -> "BRMXRFCTF008".equals(d.isinCode()))
                .allSatisfy(d -> assertThat(d.label()).isEqualTo("RENDIMENTO"));
    }

    @Test
    @DisplayName("ETF que paga rendimento (DIVD11) vem pelo mesmo endpoint de fundos")
    void etfDivd() {
        assertThat(b3.fundSupplement("DIVD").cash()).isNotEmpty()
                .allSatisfy(d -> assertThat(d.isinCode()).isEqualTo("BRDIVDCTF005"));
    }

    @Test
    @DisplayName("BDR (AAPL34): empresa, com ISIN de BDR")
    void bdrApple() {
        assertThat(b3.companySupplement("AAPL").cash()).isNotEmpty()
                .allSatisfy(d -> assertThat(d.isinCode()).isEqualTo("BRAAPLBDR004"));
    }

    @Test
    @DisplayName("Historico longo: especie no lugar do ISIN, valor por lote e paginacao")
    void historicoLongo() {
        B3HistoryPage page = b3.cashDividendsHistory("PETROBRAS", 1, 20);

        assertThat(page.page().totalRecords()).isEqualTo(343);
        B3HistoryPage.Item primeiro = page.items().get(0);
        assertThat(primeiro.typeStock()).isEqualTo("ON");
        assertThat(primeiro.corporateAction()).isEqualTo("JRS CAP PROPRIO");
        assertThat(primeiro.quotedPerShares()).isEqualTo("1");
    }

    @Test
    @DisplayName("Emissor desconhecido: a B3 responde vazio e o cliente acusa, sem inventar dado")
    void emissorDesconhecido() {
        assertThatThrownBy(() -> b3.companySupplement("ZZZZ")).isInstanceOf(B3Exception.class);
    }

    @Test
    @DisplayName("FNET: aviso estruturado do MXRF11 com data-base, valor, pagamento e isencao")
    void avisoFnet() {
        List<FnetProvento> proventos = FnetClient.parse(B3Fixtures.read("fnet_MXRF_1305736.xml"));

        assertThat(proventos).singleElement().satisfies(p -> {
            assertThat(p.isin()).isEqualTo("BRMXRFCTF008");
            assertThat(p.ticker()).isEqualTo("MXRF11");
            assertThat(p.amortization()).isFalse();
            assertThat(p.dataBase()).isEqualTo(LocalDate.of(2026, 8, 31));
            assertThat(p.value()).isEqualByComparingTo("0.1");
            assertThat(p.paymentDate()).isEqualTo(LocalDate.of(2026, 9, 15));
            assertThat(p.incomeTaxExempt()).isTrue();
        });
    }

    @Test
    @DisplayName("Numeros e datas no formato da B3; 31/12/9999 e 'sem data'")
    void valores() {
        assertThat(B3Values.decimal("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(B3Values.decimal("0,10000000000")).isEqualByComparingTo("0.1");
        assertThat(B3Values.date("31/12/9999")).isNull();
        assertThat(B3Values.date("21/08/2026")).isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    @DisplayName("Com Accept: application/json a B3 embrulha o JSON numa string: o cliente desembrulha")
    void respostaEmbrulhada() throws Exception {
        String wrapped = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(B3Fixtures.read("fund_MXRF.json"));
        org.springframework.web.reactive.function.client.WebClient.Builder builder =
                org.springframework.web.reactive.function.client.WebClient.builder().exchangeFunction(req ->
                        reactor.core.publisher.Mono.just(org.springframework.web.reactive.function.client.ClientResponse
                                .create(org.springframework.http.HttpStatus.OK)
                                .header("Content-Type", "application/json").body(wrapped).build()));
        B3Client client = new B3Client(builder, "https://x", 5, new com.fasterxml.jackson.databind.ObjectMapper());

        assertThat(client.fundSupplement("MXRF").tradingName()).isEqualTo("FII MAXI REN");
    }
}
