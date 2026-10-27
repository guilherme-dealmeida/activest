package com.activest.functional;

import com.activest.domain.currency.ExchangeRateService;
import com.activest.domain.currency.FxRateOrigin;
import com.activest.domain.transaction.dto.TransactionResponseDTO;
import com.activest.domain.user.User;
import com.activest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transação em moeda estrangeira (docs/planos/PLANO_CADASTRO_MOEDAS.md, M3: C2, C3, C8). O catálogo não tem ativo em
 * dólar: o teste cria um fictício do tipo STOCK cotado em USD.
 */
class CurrencyTransactionsIT extends FunctionalBase {

    static final String USD_ASSET = "ZZUSD";

    @Autowired
    private ExchangeRateService exchangeRates;

    private User owner;

    @BeforeEach
    void scenario() {
        jdbc.update("DELETE FROM exchange_rates");
        jdbc.update("INSERT INTO exchange_rates (currency_code, reference_date, buy_rate, sell_rate) VALUES "
            + "('USD', ?, 4.9900, 5.0000), ('USD', ?, 5.1900, 5.2000)",
            Date.valueOf(LocalDate.of(2026, 9, 1)), Date.valueOf(LocalDate.of(2026, 9, 2)));
        exchangeRates.evict();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE ticker = ?", Long.class, USD_ASSET) == 0) {
            jdbc.update("INSERT INTO assets (ticker, name, asset_type_id, currency_code, current_price, active) "
                + "VALUES (?, 'Fictício em dólar', (SELECT id FROM asset_types WHERE name = 'STOCK'), 'USD', 110, true)",
                USD_ASSET);
        }
        owner = userWithPortfolio("moeda-" + System.nanoTime() + "@activest.test");
    }

    private Map<String, Object> usdBuy(String date, String shares, String price, String fee, String fxRate) {
        Map<String, Object> body = buy(USD_ASSET, date, shares, price);
        if (fee != null) {
            body.put("brokerageFee", new BigDecimal(fee));
        }
        if (fxRate != null) {
            body.put("fxRate", new BigDecimal(fxRate));
        }
        return body;
    }

    @Test
    @DisplayName("C2: compra em US$ sem câmbio usa a PTAX venda da data; grava o lançado e os reais")
    void ptaxDefault() {
        TransactionResponseDTO r = recordSuccessfully(owner, usdBuy("2026-09-01", "10", "100", "1", null));

        assertThat(r.pricePerShare()).isEqualByComparingTo("500");
        assertThat(r.totalAmount()).isEqualByComparingTo("5000.00");
        assertThat(r.brokerageFee()).isEqualByComparingTo("5.00");
        assertThat(r.currency()).isNotNull();
        assertThat(r.currency().code()).isEqualTo("USD");
        assertThat(r.currency().symbol()).isEqualTo("US$");
        assertThat(r.currency().fxRate()).isEqualByComparingTo("5");
        assertThat(r.currency().fxRateOrigin()).isEqualTo(FxRateOrigin.PTAX);
        assertThat(r.currency().originalPricePerShare()).isEqualByComparingTo("100");
        assertThat(r.currency().originalTotalAmount()).isEqualByComparingTo("1000.00");
        assertThat(r.currency().originalFee()).isEqualByComparingTo("1.00");

        // fim de semana/feriado: carrega o ultimo boletim (D27)
        TransactionResponseDTO sunday = recordSuccessfully(owner, usdBuy("2026-09-06", "1", "100", null, null));
        assertThat(sunday.currency().fxRate()).isEqualByComparingTo("5.2");
    }

    @Test
    @DisplayName("C2/C8: câmbio informado vale e a edição reconverte; ativo em reais recusa câmbio (C3)")
    void informedAndEdit() {
        TransactionResponseDTO r = recordSuccessfully(owner, usdBuy("2026-09-01", "10", "100", null, "5.1"));
        assertThat(r.currency().fxRateOrigin()).isEqualTo(FxRateOrigin.INFORMED);
        assertThat(r.totalAmount()).isEqualByComparingTo("5100.00");

        ResponseEntity<TransactionResponseDTO> edited = put(owner, "/api/v1/transactions/" + r.id(),
            usdBuy("2026-09-02", "10", "100", null, null), TransactionResponseDTO.class);
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody().totalAmount()).isEqualByComparingTo("5200.00");
        assertThat(edited.getBody().currency().fxRateOrigin()).isEqualTo(FxRateOrigin.PTAX);

        Map<String, Object> brl = buy(CatalogIds.STOCK, "2026-09-01", "10", "45.00");
        brl.put("fxRate", new BigDecimal("5"));
        assertThat(recordExpectingError(owner, brl).getStatusCode().is4xxClientError()).isTrue();
        brl.put("fxRate", BigDecimal.ONE);
        TransactionResponseDTO ok = recordSuccessfully(owner, brl);
        assertThat(ok.currency()).isNull();
        assertThat(ok.totalAmount()).isEqualByComparingTo("450");
    }

    @Test
    @DisplayName("Sem câmbio antes do início da série: recusa pedindo a taxa; com taxa informada aceita")
    void noRate() {
        ResponseEntity<String> err = recordExpectingError(owner, usdBuy("2020-01-02", "1", "100", null, null));
        assertThat(err.getStatusCode().is4xxClientError()).isTrue();
        assertThat(err.getBody()).contains("informe a taxa");
        assertThat(recordSuccessfully(owner, usdBuy("2020-01-02", "1", "100", null, "4.02")).totalAmount())
            .isEqualByComparingTo("402.00");
    }

    @Test
    @DisplayName("Banco: transação em reais com câmbio diferente de 1 é recusada pela constraint")
    void constraint() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "1", "45.00"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "UPDATE transactions SET fx_rate = 2 WHERE currency_code = 'BRL'"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Autowired
    private com.activest.domain.performance.PortfolioTimelineBuilder timelines;

    @Test
    @DisplayName("C4: posição em US$ valorizada pela cotação × câmbio do último boletim; a série da Rentabilidade bate")
    void valuation() {
        recordSuccessfully(owner, usdBuy("2026-09-01", "10", "100", null, null)); // 5.000,00 em reais
        jdbc.update("UPDATE assets SET current_price = 110 WHERE ticker = ?", USD_ASSET);
        jdbc.update("UPDATE portfolio_assets SET appreciation = 0 WHERE asset_id = (SELECT id FROM assets WHERE ticker = ?)",
            USD_ASSET);
        // o fechamento chega pela adm-api; aqui, a rede de seguranca regrava a valorizacao
        appreciationUpdater.refreshAll();

        Map<String, Object> p = positionOf(owner, USD_ASSET);
        assertThat(number(p.get("currentPrice"))).isEqualByComparingTo("572");       // 110 × 5,2
        assertThat(number(p.get("currentValue"))).isEqualByComparingTo("5720.00");
        assertThat(number(p.get("valorization"))).isEqualByComparingTo("720.00");
        @SuppressWarnings("unchecked")
        Map<String, Object> q = (Map<String, Object>) p.get("foreignQuote");
        assertThat(q.get("currencyCode")).isEqualTo("USD");
        assertThat(number(q.get("price"))).isEqualByComparingTo("110");
        assertThat(number(q.get("fxRate"))).isEqualByComparingTo("5.2");

        timelines.evictAll();
        var t = timelines.timeline(owner.getId());
        double[] gross = t.positions().get(0).gross();
        assertThat(gross[gross.length - 1]).isEqualTo(5720.00);
    }

    @Autowired
    private com.activest.domain.portfolio.AppreciationUpdater appreciationUpdater;
}
