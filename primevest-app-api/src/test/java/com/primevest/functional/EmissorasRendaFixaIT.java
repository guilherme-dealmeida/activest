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
 * Emissoras e distribuidoras de renda fixa de ponta a ponta (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md): o seletor,
 * a validacao por produto, o codigo do papel (D4), o FIFO e a regra H do Tesouro por distribuidora (D3) e a troca de
 * distribuidora (5.1).
 */
class EmissorasRendaFixaIT extends FuncionalBase {

    private static final String TD = CatalogIds.TESOURO_IPCA_2050;

    private User dono;
    private Long corretoraA;
    private Long corretoraB;

    @BeforeEach
    void preparar() {
        dono = usuarioComCarteira("dono@primevest.test");
        corretoraA = empresaDeTeste("Corretora A", "CORRETORA_DISTRIBUIDORA");
        corretoraB = empresaDeTeste("Corretora B", "CORRETORA_DISTRIBUIDORA");
    }

    private Map<String, Object> compraTd(String data, String cotas, String preco, Long distribuidora) {
        Map<String, Object> corpo = lancamento(TD, "BUY", data, cotas, preco);
        corpo.put("indexerRate", new BigDecimal("7.09"));
        corpo.put("distributorId", distribuidora);
        return corpo;
    }

    private Map<String, Object> vendaTd(String data, String cotas, String preco, Long distribuidora) {
        Map<String, Object> corpo = lancamento(TD, "SELL", data, cotas, preco);
        corpo.put("distributorId", distribuidora);
        return corpo;
    }

    /** Distribuidora de cada titulo do Tesouro, do mais antigo para o mais novo. */
    private List<Long> distribuidorasDosTitulos() {
        return jdbc.queryForList("""
            SELECT n.distributor_id FROM fixed_income_notes n JOIN assets a ON a.id = n.asset_id
            WHERE a.ticker = ? ORDER BY n.purchase_date""", Long.class, TD);
    }

