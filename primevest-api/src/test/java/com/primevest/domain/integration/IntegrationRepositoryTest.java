package com.primevest.domain.integration;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.asset.AssetRepository;
import com.primevest.domain.integration.credential.ApiKeyCredentials;
import com.primevest.domain.integration.credential.CredentialCipher;
import com.primevest.domain.integration.credential.CredentialUnavailableException;
import com.primevest.domain.integration.credential.CredentialVault;
import com.primevest.domain.portfolio.Portfolio;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.PortfolioAssetRepository;
import com.primevest.domain.portfolio.PortfolioRepository;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionOrigin;
import com.primevest.domain.transaction.TransactionRepository;
import com.primevest.domain.user.User;
import com.primevest.domain.user.UserRepository;
import com.primevest.support.CatalogIds;
import com.primevest.support.Fixtures;
import com.primevest.support.JpaTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V25: tabelas de integracao, colunas novas de transactions e a credencial
 * cifrada gravada e lida de verdade no PostgreSQL.
 *
 * <p>Todos os ids, datas e valores sao ficticios: dado real de conta nao entra
 * no repositorio.</p>
 */
@Import({CredentialVault.class, CredentialCipher.class, JacksonAutoConfiguration.class})
class IntegrationRepositoryTest extends JpaTestBase {

    private static final String SNAPSHOT = "{\"fonte\":\"spot\",\"symbol\":\"BTCBRL\",\"orderId\":\"1000000001\"}";

    @Autowired private IntegrationConnectionRepository connections;
    @Autowired private IntegrationCredentialRepository credentials;
    @Autowired private IntegrationCursorRepository cursors;
    @Autowired private IntegrationRunRepository runs;
    @Autowired private IntegrationRunItemRepository runItems;
    @Autowired private IntegrationCryptoExclusionRepository exclusions;
    @Autowired private CredentialVault vault;
    @Autowired private TransactionRepository transactions;
    @Autowired private PortfolioRepository portfolios;
    @Autowired private PortfolioAssetRepository positions;
    @Autowired private UserRepository users;
    @Autowired private AssetRepository assets;
    @Autowired private EntityManager em;

    private User dono;
    private Asset btc;
    private IntegrationConnection binance;
    private PortfolioAsset posicaoBtc;

    @BeforeEach
    void cenario() {
        dono = users.save(Fixtures.user("dono@primevest.test"));
        btc = CatalogIds.asset(assets, CatalogIds.BTC);
        binance = connections.saveAndFlush(Fixtures.binance(dono, "Binance principal"));
        Portfolio carteira = portfolios.save(Fixtures.portfolio(dono));
        posicaoBtc = positions.save(Fixtures.position(carteira, btc));
    }

    private Transaction importada(String externalId) {
        Transaction tx = Fixtures.buy(posicaoBtc, LocalDate.of(2024, 3, 10), "0.00250000", "400000.00000000");
        tx.setOrigin(TransactionOrigin.AUTO);
        tx.setExternalId(externalId);
        tx.setConnection(binance);
        tx.setExecutedAt(LocalDateTime.of(2024, 3, 10, 14, 0, 0));
        tx.setSourceSnapshot(SNAPSHOT);
        return tx;
    }

    private Object nativo(String sql, Object id) {
        return em.createNativeQuery(sql).setParameter("id", id).getSingleResult();
    }

    // ---------------------------------------------------------------- conexao

