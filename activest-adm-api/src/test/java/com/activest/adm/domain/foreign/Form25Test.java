package com.activest.adm.domain.foreign;

import com.activest.domain.asset.ForeignExchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Leitura do Form 25 e do ticker dos deslistados (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, C6), com trechos reais. */
class Form25Test {

    /** Trecho do 25-NSE da NYSE (AES, 15/02/2024) — o índice lista o formulário também no CIK da bolsa. */
    static final String NSE = """
            <SEC-HEADER>
            CONFORMED SUBMISSION TYPE:\t25-NSE
            SUBJECT COMPANY:\t
            \tCOMPANY DATA:\t
            \t\tCOMPANY CONFORMED NAME:\t\t\tAES CORP
            \t\tCENTRAL INDEX KEY:\t\t\t0000874761
            \t\tSTANDARD INDUSTRIAL CLASSIFICATION:\tCOGENERATION SERVICES & SMALL POWER PRODUCERS [4991]
            FILED BY:\t\t
            \tCOMPANY DATA:\t
            \t\tCOMPANY CONFORMED NAME:\t\t\tNEW YORK STOCK EXCHANGE LLC
            </SEC-HEADER>
            <notificationOfRemoval>
                <exchange><cik>0000876661</cik><entityName>NEW YORK STOCK EXCHANGE LLC</entityName></exchange>
                <issuer><cik>0000874761</cik><entityName>AES CORP</entityName></issuer>
                <descriptionClassSecurity>Common Stock</descriptionClassSecurity>
            </notificationOfRemoval>
            The New York Stock Exchange hereby notifies the SEC of its intention to remove the entire class of the stated
            securities from listing and registration on the Exchange at the opening of business on February 26, 2024,
            """;

    /** Trecho do Form 25 da própria empresa (HTML livre, Almaden Minerals, 25/03/2024). */
    static final String ISSUER = """
            <P><B>FORM 25</B></P><P><B>Almaden Minerals Ltd.</B></P>
            <P>(Exact name of Issuer as specified in its charter, and name of Exchange where security is listed)</P>
            <P><B>NYSE American LLC</B></P>
            <P><B>Common Shares</B></P>
            <P>(Description of class of securities)</P>
            STANDARD INDUSTRIAL CLASSIFICATION:\tMETAL MINING [1000]
            """;

    @Test
    @DisplayName("25-NSE: classe, bolsa, SIC e saída do anexo; o CIK é o do emissor, não o da bolsa")
    void exchangeForm() {
        Form25 f = Form25.parse(NSE, "0000876661-24-000102", "25-NSE", "876661", "NEW YORK STOCK EXCHANGE LLC",
                LocalDate.of(2024, 2, 15));
        assertThat(f.cik()).isEqualTo("874761");
        assertThat(f.securityClass()).isEqualTo("Common Stock");
        assertThat(f.exchange()).isEqualTo(ForeignExchange.NYSE);
        assertThat(f.sic()).isEqualTo("4991");
        assertThat(f.effective()).isEqualTo(LocalDate.of(2024, 2, 26));
        assertThat(ForeignDelistedService.kind(f)).isEqualTo("COMMON");
    }

    @Test
    @DisplayName("Form 25 da empresa: classe e bolsa pelo texto; sem anexo, a saída é o arquivamento + 10 dias")
    void issuerForm() {
        Form25 f = Form25.parse(ISSUER, "0001171843-24-001563", "25", "1015647", "ALMADEN MINERALS LTD",
                LocalDate.of(2024, 3, 25));
        assertThat(f.securityClass()).isEqualTo("Common Shares");
        assertThat(f.exchange()).isEqualTo(ForeignExchange.NYSE_AMERICAN);
        assertThat(f.effective()).isEqualTo(LocalDate.of(2024, 4, 4));
    }

    @Test
    @DisplayName("Espécie: ação e ADS são capital, fundo pelo nome; nota, preferencial e warrant ficam fora")
    void kinds() {
        assertThat(ForeignDelistedService.kind(form("American Depositary Shares", "MAKITA CORP"))).isEqualTo("COMMON");
        assertThat(ForeignDelistedService.kind(form("Common stock and warrants", "XYZ CORP"))).isEqualTo("COMMON");
        assertThat(ForeignDelistedService.kind(form("Arrow Reverse Cap 500 ETF (YPS)", "Arrow ETF Trust")))
                .isEqualTo("FUND");
        assertThat(ForeignDelistedService.kind(form("Common Share of beneficial interest", "NUVEEN OHIO MUNI FUND")))
                .isEqualTo("FUND");
        assertThat(ForeignDelistedService.kind(form("Common units representing limited partner interests", "VNR LLC")))
                .isEqualTo("COMMON");
        assertThat(ForeignDelistedService.kind(form("4.000% Notes due 2024", "AMERICAN HONDA FINANCE"))).isNull();
        assertThat(ForeignDelistedService.kind(form("6.60% Series D Preference Shares", "RENAISSANCERE"))).isNull();
        assertThat(ForeignDelistedService.kind(form("Warrants, each whole warrant exercisable", "XYZ CORP"))).isNull();
    }

    @Test
    @DisplayName("Um Form 25 de fundo com várias séries vira uma linha por série (acesso#n)")
    void fundParts() {
        java.util.List<Form25> parts = ForeignDelistedService.parts(form("iShares A ETF; iShares B ETF", "iSHARES TRUST"),
                "FUND");
        assertThat(parts).extracting(Form25::accession).containsExactly("a#1", "a#2");
        assertThat(parts).extracting(Form25::securityClass).containsExactly("iShares A ETF", "iShares B ETF");
        assertThat(ForeignDelistedService.parts(form("Common Stock", "X"), "COMMON")).hasSize(1);
    }

    @Test
    @DisplayName("Ticker: dei:TradingSymbol do XBRL inline; senão o texto (\"under the symbol\", \"(NYSE: XYZ)\")")
    void tickers() {
        String inline = "<td><span><ix:nonNumeric contextRef=\"c-1\" name=\"dei:TradingSymbol\" id=\"f-19\">AEY"
                + "</ix:nonNumeric></span></td>";
        assertThat(DelistedTickers.extract(inline, "u", true)).get()
                .extracting(DelistedTickers.Found::ticker, DelistedTickers.Found::source).containsExactly("AEY", "XBRL");
        assertThat(DelistedTickers.extract("is listed on the New York Stock Exchange under the symbol &#147;HNZ&#148;.",
                "u", false)).get().extracting(DelistedTickers.Found::ticker).isEqualTo("HNZ");
        assertThat(DelistedTickers.extract("Cymer, Inc. (NASDAQ: CYMI) today announced", "u", false)).get()
                .extracting(DelistedTickers.Found::source).isEqualTo("FILING_TEXT");
        assertThat(DelistedTickers.extract("no symbol here", "u", false)).isEmpty();
    }

    private static Form25 form(String cls, String company) {
        return new Form25("a", "25-NSE", "1", company, LocalDate.of(2013, 5, 1), LocalDate.of(2013, 5, 11), null,
                "NYSE", ForeignExchange.NYSE, cls);
    }
}