    private BigDecimal cotasNaDistribuidora(Long distribuidora) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(CASE WHEN t.type = 'BUY' THEN t.shares ELSE -t.shares END), 0)
            FROM transactions t JOIN portfolio_assets pa ON pa.id = t.portfolio_asset_id
            JOIN fixed_income_notes n ON n.id = pa.note_id
            WHERE n.distributor_id = ?""", BigDecimal.class, distribuidora);
    }

    // ------------------------------------------------------------------
    // Seletor e validacao por produto
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Seletor e validacao")
    class Seletor {

        @Test
        @DisplayName("Emissora de CDB: so bancos; \"Instituicao nao listada\" vem por ultimo")
        @SuppressWarnings("unchecked")
        void emissoraDeCdb() {
            empresaDeTeste("Banco Seletor", "BANCO");
            empresaDeTeste("Cooperativa Seletor", "COOPERATIVA");

            List<Map<String, Object>> lista = List.of(get(dono,
                "/api/v1/fixed-income-issuers?role=ISSUER&ticker=CDB&q=Seletor", Map[].class).getBody())
                .stream().map(m -> (Map<String, Object>) m).toList();

            assertThat(lista).extracting(m -> m.get("legalName"))
                .containsExactly("Banco Seletor", "Instituição não listada");
            assertThat(lista.get(0).get("label")).isEqualTo("Banco Seletor");
        }

        @Test
        @DisplayName("Distribuidora: corretoras, bancos e cooperativas — nunca securitizadora")
        @SuppressWarnings("unchecked")
        void distribuidora() {
            empresaDeTeste("Securitizadora Seletor", "SECURITIZADORA");
            empresaDeTeste("Cooperativa Seletor", "COOPERATIVA");

            List<Map<String, Object>> lista = List.of(get(dono,
                "/api/v1/fixed-income-issuers?role=DISTRIBUTOR&q=Seletor", Map[].class).getBody())
                .stream().map(m -> (Map<String, Object>) m).toList();

            assertThat(lista).extracting(m -> m.get("legalName"))
                .containsExactly("Cooperativa Seletor", "Instituição não listada");
        }

        @Test
        @DisplayName("CDB emitido por cooperativa e recusado: o produto nao aceita esse tipo de emissora")
        void cdbDeCooperativa() {
            Map<String, Object> corpo = aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG");
            corpo.put("issuerId", empresaDeTeste("Cooperativa Emissora", "COOPERATIVA"));

            ResponseEntity<String> resposta = lancarEsperandoErro(dono, corpo);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Cooperativa Emissora não emite");
        }

        @Test
        @DisplayName("Securitizadora nao e distribuidora")
        void distribuidoraInvalida() {
            Map<String, Object> corpo = aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG");
            corpo.put("distributorId", empresaDeTeste("Securitizadora X", "SECURITIZADORA"));

            ResponseEntity<String> resposta = lancarEsperandoErro(dono, corpo);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Securitizadora X não é distribuidora de títulos");
        }

        @Test
        @DisplayName("O titulo grava emissora, distribuidora e codigo; a resposta traz o nome de exibicao")
        void gravaNoTitulo() {
            Map<String, Object> corpo = aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "1000.00", "Banco Gravado");
            corpo.put("distributorId", corretoraA);
            corpo.put("paperCode", "cdb26a1");

            TransactionResponseDTO criada = lancarComSucesso(dono, corpo);

            Map<String, Object> titulo = jdbc.queryForMap(
                "SELECT issuer_id, distributor_id, paper_code FROM fixed_income_notes WHERE id = ?", criada.noteId());
            assertThat(titulo.get("issuer_id")).isEqualTo(empresaDeTeste("Banco Gravado", "BANCO"));
            assertThat(titulo.get("distributor_id")).isEqualTo(corretoraA);
            assertThat(titulo.get("paper_code")).isEqualTo("CDB26A1");
            assertThat(criada.issuer()).isEqualTo("Banco Gravado");
        }
    }

    // ------------------------------------------------------------------
    // Codigo do papel (D4)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Codigo do papel (D4)")
    class CodigoDoPapel {

        @Test
        @DisplayName("Codigo repetido num titulo novo e recusado e aponta o titulo que ja tem o codigo")
        void repetidoRecusado() {
            Map<String, Object> primeira = aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG");
            primeira.put("paperCode", "CDB123");
            lancarComSucesso(dono, primeira);

            Map<String, Object> segunda = aplicacaoEmCdb(CatalogIds.CDB, "2026-09-05", "500.00", "BTG");
            segunda.put("paperCode", "cdb123");
            ResponseEntity<String> resposta = lancarEsperandoErro(dono, segunda);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Já existe o título CDB").contains("com o código CDB123");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixed_income_notes", Long.class)).isEqualTo(1);
        }

        @Test
        @DisplayName("Aporte no mesmo titulo com o mesmo codigo e aceito")
        void aporteNoMesmoTitulo() {
            Map<String, Object> primeira = aplicacaoEmCdb(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG");
            primeira.put("paperCode", "CDB123");
            TransactionResponseDTO aplicacao = lancarComSucesso(dono, primeira);

            Map<String, Object> aporte = new HashMap<>();
            aporte.put("ticker", CatalogIds.CDB);
            aporte.put("type", "BUY");
            aporte.put("transactionDate", "2026-09-10");
            aporte.put("totalAmount", new BigDecimal("300.00"));
            aporte.put("noteId", aplicacao.noteId());
            aporte.put("paperCode", "CDB123");
            lancarComSucesso(dono, aporte);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixed_income_notes", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Long.class)).isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------
    // Tesouro por distribuidora (D3, R13)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Tesouro por distribuidora")
    class TesouroPorDistribuidora {

        @Test
        @DisplayName("Compra sem distribuidora e recusada")
        void distribuidoraObrigatoria() {
            Map<String, Object> corpo = compraTd("2026-09-01", "3", "830.07", null);

            ResponseEntity<String> resposta = lancarEsperandoErro(dono, corpo);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Informe a distribuidora");
        }

        @Test
        @DisplayName("Regra H por distribuidora: mesma data em distribuidoras diferentes e aceita; na mesma, recusada")
        void regraH() {
            lancarComSucesso(dono, compraTd("2026-09-01", "3", "830.07", corretoraA));
            lancarComSucesso(dono, compraTd("2026-09-01", "2", "830.07", corretoraB));

            ResponseEntity<String> resposta = lancarEsperandoErro(dono, compraTd("2026-09-01", "1", "830.07", corretoraA));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Já existe um título de Tesouro IPCA+ 2050 comprado em 01/09/2026");
            assertThat(distribuidorasDosTitulos()).containsExactlyInAnyOrder(corretoraA, corretoraB);
        }

        @Test
        @DisplayName("FIFO por distribuidora: a venda na B consome so os titulos da B")
        void fifoPorDistribuidora() {
            lancarComSucesso(dono, compraTd("2026-09-01", "3", "830.07", corretoraA));
            lancarComSucesso(dono, compraTd("2026-09-15", "4", "830.10", corretoraB));

            lancarComSucesso(dono, vendaTd("2026-09-17", "2", "850.00", corretoraB));

            assertThat(cotasNaDistribuidora(corretoraA)).isEqualByComparingTo("3");
            assertThat(cotasNaDistribuidora(corretoraB)).isEqualByComparingTo("2");
        }

        @Test
        @DisplayName("Titulos do Tesouro para o seletor da venda (D7): o ticker com minusculas acha os titulos, cada um "
            + "com a sua distribuidora")
        @SuppressWarnings("unchecked")
        void titulosParaOSeletorDaVenda() {
            lancarComSucesso(dono, compraTd("2026-09-01", "3", "830.07", corretoraA));
            lancarComSucesso(dono, compraTd("2026-09-15", "4", "830.10", corretoraB));

            // O "+" do ticker vai codificado (%2B), como o navegador manda; cru, viraria espaco
            String raiz = rest.getRootUri().replaceAll("/+$", "");
            java.net.URI uri = java.net.URI.create(raiz + "/api/v1/transactions/notes?ticker="
                + java.net.URLEncoder.encode(TD, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"));
            ResponseEntity<String> resposta = rest.exchange(uri, org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers(dono)), String.class);
            assertThat(resposta.getStatusCode()).as("%s -> %s", uri, resposta.getBody()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> notas;
            try {
                notas = new com.fasterxml.jackson.databind.ObjectMapper().readValue(resposta.getBody(), List.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new AssertionError(e);
            }

            assertThat(notas).hasSize(2);
            assertThat(notas).extracting(n -> ((Map<String, Object>) n.get("noteInfo")).get("distributorName"))
                .containsExactlyInAnyOrder("Corretora A", "Corretora B");
        }

        @Test
        @DisplayName("Saldo de uma distribuidora nao cobre a venda de outra")
        void saldoInsuficienteNaDistribuidora() {
            lancarComSucesso(dono, compraTd("2026-09-01", "3", "830.07", corretoraA));
            lancarComSucesso(dono, compraTd("2026-09-15", "4", "830.10", corretoraB));

            ResponseEntity<String> resposta = lancarEsperandoErro(dono, vendaTd("2026-09-17", "5", "850.00", corretoraB));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Saldo insuficiente de Tesouro IPCA+ 2050 em 17/09/2026")
                .contains("na distribuidora Corretora B");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE type = 'SELL'", Long.class)).isZero();
        }
    }

    // ------------------------------------------------------------------
    // Troca de distribuidora (5.1)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Troca de distribuidora")
    class TrocaDeDistribuidora {

        @Test
        @DisplayName("Tesouro sem venda ligada: troca direta pela edicao da compra")
        void tesouroDireto() {
            TransactionResponseDTO compra = lancarComSucesso(dono, compraTd("2026-09-01", "3", "830.07", corretoraA));

            ResponseEntity<TransactionResponseDTO> resposta = put(dono, "/api/v1/transactions/" + compra.id(),
                compraTd("2026-09-01", "3", "830.07", corretoraB), TransactionResponseDTO.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(distribuidorasDosTitulos()).containsExactly(corretoraB);
        }

        @Test
        @DisplayName("Tesouro com venda ligada: a edicao comum recusa e manda para a troca com a cascata")
        void tesouroComVendaRecusaEdicaoComum() {
            TransactionResponseDTO compra = lancarComSucesso(dono, compraTd("2026-09-01", "3", "830.07", corretoraA));
            lancarComSucesso(dono, vendaTd("2026-09-17", "2", "850.00", corretoraA));

            ResponseEntity<String> resposta = put(dono, "/api/v1/transactions/" + compra.id(),
                compraTd("2026-09-01", "3", "830.07", corretoraB), String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Este título tem venda ligada");
            assertThat(distribuidorasDosTitulos()).containsExactly(corretoraA);
        }

        @Test
        @DisplayName("Cascata: a previa lista a compra editada (travada), a venda e a outra compra que a venda consumiu; "
            + "com tudo marcado, tudo vai para a nova distribuidora")
        @SuppressWarnings("unchecked")
        void cascataCompleta() {
            TransactionResponseDTO primeira = lancarComSucesso(dono, compraTd("2026-09-01", "3", "830.07", corretoraA));
            TransactionResponseDTO segunda = lancarComSucesso(dono, compraTd("2026-09-15", "4", "830.10", corretoraA));
            lancarComSucesso(dono, vendaTd("2026-09-17", "5", "850.00", corretoraA));

            Map<String, Object> previa = get(dono, "/api/v1/transactions/" + primeira.id()
                + "/distributor-change?distributorId=" + corretoraB, Map.class).getBody();

            assertThat(previa.get("fromName")).isEqualTo("Corretora A");
            assertThat(previa.get("toName")).isEqualTo("Corretora B");
            List<Map<String, Object>> itens = (List<Map<String, Object>>) previa.get("items");
            assertThat(itens).extracting(i -> i.get("type")).containsExactly("BUY", "BUY", "SELL");
            assertThat(itens.get(0).get("id")).isEqualTo(primeira.id().toString());
            assertThat(itens.get(0).get("locked")).isEqualTo(true);
            assertThat(itens.get(1).get("id")).isEqualTo(segunda.id().toString());
            assertThat(itens.get(1).get("locked")).isEqualTo(false);

            Map<String, Object> pedido = new HashMap<>();
            pedido.put("purchase", compraTd("2026-09-01", "3", "830.07", corretoraB));
            pedido.put("purchaseIds", List.of(segunda.id()));
            pedido.put("saleGroupIds", List.of(UUID.fromString((String) itens.get(2).get("id"))));
            ResponseEntity<String> resposta = post(dono, "/api/v1/transactions/" + primeira.id() + "/distributor-change",
                pedido, String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(distribuidorasDosTitulos()).containsExactly(corretoraB, corretoraB);
            assertThat(cotasNaDistribuidora(corretoraB)).isEqualByComparingTo("2");
            assertThat(jdbc.queryForObject(
                "SELECT COALESCE(SUM(shares), 0) FROM transactions WHERE type = 'SELL'", BigDecimal.class))
                .isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("Cascata desmarcada que nao fecha a conta: nada muda (tudo ou nada)")
        @SuppressWarnings("unchecked")
        void cascataQueNaoFecha() {
            TransactionResponseDTO primeira = lancarComSucesso(dono, compraTd("2026-09-01", "3", "830.07", corretoraA));
            lancarComSucesso(dono, compraTd("2026-09-15", "4", "830.10", corretoraA));
            lancarComSucesso(dono, vendaTd("2026-09-17", "5", "850.00", corretoraA));

            // So a compra editada vai para a B: a venda de 5 cotas fica na A, que passa a ter so 4.
            Map<String, Object> pedido = new HashMap<>();
            pedido.put("purchase", compraTd("2026-09-01", "3", "830.07", corretoraB));
            pedido.put("purchaseIds", List.of());
            pedido.put("saleGroupIds", List.of());
            ResponseEntity<String> resposta = post(dono, "/api/v1/transactions/" + primeira.id() + "/distributor-change",
                pedido, String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("Saldo insuficiente");
            assertThat(distribuidorasDosTitulos()).containsExactly(corretoraA, corretoraA);
        }

        @Test
        @DisplayName("Renda fixa: a distribuidora muda pela edicao da compra, mesmo com resgate parcial")
        void rendaFixa() {
            Map<String, Object> corpo = aplicacaoEmCdb(CatalogIds.CDB, "2026-01-10", "1000.00", "BTG");
            corpo.put("distributorId", corretoraA);
            TransactionResponseDTO aplicacao = lancarComSucesso(dono, corpo);
            Map<String, Object> resgate = new HashMap<>();
            resgate.put("ticker", CatalogIds.CDB);
            resgate.put("type", "SELL");
            resgate.put("transactionDate", "2026-09-10");
            resgate.put("totalAmount", new BigDecimal("300.00"));
            resgate.put("noteId", aplicacao.noteId());
            lancarComSucesso(dono, resgate);

            Map<String, Object> edicao = aplicacaoEmCdb(CatalogIds.CDB, "2026-01-10", "1000.00", "BTG");
            edicao.put("distributorId", corretoraB);
            edicao.put("noteId", aplicacao.noteId());
            ResponseEntity<String> resposta = put(dono, "/api/v1/transactions/" + aplicacao.id(), edicao, String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(jdbc.queryForObject("SELECT distributor_id FROM fixed_income_notes WHERE id = ?", Long.class,
                aplicacao.noteId())).isEqualTo(corretoraB);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE type = 'SELL'", Long.class))
                .isEqualTo(1);
        }
    }
}
