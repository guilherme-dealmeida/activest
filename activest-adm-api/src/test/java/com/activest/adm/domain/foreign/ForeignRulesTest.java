package com.activest.adm.domain.foreign;

import com.activest.adm.integration.foreign.NasdaqTraderClient.Listing;
import com.activest.adm.integration.foreign.OpenFigiClient.Figi;
import com.activest.domain.asset.ForeignExchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras do catálogo vivo dos EUA (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, C1–C4), com nomes reais da lista. */
class ForeignRulesTest {

    private static Listing stock(String symbol, String name) {
        return new Listing(symbol, name, ForeignExchange.NYSE, "N", false, false, null, symbol);
    }

    private static Listing etf(String symbol, String name) {
        return new Listing(symbol, name, ForeignExchange.NYSE_ARCA, "P", true, false, null, symbol);
    }

    private static Figi figi(String type) {
        return new Figi("BBG000X", "BBG000X", "X", "X", "US", type, null, "Equity", null);
    }

    @Test
    @DisplayName("C1: warrant, right, unit de SPAC, nota e papel estruturado ficam fora pelo nome; MLP e preferencial não")
    void exclusions() {
        assertThat(ForeignRules.excludedByName(stock("ACAHW", "Atlantic Coastal Acquisition Corp. II Warrant")))
                .isEqualTo("warrant");
        assertThat(ForeignRules.excludedByName(stock("XYZR", "XYZ Acquisition Corp Right"))).isEqualTo("right");
        assertThat(ForeignRules.excludedByName(stock("XYZU",
                "XYZ Acquisition Corp Units, each consisting of one Class A ordinary share and one-half of one warrant")))
                .isEqualTo("unit de SPAC");
        assertThat(ForeignRules.excludedByName(stock("ABCU", "ABC Acquisition Corp Unit"))).isEqualTo("unit de SPAC");
        assertThat(ForeignRules.excludedByName(stock("SOJE", "Southern Company (The) Series 2020A 4.95% Junior "
                + "Subordinated Notes due January 30, 2080"))).isEqualTo("nota ou título de dívida");
        assertThat(ForeignRules.excludedByName(stock("EPD",
                "Enterprise Products Partners L.P. Common Units representing Limited Partner Interests"))).isNull();
        assertThat(ForeignRules.excludedByName(stock("ABR$E",
                "Arbor Realty Trust 6.25% Series E Cumulative Redeemable Preferred Stock"))).isNull();
        assertThat(ForeignRules.excludedByName(etf("FLOT", "iShares Floating Rate Bond ETF due 2030 Notes"))).isNull();
    }

    @Test
    @DisplayName("C2: ETF da bolsa, preferencial pelo símbolo, depois o OpenFIGI (REIT, ETP e fundo fechado, ação); warrant do OpenFIGI fora")
    void types() {
        assertThat(ForeignRules.type(etf("SPY", "SPDR"), figi("ETP")))
                .isEqualTo(new ForeignRules.Typed("ETF_INTERNACIONAL", "OPENFIGI"));
        assertThat(ForeignRules.type(etf("SPY", "SPDR"), null)).isEqualTo(new ForeignRules.Typed("ETF_INTERNACIONAL", "LIST"));
        assertThat(ForeignRules.type(stock("ABR$E", "Arbor pref"), null)).isEqualTo(new ForeignRules.Typed("STOCK", "LIST"));
        assertThat(ForeignRules.type(stock("PLD", "Prologis"), figi("REIT")).type()).isEqualTo("REIT");
        assertThat(ForeignRules.type(stock("CXW", "CoreCivic"), figi("Common Stock")).type()).isEqualTo("STOCK");
        assertThat(ForeignRules.type(stock("BABA", "Alibaba"), figi("ADR")).type()).isEqualTo("STOCK");
        assertThat(ForeignRules.type(stock("PDI", "PIMCO Dynamic Income Fund"), figi("Closed-End Fund")).type())
                .isEqualTo("ETF_INTERNACIONAL");
        assertThat(ForeignRules.type(stock("XX", "X"), figi("Equity WRT"))).isNull();
        assertThat(ForeignRules.whyNot(figi("Equity WRT"))).isEqualTo("OpenFIGI: Equity WRT");
        assertThat(ForeignRules.type(stock("XX", "X"), null)).isNull();
        assertThat(ForeignRules.whyNot(null)).isEqualTo("sem FIGI no OpenFIGI");
        // preferencial da Nasdaq: símbolo sem $, o nome diz; o ETN fica com o OpenFIGI
        Listing agncl = new Listing("AGNCL", "AGNC Investment Corp. - Depositary Shares Each Representing a 1/1,000th "
                + "Interest in a Share of 7.75% Series G Fixed-Rate Reset Cumulative Redeemable Preferred Stock",
                ForeignExchange.NASDAQ, "Q", false, false, "N", null);
        assertThat(ForeignRules.type(agncl, null)).isEqualTo(new ForeignRules.Typed("STOCK", "LIST"));
        assertThat(ForeignRules.preferred(agncl)).isTrue();
        Listing grn = stock("GRN", "iPath Series B Carbon Exchange-Traded Notes");
        assertThat(ForeignRules.excludedByName(grn)).isNull();
        assertThat(ForeignRules.type(grn, figi("ETP")).type()).isEqualTo("ETF_INTERNACIONAL");
    }

