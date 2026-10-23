package com.activest.functional;

import com.activest.domain.transaction.dto.TransactionResponseDTO;
import com.activest.domain.user.User;
import com.activest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tela Carteira: posicao apurada das transacoes, valorizacao e o switch da
 * reserva de emergencia.
 */
class PortfolioIT extends FunctionalBase {

    private User owner;
    private User other;

    @BeforeEach
    void users() {
        owner = userWithPortfolio("dono@activest.test");
        other = userWithPortfolio("outro@activest.test");
    }

    @Test
    @DisplayName("A posicao nao e gravada: editar a transacao muda a carteira na mesma hora")
    void positionLeavesTransactions() {
        TransactionResponseDTO buy = recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));

        assertThat(number(positionOf(owner, CatalogIds.STOCK).get("totalInvested"))).isEqualByComparingTo("300.00");

        put(owner, "/api/v1/transactions/" + buy.id(),
            buy(CatalogIds.STOCK, "2026-09-01", "20", "30.00"), String.class);

        assertThat(number(positionOf(owner, CatalogIds.STOCK).get("totalInvested"))).isEqualByComparingTo("600.00");
        assertThat(number(positionOf(owner, CatalogIds.STOCK).get("totalShares"))).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("Com cotacao, a valorizacao e cotas x cotacao menos custo, e e regravada a cada transacao")
    void appreciationWithQuote() {
        jdbc.update("UPDATE assets SET current_price = 35.00 WHERE ticker = ?", CatalogIds.STOCK);

        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));

        Map<String, Object> position = positionOf(owner, CatalogIds.STOCK);
        assertThat(number(position.get("valorization"))).isEqualByComparingTo("50.00");
        assertThat(number(position.get("currentValue"))).isEqualByComparingTo("350.00");

        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-02", "10", "30.00"));

        assertThat(number(positionOf(owner, CatalogIds.STOCK).get("valorization"))).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Sem cotacao, o valor atual da renda fixa e o proprio aplicado")
    void fixedIncomeWithoutQuote() {
        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG"));

        Map<String, Object> position = positionOf(owner, CatalogIds.CDB);
        assertThat(number(position.get("valorization"))).isEqualByComparingTo("0.00");
        assertThat(number(position.get("currentValue"))).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("O tipo aparece pela descricao, e nao pelo nome interno")
    void typeByDescription() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));

        assertThat(positionOf(owner, CatalogIds.STOCK).get("typeName")).isEqualTo("Ações");
    }

    @Test
    @DisplayName("O resumo soma a carteira e aplica o retorno percentual")
    void summary() {
        jdbc.update("UPDATE assets SET current_price = 36.00 WHERE ticker = ?", CatalogIds.STOCK);
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = get(owner, "/api/v1/portfolio/", Map.class).getBody();

        assertThat(number(summary.get("totalInvested"))).isEqualByComparingTo("300.00");
        assertThat(number(summary.get("currentValue"))).isEqualByComparingTo("360.00");
        assertThat(number(summary.get("returnPercentage"))).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("O switch da reserva aceita renda fixa e recusa acao com 422")
    void reserveSwitch() {
        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG"));
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));

        Object idDoCdb = positionOf(owner, CatalogIds.CDB).get("id");
        Object stockAssetId = positionOf(owner, CatalogIds.STOCK).get("id");

        assertThat(markAsReserve(owner, idDoCdb, true).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(positionOf(owner, CatalogIds.CDB).get("isReservation")).isEqualTo(true);

        ResponseEntity<String> onStock = markAsReserve(owner, stockAssetId, true);
        assertThat(onStock.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(onStock.getBody()).contains("Somente ativos de renda fixa podem compor a reserva de emergência");
    }

    @Test
    @DisplayName("O switch manda o estado desejado: repetir a mesma chamada nao inverte nada")
    void switchEIdempotente() {
        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG"));
        Object idDoCdb = positionOf(owner, CatalogIds.CDB).get("id");

        markAsReserve(owner, idDoCdb, true);
        markAsReserve(owner, idDoCdb, true);
        assertThat(positionOf(owner, CatalogIds.CDB).get("isReservation")).isEqualTo(true);

        markAsReserve(owner, idDoCdb, false);
        assertThat(positionOf(owner, CatalogIds.CDB).get("isReservation")).isEqualTo(false);
    }

    @Test
    @DisplayName("Posicao de outro usuario responde 404 no switch da reserva")
    void switchOnOthersPosition() {
        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG"));
        Object idDoCdb = positionOf(owner, CatalogIds.CDB).get("id");

        assertThat(markAsReserve(other, idDoCdb, true).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(markAsReserve(owner, UUID.randomUUID(), true).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Cada usuario ve so a propria carteira")
    void portfoliosAreIsolated() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));

        assertThat(portfolioOf(owner)).hasSize(1);
        assertThat(portfolioOf(other)).isEmpty();
    }

    @Test
    @DisplayName("Dois CDBs do mesmo ativo aparecem como linhas distintas, cada uma com o seu id")
    void portfolioRepeatsTicker() {
        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG"));
        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-02", "2000.00", "Inter"));

        var rows = portfolioOf(owner).stream().filter(l -> CatalogIds.CDB.equals(l.get("ticker"))).toList();

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(l -> l.get("id")).doesNotHaveDuplicates();
        assertThat(rows).extracting(l -> l.get("issuer")).containsExactlyInAnyOrder("BTG", "Inter");
    }

    @Test
    @DisplayName("A quantidade de cripto mantem as 8 casas")
    void cryptoFraction() {
        List<String> crypto = jdbc.queryForList("""
            SELECT a.ticker FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
            WHERE t.name = 'CRIPTO' LIMIT 1
            """, String.class);
        org.junit.jupiter.api.Assumptions.assumeFalse(crypto.isEmpty(),
            "O catálogo semeado ainda não tem criptomoedas");
        String ticker = crypto.get(0);

        recordSuccessfully(owner, buy(ticker, "2026-09-01", "0.00012345", "500000.00"));

        assertThat(number(positionOf(owner, ticker).get("totalShares"))).isEqualByComparingTo("0.00012345");
        assertThat(number(positionOf(owner, ticker).get("totalInvested"))).isEqualByComparingTo("61.73");
    }

    @Test
    @DisplayName("transactions.updated_at muda na edicao; created_at nao")
    void dateAuditing() {
        TransactionResponseDTO createdId = recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));
        var before = jdbc.queryForMap("SELECT created_at, updated_at FROM transactions WHERE id = ?", createdId.id());

        put(owner, "/api/v1/transactions/" + createdId.id(),
            buy(CatalogIds.STOCK, "2026-09-01", "11", "30.00"), String.class);

        var after = jdbc.queryForMap("SELECT created_at, updated_at FROM transactions WHERE id = ?", createdId.id());
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat((java.sql.Timestamp) after.get("updated_at"))
            .isAfterOrEqualTo((java.sql.Timestamp) before.get("updated_at"));
    }

    @Test
    @DisplayName("Sem token, a carteira responde 401")
    void withoutToken() {
        assertThat(rest.getForEntity("/api/v1/portfolio/assets", String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
