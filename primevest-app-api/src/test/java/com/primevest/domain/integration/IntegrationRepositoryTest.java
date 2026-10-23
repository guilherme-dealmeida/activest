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

    private User owner;
    private Asset btc;
    private IntegrationConnection binance;
    private PortfolioAsset btcPosition;

    @BeforeEach
    void scenario() {
        owner = users.save(Fixtures.user("dono@primevest.test"));
        btc = CatalogIds.asset(assets, CatalogIds.BTC);
        binance = connections.saveAndFlush(Fixtures.binance(owner, "Binance principal"));
        Portfolio portfolioEntity = portfolios.save(Fixtures.portfolio(owner));
        btcPosition = positions.save(Fixtures.position(portfolioEntity, btc));
    }

    private Transaction imported(String externalId) {
        Transaction tx = Fixtures.buy(btcPosition, LocalDate.of(2024, 3, 10), "0.00250000", "400000.00000000");
        tx.setOrigin(TransactionOrigin.AUTO);
        tx.setExternalId(externalId);
        tx.setConnection(binance);
        tx.setExecutedAt(LocalDateTime.of(2024, 3, 10, 14, 0, 0));
        tx.setSourceSnapshot(SNAPSHOT);
        return tx;
    }

    private Object nativeQuery(String sql, Object id) {
        return em.createNativeQuery(sql).setParameter("id", id).getSingleResult();
    }

    // ---------------------------------------------------------------- conexao

    @Test
    @DisplayName("Conexao nasce ACTIVE; o apelido e unico por usuario, mas repete entre usuarios")
    void nicknameUniquePerUser() {
        assertThat(binance.getStatus()).isEqualTo(ConnectionStatus.ACTIVE);

        User other = users.save(Fixtures.user("outro@primevest.test"));
        assertThat(connections.saveAndFlush(Fixtures.binance(other, "Binance principal")).getId()).isNotNull();

        // Por ultimo: depois de um erro o PostgreSQL aborta a transacao do teste.
        assertThatThrownBy(() -> connections.saveAndFlush(Fixtures.binance(owner, "Binance principal")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_integration_connections_label");
    }

    @Test
    @DisplayName("V40: conexao com a B3 aguardando a autorizacao do investidor e aceita pelo banco")
    void b3ConnectionAwaitingAuthorization() {
        IntegrationConnection b3 = connections.saveAndFlush(IntegrationConnection.builder()
            .user(owner).provider(IntegrationProvider.B3).label("B3")
            .status(ConnectionStatus.PENDING_AUTHORIZATION)
            .consentAcceptedAt(LocalDateTime.now()).build());
        em.clear();

        assertThat(nativeQuery("SELECT status FROM integration_connections WHERE id = :id", b3.getId()))
            .isEqualTo("PENDING_AUTHORIZATION");
        assertThat(nativeQuery("SELECT provider FROM integration_connections WHERE id = :id", b3.getId())).isEqualTo("B3");
    }

    @Test
    @DisplayName("findOwnedById: conexao de outro usuario nao e encontrada")
    void ownerScope() {
        User other = users.save(Fixtures.user("outro@primevest.test"));

        assertThat(connections.findOwnedById(binance.getId(), owner.getId())).isPresent();
        assertThat(connections.findOwnedById(binance.getId(), other.getId())).isEmpty();
        assertThat(connections.findAllOwned(other.getId())).isEmpty();
    }

    // ---------------------------------------------------------------- credencial

    @Test
    @DisplayName("Vault: grava cifrado, le de volta, e o banco nao tem o segredo em claro")
    void encryptedCredential() {
        vault.store(binance, new ApiKeyCredentials("chave-publica-a7K2", "segredo-muito-secreto"));
        em.flush();
        em.clear();

        byte[] stored = (byte[]) nativeQuery("SELECT ciphertext FROM integration_credentials WHERE connection_id = :id", binance.getId());
        assertThat(new String(stored, StandardCharsets.ISO_8859_1))
            .doesNotContain("segredo-muito-secreto").doesNotContain("chave-publica");
        assertThat(nativeQuery("SELECT fingerprint FROM integration_credentials WHERE connection_id = :id", binance.getId()))
            .isEqualTo("a7K2");

        ApiKeyCredentials readBack = (ApiKeyCredentials) vault.read(binance.getId());
        assertThat(readBack.apiKey()).isEqualTo("chave-publica-a7K2");
        assertThat(readBack.apiSecret()).isEqualTo("segredo-muito-secreto");
    }

    @Test
    @DisplayName("Vault: CPF da B3 grava cifrado na mesma tabela e volta como InvestorDocument (G2)")
    void encryptedCpf() {
        vault.store(binance, new com.primevest.domain.integration.credential.InvestorDocument("529.982.247-25"));
        em.flush();
        em.clear();

        byte[] stored = (byte[]) nativeQuery("SELECT ciphertext FROM integration_credentials WHERE connection_id = :id", binance.getId());
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("52998224725");
        assertThat(nativeQuery("SELECT fingerprint FROM integration_credentials WHERE connection_id = :id", binance.getId()))
            .isEqualTo("25");
        assertThat(vault.read(binance.getId()))
            .isEqualTo(new com.primevest.domain.integration.credential.InvestorDocument("52998224725"));
    }

    @Test
    @DisplayName("Trocar a chave regrava a mesma linha, com IV novo, e zera a validacao anterior")
    void keyChange() {
        IntegrationCredential first = vault.store(binance, new ApiKeyCredentials("chave-1-AAAA", "s1"));
        first.setValidatedAt(LocalDateTime.now());
        first.setPermissions("{\"enableReading\":true}");
        em.flush();
        byte[] ivBefore = first.getIv().clone();

        IntegrationCredential second = vault.store(binance, new ApiKeyCredentials("chave-2-BBBB", "s2"));
        em.flush();

        assertThat(credentials.count()).isEqualTo(1);
        assertThat(second.getIv()).isNotEqualTo(ivBefore);
        assertThat(second.getValidatedAt()).isNull();
        assertThat(second.getPermissions()).isNull();
        assertThat(((ApiKeyCredentials) vault.read(binance.getId())).apiSecret()).isEqualTo("s2");
    }

    @Test
    @DisplayName("Ciphertext copiado para outra conexao nao decifra (AAD = id da conexao)")
    void copiedDoesNotDecrypt() {
        vault.store(binance, new ApiKeyCredentials("chave-publica-a7K2", "segredo"));
        IntegrationConnection other = connections.saveAndFlush(Fixtures.binance(owner, "Binance 2"));
        vault.store(other, new ApiKeyCredentials("outra-chave-ZZZZ", "outro-segredo"));
        em.flush();

        em.createNativeQuery("""
            UPDATE integration_credentials SET
              ciphertext = (SELECT ciphertext FROM integration_credentials WHERE connection_id = :origem),
              iv         = (SELECT iv FROM integration_credentials WHERE connection_id = :origem)
            WHERE connection_id = :destino""")
            .setParameter("origem", binance.getId())
            .setParameter("destino", other.getId())
            .executeUpdate();
        em.clear();

        assertThatThrownBy(() -> vault.read(other.getId())).isInstanceOf(CredentialUnavailableException.class);
    }

    @Test
    @DisplayName("Revogar apaga a credencial de verdade")
    void revoke() {
        vault.store(binance, new ApiKeyCredentials("chave-publica-a7K2", "segredo"));
        em.flush();

        vault.erase(binance.getId());
        em.flush();

        assertThat(credentials.existsById(binance.getId())).isFalse();
        assertThatThrownBy(() -> vault.read(binance.getId())).isInstanceOf(CredentialUnavailableException.class);
    }

    @Test
    @DisplayName("ck_integration_credentials_iv: IV fora de 12 bytes e recusado pelo banco")
    void twelveByteIv() {
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
    void manualByDefault() {
        Transaction tx = transactions.saveAndFlush(Fixtures.buy(btcPosition, LocalDate.of(2024, 3, 10), "0.001", "500000"));

        assertThat(tx.getOrigin()).isEqualTo(TransactionOrigin.MANUAL);
        assertThat(nativeQuery("SELECT origin FROM transactions WHERE id = :id", tx.getId())).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("Importada grava origem, conexao e snapshot em JSONB consultavel")
    void importedStoresSnapshot() {
        Transaction tx = transactions.saveAndFlush(imported("binance:spot:BTCBRL:1000000001"));
        em.clear();

        assertThat(nativeQuery("SELECT source_snapshot->>'orderId' FROM transactions WHERE id = :id", tx.getId()))
            .isEqualTo("1000000001");
        Transaction readBack = transactions.findById(tx.getId()).orElseThrow();
        assertThat(readBack.getOrigin()).isEqualTo(TransactionOrigin.AUTO);
        assertThat(readBack.getConnection().getId()).isEqualTo(binance.getId());
        assertThat(readBack.getEditedAt()).isNull();
    }

    @Test
    @DisplayName("findImportedIds: devolve so os ids ja importados por esta conexao")
    void alreadyImportedIds() {
        transactions.saveAndFlush(imported("binance:spot:BTCBRL:1000000001"));
        IntegrationConnection other = connections.saveAndFlush(Fixtures.binance(owner, "Binance 2"));

        assertThat(transactions.findImportedIds(binance.getId(),
            List.of("binance:spot:BTCBRL:1000000001", "binance:spot:BTCBRL:1000000009")))
            .containsExactly("binance:spot:BTCBRL:1000000001");
        assertThat(transactions.findImportedIds(other.getId(), List.of("binance:spot:BTCBRL:1000000001"))).isEmpty();
    }

    @Test
    @DisplayName("uk_transactions_external: a mesma operacao nao entra duas vezes pela mesma conexao")
    void idempotencia() {
        transactions.saveAndFlush(imported("binance:spot:BTCBRL:1000000001"));

        assertThatThrownBy(() -> transactions.saveAndFlush(imported("binance:spot:BTCBRL:1000000001")))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_transactions_external");
    }

    @Test
    @DisplayName("ck_transactions_origin: AUTO sem external_id e recusada")
    void autoWithoutIdentifier() {
        Transaction autoWithoutId = imported("x");
        autoWithoutId.setExternalId(null);

        assertThatThrownBy(() -> transactions.saveAndFlush(autoWithoutId))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_transactions_origin");
    }

    @Test
    @DisplayName("ck_transactions_origin: MANUAL com external_id e recusada")
    void manualWithIdentifier() {
        Transaction manualComId = Fixtures.buy(btcPosition, LocalDate.of(2024, 3, 10), "0.001", "500000");
        manualComId.setExternalId("binance:spot:BTCBRL:1");

        assertThatThrownBy(() -> transactions.saveAndFlush(manualComId))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_transactions_origin");
    }

    // ---------------------------------------------------------------- exclusoes

    @Test
    @DisplayName("Exclusao de cripto: unica por conexao e consultada em lote pela sincronizacao")
    void exclusionList() {
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
    void cascade() {
        vault.store(binance, new ApiKeyCredentials("chave-publica-a7K2", "segredo"));
        cursors.save(new IntegrationCursor(new IntegrationCursorId(binance.getId(), "spot:BTCBRL"), "900000001", null));
        IntegrationRun run = runs.save(IntegrationRun.builder().connection(binance).triggerType(RunTrigger.CONNECTION).build());
        runItems.save(IntegrationRunItem.builder().run(run).externalId("binance:spot:ETHBTC:1")
            .outcome(RunItemOutcome.UNMAPPED).reason("sem valor em reais (R6)").raw("{}").build());
        exclusions.save(IntegrationCryptoExclusion.builder().connection(binance)
            .externalId("binance:spot:BTCBRL:1000000002").asset(btc).sourceSnapshot(SNAPSHOT).build());
        Transaction tx = transactions.save(imported("binance:spot:BTCBRL:1000000001"));
        em.flush();

        em.createNativeQuery("DELETE FROM integration_connections WHERE id = :id")
            .setParameter("id", binance.getId()).executeUpdate();
        em.clear();

        assertThat(credentials.count()).isZero();
        assertThat(cursors.count()).isZero();
        assertThat(runs.count()).isZero();
        assertThat(runItems.count()).isZero();
        assertThat(exclusions.count()).isZero();
        assertThat(nativeQuery("SELECT connection_id FROM transactions WHERE id = :id", tx.getId())).isNull();
        assertThat(nativeQuery("SELECT origin FROM transactions WHERE id = :id", tx.getId())).isEqualTo("AUTO");
    }

    @Test
    @DisplayName("Apagar o usuario apaga as conexoes dele")
    void deletedUser() {
        UUID id = binance.getId();
        em.createNativeQuery("DELETE FROM portfolios WHERE user_id = :id").setParameter("id", owner.getId()).executeUpdate();
        em.createNativeQuery("DELETE FROM users WHERE id = :id").setParameter("id", owner.getId()).executeUpdate();
        em.clear();

        assertThat(connections.existsById(id)).isFalse();
    }
}
