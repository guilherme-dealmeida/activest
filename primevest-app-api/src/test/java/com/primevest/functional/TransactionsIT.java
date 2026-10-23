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
class TransactionsIT extends FunctionalBase {

    private User owner;
    private User other;

    @BeforeEach
    void users() {
        owner = userWithPortfolio("dono@primevest.test");
        other = userWithPortfolio("outro@primevest.test");
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private long countTransactions(User user) {
        return transactionsOf(user).size();
    }

    // ------------------------------------------------------------------
    // Renda variavel
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Renda variavel")
    class Equities {

        @Test
        @DisplayName("Compra cria a posicao e aparece na listagem e na busca por id")
        void buyCreatesPosition() {
            TransactionResponseDTO createdId = recordSuccessfully(owner,
                buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));

            assertThat(createdId.ticker()).isEqualTo(CatalogIds.STOCK);
            assertThat(createdId.totalAmount()).isEqualByComparingTo("300.00");
            assertThat(createdId.assetTypeName()).isEqualTo("Ações");

            assertThat(transactionsOf(owner)).hasSize(1);
            assertThat(get(owner, "/api/v1/transactions/" + createdId.id(), TransactionResponseDTO.class)
                .getBody().id()).isEqualTo(createdId.id());
        }

        @Test
        @DisplayName("Preco medio ponderado: 100x10, 100x20 e venda de 100 deixam PM 15 e custo 1.500")
        void weightedAveragePrice() {
            recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "100", "10.00"));
            recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-02", "100", "20.00"));
            recordSuccessfully(owner, sale(CatalogIds.STOCK, "2026-09-03", "100", "30.00"));

            var position = positionOf(owner, CatalogIds.STOCK);
            assertThat(position.get("totalShares")).isEqualTo(100.0);
            assertThat(new BigDecimal(position.get("averagePrice").toString())).isEqualByComparingTo("15.0000");
            assertThat(new BigDecimal(position.get("totalInvested").toString())).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("Ticker fora do catalogo responde 422 e nao cria ativo")
        void missingTicker() {
            ResponseEntity<String> response = recordExpectingError(owner,
                buy("XXXX9", "2026-09-01", "10", "30.00"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Ativo não encontrado no catálogo: XXXX9");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE ticker = 'XXXX9'", Long.class))
                .isZero();
        }

        @Test
        @DisplayName("Regra A — venda de ativo fora da carteira responde 422 e nao grava nada")
        void saleWithoutPosition() {
            ResponseEntity<String> response = recordExpectingError(owner,
                sale(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Não há posição de PETR4 na carteira para vender");
            assertThat(countTransactions(owner)).isZero();
        }

        @Test
        @DisplayName("Vender tudo liquida a posicao; uma nova compra a reabre")
        void settlementAndReopening() {
            recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));
            recordSuccessfully(owner, sale(CatalogIds.STOCK, "2026-09-02", "10", "31.00"));

            assertThat(portfolioOf(owner)).isEmpty();
            assertThat(jdbc.queryForObject(
                "SELECT is_settled FROM portfolio_assets LIMIT 1", Boolean.class)).isTrue();

            recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-03", "5", "32.00"));

            assertThat(portfolioOf(owner)).hasSize(1);
            assertThat(jdbc.queryForObject(
                "SELECT is_settled FROM portfolio_assets LIMIT 1", Boolean.class)).isFalse();
        }

