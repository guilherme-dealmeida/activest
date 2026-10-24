package com.activest.functional;

import com.activest.domain.transaction.dto.TransactionResponseDTO;
import com.activest.domain.user.User;
import com.activest.support.CatalogIds;
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
class FixedIncomeIssuersIT extends FunctionalBase {

    private static final String TD = CatalogIds.TREASURY_IPCA_2050;

    private User owner;
    private Long brokerA;
    private Long brokerB;

    @BeforeEach
    void prepare() {
        owner = userWithPortfolio("dono@activest.test");
        brokerA = testCompany("Corretora A", "CORRETORA_DISTRIBUIDORA");
        brokerB = testCompany("Corretora B", "CORRETORA_DISTRIBUIDORA");
    }

    private Map<String, Object> treasuryBuyTd(String data, String shares, String price, Long distributor) {
        Map<String, Object> body = entry(TD, "BUY", data, shares, price);
        body.put("indexerRate", new BigDecimal("7.09"));
        body.put("distributorId", distributor);
        return body;
    }

    private Map<String, Object> treasurySaleTd(String data, String shares, String price, Long distributor) {
        Map<String, Object> body = entry(TD, "SELL", data, shares, price);
        body.put("distributorId", distributor);
        return body;
    }

    /** Distribuidora de cada titulo do Tesouro, do mais antigo para o mais novo. */
    private List<Long> notesDistributors() {
        return jdbc.queryForList("""
            SELECT n.distributor_id FROM fixed_income_notes n JOIN assets a ON a.id = n.asset_id
            WHERE a.ticker = ? ORDER BY n.purchase_date""", Long.class, TD);
    }