    @Test
    @DisplayName("C3: BRK.B e ABR-E no catálogo; BRK/B no OpenFIGI; BRK-B e ABR-PE na SEC")
    void tickers() {
        assertThat(ForeignRules.ticker("BRK.B")).isEqualTo("BRK.B");
        assertThat(ForeignRules.ticker("ABR$E")).isEqualTo("ABR-E");
        assertThat(ForeignRules.bloomberg("BRK.B")).isEqualTo("BRK/B");
        assertThat(ForeignRules.bloomberg("ABR$E")).isNull();
        assertThat(ForeignRules.secTicker("BRK.B")).isEqualTo("BRK-B");
        assertThat(ForeignRules.secTicker("ABR$E")).isEqualTo("ABR-PE");
        assertThat(ForeignRules.root("ABR$E")).isEqualTo("ABR");
        assertThat(ForeignRules.root("BRK.B")).isEqualTo("BRK");
    }

    @Test
    @DisplayName("Nome sem a descrição do papel; o casamento com o BDR ignora Inc., pontuação e a letra da classe")
    void names() {
        assertThat(ForeignRules.displayName(stock("AAPL", "Apple Inc. - Common Stock"))).isEqualTo("Apple Inc.");
        assertThat(ForeignRules.displayName(stock("META", "Meta Platforms, Inc. - Class A Common Stock")))
                .isEqualTo("Meta Platforms, Inc.");
        assertThat(ForeignRules.displayName(stock("BRK.B", "Berkshire Hathaway Inc. New Common Stock")))
                .isEqualTo("Berkshire Hathaway Inc.");
        assertThat(ForeignRules.displayName(stock("BABA", "Alibaba Group Holding Limited American Depositary Shares "
                + "each representing eight Ordinary share"))).isEqualTo("Alibaba Group Holding Limited");
        assertThat(ForeignRules.displayName(stock("TSM", "Taiwan Semiconductor Manufacturing Company Ltd.")))
                .isEqualTo("Taiwan Semiconductor Manufacturing Company Ltd.");
        assertThat(ForeignRules.displayName(etf("SPY", "State Street SPDR S&P 500 ETF Trust")))
                .isEqualTo("State Street SPDR S&P 500 ETF Trust");
        assertThat(ForeignRules.companyKey("Berkshire Hathaway Inc. B"))
                .isEqualTo(ForeignRules.companyKey("Berkshire Hathaway Inc."));
        assertThat(ForeignRules.companyKey("Meta Platforms Inc")).isEqualTo(ForeignRules.companyKey("Meta Platforms, Inc."));
        assertThat(ForeignRules.companyKey("JPMORGAN CHASE & CO")).isEqualTo("JPMORGAN CHASE AND");
    }

