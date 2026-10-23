package com.primevest.functional;

import com.primevest.domain.user.User;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tela Estrategia: ideal por tipo, rebalanceamento e reserva de emergencia.
 */
class StrategyIT extends FunctionalBase {

    private User owner;
    private long stockId;
    private long idFii;
    private long fixedIncomeId;

    /** Cotacao do catalogo antes do teste, devolvida no fim: o catalogo e compartilhado entre as classes. */
    private final Map<String, BigDecimal> originalQuote = new HashMap<>();

    @BeforeEach
    void scenario() {
        owner = userWithPortfolio("dono@primevest.test");
        stockId = typeByName("ACAO");
        idFii = typeByName("FII");
        fixedIncomeId = typeByName("RENDA_FIXA");
        // A estrategia conta a valorizacao (12/10/2026): sem cotacao a valorizacao e zero e o valor atual e o custo,
        // como os cenarios abaixo esperam. Quem testa a valorizacao grava a cotacao no proprio teste.
        for (String ticker : List.of(CatalogIds.STOCK, CatalogIds.FII)) {
            originalQuote.put(ticker, jdbc.queryForObject(
                "SELECT current_price FROM assets WHERE ticker = ?", BigDecimal.class, ticker));
            jdbc.update("UPDATE assets SET current_price = NULL WHERE ticker = ?", ticker);
        }
    }

    @AfterEach
    void returnsQuotes() {
        originalQuote.forEach((ticker, price) ->
            jdbc.update("UPDATE assets SET current_price = ? WHERE ticker = ?", price, ticker));
    }

    private long typeByName(String name) {
        return jdbc.queryForObject("SELECT id FROM asset_types WHERE name = ?", Long.class, name);
    }

    private Map<String, Object> item(long type, String percentage) {
        return Map.of("assetTypeId", type, "targetPercentage", new BigDecimal(percentage));
    }