        @Test
        @DisplayName("Usuario B nao le, nao edita e nao exclui transacao de A — 404, nunca 403")
        void scopeByUser() {
            TransactionResponseDTO deA = recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));

            assertThat(get(other, "/api/v1/transactions/" + deA.id(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(put(other, "/api/v1/transactions/" + deA.id(),
                buy(CatalogIds.STOCK, "2026-09-01", "5", "30.00"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(delete(other, "/api/v1/transactions/" + deA.id()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(transactionsOf(other)).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Exemplos 1 a 3 — saldo por cota (regra B)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Exemplos 1 a 3 do escopo — saldo por cota em qualquer data")
    class SharesBalance {

        @BeforeEach
        void initialBuyAndSell() {
            recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));
            recordSuccessfully(owner, sale(CatalogIds.STOCK, "2026-09-04", "8", "31.00"));
        }

        @Test
        @DisplayName("Exemplo 1 — a segunda venda falha por faltarem 2 cotas em 11/09")
        void example1() {
            ResponseEntity<String> response = recordExpectingError(owner,
                sale(CatalogIds.STOCK, "2026-09-11", "4", "32.00"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody())
                .contains("Saldo insuficiente de PETR4 em 11/09/2026")
                .contains("faltam 2 cotas nessa data");
            assertThat(countTransactions(owner)).isEqualTo(2);
        }

        @Test
        @DisplayName("Exemplo 1a — uma compra de exatamente 2 cotas antes da data libera a venda")
        void example1a_buyOfTwoShares() {
            recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-10", "2", "31.00"));

            recordSuccessfully(owner, sale(CatalogIds.STOCK, "2026-09-11", "4", "32.00"));

            assertThat(countTransactions(owner)).isEqualTo(4);
        }

        @Test
        @DisplayName("Exemplo 1b — editar a compra original para 12 cotas libera a venda")
        void example1b_editTheBuy() {
            UUID originalBuy = transactionsOf(owner).stream()
                .filter(t -> t.type().name().equals("BUY")).findFirst().orElseThrow().id();

            assertThat(put(owner, "/api/v1/transactions/" + originalBuy,
                buy(CatalogIds.STOCK, "2026-09-01", "12", "30.00"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

            recordSuccessfully(owner, sale(CatalogIds.STOCK, "2026-09-11", "4", "32.00"));
        }

        @Test
        @DisplayName("Exemplo 1c — reduzir a primeira venda para 6 cotas tambem libera a segunda")
        void example1c_editTheSale() {
            UUID originalSale = transactionsOf(owner).stream()
                .filter(t -> t.type().name().equals("SELL")).findFirst().orElseThrow().id();

            assertThat(put(owner, "/api/v1/transactions/" + originalSale,
                sale(CatalogIds.STOCK, "2026-09-04", "6", "31.00"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

            recordSuccessfully(owner, sale(CatalogIds.STOCK, "2026-09-11", "4", "32.00"));
        }

        @Test
        @DisplayName("Exemplo 1d — a compra depois da venda nao resolve: a regra olha a data")
        void example1d_buyAfterSaleDoesNotFix() {
            recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-12", "2", "31.00"));

            ResponseEntity<String> response = recordExpectingError(owner,
                sale(CatalogIds.STOCK, "2026-09-11", "4", "32.00"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("em 11/09/2026");
        }

        @Test
        @DisplayName("Exemplo 2 — reduzir a compra para 5 cotas falha por faltarem 3 em 04/09, e nada muda")
        void example2() {
            UUID originalBuy = transactionsOf(owner).stream()
                .filter(t -> t.type().name().equals("BUY")).findFirst().orElseThrow().id();

            ResponseEntity<String> response = put(owner, "/api/v1/transactions/" + originalBuy,
                buy(CatalogIds.STOCK, "2026-09-01", "5", "30.00"), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody())
                .contains("em 04/09/2026")
                .contains("faltam 3 cotas");

            TransactionResponseDTO buyAfter = transactionsOf(owner).stream()
                .filter(t -> t.id().equals(originalBuy)).findFirst().orElseThrow();
            assertThat(buyAfter.shares()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("Exemplo 3 — excluir a compra falha por faltarem 8 cotas em 04/09, e a compra continua la")
        void example3() {
            UUID originalBuy = transactionsOf(owner).stream()
                .filter(t -> t.type().name().equals("BUY")).findFirst().orElseThrow().id();

            ResponseEntity<String> response = delete(owner, "/api/v1/transactions/" + originalBuy);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody())
                .contains("em 04/09/2026")
                .contains("faltam 8 cotas");
            assertThat(countTransactions(owner)).isEqualTo(2);
        }
    }


    // ------------------------------------------------------------------
    // Renda fixa (CDB) — regras E, F, G e o titulo
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Renda fixa")
    class FixedIncome {

        private Map<String, Object> investment(String data, String value, String issuer) {
            Map<String, Object> body = new HashMap<>();
            body.put("ticker", CatalogIds.CDB);
            body.put("type", "BUY");
            body.put("transactionDate", data);
            body.put("totalAmount", new BigDecimal(value));
            body.put("issuerId", testCompany(issuer, "BANCO"));
            body.put("distributorId", defaultDistributor());
            body.put("maturityDate", "2030-01-15");
            body.put("rateType", "POS_FIXADO");
            body.put("indexer", "CDI");
            body.put("indexerRate", new BigDecimal("110.125"));
            return body;
        }

        private Map<String, Object> redemption(String data, String value, UUID noteId, boolean fullSettlement) {
            Map<String, Object> body = new HashMap<>();
            body.put("ticker", CatalogIds.CDB);
            body.put("type", "SELL");
            body.put("transactionDate", data);
            body.put("totalAmount", new BigDecimal(value));
            body.put("noteId", noteId);
            if (fullSettlement) {
                body.put("isFullSettlement", true);
            }
            return body;
        }

        @Test
        @DisplayName("Dois CDBs de emissores diferentes sao duas linhas, distinguidas por emissor e vencimento")
        void twoCdbsTwoRows() {
            recordSuccessfully(owner, investment("2026-09-01", "1000.00", "BTG"));
            recordSuccessfully(owner, investment("2026-09-02", "2000.00", "Inter"));

            List<Map<String, Object>> rows = portfolioOf(owner).stream()
                .filter(l -> CatalogIds.CDB.equals(l.get("ticker"))).toList();

            assertThat(rows).hasSize(2);
            assertThat(rows).extracting(l -> l.get("issuer")).containsExactlyInAnyOrder("BTG", "Inter");
            assertThat(rows).allSatisfy(l -> assertThat(l.get("maturityDate")).isEqualTo("2030-01-15"));
        }

        @Test
        @DisplayName("A taxa do titulo guarda as 3 casas digitadas (IPCA + 6,125%)")
        void rateWithThreeDecimals() {
            TransactionResponseDTO createdId = recordSuccessfully(owner, investment("2026-09-01", "1000.00", "BTG"));

            assertThat(createdId.indexerRate()).isEqualByComparingTo("110.1250");
        }

        @Test
        @DisplayName("Renda fixa com quantidade e recusada: ela e lancada pelo valor total")
        void fixedIncomeWithQuantity() {
            Map<String, Object> body = investment("2026-09-01", "1000.00", "BTG");
            body.put("shares", new BigDecimal("1"));
            body.put("pricePerShare", new BigDecimal("1000.00"));

            ResponseEntity<String> response = recordExpectingError(owner, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Renda Fixa é lançada pelo valor total");
        }

        @Test
        @DisplayName("Venda de renda fixa sem apontar o titulo e recusada")
        void saleWithoutNote() {
            recordSuccessfully(owner, investment("2026-09-01", "1000.00", "BTG"));

            Map<String, Object> withoutNote = new HashMap<>();
            withoutNote.put("ticker", CatalogIds.CDB);
            withoutNote.put("type", "SELL");
            withoutNote.put("transactionDate", "2026-09-20");
            withoutNote.put("totalAmount", new BigDecimal("500.00"));

            ResponseEntity<String> response = recordExpectingError(owner, withoutNote);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Venda de renda fixa precisa apontar qual título está sendo vendido");
        }

        @Test
        @DisplayName("Resgate que passa do disponivel: o excedente paga IR (PENDENCIAS 27.5), a valorizacao e corrigida "
            + "e sobra o centavo da regra F")
        void partialRedemptionLeavesCent() {
            TransactionResponseDTO investment = recordSuccessfully(owner, investment("2026-01-10", "1000.00", "BTG"));

            recordSuccessfully(owner, redemption("2026-09-10", "1050.00", investment.noteId(), false));

            // sem CDI no banco de teste, a curva nao rende: os 50,00 liquidos a mais sao rendimento tributado a 20%
            // (243 dias) -> bruto 1050 / ... = 1000 + 50 / 0,8 = 1062,50; IR 12,50; valorizacao 62,50 + o centavo
            TransactionResponseDTO sale = transactionsOf(owner).stream()
                .filter(t -> "SELL".equals(t.type().name())).findFirst().orElseThrow();
            assertThat(sale.totalAmount()).isEqualByComparingTo("1062.50");
            assertThat(sale.incomeTax()).isEqualByComparingTo("12.50");
            Map<String, Object> position = positionOf(owner, CatalogIds.CDB);
            assertThat(new BigDecimal(position.get("valorization").toString())).isEqualByComparingTo("62.51");
            assertThat(position.get("isSettled")).isEqualTo(false);
        }

        @Test
        @DisplayName("Liquidacao total pelo liquido: bruto = liquido + IR + IOF, posicao zerada e ganho bruto realizado")
        void fullSettlement() {
            TransactionResponseDTO investment = recordSuccessfully(owner, investment("2026-01-10", "1000.00", "BTG"));

            recordSuccessfully(owner, redemption("2026-09-10", "1120.00", investment.noteId(), true));

            assertThat(portfolioOf(owner)).isEmpty();
            TransactionResponseDTO sale = transactionsOf(owner).stream()
                .filter(t -> "SELL".equals(t.type().name())).findFirst().orElseThrow();
            // o valor digitado e o liquido (PLANO_VENDA_RENDA_FIXA, R3): 243 dias, IR de 20% e sem IOF
            assertThat(sale.netAmount()).isEqualByComparingTo("1120.00");
            assertThat(sale.iofTax()).isEqualByComparingTo("0");
            assertThat(sale.totalAmount()).isEqualByComparingTo(
                sale.netAmount().add(sale.incomeTax()).add(sale.iofTax()));
            assertThat(sale.incomeTax()).isPositive();
            assertThat(jdbc.queryForObject(
                "SELECT appreciation FROM portfolio_assets LIMIT 1", BigDecimal.class))
                .isEqualByComparingTo(sale.totalAmount().subtract(new BigDecimal("1000.00")));
            assertThat(jdbc.queryForObject(
                "SELECT is_settled FROM portfolio_assets LIMIT 1", Boolean.class)).isTrue();
        }

        @Test
        @DisplayName("Regra G — liquidacao total abaixo do aplicado e recusada")
        void settlementBelowInvested() {
            TransactionResponseDTO investment = recordSuccessfully(owner, investment("2026-01-10", "1000.00", "BTG"));

            ResponseEntity<String> response = post(owner, "/api/v1/transactions/",
                redemption("2026-09-10", "900.00", investment.noteId(), true), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Em renda fixa não existe valorização negativa");
        }

        @Test
        @DisplayName("Editar so o valor nao apaga emissor, vencimento nem taxa do titulo")
        void editDoesNotDeleteTheNote() {
            TransactionResponseDTO createdId = recordSuccessfully(owner, investment("2026-09-01", "1000.00", "BTG"));

            Map<String, Object> onlyTheValue = new HashMap<>();
            onlyTheValue.put("ticker", CatalogIds.CDB);
            onlyTheValue.put("type", "BUY");
            onlyTheValue.put("transactionDate", "2026-09-01");
            onlyTheValue.put("totalAmount", new BigDecimal("1500.00"));
            assertThat(put(owner, "/api/v1/transactions/" + createdId.id(), onlyTheValue, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

            Map<String, Object> position = positionOf(owner, CatalogIds.CDB);
            assertThat(position.get("issuer")).isEqualTo("BTG");
            assertThat(position.get("maturityDate")).isEqualTo("2030-01-15");
            assertThat(new BigDecimal(position.get("indexerRate").toString())).isEqualByComparingTo("110.1250");
            assertThat(new BigDecimal(position.get("totalInvested").toString())).isEqualByComparingTo("1500.00");
        }

        @Test
        @DisplayName("Titulo de outro usuario nao pode receber lancamento")
        void noteOfOtherUser() {
            TransactionResponseDTO deA = recordSuccessfully(owner, investment("2026-09-01", "1000.00", "BTG"));

            Map<String, Object> body = investment("2026-09-02", "500.00", "BTG");
            body.put("noteId", deA.noteId());

            ResponseEntity<String> response = recordExpectingError(other, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Título não encontrado");
        }
    }

    // ------------------------------------------------------------------
    // Tesouro Direto — Exemplos 4, 5 e 6
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Tesouro Direto — FIFO por titulo")
    class Treasury {

        private static final String TICKER = CatalogIds.TREASURY_IPCA_2050;

        private Map<String, Object> treasuryBuy(String data, String shareCount, String price, String rate) {
            Map<String, Object> body = entry(TICKER, "BUY", data, shareCount, price);
            body.put("indexerRate", new BigDecimal(rate));
            return body;
        }

        private Map<String, Object> treasurySale(String data, String shareCount, String price) {
            return entry(TICKER, "SELL", data, shareCount, price);
        }

        /** Titulos do ativo, do mais antigo para o mais novo, com cotas e liquidacao. */
        private List<Map<String, Object>> notes() {
            return jdbc.queryForList("""
                SELECT n.purchase_date, n.indexer_rate, pa.is_settled, pa.id
                FROM portfolio_assets pa
                JOIN fixed_income_notes n ON n.id = pa.note_id
                JOIN assets a ON a.id = pa.asset_id
                WHERE a.ticker = ?
                ORDER BY n.purchase_date
                """, TICKER);
        }

        private BigDecimal noteShares(Object positionId) {
            return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN type = 'BUY' THEN shares ELSE -shares END), 0)
                FROM transactions WHERE portfolio_asset_id = ?
                """, BigDecimal.class, positionId);
        }

        @Test
        @DisplayName("Regra H — dois titulos do mesmo ativo comprados no mesmo dia sao recusados")
        void ruleH() {
            recordSuccessfully(owner, treasuryBuy("2026-09-01", "3", "830.07", "7.09"));

            ResponseEntity<String> response = recordExpectingError(owner,
                treasuryBuy("2026-09-01", "2", "830.08", "7.10"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody())
                .contains("Já existe um título de Tesouro IPCA+ 2050 comprado em 01/09/2026")
                .contains("altere a quantidade da compra que já existe");
        }

        @Test
        @DisplayName("Exemplo 4 — a venda de 5 cotas consome 3 do titulo de 01/09 (liquidado) e 2 do de 15/09")
        void example4() {
            recordSuccessfully(owner, treasuryBuy("2026-09-01", "3", "830.07", "7.09"));
            recordSuccessfully(owner, treasuryBuy("2026-09-15", "4", "830.10", "7.50"));

            TransactionResponseDTO sale = recordSuccessfully(owner, treasurySale("2026-09-17", "5", "850.00"));

            assertThat(sale.groupShares()).isEqualByComparingTo("5");

            List<Map<String, Object>> notes = notes();
            assertThat(notes).hasSize(2);
            assertThat(notes.get(0).get("is_settled")).isEqualTo(true);
            assertThat(noteShares(notes.get(0).get("id"))).isEqualByComparingTo("0");
            assertThat(notes.get(1).get("is_settled")).isEqualTo(false);
            assertThat(noteShares(notes.get(1).get("id"))).isEqualByComparingTo("2");

            // A venda foi repartida em duas partes do mesmo grupo.
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT sale_group_id) FROM transactions WHERE type = 'SELL'", Long.class))
                .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE type = 'SELL'", Long.class)).isEqualTo(2);
        }

        @Test
        @DisplayName("Exemplo 5 — a venda de 14/09 falha: o titulo de 15/09 foi comprado depois e nao conta")
        void example5() {
            recordSuccessfully(owner, treasuryBuy("2026-09-01", "3", "830.07", "7.09"));
            recordSuccessfully(owner, treasuryBuy("2026-09-15", "4", "830.10", "7.50"));

            ResponseEntity<String> response = recordExpectingError(owner, treasurySale("2026-09-14", "5", "850.00"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Saldo insuficiente de Tesouro IPCA+ 2050 em 14/09/2026");

            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE type = 'SELL'", Long.class)).isZero();
            List<Map<String, Object>> notes = notes();
            assertThat(noteShares(notes.get(0).get("id"))).isEqualByComparingTo("3");
            assertThat(noteShares(notes.get(1).get("id"))).isEqualByComparingTo("4");
        }

        @Test
        @DisplayName("Exemplo 6 — a compra retroativa de 31/08 refaz a reparticao: 4 cotas dela e 1 do titulo de 01/09")
        void example6() {
            recordSuccessfully(owner, treasuryBuy("2026-09-01", "3", "830.07", "7.09"));
            recordSuccessfully(owner, treasuryBuy("2026-09-15", "4", "830.10", "7.50"));
            recordSuccessfully(owner, treasurySale("2026-09-17", "5", "850.00"));

            // Estado do Exemplo 4, antes da compra retroativa.
            assertThat(notes().get(0).get("is_settled")).isEqualTo(true);

            recordSuccessfully(owner, treasuryBuy("2026-08-31", "4", "830.05", "6.90"));

            List<Map<String, Object>> notes = notes();
            assertThat(notes).hasSize(3);

            Map<String, Object> retroactive = notes.get(0);
            Map<String, Object> first = notes.get(1);
            Map<String, Object> second = notes.get(2);

            // a) a reparticao antiga foi desfeita: o titulo de 01/09 reabriu.
            assertThat(first.get("is_settled")).isEqualTo(false);
            assertThat(noteShares(first.get("id"))).isEqualByComparingTo("2");

            // b) a venda saiu do titulo mais antigo: 4 cotas dele, que fica liquidado.
            assertThat(retroactive.get("is_settled")).isEqualTo(true);
            assertThat(noteShares(retroactive.get("id"))).isEqualByComparingTo("0");

            // O titulo de 15/09 nao foi tocado.
            assertThat(second.get("is_settled")).isEqualTo(false);
            assertThat(noteShares(second.get("id"))).isEqualByComparingTo("4");

            // A venda continua sendo uma so, de 5 cotas, agora em duas partes.
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT sale_group_id) FROM transactions WHERE type = 'SELL'", Long.class))
                .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                "SELECT COALESCE(SUM(shares), 0) FROM transactions WHERE type = 'SELL'", BigDecimal.class))
                .isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("IR e IOF por titulo (PLANO_VENDA_RENDA_FIXA, 4.4): a venda de 17/09 paga IOF de 16 dias numa parte "
            + "e de 2 dias na outra; a previa da os mesmos numeros sem gravar")
        @SuppressWarnings("unchecked")
        void taxesByNote() {
            recordSuccessfully(owner, treasuryBuy("2026-09-01", "3", "830.07", "7.09"));
            recordSuccessfully(owner, treasuryBuy("2026-09-15", "4", "830.10", "7.50"));

            // previa: nada gravado
            Map<String, Object> preview = post(owner, "/api/v1/transactions/sale-preview",
                treasurySale("2026-09-17", "5", "850.00"), Map.class).getBody();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE type = 'SELL'", Long.class))
                .isZero();
            assertThat(number(preview.get("gross"))).isEqualByComparingTo("4250.00");
            // titulo de 01/09: 3 x (850 - 830,07) = 59,79; IOF 46% = 27,50; IR 22,5% de 32,29 = 7,26
            // titulo de 15/09: 2 x (850 - 830,10) = 39,80; IOF 93% = 37,01; IR 22,5% de 2,79 = 0,62
            assertThat(number(preview.get("iof"))).isEqualByComparingTo("64.51");
            assertThat(number(preview.get("ir"))).isEqualByComparingTo("7.88");
            assertThat(number(preview.get("net"))).isEqualByComparingTo("4177.61");
            assertThat((List<Object>) preview.get("lots")).hasSize(2);

            recordSuccessfully(owner, treasurySale("2026-09-17", "5", "850.00"));

            List<Map<String, Object>> parts = jdbc.queryForList("""
                SELECT t.shares, t.total_amount, t.iof_tax, t.income_tax, t.net_amount
                FROM transactions t JOIN portfolio_assets pa ON pa.id = t.portfolio_asset_id
                JOIN fixed_income_notes n ON n.id = pa.note_id
                WHERE t.type = 'SELL' ORDER BY n.purchase_date
                """);
            assertThat(parts).hasSize(2);
            assertThat(number(parts.get(0).get("iof_tax"))).isEqualByComparingTo("27.50");
            assertThat(number(parts.get(0).get("income_tax"))).isEqualByComparingTo("7.26");
            assertThat(number(parts.get(0).get("net_amount"))).isEqualByComparingTo("2515.24");
            assertThat(number(parts.get(1).get("iof_tax"))).isEqualByComparingTo("37.01");
            assertThat(number(parts.get(1).get("income_tax"))).isEqualByComparingTo("0.62");
            assertThat(number(parts.get(1).get("net_amount"))).isEqualByComparingTo("1662.37");
        }
    }

    // ------------------------------------------------------------------
    // Regra K
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Regra K — posicao sem transacao deixa de existir")
    class RuleK {

        private long positions() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM portfolio_assets", Long.class);
        }

        private long notes() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM fixed_income_notes", Long.class);
        }

        private void remove(Object txId) {
            assertThat(delete(owner, "/api/v1/transactions/" + txId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("Regra K — acao: a posicao fica enquanto houver transacao e sai com a ultima")
        void ruleK_stock() {
            TransactionResponseDTO first = recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-01", "10", "30.00"));
            TransactionResponseDTO second = recordSuccessfully(owner, buy(CatalogIds.STOCK, "2026-09-02", "5", "32.00"));

            remove(second.id());
            assertThat(positions()).isEqualTo(1);

            remove(first.id());
            assertThat(positions()).isZero();
            assertThat(portfolioOf(owner)).isEmpty();
        }

        @Test
        @DisplayName("Regra K — renda fixa liquidada com ganho: excluidas venda e compra, posicao, titulo e valorizacao somem")
        void ruleK_settledFixedIncome() {
            TransactionResponseDTO investment = recordSuccessfully(owner,
                cdbInvestment(CatalogIds.CDB, "2026-01-10", "1000.00", "BTG"));
            Map<String, Object> redemption = new HashMap<>();
            redemption.put("ticker", CatalogIds.CDB);
            redemption.put("type", "SELL");
            redemption.put("transactionDate", "2026-09-10");
            redemption.put("totalAmount", new BigDecimal("1300.00"));
            redemption.put("noteId", investment.noteId());
            redemption.put("isFullSettlement", true);
            TransactionResponseDTO sale = recordSuccessfully(owner, redemption);

            remove(sale.id());
            remove(investment.id());

            assertThat(positions()).isZero();
            assertThat(notes()).isZero();
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = get(owner, "/api/v1/portfolio/", Map.class).getBody();
            assertThat(number(summary.get("currentValue"))).isEqualByComparingTo("0");
            assertThat(number(summary.get("totalReturn"))).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Regra K — posicao que teve provento tambem sai, levando o provento junto")
        void ruleK_withDividend() {
            TransactionResponseDTO buy = recordSuccessfully(owner, buy(CatalogIds.FII, "2026-08-01", "10", "160.00"));
            UUID positionIdValue = jdbc.queryForObject("SELECT id FROM portfolio_assets", UUID.class);
            jdbc.update("INSERT INTO dividends (portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, type)"
                + " VALUES (?, 1.10, 11.00, DATE '2026-08-29', DATE '2026-09-12', 'RENDIMENTO')", positionIdValue);

            remove(buy.id());

            assertThat(positions()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dividends", Long.class)).isZero();
        }

        @Test
        @DisplayName("Regra K — Tesouro: excluir a unica compra apaga o titulo e libera a data para a regra H")
        void ruleK_treasury() {
            Map<String, Object> buyTreasury = entry(CatalogIds.TREASURY_IPCA_2050, "BUY", "2026-09-01", "3", "830.07");
            buyTreasury.put("indexerRate", new BigDecimal("7.09"));
            TransactionResponseDTO buy = recordSuccessfully(owner, buyTreasury);

            remove(buy.id());

            assertThat(positions()).isZero();
            assertThat(notes()).isZero();
            // A mesma compra, na mesma data, volta a ser aceita.
            recordSuccessfully(owner, buyTreasury);
        }
    }
}