    private BigDecimal sharesAtDistributor(Long distributor) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(CASE WHEN t.type = 'BUY' THEN t.shares ELSE -t.shares END), 0)
            FROM transactions t JOIN portfolio_assets pa ON pa.id = t.portfolio_asset_id
            JOIN fixed_income_notes n ON n.id = pa.note_id
            WHERE n.distributor_id = ?""", BigDecimal.class, distributor);
    }

    // ------------------------------------------------------------------
    // Seletor e validacao por produto
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Seletor e validacao")
    class Selector {

        @Test
        @DisplayName("Emissora de CDB: so bancos; \"Instituicao nao listada\" vem por ultimo")
        @SuppressWarnings("unchecked")
        void cdbIssuer() {
            testCompany("Banco Seletor", "BANCO");
            testCompany("Cooperativa Seletor", "COOPERATIVA");

            List<Map<String, Object>> list = List.of(get(owner,
                "/api/v1/fixed-income-issuers?role=ISSUER&ticker=CDB&q=Seletor", Map[].class).getBody())
                .stream().map(m -> (Map<String, Object>) m).toList();

            assertThat(list).extracting(m -> m.get("legalName"))
                .containsExactly("Banco Seletor", "Instituição não listada");
            assertThat(list.get(0).get("label")).isEqualTo("Banco Seletor");
        }

        @Test
        @DisplayName("Distribuidora: corretoras, bancos e cooperativas — nunca securitizadora")
        @SuppressWarnings("unchecked")
        void distributor() {
            // nomes proprios: o "Banco Seletor" de outro teste da classe fica no cadastro e tambem distribui
            testCompany("Securitizadora Quimera", "SECURITIZADORA");
            testCompany("Cooperativa Quimera", "COOPERATIVA");

            List<Map<String, Object>> list = List.of(get(owner,
                "/api/v1/fixed-income-issuers?role=DISTRIBUTOR&q=Quimera", Map[].class).getBody())
                .stream().map(m -> (Map<String, Object>) m).toList();

            assertThat(list).extracting(m -> m.get("legalName"))
                .containsExactly("Cooperativa Quimera", "Instituição não listada");
        }

        @Test
        @DisplayName("CDB emitido por cooperativa e recusado: o produto nao aceita esse tipo de emissora")
        void creditUnionCdb() {
            Map<String, Object> body = cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG");
            body.put("issuerId", testCompany("Cooperativa Emissora", "COOPERATIVA"));

            ResponseEntity<String> response = recordExpectingError(owner, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Cooperativa Emissora não emite");
        }

        @Test
        @DisplayName("Securitizadora nao e distribuidora")
        void invalidDistributor() {
            Map<String, Object> body = cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG");
            body.put("distributorId", testCompany("Securitizadora X", "SECURITIZADORA"));

            ResponseEntity<String> response = recordExpectingError(owner, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Securitizadora X não é distribuidora de títulos");
        }

        @Test
        @DisplayName("O titulo grava emissora, distribuidora e codigo; a resposta traz o nome de exibicao")
        void storesOnNote() {
            Map<String, Object> body = cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "Banco Gravado");
            body.put("distributorId", brokerA);
            body.put("paperCode", "cdb26a1");

            TransactionResponseDTO createdId = recordSuccessfully(owner, body);

            Map<String, Object> note = jdbc.queryForMap(
                "SELECT issuer_id, distributor_id, paper_code FROM fixed_income_notes WHERE id = ?", createdId.noteId());
            assertThat(note.get("issuer_id")).isEqualTo(testCompany("Banco Gravado", "BANCO"));
            assertThat(note.get("distributor_id")).isEqualTo(brokerA);
            assertThat(note.get("paper_code")).isEqualTo("CDB26A1");
            assertThat(createdId.issuer()).isEqualTo("Banco Gravado");
        }
    }

    // ------------------------------------------------------------------
    // Logo por raiz de CNPJ (Adendo A)
    // ------------------------------------------------------------------

    @org.springframework.beans.factory.annotation.Autowired
    private com.activest.domain.fixedincome.FixedIncomeLogos logosRf;

    @Nested
    @DisplayName("Logo por raiz de CNPJ (Adendo A)")
    class Logo {

        @Test
        @DisplayName("Emissora com arquivo em logos/rf: o titulo traz a URL, servida sem token e com a CSP dos logos")
        void issuerLogo() throws Exception {
            Long bank = testCompany("Banco Com Logo", "BANCO");
            String root = jdbc.queryForObject("SELECT cnpj_root FROM fixed_income_issuers WHERE id = ?", String.class,
                bank);
            java.nio.file.Path rf = java.nio.file.Files.createDirectories(logosRf.dir());
            java.nio.file.Files.writeString(rf.resolve(root + ".svg"),
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><rect width=\"1\" height=\"1\"/></svg>");
            logosRf.reindex();
            try {
                Map<String, Object> body = cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "Banco Com Logo");
                TransactionResponseDTO createdId = recordSuccessfully(owner, body);

                String url = "/api/v1/fixed-income-issuers/logos/" + root;
                assertThat(createdId.noteInfo().issuerLogoUrl()).isEqualTo(url);

                ResponseEntity<byte[]> logo = rest.getForEntity(url, byte[].class);
                assertThat(logo.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(logo.getHeaders().getContentType().toString()).isEqualTo("image/svg+xml");
                assertThat(logo.getHeaders().getFirst("Content-Security-Policy")).contains("sandbox");
                assertThat(rest.getForEntity("/api/v1/fixed-income-issuers/logos/00000001", byte[].class)
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            } finally {
                java.nio.file.Files.deleteIfExists(rf.resolve(root + ".svg"));
                logosRf.reindex();
            }
        }

        @Test
        @DisplayName("Emissora e lider sem arquivo: vale o arquivo de outra empresa do grupo")
        void logoFromOtherGroupCompany() throws Exception {
            Long issuerEntity = testCompany("Banco Do Grupo Sem Logo", "BANCO");
            Long irma = testCompany("Financeira Do Grupo Com Logo", "FINANCEIRA");
            String siblingRoot = jdbc.queryForObject("SELECT cnpj_root FROM fixed_income_issuers WHERE id = ?",
                String.class, irma);
            Long group = jdbc.queryForObject("""
                INSERT INTO fixed_income_groups (bcb_code, name, name_source, leader_cnpj_root)
                VALUES ('C9' || floor(random() * 1000000)::text, 'Grupo De Teste', 'MANUAL', '99999998') RETURNING id""",
                Long.class);
            jdbc.update("UPDATE fixed_income_issuers SET group_id = ?, group_source = 'MANUAL' WHERE id IN (?, ?)",
                group, issuerEntity, irma);
            java.nio.file.Path rf = java.nio.file.Files.createDirectories(logosRf.dir());
            java.nio.file.Files.writeString(rf.resolve(siblingRoot + ".svg"),
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><rect width=\"1\" height=\"1\"/></svg>");
            logosRf.reindex();
            try {
                TransactionResponseDTO createdId = recordSuccessfully(owner,
                    cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "Banco Do Grupo Sem Logo"));

                assertThat(createdId.noteInfo().issuerLogoUrl())
                    .isEqualTo("/api/v1/fixed-income-issuers/logos/" + siblingRoot);
            } finally {
                java.nio.file.Files.deleteIfExists(rf.resolve(siblingRoot + ".svg"));
                logosRf.reindex();
                jdbc.update("UPDATE fixed_income_issuers SET group_id = NULL, group_source = NULL WHERE id IN (?, ?)",
                    issuerEntity, irma);
            }
        }

        @Test
        @DisplayName("Sem arquivo e sem ticker no grupo: o titulo fica sem logo (a tela mostra RF)")
        void withoutLogo() {
            TransactionResponseDTO createdId = recordSuccessfully(owner,
                cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "Banco Sem Logo"));

            assertThat(createdId.noteInfo().issuerLogoUrl()).isNull();
        }
    }

    // ------------------------------------------------------------------
    // Codigo do papel (D4)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Codigo do papel (D4)")
    class SecurityCode {

        @Test
        @DisplayName("Codigo repetido num titulo novo e recusado e aponta o titulo que ja tem o codigo")
        void repeatedRejected() {
            Map<String, Object> first = cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG");
            first.put("paperCode", "CDB123");
            recordSuccessfully(owner, first);

            Map<String, Object> second = cdbInvestment(CatalogIds.CDB, "2026-09-05", "500.00", "BTG");
            second.put("paperCode", "cdb123");
            ResponseEntity<String> response = recordExpectingError(owner, second);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Já existe o título CDB").contains("com o código CDB123");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixed_income_notes", Long.class)).isEqualTo(1);
        }

        @Test
        @DisplayName("Aporte no mesmo titulo com o mesmo codigo e aceito")
        void contributionToSameNote() {
            Map<String, Object> first = cdbInvestment(CatalogIds.CDB, "2026-09-01", "1000.00", "BTG");
            first.put("paperCode", "CDB123");
            TransactionResponseDTO investment = recordSuccessfully(owner, first);

            Map<String, Object> contribution = new HashMap<>();
            contribution.put("ticker", CatalogIds.CDB);
            contribution.put("type", "BUY");
            contribution.put("transactionDate", "2026-09-10");
            contribution.put("totalAmount", new BigDecimal("300.00"));
            contribution.put("noteId", investment.noteId());
            contribution.put("paperCode", "CDB123");
            recordSuccessfully(owner, contribution);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixed_income_notes", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Long.class)).isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------
    // Tesouro por distribuidora (D3, R13)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Tesouro por distribuidora")
    class TreasuryByDistributor {

        @Test
        @DisplayName("Compra sem distribuidora e recusada")
        void distributorRequired() {
            Map<String, Object> body = treasuryBuyTd("2026-09-01", "3", "830.07", null);

            ResponseEntity<String> response = recordExpectingError(owner, body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Informe a distribuidora");
        }

        @Test
        @DisplayName("Regra H por distribuidora: mesma data em distribuidoras diferentes e aceita; na mesma, recusada")
        void ruleH() {
            recordSuccessfully(owner, treasuryBuyTd("2026-09-01", "3", "830.07", brokerA));
            recordSuccessfully(owner, treasuryBuyTd("2026-09-01", "2", "830.07", brokerB));

            ResponseEntity<String> response = recordExpectingError(owner, treasuryBuyTd("2026-09-01", "1", "830.07", brokerA));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Já existe um título de Tesouro IPCA+ 2050 comprado em 01/09/2026");
            assertThat(notesDistributors()).containsExactlyInAnyOrder(brokerA, brokerB);
        }

        @Test
        @DisplayName("FIFO por distribuidora: a venda na B consome so os titulos da B")
        void fifoByDistributor() {
            recordSuccessfully(owner, treasuryBuyTd("2026-09-01", "3", "830.07", brokerA));
            recordSuccessfully(owner, treasuryBuyTd("2026-09-15", "4", "830.10", brokerB));

            recordSuccessfully(owner, treasurySaleTd("2026-09-17", "2", "850.00", brokerB));

            assertThat(sharesAtDistributor(brokerA)).isEqualByComparingTo("3");
            assertThat(sharesAtDistributor(brokerB)).isEqualByComparingTo("2");
        }

        @Test
        @DisplayName("Titulos do Tesouro para o seletor da venda (D7): o ticker com minusculas acha os titulos, cada um "
            + "com a sua distribuidora")
        @SuppressWarnings("unchecked")
        void notesForSaleSelector() {
            recordSuccessfully(owner, treasuryBuyTd("2026-09-01", "3", "830.07", brokerA));
            recordSuccessfully(owner, treasuryBuyTd("2026-09-15", "4", "830.10", brokerB));

            // O "+" do ticker vai codificado (%2B), como o navegador manda; cru, viraria espaco
            String root = rest.getRootUri().replaceAll("/+$", "");
            java.net.URI uri = java.net.URI.create(root + "/api/v1/transactions/notes?ticker="
                + java.net.URLEncoder.encode(TD, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"));
            ResponseEntity<String> response = rest.exchange(uri, org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers(owner)), String.class);
            assertThat(response.getStatusCode()).as("%s -> %s", uri, response.getBody()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> notes;
            try {
                notes = new com.fasterxml.jackson.databind.ObjectMapper().readValue(response.getBody(), List.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new AssertionError(e);
            }

            assertThat(notes).hasSize(2);
            assertThat(notes).extracting(n -> ((Map<String, Object>) n.get("noteInfo")).get("distributorName"))
                .containsExactlyInAnyOrder("Corretora A", "Corretora B");
        }

        @Test
        @DisplayName("Saldo de uma distribuidora nao cobre a venda de outra")
        void insufficientBalanceAtDistributor() {
            recordSuccessfully(owner, treasuryBuyTd("2026-09-01", "3", "830.07", brokerA));
            recordSuccessfully(owner, treasuryBuyTd("2026-09-15", "4", "830.10", brokerB));

            ResponseEntity<String> response = recordExpectingError(owner, treasurySaleTd("2026-09-17", "5", "850.00", brokerB));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Saldo insuficiente de Tesouro IPCA+ 2050 em 17/09/2026")
                .contains("na distribuidora Corretora B");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE type = 'SELL'", Long.class)).isZero();
        }
    }

    // ------------------------------------------------------------------
    // Troca de distribuidora (5.1)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Troca de distribuidora")
    class DistributorChange {

        @Test
        @DisplayName("Tesouro sem venda ligada: troca direta pela edicao da compra")
        void treasuryDirect() {
            TransactionResponseDTO buy = recordSuccessfully(owner, treasuryBuyTd("2026-09-01", "3", "830.07", brokerA));

            ResponseEntity<TransactionResponseDTO> response = put(owner, "/api/v1/transactions/" + buy.id(),
                treasuryBuyTd("2026-09-01", "3", "830.07", brokerB), TransactionResponseDTO.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(notesDistributors()).containsExactly(brokerB);
        }

        @Test
        @DisplayName("Tesouro com venda ligada: a edicao comum recusa e manda para a troca com a cascata")
        void treasuryWithSaleRejectsPlainEdit() {
            TransactionResponseDTO buy = recordSuccessfully(owner, treasuryBuyTd("2026-09-01", "3", "830.07", brokerA));
            recordSuccessfully(owner, treasurySaleTd("2026-09-17", "2", "850.00", brokerA));

            ResponseEntity<String> response = put(owner, "/api/v1/transactions/" + buy.id(),
                treasuryBuyTd("2026-09-01", "3", "830.07", brokerB), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Este título tem venda ligada");
            assertThat(notesDistributors()).containsExactly(brokerA);
        }

        @Test
        @DisplayName("Cascata: a previa lista a compra editada (travada), a venda e a outra compra que a venda consumiu; "
            + "com tudo marcado, tudo vai para a nova distribuidora")
        @SuppressWarnings("unchecked")
        void fullCascade() {
            TransactionResponseDTO first = recordSuccessfully(owner, treasuryBuyTd("2026-09-01", "3", "830.07", brokerA));
            TransactionResponseDTO second = recordSuccessfully(owner, treasuryBuyTd("2026-09-15", "4", "830.10", brokerA));
            recordSuccessfully(owner, treasurySaleTd("2026-09-17", "5", "850.00", brokerA));

            Map<String, Object> preview = get(owner, "/api/v1/transactions/" + first.id()
                + "/distributor-change?distributorId=" + brokerB, Map.class).getBody();

            assertThat(preview.get("fromName")).isEqualTo("Corretora A");
            assertThat(preview.get("toName")).isEqualTo("Corretora B");
            List<Map<String, Object>> runItemList = (List<Map<String, Object>>) preview.get("items");
            assertThat(runItemList).extracting(i -> i.get("type")).containsExactly("BUY", "BUY", "SELL");
            assertThat(runItemList.get(0).get("id")).isEqualTo(first.id().toString());
            assertThat(runItemList.get(0).get("locked")).isEqualTo(true);
            assertThat(runItemList.get(1).get("id")).isEqualTo(second.id().toString());
            assertThat(runItemList.get(1).get("locked")).isEqualTo(false);

            Map<String, Object> request = new HashMap<>();
            request.put("purchase", treasuryBuyTd("2026-09-01", "3", "830.07", brokerB));
            request.put("purchaseIds", List.of(second.id()));
            request.put("saleGroupIds", List.of(UUID.fromString((String) runItemList.get(2).get("id"))));
            ResponseEntity<String> response = post(owner, "/api/v1/transactions/" + first.id() + "/distributor-change",
                request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(notesDistributors()).containsExactly(brokerB, brokerB);
            assertThat(sharesAtDistributor(brokerB)).isEqualByComparingTo("2");
            assertThat(jdbc.queryForObject(
                "SELECT COALESCE(SUM(shares), 0) FROM transactions WHERE type = 'SELL'", BigDecimal.class))
                .isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("Cascata desmarcada que nao fecha a conta: nada muda (tudo ou nada)")
        @SuppressWarnings("unchecked")
        void cascadeThatDoesNotClose() {
            TransactionResponseDTO first = recordSuccessfully(owner, treasuryBuyTd("2026-09-01", "3", "830.07", brokerA));
            recordSuccessfully(owner, treasuryBuyTd("2026-09-15", "4", "830.10", brokerA));
            recordSuccessfully(owner, treasurySaleTd("2026-09-17", "5", "850.00", brokerA));

            // So a compra editada vai para a B: a venda de 5 cotas fica na A, que passa a ter so 4.
            Map<String, Object> request = new HashMap<>();
            request.put("purchase", treasuryBuyTd("2026-09-01", "3", "830.07", brokerB));
            request.put("purchaseIds", List.of());
            request.put("saleGroupIds", List.of());
            ResponseEntity<String> response = post(owner, "/api/v1/transactions/" + first.id() + "/distributor-change",
                request, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).contains("Saldo insuficiente");
            assertThat(notesDistributors()).containsExactly(brokerA, brokerA);
        }

        @Test
        @DisplayName("Renda fixa: a distribuidora muda pela edicao da compra, mesmo com resgate parcial")
        void fixedIncome() {
            Map<String, Object> body = cdbInvestment(CatalogIds.CDB, "2026-01-10", "1000.00", "BTG");
            body.put("distributorId", brokerA);
            TransactionResponseDTO investment = recordSuccessfully(owner, body);
            Map<String, Object> redemption = new HashMap<>();
            redemption.put("ticker", CatalogIds.CDB);
            redemption.put("type", "SELL");
            redemption.put("transactionDate", "2026-09-10");
            redemption.put("totalAmount", new BigDecimal("300.00"));
            redemption.put("noteId", investment.noteId());
            recordSuccessfully(owner, redemption);

            Map<String, Object> edit = cdbInvestment(CatalogIds.CDB, "2026-01-10", "1000.00", "BTG");
            edit.put("distributorId", brokerB);
            edit.put("noteId", investment.noteId());
            ResponseEntity<String> response = put(owner, "/api/v1/transactions/" + investment.id(), edit, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(jdbc.queryForObject("SELECT distributor_id FROM fixed_income_notes WHERE id = ?", Long.class,
                investment.noteId())).isEqualTo(brokerB);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE type = 'SELL'", Long.class))
                .isEqualTo(1);
        }
    }
}