    @Test
    @DisplayName("Conexao nasce ACTIVE; o apelido e unico por usuario, mas repete entre usuarios")
    void apelidoUnicoPorUsuario() {
        assertThat(binance.getStatus()).isEqualTo(ConnectionStatus.ACTIVE);

        User outro = users.save(Fixtures.user("outro@primevest.test"));
        assertThat(connections.saveAndFlush(Fixtures.binance(outro, "Binance principal")).getId()).isNotNull();

        // Por ultimo: depois de um erro o PostgreSQL aborta a transacao do teste.
        assertThatThrownBy(() -> connections.saveAndFlush(Fixtures.binance(dono, "Binance principal")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_integration_connections_label");
    }

    @Test
    @DisplayName("findOwnedById: conexao de outro usuario nao e encontrada")
    void escopoDoDono() {
        User outro = users.save(Fixtures.user("outro@primevest.test"));

        assertThat(connections.findOwnedById(binance.getId(), dono.getId())).isPresent();
        assertThat(connections.findOwnedById(binance.getId(), outro.getId())).isEmpty();
        assertThat(connections.findAllOwned(outro.getId())).isEmpty();
    }

    // ---------------------------------------------------------------- credencial

    @Test
    @DisplayName("Vault: grava cifrado, le de volta, e o banco nao tem o segredo em claro")
    void credencialCifrada() {
        vault.store(binance, new ApiKeyCredentials("chave-publica-a7K2", "segredo-muito-secreto"));
        em.flush();
        em.clear();

        byte[] gravado = (byte[]) nativo("SELECT ciphertext FROM integration_credentials WHERE connection_id = :id", binance.getId());
        assertThat(new String(gravado, StandardCharsets.ISO_8859_1))
            .doesNotContain("segredo-muito-secreto").doesNotContain("chave-publica");
        assertThat(nativo("SELECT fingerprint FROM integration_credentials WHERE connection_id = :id", binance.getId()))
            .isEqualTo("a7K2");

        ApiKeyCredentials lida = vault.read(binance.getId());
        assertThat(lida.apiKey()).isEqualTo("chave-publica-a7K2");
        assertThat(lida.apiSecret()).isEqualTo("segredo-muito-secreto");
    }

    @Test
    @DisplayName("Trocar a chave regrava a mesma linha, com IV novo, e zera a validacao anterior")
    void trocaDeChave() {
        IntegrationCredential primeira = vault.store(binance, new ApiKeyCredentials("chave-1-AAAA", "s1"));
        primeira.setValidatedAt(LocalDateTime.now());
        primeira.setPermissions("{\"enableReading\":true}");
        em.flush();
        byte[] ivAntes = primeira.getIv().clone();

        IntegrationCredential segunda = vault.store(binance, new ApiKeyCredentials("chave-2-BBBB", "s2"));
        em.flush();

        assertThat(credentials.count()).isEqualTo(1);
        assertThat(segunda.getIv()).isNotEqualTo(ivAntes);
        assertThat(segunda.getValidatedAt()).isNull();
        assertThat(segunda.getPermissions()).isNull();
        assertThat(vault.read(binance.getId()).apiSecret()).isEqualTo("s2");
    }

    @Test
    @DisplayName("Ciphertext copiado para outra conexao nao decifra (AAD = id da conexao)")
    void copiadoNaoDecifra() {
        vault.store(binance, new ApiKeyCredentials("chave-publica-a7K2", "segredo"));
        IntegrationConnection outra = connections.saveAndFlush(Fixtures.binance(dono, "Binance 2"));
        vault.store(outra, new ApiKeyCredentials("outra-chave-ZZZZ", "outro-segredo"));
        em.flush();

        em.createNativeQuery("""
            UPDATE integration_credentials SET
              ciphertext = (SELECT ciphertext FROM integration_credentials WHERE connection_id = :origem),
              iv         = (SELECT iv FROM integration_credentials WHERE connection_id = :origem)
            WHERE connection_id = :destino""")
            .setParameter("origem", binance.getId())
            .setParameter("destino", outra.getId())
            .executeUpdate();
        em.clear();

        assertThatThrownBy(() -> vault.read(outra.getId())).isInstanceOf(CredentialUnavailableException.class);
    }

    @Test
    @DisplayName("Revogar apaga a credencial de verdade")
    void revogar() {
        vault.store(binance, new ApiKeyCredentials("chave-publica-a7K2", "segredo"));
        em.flush();

        vault.erase(binance.getId());
        em.flush();

        assertThat(credentials.existsById(binance.getId())).isFalse();
        assertThatThrownBy(() -> vault.read(binance.getId())).isInstanceOf(CredentialUnavailableException.class);
    }

    @Test
    @DisplayName("ck_integration_credentials_iv: IV fora de 12 bytes e recusado pelo banco")
    void ivDeDozeBytes() {
        IntegrationCredential c = new IntegrationCredential();
        c.setConnection(binance);
        c.setCiphertext(new byte[] {1, 2, 3});
        c.setIv(new byte[16]);
        c.setKeyVersion((short) 1);
        c.setFingerprint("a7K2");

        assertThatThrownBy(() -> credentials.saveAndFlush(c))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_integration_credentials_iv");
    }

    // ---------------------------------------------------------------- transactions

    @Test
    @DisplayName("Transacao manual continua nascendo MANUAL, sem nenhum campo de importacao")
    void manualPorPadrao() {
        Transaction tx = transactions.saveAndFlush(Fixtures.buy(posicaoBtc, LocalDate.of(2024, 3, 10), "0.001", "500000"));

        assertThat(tx.getOrigin()).isEqualTo(TransactionOrigin.MANUAL);
        assertThat(nativo("SELECT origin FROM transactions WHERE id = :id", tx.getId())).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Importada grava origem, conexao e snapshot em JSONB consultavel")
    void importadaGravaSnapshot() {
        Transaction tx = transactions.saveAndFlush(importada("binance:spot:BTCBRL:1000000001"));
        em.clear();

        assertThat(nativo("SELECT source_snapshot->>'orderId' FROM transactions WHERE id = :id", tx.getId()))
            .isEqualTo("1000000001");
        Transaction lida = transactions.findById(tx.getId()).orElseThrow();
        assertThat(lida.getOrigin()).isEqualTo(TransactionOrigin.AUTO);
        assertThat(lida.getConnection().getId()).isEqualTo(binance.getId());
        assertThat(lida.getEditedAt()).isNull();
    }

    @Test
    @DisplayName("findImportedIds: devolve so os ids ja importados por esta conexao")
    void idsJaImportados() {
        transactions.saveAndFlush(importada("binance:spot:BTCBRL:1000000001"));
        IntegrationConnection outra = connections.saveAndFlush(Fixtures.binance(dono, "Binance 2"));

        assertThat(transactions.findImportedIds(binance.getId(),
            List.of("binance:spot:BTCBRL:1000000001", "binance:spot:BTCBRL:1000000009")))
            .containsExactly("binance:spot:BTCBRL:1000000001");
        assertThat(transactions.findImportedIds(outra.getId(), List.of("binance:spot:BTCBRL:1000000001"))).isEmpty();
    }

    @Test
    @DisplayName("uk_transactions_external: a mesma operacao nao entra duas vezes pela mesma conexao")
    void idempotencia() {
        transactions.saveAndFlush(importada("binance:spot:BTCBRL:1000000001"));

        assertThatThrownBy(() -> transactions.saveAndFlush(importada("binance:spot:BTCBRL:1000000001")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_transactions_external");
    }

    @Test
    @DisplayName("ck_transactions_origin: AUTO sem external_id e recusada")
    void autoSemIdentificador() {
        Transaction autoSemId = importada("x");
        autoSemId.setExternalId(null);

        assertThatThrownBy(() -> transactions.saveAndFlush(autoSemId))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_transactions_origin");
    }

    @Test
    @DisplayName("ck_transactions_origin: MANUAL com external_id e recusada")
    void manualComIdentificador() {
        Transaction manualComId = Fixtures.buy(posicaoBtc, LocalDate.of(2024, 3, 10), "0.001", "500000");
        manualComId.setExternalId("binance:spot:BTCBRL:1");

        assertThatThrownBy(() -> transactions.saveAndFlush(manualComId))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_transactions_origin");
    }

    // ---------------------------------------------------------------- exclusoes

    @Test
    @DisplayName("Exclusao de cripto: unica por conexao e consultada em lote pela sincronizacao")
    void exclusoes() {
        exclusions.saveAndFlush(IntegrationCryptoExclusion.builder()
            .connection(binance).externalId("binance:spot:BTCBRL:1000000002").asset(btc)
            .sourceSnapshot(SNAPSHOT).build());

        assertThat(exclusions.findExcludedIds(binance.getId(),
            List.of("binance:spot:BTCBRL:1000000001", "binance:spot:BTCBRL:1000000002")))
            .containsExactly("binance:spot:BTCBRL:1000000002");

        assertThatThrownBy(() -> exclusions.saveAndFlush(IntegrationCryptoExclusion.builder()
                .connection(binance).externalId("binance:spot:BTCBRL:1000000002").asset(btc)
                .sourceSnapshot(SNAPSHOT).build()))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_integration_crypto_exclusions");
    }

    // ---------------------------------------------------------------- cascata

    @Test
    @DisplayName("Apagar a conexao leva credencial, cursores, execucoes, itens e exclusoes; a transacao fica, sem vinculo")
    void cascata() {
        vault.store(binance, new ApiKeyCredentials("chave-publica-a7K2", "segredo"));
        cursors.save(new IntegrationCursor(new IntegrationCursorId(binance.getId(), "spot:BTCBRL"), "900000001", null));
        IntegrationRun run = runs.save(IntegrationRun.builder().connection(binance).triggerType(RunTrigger.CONNECTION).build());
        runItems.save(IntegrationRunItem.builder().run(run).externalId("binance:spot:ETHBTC:1")
            .outcome(RunItemOutcome.UNMAPPED).reason("sem valor em reais (R6)").raw("{}").build());
        exclusions.save(IntegrationCryptoExclusion.builder().connection(binance)
            .externalId("binance:spot:BTCBRL:1000000002").asset(btc).sourceSnapshot(SNAPSHOT).build());
        Transaction tx = transactions.save(importada("binance:spot:BTCBRL:1000000001"));
        em.flush();

        em.createNativeQuery("DELETE FROM integration_connections WHERE id = :id")
            .setParameter("id", binance.getId()).executeUpdate();
        em.clear();

        assertThat(credentials.count()).isZero();
        assertThat(cursors.count()).isZero();
        assertThat(runs.count()).isZero();
        assertThat(runItems.count()).isZero();
        assertThat(exclusions.count()).isZero();
        assertThat(nativo("SELECT connection_id FROM transactions WHERE id = :id", tx.getId())).isNull();
        assertThat(nativo("SELECT origin FROM transactions WHERE id = :id", tx.getId())).isEqualTo("AUTO");
    }

    @Test
    @DisplayName("Apagar o usuario apaga as conexoes dele")
    void usuarioApagado() {
        UUID id = binance.getId();
        em.createNativeQuery("DELETE FROM portfolios WHERE user_id = :id").setParameter("id", dono.getId()).executeUpdate();
        em.createNativeQuery("DELETE FROM users WHERE id = :id").setParameter("id", dono.getId()).executeUpdate();
        em.clear();

        assertThat(connections.existsById(id)).isFalse();
    }
}
