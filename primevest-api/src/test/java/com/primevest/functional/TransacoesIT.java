package com.primevest.functional;

import com.primevest.domain.transaction.dto.TransactionResponseDTO;
import com.primevest.domain.user.User;
import com.primevest.support.CatalogIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transacoes de ponta a ponta: HTTP, regras e banco.
 *
 * <p>Os cenarios com nome de "exemplo" sao os do escopo de vendas, com os
 * numeros que o usuario definiu.</p>
 */
class TransacoesIT extends FuncionalBase {

    private User dono;
    private User outro;

    @BeforeEach
    void usuarios() {
        dono = usuarioComCarteira("dono@primevest.test");
        outro = usuarioComCarteira("outro@primevest.test");
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private long contarTransacoes(User user) {
        return transacoesDe(user).size();
    }

    // ------------------------------------------------------------------
    // Renda variavel
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Renda variavel")
    class RendaVariavel {

        @Test
        @DisplayName("Compra cria a posicao e aparece na listagem e na busca por id")
        void compraCriaPosicao() {
            TransactionResponseDTO criada = lancarComSucesso(dono,
                compra(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));

            assertThat(criada.ticker()).isEqualTo(CatalogIds.ACAO);
            assertThat(criada.totalAmount()).isEqualByComparingTo("300.00");
            assertThat(criada.assetTypeName()).isEqualTo("Ações");

            assertThat(transacoesDe(dono)).hasSize(1);
            assertThat(get(dono, "/api/v1/transactions/" + criada.id(), TransactionResponseDTO.class)
                .getBody().id()).isEqualTo(criada.id());
        }

        @Test
        @DisplayName("Preco medio ponderado: 100x10, 100x20 e venda de 100 deixam PM 15 e custo 1.500")
        void precoMedioPonderado() {
            lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "100", "10.00"));
            lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-02", "100", "20.00"));
            lancarComSucesso(dono, venda(CatalogIds.ACAO, "2026-09-03", "100", "30.00"));

            var posicao = posicaoDe(dono, CatalogIds.ACAO);
            assertThat(posicao.get("totalShares")).isEqualTo(100.0);
            assertThat(new BigDecimal(posicao.get("averagePrice").toString())).isEqualByComparingTo("15.0000");
            assertThat(new BigDecimal(posicao.get("totalInvested").toString())).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("Ticker fora do catalogo responde 422 e nao cria ativo")
        void tickerInexistente() {
            ResponseEntity<String> resposta = lancarEsperandoErro(dono,
                compra("XXXX9", "2026-09-01", "10", "30.00"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Ativo não encontrado no catálogo: XXXX9");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE ticker = 'XXXX9'", Long.class))
                .isZero();
        }

        @Test
        @DisplayName("Regra A — venda de ativo fora da carteira responde 422 e nao grava nada")
        void vendaSemPosicao() {
            ResponseEntity<String> resposta = lancarEsperandoErro(dono,
                venda(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Não há posição de PETR4 na carteira para vender");
            assertThat(contarTransacoes(dono)).isZero();
        }

        @Test
        @DisplayName("Vender tudo liquida a posicao; uma nova compra a reabre")
        void liquidacaoEReabertura() {
            lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));
            lancarComSucesso(dono, venda(CatalogIds.ACAO, "2026-09-02", "10", "31.00"));

            assertThat(carteiraDe(dono)).isEmpty();
            assertThat(jdbc.queryForObject(
                "SELECT is_settled FROM portfolio_assets LIMIT 1", Boolean.class)).isTrue();

            lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-03", "5", "32.00"));

            assertThat(carteiraDe(dono)).hasSize(1);
            assertThat(jdbc.queryForObject(
                "SELECT is_settled FROM portfolio_assets LIMIT 1", Boolean.class)).isFalse();
        }

        @Test
        @DisplayName("Usuario B nao le, nao edita e nao exclui transacao de A — 404, nunca 403")
        void escopoPorUsuario() {
            TransactionResponseDTO deA = lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));

