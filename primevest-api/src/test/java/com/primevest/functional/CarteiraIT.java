package com.primevest.functional;

import com.primevest.domain.transaction.dto.TransactionResponseDTO;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tela Carteira: posicao apurada das transacoes, valorizacao e o switch da
 * reserva de emergencia.
 */
class CarteiraIT extends FuncionalBase {

    private User dono;
    private User outro;

    @BeforeEach
    void usuarios() {
        dono = usuarioComCarteira("dono@primevest.test");
        outro = usuarioComCarteira("outro@primevest.test");
    }

    @Test
    @DisplayName("A posicao nao e gravada: editar a transacao muda a carteira na mesma hora")
    void posicaoSaiDasTransacoes() {
        TransactionResponseDTO compra = lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));

        assertThat(numero(posicaoDe(dono, CatalogIds.ACAO).get("totalInvested"))).isEqualByComparingTo("300.00");

        put(dono, "/api/v1/transactions/" + compra.id(),
            compra(CatalogIds.ACAO, "2026-09-01", "20", "30.00"), String.class);

        assertThat(numero(posicaoDe(dono, CatalogIds.ACAO).get("totalInvested"))).isEqualByComparingTo("600.00");
        assertThat(numero(posicaoDe(dono, CatalogIds.ACAO).get("totalShares"))).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("Com cotacao, a valorizacao e cotas x cotacao menos custo, e e regravada a cada transacao")
    void valorizacaoComCotacao() {
        jdbc.update("UPDATE assets SET current_price = 35.00 WHERE ticker = ?", CatalogIds.ACAO);

        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));

        Map<String, Object> posicao = posicaoDe(dono, CatalogIds.ACAO);
        assertThat(numero(posicao.get("valorization"))).isEqualByComparingTo("50.00");
        assertThat(numero(posicao.get("currentValue"))).isEqualByComparingTo("350.00");

        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-02", "10", "30.00"));

        assertThat(numero(posicaoDe(dono, CatalogIds.ACAO).get("valorization"))).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Sem cotacao, o valor atual da renda fixa e o proprio aplicado")
    void rendaFixaSemCotacao() {
        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG"));

        Map<String, Object> posicao = posicaoDe(dono, CatalogIds.CDB);
        assertThat(numero(posicao.get("valorization"))).isEqualByComparingTo("0.00");
        assertThat(numero(posicao.get("currentValue"))).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("O tipo aparece pela descricao, e nao pelo nome interno")
    void tipoPelaDescricao() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));

        assertThat(posicaoDe(dono, CatalogIds.ACAO).get("typeName")).isEqualTo("Ações");
    }

    @Test
    @DisplayName("O resumo soma a carteira e aplica o retorno percentual")
    void resumo() {
        jdbc.update("UPDATE assets SET current_price = 36.00 WHERE ticker = ?", CatalogIds.ACAO);
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resumo = get(dono, "/api/v1/portfolio/", Map.class).getBody();

        assertThat(numero(resumo.get("totalInvested"))).isEqualByComparingTo("300.00");
        assertThat(numero(resumo.get("currentValue"))).isEqualByComparingTo("360.00");
        assertThat(numero(resumo.get("returnPercentage"))).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("O switch da reserva aceita renda fixa e recusa acao com 422")
    void switchDaReserva() {
        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG"));
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));

        Object idDoCdb = posicaoDe(dono, CatalogIds.CDB).get("id");
        Object idDaAcao = posicaoDe(dono, CatalogIds.ACAO).get("id");

        assertThat(marcarComoReserva(dono, idDoCdb, true).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(posicaoDe(dono, CatalogIds.CDB).get("isReservation")).isEqualTo(true);

        ResponseEntity<String> naAcao = marcarComoReserva(dono, idDaAcao, true);
        assertThat(naAcao.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(naAcao.getBody()).contains("Somente ativos de renda fixa podem compor a reserva de emergência");
    }

    @Test
    @DisplayName("O switch manda o estado desejado: repetir a mesma chamada nao inverte nada")
    void switchEIdempotente() {
        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG"));
        Object idDoCdb = posicaoDe(dono, CatalogIds.CDB).get("id");

        marcarComoReserva(dono, idDoCdb, true);
        marcarComoReserva(dono, idDoCdb, true);
        assertThat(posicaoDe(dono, CatalogIds.CDB).get("isReservation")).isEqualTo(true);

        marcarComoReserva(dono, idDoCdb, false);
        assertThat(posicaoDe(dono, CatalogIds.CDB).get("isReservation")).isEqualTo(false);
    }

    @Test
    @DisplayName("Posicao de outro usuario responde 404 no switch da reserva")
    void switchEmPosicaoDeOutro() {
        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG"));
        Object idDoCdb = posicaoDe(dono, CatalogIds.CDB).get("id");

        assertThat(marcarComoReserva(outro, idDoCdb, true).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(marcarComoReserva(dono, UUID.randomUUID(), true).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Cada usuario ve so a propria carteira")
    void carteirasSaoIsoladas() {
        lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));

        assertThat(carteiraDe(dono)).hasSize(1);
        assertThat(carteiraDe(outro)).isEmpty();
    }

    @Test
    @DisplayName("Dois CDBs do mesmo ativo aparecem como linhas distintas, cada uma com o seu id")
    void carteiraRepeteTicker() {
        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG"));
        lancarComSucesso(dono, aplicacaoEmCdb(CatalogIds.CDB, "2026-09-02", "2000.00", "Inter"));

        var linhas = carteiraDe(dono).stream().filter(l -> CatalogIds.CDB.equals(l.get("ticker"))).toList();

        assertThat(linhas).hasSize(2);
        assertThat(linhas).extracting(l -> l.get("id")).doesNotHaveDuplicates();
        assertThat(linhas).extracting(l -> l.get("issuer")).containsExactlyInAnyOrder("BTG", "Inter");
    }

    @Test
    @DisplayName("A quantidade de cripto mantem as 8 casas")
    void fracaoDeCripto() {
        List<String> cripto = jdbc.queryForList("""
            SELECT a.ticker FROM assets a JOIN asset_types t ON t.id = a.asset_type_id
            WHERE t.name = 'CRIPTO' LIMIT 1
            """, String.class);
        org.junit.jupiter.api.Assumptions.assumeFalse(cripto.isEmpty(),
            "O catálogo semeado ainda não tem criptomoedas");
        String ticker = cripto.get(0);

        lancarComSucesso(dono, compra(ticker, "2026-09-01", "0.00012345", "500000.00"));

        assertThat(numero(posicaoDe(dono, ticker).get("totalShares"))).isEqualByComparingTo("0.00012345");
        assertThat(numero(posicaoDe(dono, ticker).get("totalInvested"))).isEqualByComparingTo("61.73");
    }

    @Test
    @DisplayName("transactions.updated_at muda na edicao; created_at nao")
    void auditoriaDeDatas() {
        TransactionResponseDTO criada = lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));
        var antes = jdbc.queryForMap("SELECT created_at, updated_at FROM transactions WHERE id = ?", criada.id());

        put(dono, "/api/v1/transactions/" + criada.id(),
            compra(CatalogIds.ACAO, "2026-09-01", "11", "30.00"), String.class);

        var depois = jdbc.queryForMap("SELECT created_at, updated_at FROM transactions WHERE id = ?", criada.id());
        assertThat(depois.get("created_at")).isEqualTo(antes.get("created_at"));
        assertThat((java.sql.Timestamp) depois.get("updated_at"))
            .isAfterOrEqualTo((java.sql.Timestamp) antes.get("updated_at"));
    }

    @Test
    @DisplayName("Sem token, a carteira responde 401")
    void semToken() {
        assertThat(rest.getForEntity("/api/v1/portfolio/assets", String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