    private ResponseEntity<String> saveStrategy(Object... runItemList) {
        return put(owner, "/api/v1/strategy/", Map.of("items", List.of(runItemList)), String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rebalanceData() {
        return get(owner, "/api/v1/strategy/rebalance", Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> reserve() {
        return get(owner, "/api/v1/strategy/reservation", Map.class).getBody();
    }

    @Test
    @DisplayName("PUT e full replace: salvar sem um tipo remove aquele tipo")
    void fullReplace() {
        assertThat(saveStrategy(item(stockId, "60.00"), item(idFii, "40.00")).getStatusCode())
            .isEqualTo(HttpStatus.OK);

        assertThat(saveStrategy(item(stockId, "100.00")).getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> strategy = List.of(get(owner, "/api/v1/strategy/", Map[].class).getBody())
            .stream().map(m -> (Map<String, Object>) m).toList();
        assertThat(strategy).hasSize(1);
        assertThat(strategy.get(0).get("assetTypeName")).isEqualTo("Ações");
    }

    @Test
    @DisplayName("F7 — a soma precisa fechar 100%; 33,33 tres vezes passa e 90% nao")
    void sumOfHundred() {
        assertThat(saveStrategy(item(stockId, "33.33"), item(idFii, "33.33"), item(fixedIncomeId, "33.33"))
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> ninety = saveStrategy(item(stockId, "60.00"), item(idFii, "30.00"));
        assertThat(ninety.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ninety.getBody()).contains("A soma dos percentuais ideais deve ser 100%");

        // A estrategia anterior continua intacta.
        assertThat(get(owner, "/api/v1/strategy/", Map[].class).getBody()).hasSize(3);
    }

    @Test
    @DisplayName("F3 — o mesmo tipo duas vezes responde 422, e nao erro de banco")
    void repeatedType() {
        ResponseEntity<String> response = saveStrategy(item(stockId, "60.00"), item(stockId, "40.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("aparece mais de uma vez na estratégia");
    }

    @Test
    @DisplayName("F8 — o DELETE da estrategia nao existe mais")
    void deleteRemoved() {
        assertThat(delete(owner, "/api/v1/strategy/" + stockId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Regra A do rebalanceamento: a base e o tipo mais acima do ideal, e ele fica com sugestao zero")
    void rebalanceByType() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "100", "60.00"));
        recordSuccessfully(owner, buy(CatalogIds.FII, "2026-09-01", "20", "100.00"));
        saveStrategy(item(stockId, "50.00"), item(idFii, "50.00"));

        Map<String, Object> rebalance = rebalanceData();

        assertThat(number(rebalance.get("totalPortfolioValue"))).isEqualByComparingTo("8000.00");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runItemList = (List<Map<String, Object>>) rebalance.get("items");
        Map<String, Object> shares = runItemList.stream().filter(i -> "Ações".equals(i.get("typeName")))
            .findFirst().orElseThrow();
        Map<String, Object> fiis = runItemList.stream().filter(i -> "FIIs".equals(i.get("typeName")))
            .findFirst().orElseThrow();

        assertThat(number(shares.get("amountToRebalance"))).isEqualByComparingTo("0.00");
        assertThat(number(fiis.get("amountToRebalance"))).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("Regra C — a posicao marcada como reserva sai do rebalanceamento por tipo")
    void reserveOutOfRebalanceByType() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "100", "60.00"));
        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        saveStrategy(item(stockId, "50.00"), item(fixedIncomeId, "50.00"));

        assertThat(number(rebalanceData().get("totalPortfolioValue"))).isEqualByComparingTo("8000.00");

        markAsReserve(owner, positionOf(owner, CatalogIds.CDB).get("id"), true);

        assertThat(number(rebalanceData().get("totalPortfolioValue"))).isEqualByComparingTo("6000.00");
    }

    @Test
    @DisplayName("Reserva por percentual: carteira 10.000 com 2.000 de reserva e ideal 30% pede 1.428,57")
    void reserveContributionByPercentage() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "100", "80.00"));
        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        markAsReserve(owner, positionOf(owner, CatalogIds.CDB).get("id"), true);

        put(owner, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "PERCENTAGE", "targetPercentage", new BigDecimal("30.00")), String.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) rebalanceData().get("reservation");

        assertThat(number(row.get("currentValue"))).isEqualByComparingTo("2000.00");
        assertThat(number(row.get("amountToRebalance"))).isEqualByComparingTo("1428.57");
        assertThat(number(row.get("currentPercentage"))).isEqualByComparingTo("20.00");
        assertThat(number(row.get("diffPercentage"))).isEqualByComparingTo("-10.00");
    }

    @Test
    @DisplayName("Reserva por valor: ideal R$ 3.000 pede 1.000; ideal 10% devolve −1.111,11")
    void reserveContributionByValueAndExcess() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "100", "80.00"));
        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        markAsReserve(owner, positionOf(owner, CatalogIds.CDB).get("id"), true);

        put(owner, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "VALUE", "targetValue", new BigDecimal("3000.00")), String.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> byValue = (Map<String, Object>) rebalanceData().get("reservation");
        assertThat(number(byValue.get("amountToRebalance"))).isEqualByComparingTo("1000.00");