    @Test
    @DisplayName("C4: categoria do ETF pelas palavras, alavancado primeiro; Short Treasury é renda fixa, Short S&P é inverso")
    void etfCategories() {
        assertThat(ForeignRules.etfCategory("Direxion Daily Semiconductor Bull 3X Shares")).isEqualTo("ALAVANCADOS_INVERSOS");
        assertThat(ForeignRules.etfCategory("ProShares UltraShort 20+ Year Treasury")).isEqualTo("ALAVANCADOS_INVERSOS");
        assertThat(ForeignRules.etfCategory("ProShares Short S&P500")).isEqualTo("ALAVANCADOS_INVERSOS");
        assertThat(ForeignRules.etfCategory("iShares Short Treasury Bond ETF")).isEqualTo("RENDA_FIXA");
        assertThat(ForeignRules.etfCategory("JPMorgan Ultra-Short Income ETF")).isEqualTo("RENDA_FIXA");
        assertThat(ForeignRules.etfCategory("iShares Bitcoin Trust ETF")).isEqualTo("CRIPTO");
        assertThat(ForeignRules.etfCategory("SPDR Gold Shares")).isEqualTo("OURO_COMMODITIES");
        assertThat(ForeignRules.etfCategory("Vanguard Real Estate ETF")).isEqualTo("IMOBILIARIO");
        assertThat(ForeignRules.etfCategory("Schwab U.S. Dividend Equity ETF")).isEqualTo("DIVIDENDOS_RENDA");
        assertThat(ForeignRules.etfCategory("Innovator U.S. Equity Power Buffer ETF - October")).isEqualTo("OUTROS_ESTRATEGIA");
        assertThat(ForeignRules.etfCategory("iShares Core MSCI Emerging Markets ETF")).isEqualTo("ACOES_INTERNACIONAIS");
        assertThat(ForeignRules.etfCategory("Vanguard S&P 500 ETF")).isEqualTo("ACOES_EUA");
        assertThat(ForeignRules.etfCategory("Invesco QQQ Trust, Series 1")).isEqualTo("ACOES_EUA");
        assertThat(ForeignRules.etfCategory("Roundhill Generative AI & Technology")).isEqualTo("ACOES_EUA");
        assertThat(ForeignRules.etfCategory("Vanguard Communication Services ETF")).isEqualTo("ACOES_EUA");
        assertThat(ForeignRules.etfCategory("State Street SPDR Russell 1000 Low Volatility Focus ETF")).isEqualTo("ACOES_EUA");
        assertThat(ForeignRules.etfCategory("Principal Inflation Protection ETF")).isEqualTo("RENDA_FIXA");
        assertThat(ForeignRules.etfCategory("Pacer Swan SOS Flex (January) ETF")).isEqualTo("OUTROS_ESTRATEGIA");
    }

    @Test
    @DisplayName("C4: grupo do REIT pelo setor da Nareit; Industrial/Office dos anos antigos pelo subsetor")
    void reitCategories() {
        assertThat(ForeignRules.reitCategory("Industrial", null)).isEqualTo("INDUSTRIAL");
        assertThat(ForeignRules.reitCategory("Mortgage", "Home Financing")).isEqualTo("HIPOTECAS");
        assertThat(ForeignRules.reitCategory("Telecommunications", null)).isEqualTo("TORRES_TELECOM");
        assertThat(ForeignRules.reitCategory("Timberland", null)).isEqualTo("ESPECIAIS");
        assertThat(ForeignRules.reitCategory("Industrial/Office", "Office")).isEqualTo("ESCRITORIOS");
        assertThat(ForeignRules.reitCategory("Industrial/Office", "Mixed")).isEqualTo("DIVERSIFICADOS");
        assertThat(ForeignRules.reitCategory("Unknown", null)).isNull();
    }

    @Test
    @DisplayName("Tabela SIC → setor: 7372 software, 2834 farmacêutica, 6798 REIT em Finanças, 8880 (ADR) sem setor")
    void sicTable() {
        SicCategories sic = new SicCategories();
        assertThat(sic.category("7372")).isEqualTo("SERVICOS_DE_TECNOLOGIA");
        assertThat(sic.category("2834")).isEqualTo("TECNOLOGIA_EM_SAUDE");
        assertThat(sic.category("6798")).isEqualTo("FINANCAS");
        assertThat(sic.category("100")).isEqualTo("INDUSTRIA_DE_TRANSFORMACAO");
        assertThat(sic.category("8880")).isNull();
        assertThat(sic.description("3674")).isEqualTo("SEMICONDUCTORS & RELATED DEVICES");
        // código antigo que a SEC ainda usa: o da tabela, ou o setor mais comum do grupo
        assertThat(sic.category("6200")).isEqualTo("FINANCAS");
        assertThat(sic.category("3699")).isEqualTo("TECNOLOGIA_ELETRONICA");
    }
}
