package com.activest.functional;

import com.activest.domain.catalog.CatalogChange;
import com.activest.domain.portfolio.PositionRefreshService;
import com.activest.domain.user.User;
import com.activest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A carteira reage ao "catalogo mudou" da adm-api (docs/planos/PLANO_ADM_API.md, 10.3): o evento entra no
 * catalogo depois da compra, e so o recalculo da app-api o transforma em provento do usuario.
 */
class PositionRefreshIT extends FunctionalBase {

    @Autowired private PositionRefreshService positionRefreshService;

    private User owner;
    private Long fii;

    @BeforeEach
    void scenario() {
        owner = userWithPortfolio("carteira@activest.test");
        fii = jdbc.queryForObject("SELECT id FROM assets WHERE ticker = ?", Long.class, CatalogIds.FII);
        recordSuccessfully(owner, buy(CatalogIds.FII, "2026-08-01", "10", "160.00"));
        // como a rotina da adm-api gravaria: so catalogo, nenhuma posicao tocada
        jdbc.update("""
            INSERT INTO asset_cash_events (asset_id, type, label, data_com, payment_date, value_per_share, source)
            VALUES (?, 'RENDIMENTO', 'RENDIMENTO', DATE '2026-08-29', DATE '2026-09-15', 1.10, 'B3')""", fii);
    }

    private BigDecimal sumDividends() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(total_amount), 0) FROM dividends", BigDecimal.class);
    }

    @Test
    @DisplayName("PROVENTOS: o evento novo do catalogo vira provento na posicao so quando a carteira e avisada")
    void dividends() {
        assertThat(sumDividends()).isZero();

        PositionRefreshService.Result r = positionRefreshService.refresh(
                new CatalogChange(CatalogChange.Origin.PROVENTOS, Set.of(fii)));

        assertThat(r.positions()).isEqualTo(1);
        assertThat(r.newDividends()).isEqualTo(1);
        assertThat(sumDividends()).isEqualByComparingTo("11.00");
    }

    @Test
    @DisplayName("FECHAMENTO: preco novo so revaloriza, nao gera provento")
    void closing() {
        jdbc.update("UPDATE assets SET current_price = 170.00 WHERE id = ?", fii);

        positionRefreshService.refresh(new CatalogChange(CatalogChange.Origin.FECHAMENTO, Set.of(fii)));

        assertThat(jdbc.queryForObject("SELECT appreciation FROM portfolio_assets WHERE asset_id = ?",
                BigDecimal.class, fii)).isEqualByComparingTo("100.00");
        assertThat(sumDividends()).isZero();
    }

    @Test
    @DisplayName("Rede de seguranca diaria: sem mensagem nenhuma, recalcular tudo acerta a carteira")
    void recalculatesAll() {
        positionRefreshService.refreshAll("teste");

        assertThat(sumDividends()).isEqualByComparingTo("11.00");
    }
}