        put(owner, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "PERCENTAGE", "targetPercentage", new BigDecimal("10.00")), String.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> excess = (Map<String, Object>) rebalanceData().get("reservation");
        assertThat(number(excess.get("amountToRebalance"))).isEqualByComparingTo("-1111.11");
    }

    @Test
    @DisplayName("A leitura da aba Estrategia Reserva cria a linha padrao; o rebalanceamento nao grava nada")
    void reserveRowCreation() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM strategy_reservations", Long.class)).isZero();

        rebalanceData();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM strategy_reservations", Long.class)).isZero();

        Map<String, Object> reserve = reserve();
        assertThat(reserve.get("typeReservation")).isEqualTo("PERCENTAGE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM strategy_reservations", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Trocar o tipo da reserva limpa o alvo do tipo anterior")
    void reserveTypeChange() {
        put(owner, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "PERCENTAGE", "targetPercentage", new BigDecimal("30.00")), String.class);
        put(owner, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "VALUE", "targetValue", new BigDecimal("30000.00")), String.class);

        Map<String, Object> reserve = reserve();
        assertThat(reserve.get("typeReservation")).isEqualTo("VALUE");
        assertThat(reserve.get("targetPercentage")).isNull();
        assertThat(number(reserve.get("targetValue"))).isEqualByComparingTo("30000.00");
        assertThat(jdbc.queryForObject("SELECT type_reservation FROM strategy_reservations", Integer.class))
            .isEqualTo(2);
    }

    @Test
    @DisplayName("Reserva por valor acima da carteira: o % ideal para o diff nao passa de 100")
    void idealCappedAtHundred() {
        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        markAsReserve(owner, positionOf(owner, CatalogIds.CDB).get("id"), true);

        put(owner, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "VALUE", "targetValue", new BigDecimal("30000.00")), String.class);

        Map<String, Object> reserve = reserve();
        assertThat(number(reserve.get("idealPercentage"))).isEqualByComparingTo("100.00");
        assertThat(number(reserve.get("currentPercentage"))).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Marcar um CDB como reserva muda ao mesmo tempo o rebalanceamento, a reserva e a alocacao")
    void singleSwitchChangesAllThreeReads() {
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "100", "80.00"));
        recordSuccessfully(owner, cdbInvestment(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        saveStrategy(item(stockId, "50.00"), item(fixedIncomeId, "50.00"));

        markAsReserve(owner, positionOf(owner, CatalogIds.CDB).get("id"), true);

        assertThat(number(rebalanceData().get("totalPortfolioValue"))).isEqualByComparingTo("8000.00");
        assertThat(number(reserve().get("reservationValue"))).isEqualByComparingTo("2000.00");
        @SuppressWarnings("unchecked")
        Map<String, Object> reserveAllocation =
            get(owner, "/api/v1/portfolio/allocation/reservation", Map.class).getBody();
        assertThat(number(reserveAllocation.get("reservationValue"))).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("A base da estrategia e o valor atual, a mesma da alocacao (12/10/2026; antes era o custo)")
    void sameBaseAsAllocation() {
        jdbc.update("UPDATE assets SET current_price = 100.00 WHERE ticker = ?", CatalogIds.STOCK);
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "100", "80.00"));
        saveStrategy(item(stockId, "100.00"));

        // Custo 8.000 + valorizacao 2.000 = 10.000 nas duas telas.
        assertThat(number(rebalanceData().get("totalPortfolioValue"))).isEqualByComparingTo("10000.00");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allocation =
            List.of(get(owner, "/api/v1/portfolio/allocation", Map[].class).getBody())
                .stream().map(m -> (Map<String, Object>) m).toList();
        assertThat(number(allocation.get(0).get("value"))).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("A valorizacao muda a sugestao: o tipo que subiu vira a base e o outro recebe o aporte")
    void appreciationChangesSuggestion() {
        // Pelo custo as duas estao iguais (5.000 x 5.000) e nada seria sugerido. A acao subiu 20%.
        jdbc.update("UPDATE assets SET current_price = 60.00 WHERE ticker = ?", CatalogIds.STOCK);
        jdbc.update("UPDATE assets SET current_price = 100.00 WHERE ticker = ?", CatalogIds.FII);
        recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "100", "50.00"));
        recordSuccessfully(owner, buy(CatalogIds.FII, "2026-09-01", "50", "100.00"));
        saveStrategy(item(stockId, "50.00"), item(idFii, "50.00"));

        Map<String, Object> rebalance = rebalanceData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runItemList = (List<Map<String, Object>>) rebalance.get("items");
        Map<String, Object> shares = runItemList.stream().filter(i -> "Ações".equals(i.get("typeName")))
            .findFirst().orElseThrow();
        Map<String, Object> fiis = runItemList.stream().filter(i -> "FIIs".equals(i.get("typeName")))
            .findFirst().orElseThrow();

        assertThat(number(shares.get("currentValue"))).isEqualByComparingTo("6000.00");
        assertThat(number(shares.get("amountToRebalance"))).isEqualByComparingTo("0.00");
        assertThat(number(fiis.get("amountToRebalance"))).isEqualByComparingTo("1000.00");
    }
}
