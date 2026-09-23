package com.primevest.functional;

import com.primevest.domain.user.User;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tela Estrategia: ideal por tipo, rebalanceamento e reserva de emergencia.
 */
class EstrategiaIT extends FuncionalBase {

    private User dono;
    private long idAcao;
    private long idFii;
    private long idRendaFixa;

    @BeforeEach
    void cenario() {
        dono = usuarioComCarteira("dono@primevest.test");
        idAcao = tipoPorNome("ACAO");
        idFii = tipoPorNome("FII");
        idRendaFixa = tipoPorNome("RENDA_FIXA");
    }

    private long tipoPorNome(String nome) {
        return jdbc.queryForObject("SELECT id FROM asset_types WHERE name = ?", Long.class, nome);
    }

    private Map<String, Object> item(long tipo, String percentual) {
        return Map.of("assetTypeId", tipo, "targetPercentage", new BigDecimal(percentual));
    }

    private ResponseEntity<String> salvarEstrategia(Object... itens) {
        return put(dono, "/api/v1/strategy/", Map.of("items", List.of(itens)), String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rebalanceamento() {
        return get(dono, "/api/v1/strategy/rebalance", Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> reserva() {
        return get(dono, "/api/v1/strategy/reservation", Map.class).getBody();
    }

    @Test
    @DisplayName("PUT e full replace: salvar sem um tipo remove aquele tipo")
    void fullReplace() {
        assertThat(salvarEstrategia(item(idAcao, "60.00"), item(idFii, "40.00")).getStatusCode())
            .isEqualTo(HttpStatus.OK);

        assertThat(salvarEstrategia(item(idAcao, "100.00")).getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> estrategia = List.of(get(dono, "/api/v1/strategy/", Map[].class).getBody())
            .stream().map(m -> (Map<String, Object>) m).toList();
        assertThat(estrategia).hasSize(1);
        assertThat(estrategia.get(0).get("assetTypeName")).isEqualTo("Ações");
    }

    @Test
    @DisplayName("F7 — a soma precisa fechar 100%; 33,33 tres vezes passa e 90% nao")
    void somaDeCem() {
        assertThat(salvarEstrategia(item(idAcao, "33.33"), item(idFii, "33.33"), item(idRendaFixa, "33.33"))
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> noventa = salvarEstrategia(item(idAcao, "60.00"), item(idFii, "30.00"));
        assertThat(noventa.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(noventa.getBody()).contains("A soma dos percentuais ideais deve ser 100%");

        // A estrategia anterior continua intacta.
        assertThat(get(dono, "/api/v1/strategy/", Map[].class).getBody()).hasSize(3);
    }

    @Test
    @DisplayName("F3 — o mesmo tipo duas vezes responde 422, e nao erro de banco")
    void tipoRepetido() {
        ResponseEntity<String> resposta = salvarEstrategia(item(idAcao, "60.00"), item(idAcao, "40.00"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("aparece mais de uma vez na estratégia");
    }

    @Test
    @DisplayName("F8 — o DELETE da estrategia nao existe mais")
    void deleteRemovido() {
        assertThat(delete(dono, "/api/v1/strategy/" + idAcao).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Regra A do rebalanceamento: a base e o tipo mais acima do ideal, e ele fica com sugestao zero")
    void rebalanceamentoPorTipo() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "100", "60.00"));
        lancarComSucesso(dono, compra(CatalogIds.FII, "2026-09-01", "20", "100.00"));
        salvarEstrategia(item(idAcao, "50.00"), item(idFii, "50.00"));

        Map<String, Object> rebalance = rebalanceamento();

        assertThat(numero(rebalance.get("totalPortfolioValue"))).isEqualByComparingTo("8000.00");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itens = (List<Map<String, Object>>) rebalance.get("items");
        Map<String, Object> acoes = itens.stream().filter(i -> "Ações".equals(i.get("typeName")))
            .findFirst().orElseThrow();
        Map<String, Object> fiis = itens.stream().filter(i -> "FIIs".equals(i.get("typeName")))
            .findFirst().orElseThrow();

        assertThat(numero(acoes.get("amountToRebalance"))).isEqualByComparingTo("0.00");
        assertThat(numero(fiis.get("amountToRebalance"))).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("Regra C — a posicao marcada como reserva sai do rebalanceamento por tipo")
    void reservaForaDoRebalanceamentoPorTipo() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "100", "60.00"));
        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        salvarEstrategia(item(idAcao, "50.00"), item(idRendaFixa, "50.00"));

        assertThat(numero(rebalanceamento().get("totalPortfolioValue"))).isEqualByComparingTo("8000.00");

        marcarComoReserva(dono, posicaoDe(dono, CatalogIds.CDB).get("id"), true);

        assertThat(numero(rebalanceamento().get("totalPortfolioValue"))).isEqualByComparingTo("6000.00");
    }

    @Test
    @DisplayName("Reserva por percentual: carteira 10.000 com 2.000 de reserva e ideal 30% pede 1.428,57")
    void aporteDaReservaPorPercentual() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "100", "80.00"));
        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        marcarComoReserva(dono, posicaoDe(dono, CatalogIds.CDB).get("id"), true);

        put(dono, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "PERCENTAGE", "targetPercentage", new BigDecimal("30.00")), String.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> linha = (Map<String, Object>) rebalanceamento().get("reservation");

        assertThat(numero(linha.get("currentValue"))).isEqualByComparingTo("2000.00");
        assertThat(numero(linha.get("amountToRebalance"))).isEqualByComparingTo("1428.57");
        assertThat(numero(linha.get("currentPercentage"))).isEqualByComparingTo("20.00");
        assertThat(numero(linha.get("diffPercentage"))).isEqualByComparingTo("-10.00");
    }

    @Test
    @DisplayName("Reserva por valor: ideal R$ 3.000 pede 1.000; ideal 10% devolve −1.111,11")
    void aporteDaReservaPorValorEExcesso() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "100", "80.00"));
        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        marcarComoReserva(dono, posicaoDe(dono, CatalogIds.CDB).get("id"), true);

        put(dono, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "VALUE", "targetValue", new BigDecimal("3000.00")), String.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> porValor = (Map<String, Object>) rebalanceamento().get("reservation");
        assertThat(numero(porValor.get("amountToRebalance"))).isEqualByComparingTo("1000.00");

        put(dono, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "PERCENTAGE", "targetPercentage", new BigDecimal("10.00")), String.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> excesso = (Map<String, Object>) rebalanceamento().get("reservation");
        assertThat(numero(excesso.get("amountToRebalance"))).isEqualByComparingTo("-1111.11");
    }