            assertThat(get(outro, "/api/v1/transactions/" + deA.id(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(put(outro, "/api/v1/transactions/" + deA.id(),
                compra(CatalogIds.ACAO, "2026-09-01", "5", "30.00"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(delete(outro, "/api/v1/transactions/" + deA.id()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(transacoesDe(outro)).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Exemplos 1 a 3 — saldo por cota (regra B)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Exemplos 1 a 3 do escopo — saldo por cota em qualquer data")
    class SaldoPorCota {

        @BeforeEach
        void compraEVendaIniciais() {
            lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-01", "10", "30.00"));
            lancarComSucesso(dono, venda(CatalogIds.ACAO, "2026-09-04", "8", "31.00"));
        }

        @Test
        @DisplayName("Exemplo 1 — a segunda venda falha por faltarem 2 cotas em 11/09")
        void exemplo1() {
            ResponseEntity<String> resposta = lancarEsperandoErro(dono,
                venda(CatalogIds.ACAO, "2026-09-11", "4", "32.00"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody())
                .contains("Saldo insuficiente de PETR4 em 11/09/2026")
                .contains("faltam 2 cotas nessa data");
            assertThat(contarTransacoes(dono)).isEqualTo(2);
        }

        @Test
        @DisplayName("Exemplo 1a — uma compra de exatamente 2 cotas antes da data libera a venda")
        void exemplo1a_compraDeDuasCotas() {
            lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-10", "2", "31.00"));

            lancarComSucesso(dono, venda(CatalogIds.ACAO, "2026-09-11", "4", "32.00"));

            assertThat(contarTransacoes(dono)).isEqualTo(4);
        }

        @Test
        @DisplayName("Exemplo 1b — editar a compra original para 12 cotas libera a venda")
        void exemplo1b_editarACompra() {
            UUID compraOriginal = transacoesDe(dono).stream()
                .filter(t -> t.type().name().equals("BUY")).findFirst().orElseThrow().id();

            assertThat(put(dono, "/api/v1/transactions/" + compraOriginal,
                compra(CatalogIds.ACAO, "2026-09-01", "12", "30.00"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

            lancarComSucesso(dono, venda(CatalogIds.ACAO, "2026-09-11", "4", "32.00"));
        }

        @Test
        @DisplayName("Exemplo 1c — reduzir a primeira venda para 6 cotas tambem libera a segunda")
        void exemplo1c_editarAVenda() {
            UUID vendaOriginal = transacoesDe(dono).stream()
                .filter(t -> t.type().name().equals("SELL")).findFirst().orElseThrow().id();

            assertThat(put(dono, "/api/v1/transactions/" + vendaOriginal,
                venda(CatalogIds.ACAO, "2026-09-04", "6", "31.00"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

            lancarComSucesso(dono, venda(CatalogIds.ACAO, "2026-09-11", "4", "32.00"));
        }

        @Test
        @DisplayName("Exemplo 1d — a compra depois da venda nao resolve: a regra olha a data")
        void exemplo1d_compraDepoisDaVendaNaoResolve() {
            lancarComSucesso(dono, compra(CatalogIds.ACAO, "2026-09-12", "2", "31.00"));

            ResponseEntity<String> resposta = lancarEsperandoErro(dono,
                venda(CatalogIds.ACAO, "2026-09-11", "4", "32.00"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("em 11/09/2026");
        }

        @Test
        @DisplayName("Exemplo 2 — reduzir a compra para 5 cotas falha por faltarem 3 em 04/09, e nada muda")
        void exemplo2() {
            UUID compraOriginal = transacoesDe(dono).stream()
                .filter(t -> t.type().name().equals("BUY")).findFirst().orElseThrow().id();

            ResponseEntity<String> resposta = put(dono, "/api/v1/transactions/" + compraOriginal,
                compra(CatalogIds.ACAO, "2026-09-01", "5", "30.00"), String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody())
                .contains("em 04/09/2026")
                .contains("faltam 3 cotas");

            TransactionResponseDTO compraDepois = transacoesDe(dono).stream()
                .filter(t -> t.id().equals(compraOriginal)).findFirst().orElseThrow();
            assertThat(compraDepois.shares()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("Exemplo 3 — excluir a compra falha por faltarem 8 cotas em 04/09, e a compra continua la")
        void exemplo3() {
            UUID compraOriginal = transacoesDe(dono).stream()
                .filter(t -> t.type().name().equals("BUY")).findFirst().orElseThrow().id();

            ResponseEntity<String> resposta = delete(dono, "/api/v1/transactions/" + compraOriginal);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody())
                .contains("em 04/09/2026")
                .contains("faltam 8 cotas");
            assertThat(contarTransacoes(dono)).isEqualTo(2);
        }
    }


    // ------------------------------------------------------------------
    // Renda fixa (CDB) — regras E, F, G e o titulo
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Renda fixa")
    class RendaFixa {

        private Map<String, Object> aplicacao(String data, String valor, String emissor) {
            Map<String, Object> corpo = new HashMap<>();
            corpo.put("ticker", CatalogIds.CDB);
            corpo.put("type", "BUY");
            corpo.put("transactionDate", data);
            corpo.put("totalAmount", new BigDecimal(valor));
            corpo.put("issuer", emissor);
            corpo.put("maturityDate", "2030-01-15");
            corpo.put("rateType", "POS_FIXADO");
            corpo.put("indexer", "CDI");
            corpo.put("indexerRate", new BigDecimal("110.125"));
            return corpo;
        }

        private Map<String, Object> resgate(String data, String valor, UUID noteId, boolean liquidacaoTotal) {
            Map<String, Object> corpo = new HashMap<>();
            corpo.put("ticker", CatalogIds.CDB);
            corpo.put("type", "SELL");
            corpo.put("transactionDate", data);
            corpo.put("totalAmount", new BigDecimal(valor));
            corpo.put("noteId", noteId);
            if (liquidacaoTotal) {
                corpo.put("isFullSettlement", true);
            }
            return corpo;
        }

        @Test
        @DisplayName("Dois CDBs de emissores diferentes sao duas linhas, distinguidas por emissor e vencimento")
        void doisCdbsDuasLinhas() {
            lancarComSucesso(dono, aplicacao("2026-09-01", "1000.00", "BTG"));
            lancarComSucesso(dono, aplicacao("2026-09-02", "2000.00", "Inter"));

            List<Map<String, Object>> linhas = carteiraDe(dono).stream()
                .filter(l -> CatalogIds.CDB.equals(l.get("ticker"))).toList();

            assertThat(linhas).hasSize(2);
            assertThat(linhas).extracting(l -> l.get("issuer")).containsExactlyInAnyOrder("BTG", "Inter");
            assertThat(linhas).allSatisfy(l -> assertThat(l.get("maturityDate")).isEqualTo("2030-01-15"));
        }

        @Test
        @DisplayName("A taxa do titulo guarda as 3 casas digitadas (IPCA + 6,125%)")
        void taxaComTresCasas() {
            TransactionResponseDTO criada = lancarComSucesso(dono, aplicacao("2026-09-01", "1000.00", "BTG"));

            assertThat(criada.indexerRate()).isEqualByComparingTo("110.1250");
        }

        @Test
        @DisplayName("Renda fixa com quantidade e recusada: ela e lancada pelo valor total")
        void rendaFixaComQuantidade() {
            Map<String, Object> corpo = aplicacao("2026-09-01", "1000.00", "BTG");
            corpo.put("shares", new BigDecimal("1"));
            corpo.put("pricePerShare", new BigDecimal("1000.00"));

            ResponseEntity<String> resposta = lancarEsperandoErro(dono, corpo);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Renda Fixa é lançada pelo valor total");
        }

        @Test
        @DisplayName("Venda de renda fixa sem apontar o titulo e recusada")
        void vendaSemTitulo() {
            lancarComSucesso(dono, aplicacao("2026-09-01", "1000.00", "BTG"));

            Map<String, Object> semTitulo = new HashMap<>();
            semTitulo.put("ticker", CatalogIds.CDB);
            semTitulo.put("type", "SELL");
            semTitulo.put("transactionDate", "2026-09-20");
            semTitulo.put("totalAmount", new BigDecimal("500.00"));

            ResponseEntity<String> resposta = lancarEsperandoErro(dono, semTitulo);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Venda de renda fixa precisa apontar qual título está sendo vendido");
        }

        @Test
        @DisplayName("Resgate que alcanca o saldo corrige a valorizacao e deixa o centavo da regra F")
        void resgateParcialDeixaCentavo() {
            TransactionResponseDTO aplicacao = lancarComSucesso(dono, aplicacao("2026-01-10", "1000.00", "BTG"));

            lancarComSucesso(dono, resgate("2026-09-10", "1050.00", aplicacao.noteId(), false));

            Map<String, Object> posicao = posicaoDe(dono, CatalogIds.CDB);
            assertThat(new BigDecimal(posicao.get("valorization").toString())).isEqualByComparingTo("50.01");
            assertThat(posicao.get("isSettled")).isEqualTo(false);
        }

        @Test
        @DisplayName("Liquidacao total zera a posicao e grava o ganho realizado")
        void liquidacaoTotal() {
            TransactionResponseDTO aplicacao = lancarComSucesso(dono, aplicacao("2026-01-10", "1000.00", "BTG"));

            lancarComSucesso(dono, resgate("2026-09-10", "1120.00", aplicacao.noteId(), true));

            assertThat(carteiraDe(dono)).isEmpty();
            assertThat(jdbc.queryForObject(
                "SELECT appreciation FROM portfolio_assets LIMIT 1", BigDecimal.class))
                .isEqualByComparingTo("120.00");
            assertThat(jdbc.queryForObject(
                "SELECT is_settled FROM portfolio_assets LIMIT 1", Boolean.class)).isTrue();
        }

        @Test
        @DisplayName("Regra G — liquidacao total abaixo do aplicado e recusada")
        void liquidacaoAbaixoDoAplicado() {
            TransactionResponseDTO aplicacao = lancarComSucesso(dono, aplicacao("2026-01-10", "1000.00", "BTG"));

            ResponseEntity<String> resposta = post(dono, "/api/v1/transactions/",
                resgate("2026-09-10", "900.00", aplicacao.noteId(), true), String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Em renda fixa não existe valorização negativa");
        }

        @Test
        @DisplayName("Editar so o valor nao apaga emissor, vencimento nem taxa do titulo")
        void edicaoNaoApagaOTitulo() {
            TransactionResponseDTO criada = lancarComSucesso(dono, aplicacao("2026-09-01", "1000.00", "BTG"));

            Map<String, Object> soOValor = new HashMap<>();
            soOValor.put("ticker", CatalogIds.CDB);
            soOValor.put("type", "BUY");
            soOValor.put("transactionDate", "2026-09-01");
            soOValor.put("totalAmount", new BigDecimal("1500.00"));
            assertThat(put(dono, "/api/v1/transactions/" + criada.id(), soOValor, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

            Map<String, Object> posicao = posicaoDe(dono, CatalogIds.CDB);
            assertThat(posicao.get("issuer")).isEqualTo("BTG");
            assertThat(posicao.get("maturityDate")).isEqualTo("2030-01-15");
            assertThat(new BigDecimal(posicao.get("indexerRate").toString())).isEqualByComparingTo("110.1250");
            assertThat(new BigDecimal(posicao.get("totalInvested").toString())).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("Titulo de outro usuario nao pode receber lancamento")
        void tituloDeOutroUsuario() {
            TransactionResponseDTO deA = lancarComSucesso(dono, aplicacao("2026-09-01", "1000.00", "BTG"));

            Map<String, Object> corpo = aplicacao("2026-09-02", "500.00", "BTG");
            corpo.put("noteId", deA.noteId());

            ResponseEntity<String> resposta = lancarEsperandoErro(outro, corpo);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Título não encontrado");
        }
    }

    // ------------------------------------------------------------------
    // Tesouro Direto — Exemplos 4, 5 e 6
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Tesouro Direto — FIFO por titulo")
    class Tesouro {

        private static final String TICKER = CatalogIds.TESOURO_IPCA_2050;

        private Map<String, Object> compraDeTesouro(String data, String cotas, String preco, String taxa) {
            Map<String, Object> corpo = lancamento(TICKER, "BUY", data, cotas, preco);
            corpo.put("issuer", "Tesouro Nacional");
            corpo.put("indexerRate", new BigDecimal(taxa));
            return corpo;
        }

        private Map<String, Object> vendaDeTesouro(String data, String cotas, String preco) {
            return lancamento(TICKER, "SELL", data, cotas, preco);
        }

        /** Titulos do ativo, do mais antigo para o mais novo, com cotas e liquidacao. */
        private List<Map<String, Object>> titulos() {
            return jdbc.queryForList("""
                SELECT n.purchase_date, n.indexer_rate, pa.is_settled, pa.id
                FROM portfolio_assets pa
                JOIN fixed_income_notes n ON n.id = pa.note_id
                JOIN assets a ON a.id = pa.asset_id
                WHERE a.ticker = ?
                ORDER BY n.purchase_date
                """, TICKER);
        }

        private BigDecimal cotasDoTitulo(Object positionId) {
            return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN type = 'BUY' THEN shares ELSE -shares END), 0)
                FROM transactions WHERE portfolio_asset_id = ?
                """, BigDecimal.class, positionId);
        }

        @Test
        @DisplayName("Regra H — dois titulos do mesmo ativo comprados no mesmo dia sao recusados")
        void regraH() {
            lancarComSucesso(dono, compraDeTesouro("2026-09-01", "3", "830.07", "7.09"));

            ResponseEntity<String> resposta = lancarEsperandoErro(dono,
                compraDeTesouro("2026-09-01", "2", "830.08", "7.10"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody())
                .contains("Já existe um título de Tesouro IPCA+ 2050 comprado em 01/09/2026")
                .contains("altere a quantidade da compra que já existe");
        }

        @Test
        @DisplayName("Exemplo 4 — a venda de 5 cotas consome 3 do titulo de 01/09 (liquidado) e 2 do de 15/09")
        void exemplo4() {
            lancarComSucesso(dono, compraDeTesouro("2026-09-01", "3", "830.07", "7.09"));
            lancarComSucesso(dono, compraDeTesouro("2026-09-15", "4", "830.10", "7.50"));

            TransactionResponseDTO venda = lancarComSucesso(dono, vendaDeTesouro("2026-09-17", "5", "850.00"));

            assertThat(venda.groupShares()).isEqualByComparingTo("5");

            List<Map<String, Object>> titulos = titulos();
            assertThat(titulos).hasSize(2);
            assertThat(titulos.get(0).get("is_settled")).isEqualTo(true);
            assertThat(cotasDoTitulo(titulos.get(0).get("id"))).isEqualByComparingTo("0");
            assertThat(titulos.get(1).get("is_settled")).isEqualTo(false);
            assertThat(cotasDoTitulo(titulos.get(1).get("id"))).isEqualByComparingTo("2");

            // A venda foi repartida em duas partes do mesmo grupo.
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT sale_group_id) FROM transactions WHERE type = 'SELL'", Long.class))
                .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE type = 'SELL'", Long.class)).isEqualTo(2);
        }

        @Test
        @DisplayName("Exemplo 5 — a venda de 14/09 falha: o titulo de 15/09 foi comprado depois e nao conta")
        void exemplo5() {
            lancarComSucesso(dono, compraDeTesouro("2026-09-01", "3", "830.07", "7.09"));
            lancarComSucesso(dono, compraDeTesouro("2026-09-15", "4", "830.10", "7.50"));

            ResponseEntity<String> resposta = lancarEsperandoErro(dono, vendaDeTesouro("2026-09-14", "5", "850.00"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Saldo insuficiente de Tesouro IPCA+ 2050 em 14/09/2026");

            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE type = 'SELL'", Long.class)).isZero();
            List<Map<String, Object>> titulos = titulos();
            assertThat(cotasDoTitulo(titulos.get(0).get("id"))).isEqualByComparingTo("3");
            assertThat(cotasDoTitulo(titulos.get(1).get("id"))).isEqualByComparingTo("4");
        }

        @Test
        @DisplayName("Exemplo 6 — a compra retroativa de 31/08 refaz a reparticao: 4 cotas dela e 1 do titulo de 01/09")
        void exemplo6() {
            lancarComSucesso(dono, compraDeTesouro("2026-09-01", "3", "830.07", "7.09"));
            lancarComSucesso(dono, compraDeTesouro("2026-09-15", "4", "830.10", "7.50"));
            lancarComSucesso(dono, vendaDeTesouro("2026-09-17", "5", "850.00"));

            // Estado do Exemplo 4, antes da compra retroativa.
            assertThat(titulos().get(0).get("is_settled")).isEqualTo(true);

            lancarComSucesso(dono, compraDeTesouro("2026-08-31", "4", "830.05", "6.90"));

            List<Map<String, Object>> titulos = titulos();
            assertThat(titulos).hasSize(3);

            Map<String, Object> retroativa = titulos.get(0);
            Map<String, Object> primeira = titulos.get(1);
            Map<String, Object> segunda = titulos.get(2);

            // a) a reparticao antiga foi desfeita: o titulo de 01/09 reabriu.
            assertThat(primeira.get("is_settled")).isEqualTo(false);
            assertThat(cotasDoTitulo(primeira.get("id"))).isEqualByComparingTo("2");

            // b) a venda saiu do titulo mais antigo: 4 cotas dele, que fica liquidado.
            assertThat(retroativa.get("is_settled")).isEqualTo(true);
            assertThat(cotasDoTitulo(retroativa.get("id"))).isEqualByComparingTo("0");

            // O titulo de 15/09 nao foi tocado.
            assertThat(segunda.get("is_settled")).isEqualTo(false);
            assertThat(cotasDoTitulo(segunda.get("id"))).isEqualByComparingTo("4");

            // A venda continua sendo uma so, de 5 cotas, agora em duas partes.
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT sale_group_id) FROM transactions WHERE type = 'SELL'", Long.class))
                .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                "SELECT COALESCE(SUM(shares), 0) FROM transactions WHERE type = 'SELL'", BigDecimal.class))
                .isEqualByComparingTo("5");
        }
    }
}