    @Test
    @DisplayName("A leitura da aba Estrategia Reserva cria a linha padrao; o rebalanceamento nao grava nada")
    void nascimentoDaLinhaDaReserva() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM strategy_reservations", Long.class)).isZero();

        rebalanceamento();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM strategy_reservations", Long.class)).isZero();

        Map<String, Object> reserva = reserva();
        assertThat(reserva.get("typeReservation")).isEqualTo("PERCENTAGE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM strategy_reservations", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Trocar o tipo da reserva limpa o alvo do tipo anterior")
    void trocaDeTipoDaReserva() {
        put(dono, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "PERCENTAGE", "targetPercentage", new BigDecimal("30.00")), String.class);
        put(dono, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "VALUE", "targetValue", new BigDecimal("30000.00")), String.class);

        Map<String, Object> reserva = reserva();
        assertThat(reserva.get("typeReservation")).isEqualTo("VALUE");
        assertThat(reserva.get("targetPercentage")).isNull();
        assertThat(numero(reserva.get("targetValue"))).isEqualByComparingTo("30000.00");
        assertThat(jdbc.queryForObject("SELECT type_reservation FROM strategy_reservations", Integer.class))
            .isEqualTo(2);
    }

    @Test
    @DisplayName("Reserva por valor acima da carteira: o % ideal para o diff nao passa de 100")
    void idealLimitadoACem() {
        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        marcarComoReserva(dono, posicaoDe(dono, CatalogIds.CDB).get("id"), true);

        put(dono, "/api/v1/strategy/reservation",
            Map.of("typeReservation", "VALUE", "targetValue", new BigDecimal("30000.00")), String.class);

        Map<String, Object> reserva = reserva();
        assertThat(numero(reserva.get("idealPercentage"))).isEqualByComparingTo("100.00");
        assertThat(numero(reserva.get("currentPercentage"))).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Marcar um CDB como reserva muda ao mesmo tempo o rebalanceamento, a reserva e a alocacao")
    void umSoSwitchMudaAsTresLeituras() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "100", "80.00"));
        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "2000.00", "BTG"));
        salvarEstrategia(item(idAcao, "50.00"), item(idRendaFixa, "50.00"));

        marcarComoReserva(dono, posicaoDe(dono, CatalogIds.CDB).get("id"), true);

        assertThat(numero(rebalanceamento().get("totalPortfolioValue"))).isEqualByComparingTo("8000.00");
        assertThat(numero(reserva().get("reservationValue"))).isEqualByComparingTo("2000.00");
        @SuppressWarnings("unchecked")
        Map<String, Object> alocacaoDaReserva =
            get(dono, "/api/v1/portfolio/allocation/reservation", Map.class).getBody();
        assertThat(numero(alocacaoDaReserva.get("reservationValue"))).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("A base da estrategia e o custo, e a da alocacao e o valor atual")
    void basesDiferentes() {
        jdbc.update("UPDATE assets SET current_price = 100.00 WHERE ticker = ?", CatalogIds.ACAO);
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "100", "80.00"));
        salvarEstrategia(item(idAcao, "100.00"));

        // Estrategia: custo (8.000). Alocacao: valor atual (10.000).
        assertThat(numero(rebalanceamento().get("totalPortfolioValue"))).isEqualByComparingTo("8000.00");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alocacao =
            List.of(get(dono, "/api/v1/portfolio/allocation", Map[].class).getBody())
                .stream().map(m -> (Map<String, Object>) m).toList();
        assertThat(numero(alocacao.get(0).get("value"))).isEqualByComparingTo("10000.00");
    }
}
